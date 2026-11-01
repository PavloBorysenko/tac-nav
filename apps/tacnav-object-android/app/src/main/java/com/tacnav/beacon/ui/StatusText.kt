package com.tacnav.beacon.ui

import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.LifeState
import java.util.Locale

/** The words the screen and the notification show. */
object StatusText {
    fun clock(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0) + 999) / 1_000
        val hours = seconds / 3_600
        val minutes = seconds % 3_600 / 60
        val rest = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, rest)
        }
    }

    fun life(state: GameState): String = when (state.life) {
        LifeState.WAITING -> "Waiting"
        LifeState.DEPLOYING -> "Deploying"
        LifeState.ACTIVE -> "Active"
        LifeState.DESTROYED -> "Destroyed"
    }

    fun hits(state: GameState, settings: ObjectSettings): String {
        val limit = (state.session?.settings ?: settings).hitsToDestroy
        return if (limit > 0) "${state.hits} of $limit" else "${state.hits}"
    }

    /** Time left, or null when no countdown has started. */
    fun downtime(state: GameState, settings: ObjectSettings): String? {
        val active = state.session?.settings ?: settings
        val limit = active.downtimeMillis ?: return null
        if (!state.downtimeStarted || state.life == LifeState.DESTROYED) return null
        val left = clock(state.downtimeRemainingMillis ?: limit)
        return if (state.life == LifeState.ACTIVE) {
            "Downtime left $left"
        } else {
            "Time left to reactivate $left"
        }
    }

    fun fullReset(state: GameState, settings: ObjectSettings): String? {
        val active = state.session?.settings ?: settings
        val reset = active.downtimeResetMillis ?: return null
        if (!state.downtimeStarted || state.life != LifeState.ACTIVE) return null
        return "Full reset in ${clock(reset - state.activeStreakMillis)}"
    }

    fun summary(state: GameState, settings: ObjectSettings): String = buildList {
        add(life(state))
        if (state.life == LifeState.ACTIVE) add(clock(state.sessionMillis))
        downtime(state, settings)?.let(::add)
    }.joinToString(" · ")
}
