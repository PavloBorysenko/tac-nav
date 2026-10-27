package com.tacnav.mortar.session

import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.CalibrationMath
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
    private val onChange: (List<ShotRecord>) -> Unit = {},
) {
    private val lock = Any()
    private val shots = linkedMapOf<String, ShotRecord>()
    private var ownPositionObjectId: Long? = null
    private var directionLineObjectId: Long? = null

    fun currentShots(): List<ShotRecord> = synchronized(lock) { shots.values.toList() }

    // Every phase change is persisted before the next network side effect so a
    // process death between search and delivery resumes instead of rerolling.
    private fun store(shot: ShotRecord) {
        val all = synchronized(lock) {
            shots[shot.shotId] = shot
            shots.values.toList()
        }
        onChange(all)
    }

    private fun stored(shotId: String): ShotRecord? = synchronized(lock) { shots[shotId] }

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
                store(shot)
                return shot
            }
            else -> error(gate.reason)
        }
    }

    fun restore(records: List<ShotRecord>) {
        synchronized(lock) {
            shots.clear()
            records.forEach { shots[it.shotId] = it }
        }
    }

    fun clear() {
        synchronized(lock) {
            shots.clear()
            ownPositionObjectId = null
            directionLineObjectId = null
        }
        onChange(emptyList())
    }

    suspend fun resolveDue(
        elapsedMillis: Long,
        nowEpochMillis: Long,
        settings: GameplaySettings,
        context: ActiveContext,
        online: Boolean,
    ) {
        currentShots().filter { shot ->
            shot.phase == ShotPhase.IN_FLIGHT &&
                (elapsedMillis >= shot.dueAtElapsedMillis || nowEpochMillis >= shot.dueAtEpochMillis)
        }.forEach { shot ->
            store(shot.copy(phase = ShotPhase.DUE))
        }
        if (!online) return
        currentShots().filter { it.phase == ShotPhase.DUE || retryable(it.phase) }.forEach { shot ->
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
            store(current)
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
                current = current.copy(phase = ShotPhase.IMPACT_CREATED, failure = null)
                store(current)
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
                current = current.copy(phase = ShotPhase.MESSAGES_SENT, failure = null)
                store(current)
            }
            if (current.phase == ShotPhase.MESSAGES_SENT) {
                store(current.copy(phase = ShotPhase.COMPLETED))
            }
        } catch (error: Exception) {
            val latest = stored(shot.shotId) ?: current
            store(
                latest.copy(
                    phase = if (latest.phase == ShotPhase.DUE) ShotPhase.FAILED else latest.phase,
                    failure = error.message ?: "Resolution failed.",
                ),
            )
        }
    }

    suspend fun publishOwnPosition(
        shot: ShotRecord,
        settings: GameplaySettings,
        context: ActiveContext,
    ) {
        val request = MortarMark.ownPositionRequest(shot, settings, context) ?: return
        val existing = ownPositionObjectId
        if (existing != null) {
            val updated = runCatching { geo.update(existing, request) }.getOrNull()
            if (updated != null) {
                ownPositionObjectId = updated.objectId
                return
            }
        }
        ownPositionObjectId = geo.create(request).objectId
    }

    suspend fun publishDirectionLine(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        accuracyMetres: Double,
        settings: GameplaySettings,
        context: ActiveContext,
    ): Double {
        val knownBearing = Geodesy.normalizeBearing(bearingDegrees)
        val request = MortarMark.directionLineRequest(
            latitude,
            longitude,
            knownBearing,
            accuracyMetres,
            settings,
            context,
        )
        val existing = directionLineObjectId
        if (existing != null) {
            val updated = runCatching { geo.update(existing, request) }.getOrNull()
            if (updated != null) {
                directionLineObjectId = updated.objectId
                return knownBearing
            }
        }
        directionLineObjectId = geo.create(request).objectId
        return knownBearing
    }
}

object MortarMark {
    const val CLIENT_OBJECT_ID = "mortar-position"

    fun ownPositionRequest(
        shot: ShotRecord,
        settings: GameplaySettings,
        context: ActiveContext,
    ): CreateObjectRequest? {
        if (!settings.markOwnPositionOnShot) {
            return null
        }
        return CreateObjectRequest(
            context = context,
            clientObjectId = CLIENT_OBJECT_ID,
            kind = "marker",
            geometry = Geometry.Marker(shot.snapshot.latitude, shot.snapshot.longitude),
            title = "${settings.mortarName} position",
            iconId = null,
            visibleTeamIds = emptyList(),
            ttlSeconds = GameplaySettings.OWN_POSITION_TTL_SECONDS,
            positionAccuracyMetres = shot.snapshot.accuracyMetres,
        )
    }

    const val DIRECTION_CLIENT_OBJECT_ID = "mortar-calibration-direction"
    const val DIRECTION_LINE_TTL_SECONDS = 30L

    fun directionLineRequest(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        accuracyMetres: Double,
        settings: GameplaySettings,
        context: ActiveContext,
    ): CreateObjectRequest = CreateObjectRequest(
        context = context,
        clientObjectId = DIRECTION_CLIENT_OBJECT_ID,
        kind = "polyline",
        geometry = CalibrationMath.directionLine(latitude, longitude, bearingDegrees),
        title = "${settings.mortarName} direction",
        iconId = null,
        visibleTeamIds = listOf(context.teamId),
        ttlSeconds = DIRECTION_LINE_TTL_SECONDS,
        positionAccuracyMetres = accuracyMetres,
    )
}
