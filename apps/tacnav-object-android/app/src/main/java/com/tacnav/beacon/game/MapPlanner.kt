package com.tacnav.beacon.game

import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.Geometry
import com.tacnav.beacon.domain.ObjectVisibility
import com.tacnav.beacon.domain.Shapes
import com.tacnav.beacon.network.ObjectRequest

sealed interface MapOp {
    data class WriteObject(val id: Long?, val request: ObjectRequest, val life: LifeState) : MapOp
    data class WriteArea(val id: Long?, val request: ObjectRequest) : MapOp
    data class CreateWreck(val request: ObjectRequest) : MapOp
    data class Delete(val id: Long, val mapId: Long, val teamId: Long) : MapOp
}

/**
 * Decides the next map write from the state alone.
 *
 * Each write rebuilds the picture of every audience on the map, so a still
 * object writes only often enough to keep its lease.
 */
object MapPlanner {
    const val LEASE_SECONDS = 60L
    const val MOVING_WRITE_MILLIS = 5_000L
    const val STILL_WRITE_MILLIS = 30_000L
    const val DEPLOYING_TEXT = "Deploying"

    fun next(state: GameState, nowElapsed: Long): MapOp? {
        if (nowElapsed < state.retryAtMillis) return null
        state.wreck?.let { return MapOp.CreateWreck(wreckRequest(it)) }
        state.pendingDeletes.firstOrNull()?.let { id ->
            val context = (state.session ?: state.lastSession)?.context ?: return null
            return MapOp.Delete(id, context.mapId, context.teamId)
        }
        val session = state.session ?: return null
        if (state.life != LifeState.DEPLOYING && state.life != LifeState.ACTIVE) return null
        val position = state.position ?: return null
        if (objectDue(state, nowElapsed)) {
            return MapOp.WriteObject(
                state.objectId,
                objectRequest(state, session, position),
                state.life,
            )
        }
        val centre = state.areaCentre
        if (state.life == LifeState.ACTIVE && centre != null && areaDue(state)) {
            val settings = session.settings
            return MapOp.WriteArea(
                state.areaId,
                ObjectRequest(
                    context = session.context,
                    geometry = Geometry.Circle(
                        centre.latitude,
                        centre.longitude,
                        settings.randomAreaRadiusMetres,
                    ),
                    title = "${settings.objectName} area",
                    description = sessionText(state),
                    iconId = null,
                    visibleTeamIds = emptyList(),
                    ttlSeconds = LEASE_SECONDS,
                    positionAccuracyMetres = null,
                    searchable = false,
                ),
            )
        }
        return null
    }

    private fun objectDue(state: GameState, nowElapsed: Long): Boolean {
        val written = state.objectWrittenAtMillis
        return when {
            state.objectId == null || written == null -> true
            state.publishedLife != state.life -> true
            nowElapsed - written >= STILL_WRITE_MILLIS -> true
            else -> state.positionDirty && nowElapsed - written >= MOVING_WRITE_MILLIS
        }
    }

    private fun areaDue(state: GameState): Boolean {
        val written = state.areaWrittenAtMillis
        return state.areaId == null || state.areaDirty || written == null ||
            written < (state.objectWrittenAtMillis ?: 0)
    }

    private fun objectRequest(
        state: GameState,
        session: SessionSnapshot,
        position: Fix,
    ): ObjectRequest {
        val settings = session.settings
        val hidden = state.life == LifeState.DEPLOYING ||
            settings.randomAreaEnabled ||
            settings.visibility == ObjectVisibility.TEAM
        val status = if (state.life == LifeState.DEPLOYING) DEPLOYING_TEXT else sessionText(state)
        return ObjectRequest(
            context = session.context,
            geometry = Shapes.geometry(settings, position.coordinate, session.headingDegrees),
            title = settings.objectName,
            description = listOf(settings.description, status)
                .filter(String::isNotBlank)
                .joinToString("\n"),
            iconId = settings.iconId,
            visibleTeamIds = if (hidden) listOf(session.context.teamId) else emptyList(),
            ttlSeconds = LEASE_SECONDS,
            positionAccuracyMetres = position.accuracyMetres,
            searchable = true,
        )
    }

    private fun wreckRequest(wreck: WreckPlan): ObjectRequest {
        val snapshot = wreck.snapshot
        val settings = snapshot.settings
        return ObjectRequest(
            context = snapshot.context,
            geometry = Shapes.geometry(
                settings,
                Coordinate(wreck.latitude, wreck.longitude),
                snapshot.headingDegrees,
            ),
            title = "${settings.objectName} destroyed",
            description = settings.description,
            iconId = settings.destroyedIconId,
            visibleTeamIds = emptyList(),
            ttlSeconds = 0,
            positionAccuracyMetres = wreck.accuracyMetres,
            searchable = false,
            idempotencyKey = wreck.key,
        )
    }

    private fun sessionText(state: GameState): String =
        "Active ${state.sessionMillis / 60_000} min"
}
