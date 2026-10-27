package com.tacnav.mortar.ui

import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.network.MailboxDelivery
import java.util.Locale

/**
 * Local troubleshooting text. It receives only shot and delivery records, never
 * the activation, so a bearer credential cannot reach the screen.
 */
object Diagnostics {
    fun shotLines(shot: ShotRecord, nowEpochMillis: Long): List<String> = buildList {
        val s = shot.snapshot
        add("Shot ${shot.shotId.take(8)} · ${shot.phase.label()}")
        add(
            "Origin ${s.latitude.coord()}, ${s.longitude.coord()} ±${s.accuracyMetres.int()} m, " +
                "fix age ${"%.1f".format(Locale.US, s.ageSeconds)} s",
        )
        add("Heading ${s.trueHeadingDegrees.int()}°, elevation ${"%.1f".format(Locale.US, s.elevationDegrees)}°, range ${shot.rangeMetres.int()} m")
        add("Impact ${shot.impactLatitude.coord()}, ${shot.impactLongitude.coord()}")
        if (shot.phase == ShotPhase.IN_FLIGHT) {
            add("Lands in ${secondsLeft(shot, nowEpochMillis)} s")
        }
        if (shot.facts.isEmpty() && shot.phase.ordinal >= ShotPhase.SEARCHED.ordinal) {
            add("Search found no objects.")
        }
        shot.facts.forEach { fact ->
            val outcome = if (fact.objectId in shot.hits) "hit" else "miss"
            add(
                "Object ${fact.objectId}: ${fact.distanceMetres.int()} m, " +
                    "P ${"%.2f".format(Locale.US, fact.probability)}, " +
                    "roll ${"%.3f".format(Locale.US, fact.roll)} → $outcome",
            )
        }
        add("Delivery: ${deliveryState(shot)}")
        shot.failure?.let { add("Last error: $it") }
    }

    fun deliveryLine(delivery: MailboxDelivery): String =
        "Received ${delivery.eventType} #${delivery.deliveryReference} for object " +
            "${delivery.recipientObjectId} from team ${delivery.sourceTeamId}"

    fun secondsLeft(shot: ShotRecord, nowEpochMillis: Long): Long =
        ((shot.dueAtEpochMillis - nowEpochMillis + 999) / 1_000).coerceAtLeast(0)

    private fun deliveryState(shot: ShotRecord): String = when (shot.phase) {
        ShotPhase.IN_FLIGHT, ShotPhase.DUE, ShotPhase.FAILED -> "not started"
        ShotPhase.SEARCHED -> "waiting for impact marker"
        ShotPhase.IMPACT_CREATED ->
            if (shot.hits.isEmpty()) "no hits to send" else "pending for ${shot.hits.size} object(s)"
        ShotPhase.MESSAGES_SENT, ShotPhase.COMPLETED ->
            if (shot.hits.isEmpty()) "no hits to send" else "sent to ${shot.hits.size} object(s)"
    }

    private fun ShotPhase.label(): String = name.lowercase(Locale.US).replace('_', ' ')

    private fun Double.coord(): String = "%.6f".format(Locale.US, this)

    private fun Double.int(): Int = toInt()
}
