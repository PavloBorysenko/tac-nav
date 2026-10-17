package com.tacnav.mortar.session

import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.domain.GeometryMath
import com.tacnav.mortar.domain.HitFact
import com.tacnav.mortar.domain.HitMath
import com.tacnav.mortar.domain.SensorGate
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.domain.ValidShot
import com.tacnav.mortar.network.AddressedEventRequest
import com.tacnav.mortar.network.Coordinate
import com.tacnav.mortar.network.CreateObjectRequest
import com.tacnav.mortar.network.DeviceMailboxApi
import com.tacnav.mortar.network.GeoObjectApi
import com.tacnav.mortar.network.Geometry
import com.tacnav.mortar.network.GeometrySearchRequest
import java.security.SecureRandom
import java.util.UUID

class FiringEngine(
    private val geo: GeoObjectApi,
    private val mailbox: DeviceMailboxApi,
    private val random: () -> Double = { SecureRandom().nextDouble() },
    private val uuid: () -> String = { UUID.randomUUID().toString() },
) {
    private val shots = linkedMapOf<String, ShotRecord>()

    fun currentShots(): List<ShotRecord> = shots.values.toList()

    fun accept(
        snapshot: SensorSnapshot,
        settings: GameplaySettings,
        elapsedMillis: Long,
        nowEpochMillis: Long,
    ): ShotRecord {
        when (val gate = SensorGate.accept(snapshot, settings)) {
            is ValidShot -> {
                val impact = Geodesy.destination(
                    snapshot.latitude,
                    snapshot.longitude,
                    snapshot.trueHeadingDegrees,
                    gate.rangeMetres,
                )
                val shot = ShotRecord(
                    shotId = uuid(),
                    snapshot = snapshot,
                    rangeMetres = gate.rangeMetres,
                    impactLatitude = impact.latitude,
                    impactLongitude = impact.longitude,
                    dueAtElapsedMillis = elapsedMillis + settings.flightDurationSeconds * 1_000,
                    dueAtEpochMillis = nowEpochMillis + settings.flightDurationSeconds * 1_000,
                )
                shots[shot.shotId] = shot
                return shot
            }
            else -> error(gate.reason)
        }
    }

    fun restore(records: List<ShotRecord>) {
        shots.clear()
        records.forEach { shots[it.shotId] = it }
    }

    suspend fun resolveDue(
        elapsedMillis: Long,
        nowEpochMillis: Long,
        settings: GameplaySettings,
        context: ActiveContext,
        online: Boolean,
    ) {
        shots.values.filter { shot ->
            shot.phase == ShotPhase.IN_FLIGHT &&
                (elapsedMillis >= shot.dueAtElapsedMillis || nowEpochMillis >= shot.dueAtEpochMillis)
        }.forEach { shot ->
            shots[shot.shotId] = shot.copy(phase = ShotPhase.DUE)
        }
        if (!online) return
        shots.values.filter { it.phase == ShotPhase.DUE || retryable(it.phase) }.forEach { shot ->
            resolveOne(shot, settings, context)
        }
    }

    private fun retryable(phase: ShotPhase): Boolean =
        phase == ShotPhase.SEARCHED || phase == ShotPhase.IMPACT_CREATED || phase == ShotPhase.FAILED

    private suspend fun searchHits(
        shot: ShotRecord,
        settings: GameplaySettings,
        context: ActiveContext,
    ): ShotRecord {
        val results = geo.search(
            GeometrySearchRequest(
                context,
                Geometry.Circle(
                    shot.impactLatitude,
                    shot.impactLongitude,
                    settings.damageRadiusMetres,
                ),
            ),
        )
        val rolls = shot.rolls.toMutableMap()
        val facts = mutableListOf<HitFact>()
        val hits = mutableListOf<Long>()
        val impact = Coordinate(shot.impactLatitude, shot.impactLongitude)
        results.forEach { result ->
            val distance = GeometryMath.nearestDistanceMetres(impact, result.geometry)
            val probability = HitMath.probability(distance, settings)
            val roll = rolls.getOrPut(result.objectId, random)
            val fact = HitFact(result.objectId, distance, probability, roll)
            facts += fact
            if (roll <= probability) hits += result.objectId
        }
        return shot.copy(
            phase = ShotPhase.SEARCHED,
            rolls = rolls,
            facts = facts,
            hits = hits,
            failure = null,
        )
    }

    private suspend fun resolveOne(
        shot: ShotRecord,
        settings: GameplaySettings,
        context: ActiveContext,
    ) {
        var current = shot
        if (current.phase == ShotPhase.DUE || current.phase == ShotPhase.FAILED) {
            current = try {
                searchHits(current, settings, context)
            } catch (error: Exception) {
                current.copy(
                    phase = ShotPhase.SEARCHED,
                    failure = error.message ?: "Search failed.",
                )
            }
            shots[current.shotId] = current
        }
        try {
            if (current.phase == ShotPhase.SEARCHED) {
                geo.create(
                    CreateObjectRequest(
                        context = context,
                        clientObjectId = current.shotId,
                        kind = "marker",
                        geometry = Geometry.Marker(current.impactLatitude, current.impactLongitude),
                        title = "${settings.mortarName} impact",
                        iconId = settings.impactIconId,
                        visibleTeamIds = emptyList(),
                        ttlSeconds = settings.impactTtlSeconds,
                        positionAccuracyMetres = current.snapshot.accuracyMetres,
                    ),
                )
                current = current.copy(phase = ShotPhase.IMPACT_CREATED)
                shots[current.shotId] = current
            }
            if (current.phase == ShotPhase.IMPACT_CREATED) {
                if (current.hits.isNotEmpty()) {
                    mailbox.submit(
                        AddressedEventRequest(
                            context = context,
                            clientEventId = current.shotId,
                            type = "hit",
                            recipientObjectIds = current.hits,
                            opaquePayload = mapOf(
                                "shot_id" to current.shotId,
                                "impact" to mapOf(
                                    "lat" to current.impactLatitude,
                                    "lng" to current.impactLongitude,
                                ),
                                "range_m" to current.rangeMetres,
                                "source" to mapOf(
                                    "map_id" to context.mapId,
                                    "team_id" to context.teamId,
                                ),
                                "objects" to current.facts.filter { it.objectId in current.hits }.map { fact ->
                                    mapOf(
                                        "id" to fact.objectId,
                                        "distance_m" to fact.distanceMetres,
                                        "probability" to fact.probability,
                                        "roll" to fact.roll,
                                    )
                                },
                            ),
                        ),
                    )
                }
                current = current.copy(phase = ShotPhase.MESSAGES_SENT)
                shots[current.shotId] = current
            }
            if (current.phase == ShotPhase.MESSAGES_SENT) {
                shots[current.shotId] = current.copy(phase = ShotPhase.COMPLETED)
            }
        } catch (error: Exception) {
            val latest = shots[shot.shotId] ?: current
            shots[latest.shotId] = latest.copy(
                phase = if (latest.phase == ShotPhase.DUE) ShotPhase.FAILED else latest.phase,
                failure = error.message ?: "Resolution failed.",
            )
        }
    }
}
