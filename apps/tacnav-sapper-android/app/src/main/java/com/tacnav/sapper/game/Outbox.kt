package com.tacnav.sapper.game

import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.domain.SapperSettings

/** A write kept until the server accepts it. Creates carry an idempotency key. */
sealed interface PendingWrite {
    val key: String
    val context: ActiveContext

    data class CreateMinefield(
        override val key: String,
        override val context: ActiveContext,
        val polygon: Geometry.Polygon,
        val iconId: Long?,
    ) : PendingWrite

    data class CreateNoMines(
        override val key: String,
        override val context: ActiveContext,
        val position: Coordinate,
        val accuracyMetres: Double,
        val iconId: Long?,
    ) : PendingWrite

    data class MarkField(
        override val key: String,
        override val context: ActiveContext,
        val fieldId: Long,
        val centre: Coordinate,
        val visibleTeamIds: List<Long>,
        val updatedAt: String,
    ) : PendingWrite

    data class ExpireField(
        override val key: String,
        override val context: ActiveContext,
        val fieldId: Long,
    ) : PendingWrite
}

/** Accepted writes per activation. */
data class Counters(
    val minefields: Int = 0,
    val noMinesMarkers: Int = 0,
)

/** Everything a restart must not lose. */
data class GameState(
    val counters: Counters = Counters(),
    val laying: Laying? = null,
    val clearProgress: List<ClearProgress> = emptyList(),
    val outbox: List<PendingWrite> = emptyList(),
)

object Limits {
    /** Null means unlimited. Pending writes count, so a retry cannot exceed the limit. */
    fun left(max: Int, accepted: Int, pending: Int): Int? =
        if (max == 0) null else (max - accepted - pending).coerceAtLeast(0)

    fun minefieldsLeft(settings: SapperSettings, state: GameState): Int? = left(
        settings.maxMinefields,
        state.counters.minefields,
        state.outbox.count { it is PendingWrite.CreateMinefield },
    )

    fun noMinesLeft(settings: SapperSettings, state: GameState): Int? = left(
        settings.maxNoMinesMarkers,
        state.counters.noMinesMarkers,
        state.outbox.count { it is PendingWrite.CreateNoMines },
    )

    fun allows(left: Int?): Boolean = left == null || left > 0
}

/** What the server said about one write. */
sealed interface WriteOutcome {
    data object Accepted : WriteOutcome
    data object Gone : WriteOutcome

    /** A newer edit came first; [refreshed] is the field as it is now, or null if it left the map. */
    data class Conflict(val refreshed: MapObject?) : WriteOutcome
    data class Rejected(val message: String) : WriteOutcome
    data object Failed : WriteOutcome
}

object Outbox {
    /** Applies one outcome. Returns the new state and a message to show, if any. */
    fun apply(
        state: GameState,
        write: PendingWrite,
        outcome: WriteOutcome,
    ): Pair<GameState, String?> {
        val rest = state.outbox.filterNot { it.key == write.key }
        return when (outcome) {
            WriteOutcome.Accepted -> state.copy(outbox = rest, counters = counted(state.counters, write)) to null
            WriteOutcome.Gone -> state.copy(outbox = rest) to null
            WriteOutcome.Failed -> state to null
            is WriteOutcome.Rejected -> state.copy(outbox = rest) to outcome.message
            is WriteOutcome.Conflict -> {
                val again = (write as? PendingWrite.MarkField)?.let { retryMark(it, outcome.refreshed) }
                val outbox = state.outbox.map { if (it.key == write.key) again else it }.filterNotNull()
                state.copy(outbox = outbox) to null
            }
        }
    }

    /**
     * Recomputes a mark from the field as it is now. Nothing is left to do when the
     * field is gone, visible to everyone, or already visible to the team.
     */
    fun retryMark(write: PendingWrite.MarkField, refreshed: MapObject?): PendingWrite.MarkField? {
        val field = refreshed ?: return null
        val team = write.context.teamId
        if (field.visibleTeamIds.isEmpty() || team in field.visibleTeamIds) return null
        return write.copy(
            visibleTeamIds = MarkHold.union(field.visibleTeamIds, team),
            updatedAt = field.updatedAt,
        )
    }

    private fun counted(counters: Counters, write: PendingWrite): Counters = when (write) {
        is PendingWrite.CreateMinefield -> counters.copy(minefields = counters.minefields + 1)
        is PendingWrite.CreateNoMines -> counters.copy(noMinesMarkers = counters.noMinesMarkers + 1)
        else -> counters
    }

    const val RETRY_MILLIS = 5_000L
}
