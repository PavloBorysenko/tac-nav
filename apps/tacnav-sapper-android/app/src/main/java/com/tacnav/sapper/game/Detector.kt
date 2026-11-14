package com.tacnav.sapper.game

import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geodesy
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.domain.Plane
import com.tacnav.sapper.domain.SapperSettings
import kotlin.math.max

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val epochMillis: Long,
) {
    val coordinate: Coordinate get() = Coordinate(latitude, longitude)

    /** Writes need a recent fix that is no worse than the configured limit. */
    fun usable(nowEpochMillis: Long, limitMetres: Double): Boolean =
        nowEpochMillis - epochMillis <= MAX_FIX_AGE_MILLIS && accuracyMetres <= limitMetres

    companion object {
        const val MAX_FIX_AGE_MILLIS = 15_000L
    }
}

/** Where and when the last successful search ran. */
data class SearchMark(val coordinate: Coordinate, val epochMillis: Long)

/** Search often while walking, rarely while standing. */
object SearchPolicy {
    const val MOVING_MILLIS = 5_000L
    const val STILL_MILLIS = 30_000L

    fun due(last: SearchMark?, fix: Fix, nowEpochMillis: Long): Boolean {
        if (last == null) return true
        val moved = Geodesy.distanceMetres(last.coordinate, fix.coordinate) > fix.accuracyMetres
        val interval = if (moved) MOVING_MILLIS else STILL_MILLIS
        return nowEpochMillis - last.epochMillis >= interval
    }
}

enum class DetectorLevel { NONE, FAR, NEAR, INSIDE }

data class DetectorStep(
    val level: DetectorLevel,
    /** Number of short signals to play now: 0, 1, or 2. */
    val signals: Int,
    /** Field the phone may mark for its team, if any. */
    val markTarget: MapObject?,
    /** True when the phone is inside at least one kept field. */
    val insideAny: Boolean,
)

/**
 * Detector levels with hysteresis. A level is entered with strict limits and
 * kept with relaxed ones, so compass and GPS jitter do not repeat signals.
 */
object DetectorMachine {
    const val SECTOR_MARGIN_DEGREES = 10.0
    const val INSIDE_EXIT_METRES = 1.0

    fun step(
        previous: DetectorLevel,
        fields: List<MapObject>,
        position: Coordinate,
        headingDegrees: Double?,
        settings: SapperSettings,
        teamId: Long,
    ): DetectorStep {
        val radius = settings.detectRadiusMetres
        val half = settings.detectSectorDegrees / 2
        val margin = max(1.0, 0.05 * radius)
        var enter = DetectorLevel.NONE
        var hold = DetectorLevel.NONE
        val inside = mutableListOf<Pair<MapObject, Double>>()

        fields.forEach { field ->
            val view = Plane.view(position, field.geometry)
            if (view.inside) inside += field to view.centreDistanceMetres
            val angle = if (view.inside) 0.0 else headingDegrees?.let { heading ->
                view.bearingDegrees?.let { Geodesy.angleBetween(it, heading) }
            }
            val fieldEnter = when {
                view.inside -> DetectorLevel.INSIDE
                angle == null || angle > half -> DetectorLevel.NONE
                view.distanceMetres <= radius / 2 -> DetectorLevel.NEAR
                view.distanceMetres <= radius -> DetectorLevel.FAR
                else -> DetectorLevel.NONE
            }
            val fieldHold = when {
                view.inside || view.distanceMetres <= INSIDE_EXIT_METRES -> DetectorLevel.INSIDE
                angle == null || angle > half + SECTOR_MARGIN_DEGREES -> DetectorLevel.NONE
                view.distanceMetres <= radius / 2 + margin -> DetectorLevel.NEAR
                view.distanceMetres <= radius + margin -> DetectorLevel.FAR
                else -> DetectorLevel.NONE
            }
            if (fieldEnter > enter) enter = fieldEnter
            if (fieldHold > hold) hold = fieldHold
        }

        val kept = minOf(previous, hold)
        val level = maxOf(enter, kept)
        val signals = when {
            level <= previous -> 0
            level == DetectorLevel.FAR -> 1
            else -> 2
        }
        val markTarget = if (level == DetectorLevel.INSIDE) {
            inside.filter { (field, _) -> markable(field, teamId) }
                .minByOrNull { it.second }
                ?.first
        } else {
            null
        }
        return DetectorStep(level, signals, markTarget, inside.isNotEmpty())
    }

    /** Another team's or a neutral field that the phone's team cannot see yet. */
    fun markable(field: MapObject, teamId: Long): Boolean =
        field.ownerTeamId != teamId &&
            field.visibleTeamIds.isNotEmpty() &&
            teamId !in field.visibleTeamIds
}
