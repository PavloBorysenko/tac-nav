package com.tacnav.beacon.game

import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.Geodesy
import com.tacnav.beacon.domain.ObjectSettings

enum class LifeState {
    WAITING,
    DEPLOYING,
    ACTIVE,
    DESTROYED,
}

enum class SignalKind {
    HIT,
    WARNING,
    DESTROYED,
}

/** Both clocks: elapsed time drives live timers, wall time survives a reboot. */
data class Clock(
    val elapsedMillis: Long,
    val epochMillis: Long,
)

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val epochMillis: Long,
) {
    val coordinate: Coordinate
        get() = Coordinate(latitude, longitude)

    fun usable(limitMetres: Double, nowEpochMillis: Long): Boolean =
        accuracyMetres <= limitMetres && nowEpochMillis - epochMillis <= MAX_AGE_MILLIS

    fun distanceTo(other: Fix): Double =
        Geodesy.distanceMetres(coordinate, other.coordinate)

    companion object {
        const val MAX_AGE_MILLIS = 15_000L
    }
}

/** Settings and context frozen when deployment starts. */
data class SessionSnapshot(
    val settings: ObjectSettings,
    val context: ActiveContext,
    val headingDegrees: Double,
)

data class WreckPlan(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val snapshot: SessionSnapshot,
)

/** What the machine needs from outside on every input. */
data class Environment(
    val settings: ObjectSettings,
    val context: ActiveContext,
    val headingDegrees: Double?,
)

data class GameState(
    val wreckKey: String,
    val life: LifeState = LifeState.WAITING,
    val armed: Boolean = true,
    val automaticDeployment: Boolean = false,
    val hits: Int = 0,
    val appliedEvents: Set<String> = emptySet(),
    val lifeObjectIds: Set<Long> = emptySet(),
    val objectId: Long? = null,
    val areaId: Long? = null,
    val wreck: WreckPlan? = null,
    val pendingDeletes: Set<Long> = emptySet(),
    val session: SessionSnapshot? = null,
    val lastSession: SessionSnapshot? = null,
    val deployedMillis: Long = 0,
    val sessionMillis: Long = 0,
    val activeStreakMillis: Long = 0,
    val anchor: Fix? = null,
    val position: Fix? = null,
    val lastFix: Fix? = null,
    val areaCentre: Coordinate? = null,
    val stillOrigin: Fix? = null,
    val stillMillis: Long = 0,
    val awayCount: Int = 0,
    val downtimeStarted: Boolean = false,
    val downtimeRemainingMillis: Long? = null,
    val lastActivityEpochMillis: Long = 0,
    // Runtime only: lost on purpose, so a restored process writes at once.
    val lastElapsedMillis: Long? = null,
    val positionDirty: Boolean = false,
    val areaDirty: Boolean = false,
    val publishedLife: LifeState? = null,
    val objectWrittenAtMillis: Long? = null,
    val areaWrittenAtMillis: Long? = null,
    val retryAtMillis: Long = 0,
) {
    /** Fields whose loss would change the game, so they are saved at once. */
    fun durableKey(): List<Any?> = listOf(
        wreckKey,
        life,
        armed,
        hits,
        appliedEvents,
        lifeObjectIds,
        objectId,
        areaId,
        wreck,
        pendingDeletes,
        session,
        areaCentre,
        downtimeStarted,
    )
}

data class Transition(
    val state: GameState,
    val signals: List<SignalKind> = emptyList(),
    val message: String? = null,
)
