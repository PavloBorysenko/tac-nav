package com.tacnav.sapper.game

import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geodesy
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.domain.Plane
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.Shapes

/**
 * Mark minefield: hold time accumulated per field. Releasing the button, losing
 * GPS, or stepping out of the field stops the hold and keeps the time. It is
 * persisted, so it survives a restart.
 */
data class MarkHold(
    val progress: Map<Long, Long> = emptyMap(),
    val targetId: Long? = null,
    val lastTickMillis: Long? = null,
) {
    val pressed: Boolean get() = lastTickMillis != null

    fun press(field: MapObject, nowMillis: Long): MarkHold = copy(targetId = field.id, lastTickMillis = nowMillis)

    fun release(): MarkHold = copy(targetId = null, lastTickMillis = null)

    fun heldMillis(fieldId: Long?): Long = fieldId?.let { progress[it] } ?: 0

    /** Returns the new hold and, when the hold completes, the marked field. */
    fun advance(
        nowMillis: Long,
        target: MapObject?,
        usable: Boolean,
        settings: SapperSettings,
    ): Pair<MarkHold, MapObject?> {
        val last = lastTickMillis ?: return this to null
        if (target == null || target.id != targetId || !usable) return release() to null
        val held = heldMillis(target.id) + (nowMillis - last).coerceAtLeast(0)
        if (held >= settings.markHoldSeconds * 1_000) return MarkHold(progress - target.id) to target
        return copy(progress = progress + (target.id to held), lastTickMillis = nowMillis) to null
    }

    /** Drops progress for fields the team can already see. */
    fun forget(fieldIds: Collection<Long>): MarkHold = copy(progress = progress - fieldIds.toSet())

    fun fraction(fieldId: Long?, settings: SapperSettings): Float =
        (heldMillis(fieldId).toFloat() / (settings.markHoldSeconds * 1_000)).coerceIn(0f, 1f)

    companion object {
        /** The new visibility keeps every team already listed. */
        fun union(visible: List<Long>, teamId: Long): List<Long> = (visible + teamId).distinct()
    }
}

/** A laying in progress. It is persisted, so it survives a restart. */
data class Laying(
    val id: String,
    val start: Fix,
    val headingDegrees: Double,
    val heldMillis: Long = 0,
    val locked: Boolean = false,
)

data class LayHold(
    val laying: Laying? = null,
    val lastTickMillis: Long? = null,
) {
    val pressed: Boolean get() = lastTickMillis != null
}

object LayingMachine {
    /** Moved farther than half the shorter side, after allowing for both fixes' accuracy. */
    fun movedAway(laying: Laying, fix: Fix, settings: SapperSettings): Boolean {
        val distance = Geodesy.distanceMetres(laying.start.coordinate, fix.coordinate)
        return distance - (laying.start.accuracyMetres + fix.accuracyMetres) >
            Shapes.shorterSide(settings) / 2
    }

    /** Returns the new hold, or a reason the hold cannot start. */
    fun press(
        hold: LayHold,
        fix: Fix?,
        usable: Boolean,
        headingDegrees: Double?,
        nowMillis: Long,
        minefieldsLeft: Boolean,
        newId: () -> String,
    ): Pair<LayHold, String?> {
        if (!minefieldsLeft) return hold to NO_MINEFIELDS_LEFT
        if (fix == null || !usable) return hold to GPS_REQUIRED
        val current = hold.laying
        if (current != null && current.locked) return hold to MOVED_AWAY
        val laying = current ?: run {
            val heading = headingDegrees ?: return hold to COMPASS_REQUIRED
            Laying(newId(), fix, heading)
        }
        return LayHold(laying, nowMillis) to null
    }

    fun release(hold: LayHold): LayHold = hold.copy(lastTickMillis = null)

    fun reset(): LayHold = LayHold()

    /** Returns the new hold and, when the hold completes, the finished laying. */
    fun advance(
        hold: LayHold,
        nowMillis: Long,
        fix: Fix?,
        usable: Boolean,
        settings: SapperSettings,
    ): Pair<LayHold, Laying?> {
        val laying = hold.laying ?: return hold to null
        if (fix != null && !laying.locked && movedAway(laying, fix, settings)) {
            return LayHold(laying.copy(locked = true), null) to null
        }
        val last = hold.lastTickMillis ?: return hold to null
        if (!usable || laying.locked) return hold.copy(lastTickMillis = null) to null
        val held = laying.heldMillis + (nowMillis - last).coerceAtLeast(0)
        if (held >= settings.layHoldSeconds * 1_000) return LayHold() to laying.copy(heldMillis = held)
        return LayHold(laying.copy(heldMillis = held), nowMillis) to null
    }

