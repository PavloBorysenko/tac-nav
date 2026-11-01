package com.tacnav.beacon.game

import com.tacnav.beacon.domain.HintArea
import com.tacnav.beacon.domain.Geodesy
import com.tacnav.beacon.domain.ObjectSettings

/**
 * Every game rule, as pure transitions over [GameState].
 *
 * Nothing here touches Android, the network, or a real clock, so the whole
 * life cycle can be driven in a test. The server never decides any of this.
 */
class ObjectMachine(
    private val random: () -> Double,
    private val newKey: () -> String,
) {
    private fun rules(state: GameState, env: Environment): ObjectSettings =
        state.session?.settings ?: env.settings

    fun canActivate(state: GameState, settings: ObjectSettings, nowEpochMillis: Long): Boolean =
        state.life == LifeState.WAITING &&
            state.lastFix?.usable(settings.maxLocationAccuracyMetres, nowEpochMillis) == true

    fun activate(
        state: GameState,
        env: Environment,
        clock: Clock,
        automatic: Boolean = false,
    ): Transition {
        if (state.life != LifeState.WAITING) return Transition(state)
        val fix = state.lastFix
        if (!canActivate(state, env.settings, clock.epochMillis) || fix == null) {
            return Transition(state, message = GPS_REFUSED)
        }
        val snapshot = SessionSnapshot(env.settings, env.context, env.headingDegrees ?: 0.0)
        val deploying = state.copy(
            life = LifeState.DEPLOYING,
            armed = true,
            automaticDeployment = automatic,
            session = snapshot,
            lastSession = snapshot,
            deployedMillis = 0,
            sessionMillis = 0,
            anchor = fix,
            position = fix,
            positionDirty = true,
            awayCount = 0,
            stillMillis = 0,
            stillOrigin = if (automatic) state.stillOrigin else null,
            publishedLife = null,
            objectWrittenAtMillis = null,
        )
        return if (env.settings.activationSeconds <= 0) {
            Transition(enterActive(deploying))
        } else {
            Transition(deploying)
        }
    }

    private fun enterActive(state: GameState): GameState {
        val settings = state.session?.settings ?: return state
        val centre = if (settings.randomAreaEnabled) rollArea(state, settings) else null
        val immediateReset = settings.downtimeResetMillis == 0L
        return state.copy(
            life = LifeState.ACTIVE,
            sessionMillis = 0,
            activeStreakMillis = 0,
            awayCount = 0,
            areaCentre = centre,
            areaDirty = centre != null,
            downtimeRemainingMillis = if (state.downtimeStarted && immediateReset) {
                settings.downtimeMillis
            } else {
                state.downtimeRemainingMillis
            },
        )
    }

    private fun rollArea(state: GameState, settings: ObjectSettings) = state.position?.let { fix ->
        HintArea.centre(fix.coordinate, settings.randomAreaRadiusMetres, random(), random())
    }

    /** The button works from Deploying or Active and stops automatic activation. */
    fun deactivate(state: GameState, env: Environment): Transition {
        if (state.life != LifeState.DEPLOYING && state.life != LifeState.ACTIVE) {
            return Transition(state)
        }
        return Transition(leave(state, env).copy(armed = false))
    }

    fun arm(state: GameState): Transition = Transition(
        if (state.life == LifeState.WAITING) {
            state.copy(armed = true, stillOrigin = null, stillMillis = 0)
        } else {
            state
        },
    )

    /** Back to Waiting: the object and area leave the map, and downtime can start. */
    private fun leave(state: GameState, env: Environment): GameState {
        val limit = rules(state, env).downtimeMillis
        val wasActive = state.life == LifeState.ACTIVE
        val startsDowntime = wasActive && limit != null
        return state.copy(
            life = LifeState.WAITING,
            pendingDeletes = state.pendingDeletes + listOfNotNull(state.objectId, state.areaId),
            objectId = null,
            areaId = null,
            areaCentre = null,
            areaDirty = false,
            session = null,
            automaticDeployment = false,
            anchor = null,
            stillOrigin = null,
            stillMillis = 0,
            awayCount = 0,
            activeStreakMillis = 0,
            publishedLife = null,
            objectWrittenAtMillis = null,
            areaWrittenAtMillis = null,
            positionDirty = false,
            downtimeStarted = state.downtimeStarted || startsDowntime,
            downtimeRemainingMillis = if (startsDowntime) {
                state.downtimeRemainingMillis ?: limit
            } else {
                state.downtimeRemainingMillis
            },
        )
    }

    fun onFix(state: GameState, env: Environment, fix: Fix, clock: Clock): Transition {
        val settings = rules(state, env)
        val updated = state.copy(lastFix = fix)
        val usable = fix.usable(settings.maxLocationAccuracyMetres, clock.epochMillis)
        return when (state.life) {
            LifeState.DEPLOYING -> onDeployingFix(updated, env, settings, fix, usable)
            LifeState.ACTIVE -> if (usable) {
                onActiveFix(updated, env, settings, fix)
            } else {
                Transition(updated)
            }
            LifeState.WAITING -> Transition(
                if (usable) followStillness(updated, settings, fix) else updated,
            )
            LifeState.DESTROYED -> Transition(updated)
        }
    }

    private fun onDeployingFix(
        state: GameState,
        env: Environment,
        settings: ObjectSettings,
        fix: Fix,
        usable: Boolean,
    ): Transition {
        if (fix.accuracyMetres > settings.maxLocationAccuracyMetres) {
            return Transition(leave(state, env), message = GPS_LOST)
        }
        if (!usable) return Transition(state)
        val origin = state.stillOrigin
        if (state.automaticDeployment && origin != null &&
            origin.distanceTo(fix) > settings.stillRadiusMetres
        ) {
            return Transition(leave(state, env), message = MOVED_DURING_DEPLOYMENT)
        }
        val anchor = state.anchor
        val better = anchor == null || fix.accuracyMetres < anchor.accuracyMetres
        return Transition(
            trackPosition(state, fix).copy(anchor = if (better) fix else anchor),
        )
    }

    private fun onActiveFix(
        state: GameState,
        env: Environment,
        settings: ObjectSettings,
        fix: Fix,
    ): Transition {
        val anchor = state.anchor
        var away = 0
        if (settings.autoDeactivateMetres > 0 && anchor != null) {
            val margin = anchor.distanceTo(fix) - fix.accuracyMetres - anchor.accuracyMetres
            away = if (margin > settings.autoDeactivateMetres) state.awayCount + 1 else 0
        }
        if (away >= AWAY_FIXES) {
            return Transition(leave(state, env), message = MOVED_AWAY)
        }
        val tracked = trackPosition(state, fix).copy(awayCount = away)
        val centre = tracked.areaCentre
        val position = tracked.position
        if (settings.randomAreaEnabled && centre != null && position != null &&
            Geodesy.distanceMetres(centre, position.coordinate) > settings.randomAreaRadiusMetres
        ) {
            return Transition(
                tracked.copy(areaCentre = rollArea(tracked, settings), areaDirty = true),
            )
        }
        return Transition(tracked)
    }

    private fun trackPosition(state: GameState, fix: Fix): GameState {
        val position = state.position
        return if (position == null || position.distanceTo(fix) > fix.accuracyMetres) {
            state.copy(position = fix, positionDirty = true)
        } else {
            state
        }
    }

    private fun followStillness(
        state: GameState,
        settings: ObjectSettings,
        fix: Fix,
    ): GameState {
        if (!settings.autoActivateEnabled || !state.armed) return state
        val origin = state.stillOrigin
        return if (origin == null || origin.distanceTo(fix) > settings.stillRadiusMetres) {
            state.copy(stillOrigin = fix, stillMillis = 0)
        } else {
            state
        }
    }

    fun tick(state: GameState, env: Environment, clock: Clock): Transition {
        val delta = state.lastElapsedMillis
            ?.let { (clock.elapsedMillis - it).coerceAtLeast(0) }
            ?: 0
        val current = state.copy(
            lastElapsedMillis = clock.elapsedMillis,
            lastActivityEpochMillis = clock.epochMillis,
        )
        val advanced = when (current.life) {
            LifeState.ACTIVE -> current.copy(sessionMillis = current.sessionMillis + delta)
            LifeState.DEPLOYING -> advanceDeployment(current, delta)
            LifeState.WAITING -> return advanceWaiting(current, env, clock, delta)
            LifeState.DESTROYED -> current
        }
        return applyDowntime(advanced, env, clock, delta)
    }

    private fun advanceDeployment(state: GameState, delta: Long): GameState {
        val settings = state.session?.settings ?: return state
        val deployed = state.deployedMillis + delta
        return if (deployed >= settings.activationSeconds * 1_000) {
            enterActive(state.copy(deployedMillis = deployed))
        } else {
            state.copy(deployedMillis = deployed)
        }
    }

    private fun advanceWaiting(
        state: GameState,
        env: Environment,
        clock: Clock,
        delta: Long,
    ): Transition {
        val settings = env.settings
        val fix = state.lastFix
        val counting = settings.autoActivateEnabled && state.armed && state.stillOrigin != null &&
            fix?.usable(settings.maxLocationAccuracyMetres, clock.epochMillis) == true
        val waited = if (counting) state.stillMillis + delta else state.stillMillis
        val settled = state.copy(stillMillis = waited)
        val reached = counting && waited >= settings.autoActivateSeconds * 1_000
        val next = if (reached) activate(settled, env, clock, automatic = true).state else settled
        return applyDowntime(next, env, clock, delta)
    }

    private fun applyDowntime(
        state: GameState,
        env: Environment,
        clock: Clock,
        delta: Long,
    ): Transition {
        val settings = rules(state, env)
        val limit = settings.downtimeMillis
            ?: return Transition(state.copy(downtimeRemainingMillis = null))
        return when (state.life) {
            LifeState.DESTROYED -> Transition(state)
            LifeState.ACTIVE -> {
                val streak = state.activeStreakMillis + delta
                val reset = settings.downtimeResetMillis ?: limit
                Transition(
                    state.copy(
                        activeStreakMillis = streak,
                        downtimeRemainingMillis = if (state.downtimeStarted && streak >= reset) {
                            limit
                        } else {
                            state.downtimeRemainingMillis
                        },
                    ),
                )
            }
            else -> if (state.downtimeStarted) {
                countDown(state, env, clock, limit, delta)
            } else {
                Transition(state)
            }
        }
    }

    private fun countDown(
        state: GameState,
        env: Environment,
        clock: Clock,
        limit: Long,
        delta: Long,
    ): Transition {
        val before = (state.downtimeRemainingMillis ?: limit).coerceAtMost(limit)
        val after = before - delta
        if (after <= 0) {
            return destroy(
                state.copy(downtimeRemainingMillis = 0),
                env,
                clock,
                listOf(SignalKind.DESTROYED),
            )
        }
        val warned = WARNING_MARKS.any { mark -> before > mark && after <= mark }
        return Transition(
            state.copy(downtimeRemainingMillis = after),
            signals = if (warned) listOf(SignalKind.WARNING) else emptyList(),
        )
    }

    fun onDelivery(
        state: GameState,
        env: Environment,
        type: String,
        eventId: String,
        recipientObjectId: Long,
        clock: Clock,
    ): Transition {
        if (type != HIT_EVENT || eventId in state.appliedEvents) return Transition(state)
        if (recipientObjectId !in state.lifeObjectIds) return Transition(state)
        if (state.life == LifeState.DESTROYED) return Transition(state)
        val counted = state.copy(hits = state.hits + 1, appliedEvents = state.appliedEvents + eventId)
        val limit = rules(state, env).hitsToDestroy
        return if (limit > 0 && counted.hits >= limit) {
            destroy(counted, env, clock, listOf(SignalKind.HIT, SignalKind.DESTROYED))
        } else {
            Transition(counted, signals = listOf(SignalKind.HIT))
        }
    }

    /**
     * Destroyed takes effect here, before anything reaches the network.
     *
     * The position is fixed now, so a wreck sent later still lands where the
     * object died. The live objects go only after the wreck is accepted.
     */
    private fun destroy(
        state: GameState,
        env: Environment,
        clock: Clock,
        signals: List<SignalKind>,
    ): Transition {
        val settings = rules(state, env)
        val snapshot = state.session
            ?: state.lastSession
            ?: SessionSnapshot(settings, env.context, env.headingDegrees ?: 0.0)
        val current = state.lastFix?.takeIf {
            it.usable(settings.maxLocationAccuracyMetres, clock.epochMillis)
        }
        val fix = current ?: state.position ?: state.lastFix
        return Transition(
            state.copy(
                life = LifeState.DESTROYED,
                wreck = fix?.let {
                    WreckPlan(state.wreckKey, it.latitude, it.longitude, it.accuracyMetres, snapshot)
                },
                pendingDeletes = state.pendingDeletes + listOfNotNull(state.objectId, state.areaId),
                objectId = null,
                areaId = null,
                areaCentre = null,
                areaDirty = false,
                session = null,
                automaticDeployment = false,
                anchor = null,
                stillOrigin = null,
                stillMillis = 0,
                publishedLife = null,
                objectWrittenAtMillis = null,
                areaWrittenAtMillis = null,
            ),
            signals = signals,
        )
    }

    /** The same fresh start for the protected reset and for clearing a phone. */
    fun reset(state: GameState): Transition = Transition(
        GameState(
            wreckKey = newKey(),
            wreck = state.wreck,
            pendingDeletes = state.pendingDeletes + listOfNotNull(state.objectId, state.areaId),
            lastSession = state.session ?: state.lastSession,
            lastFix = state.lastFix,
        ),
    )

    /**
     * Picks up after the process or the phone was off.
     *
     * The session continues, but the gap counts as downtime, otherwise switching
     * the phone off would be a free way around the allowance.
     */
    fun restore(state: GameState, env: Environment, clock: Clock): Transition {
        val gap = (clock.epochMillis - state.lastActivityEpochMillis)
            .takeIf { state.lastActivityEpochMillis > 0 }
            ?.coerceAtLeast(0)
            ?: 0
        val resumed = state.copy(
            lastElapsedMillis = clock.elapsedMillis,
            lastActivityEpochMillis = clock.epochMillis,
        )
        val settled = when (resumed.life) {
            LifeState.ACTIVE -> resumed.copy(
                sessionMillis = resumed.sessionMillis + gap,
                activeStreakMillis = 0,
            )
            LifeState.DEPLOYING -> leave(resumed, env)
            else -> resumed
        }
        if (settled.life == LifeState.DESTROYED) return Transition(settled)
        val limit = rules(settled, env).downtimeMillis ?: return Transition(settled)
        if (settled.life == LifeState.ACTIVE) {
            return countDown(settled.copy(downtimeStarted = true), env, clock, limit, gap)
        }
        return if (settled.downtimeStarted) {
            countDown(settled, env, clock, limit, gap)
        } else {
            Transition(settled)
        }
    }

    fun objectWritten(state: GameState, id: Long, life: LifeState, nowElapsed: Long): GameState {
        val known = state.copy(lifeObjectIds = state.lifeObjectIds + id)
        val live = state.life == LifeState.DEPLOYING || state.life == LifeState.ACTIVE
        if (!live || state.session == null) {
            return if (id == state.objectId) known else known.copy(pendingDeletes = known.pendingDeletes + id)
        }
        return known.copy(
            objectId = id,
            publishedLife = life,
            positionDirty = false,
            objectWrittenAtMillis = nowElapsed,
        )
    }

    fun areaWritten(state: GameState, id: Long, nowElapsed: Long): GameState {
        if (state.life != LifeState.ACTIVE || state.areaCentre == null) {
            return state.copy(pendingDeletes = state.pendingDeletes + id)
        }
        return state.copy(areaId = id, areaDirty = false, areaWrittenAtMillis = nowElapsed)
    }

    fun objectGone(state: GameState): GameState = state.copy(objectId = null, publishedLife = null)

    fun areaGone(state: GameState): GameState = state.copy(areaId = null)

    fun wreckAccepted(state: GameState): GameState = state.copy(wreck = null)

    fun deleted(state: GameState, id: Long): GameState =
        state.copy(pendingDeletes = state.pendingDeletes - id)

    fun writeFailed(state: GameState, nowElapsed: Long): GameState =
        state.copy(retryAtMillis = nowElapsed + RETRY_MILLIS)

    companion object {
        const val HIT_EVENT = "hit"
        const val RETRY_MILLIS = 5_000L
        const val GPS_REFUSED = "GPS accuracy is worse than the limit."
        const val GPS_LOST = "GPS accuracy worsened, so deployment was cancelled."
        const val MOVED_DURING_DEPLOYMENT = "The phone moved, so deployment was cancelled."
        const val MOVED_AWAY = "The phone moved too far, so the object was deactivated."
        private const val AWAY_FIXES = 3
        private val WARNING_MARKS = listOf(300_000L, 60_000L)
    }
}