    fun geometry(laying: Laying, settings: SapperSettings): Geometry.Polygon = Shapes.rectangle(
        laying.start.coordinate,
        settings.minefieldLengthMetres,
        settings.minefieldWidthMetres,
        laying.headingDegrees,
    )

    fun fraction(laying: Laying?, settings: SapperSettings): Float =
        ((laying?.heldMillis ?: 0).toFloat() / (settings.layHoldSeconds * 1_000)).coerceIn(0f, 1f)

    const val NO_MINEFIELDS_LEFT = "No minefields left for this activation."
    const val GPS_REQUIRED = "Waiting for an accurate GPS fix."
    const val MOVED_AWAY = "You moved away from the start point. Reset laying to start again."
    const val COMPASS_REQUIRED = "Waiting for the compass."
}

/** Clearing progress for one field. It is persisted, so it survives a restart. */
data class ClearProgress(
    val fieldId: Long,
    val geometry: Geometry,
    val heldMillis: Long,
)

data class ClearHold(
    val progress: Map<Long, ClearProgress> = emptyMap(),
    val targetId: Long? = null,
    val lastTickMillis: Long? = null,
) {
    val pressed: Boolean get() = lastTickMillis != null
}

object ClearingMachine {
    /** The field the phone stands in, the one whose centre is nearest, or else the nearest field. */
    fun target(fields: List<MapObject>, position: Coordinate): MapObject? {
        val views = fields.map { it to Plane.view(position, it.geometry) }
        val inside = views.filter { it.second.inside }
        return if (inside.isNotEmpty()) {
            inside.minByOrNull { it.second.centreDistanceMetres }?.first
        } else {
            views.minByOrNull { it.second.distanceMetres }?.first
        }
    }

    fun press(
        hold: ClearHold,
        target: MapObject?,
        usable: Boolean,
        nowMillis: Long,
    ): Pair<ClearHold, String?> {
        if (target == null) return hold to NO_FIELD
        if (!usable) return hold to LayingMachine.GPS_REQUIRED
        return hold.copy(targetId = target.id, lastTickMillis = nowMillis) to null
    }

    fun release(hold: ClearHold): ClearHold = hold.copy(targetId = null, lastTickMillis = null)

    /** Returns the new hold and, when the hold completes, the cleared field. */
    fun advance(
        hold: ClearHold,
        nowMillis: Long,
        target: MapObject?,
        usable: Boolean,
        settings: SapperSettings,
    ): Pair<ClearHold, MapObject?> {
        val last = hold.lastTickMillis ?: return hold to null
        if (target == null || target.id != hold.targetId || !usable) return release(hold) to null
        val before = hold.progress[target.id]?.heldMillis ?: 0
        val held = before + (nowMillis - last).coerceAtLeast(0)
        if (held >= settings.clearHoldSeconds * 1_000) {
            return ClearHold(hold.progress - target.id) to target
        }
        val entry = ClearProgress(target.id, target.geometry, held)
        return hold.copy(progress = hold.progress + (target.id to entry), lastTickMillis = nowMillis) to null
    }

    /**
     * Drops progress for fields that a successful search should have found but did
     * not, because they left the map. Fields outside the searched circle are kept.
     */
    fun afterSearch(
        hold: ClearHold,
        results: List<MapObject>,
        centre: Coordinate,
        radiusMetres: Double,
    ): ClearHold {
        val found = results.map(MapObject::id).toSet()
        val kept = hold.progress.filter { (id, entry) ->
            id in found || Plane.view(centre, entry.geometry).distanceMetres > radiusMetres
        }
        return hold.copy(progress = kept)
    }

    fun forget(hold: ClearHold, fieldId: Long): ClearHold = hold.copy(progress = hold.progress - fieldId)

    fun fraction(hold: ClearHold, fieldId: Long?, settings: SapperSettings): Float {
        val held = fieldId?.let { hold.progress[it]?.heldMillis } ?: 0
        return (held.toFloat() / (settings.clearHoldSeconds * 1_000)).coerceIn(0f, 1f)
    }

    const val NO_FIELD = "No minefield here."
}
