package com.tacnav.sapper

import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.Shapes
import com.tacnav.sapper.game.Counters
import com.tacnav.sapper.game.GameState
import com.tacnav.sapper.game.Limits
import com.tacnav.sapper.game.Outbox
import com.tacnav.sapper.game.PendingWrite
import com.tacnav.sapper.game.WriteOutcome
import com.tacnav.sapper.session.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxTest {
    private val context = ActiveContext(4, Fields.BLUE)
    private val minefield = PendingWrite.CreateMinefield(
        "lay-1",
        context,
        Shapes.rectangle(Fields.HOME, 10.0, 10.0, 0.0),
        null,
    )
    private val mark = PendingWrite.MarkField("mark-1", context, 9, Fields.HOME, listOf(Fields.RED, Fields.BLUE), "t1")

    @Test
    fun failedCreateStaysQueuedWithTheSameKey() {
        val state = GameState(outbox = listOf(minefield))
        val (after, _) = Outbox.apply(state, minefield, WriteOutcome.Failed)
        assertEquals(listOf(minefield), after.outbox)
        assertEquals(0, after.counters.minefields)
    }

    @Test
    fun countersRiseOnlyOnAcceptance() {
        val noMines = PendingWrite.CreateNoMines("tap-1", context, Fields.HOME, 3.0, null)
        var state = GameState(outbox = listOf(minefield, noMines))
        state = Outbox.apply(state, minefield, WriteOutcome.Accepted).first
        state = Outbox.apply(state, noMines, WriteOutcome.Accepted).first
        assertEquals(Counters(1, 1), state.counters)
        assertTrue(state.outbox.isEmpty())
    }

    @Test
    fun goneCountsAsDone() {
        val expire = PendingWrite.ExpireField("clear-1", context, 9)
        val (after, message) = Outbox.apply(GameState(outbox = listOf(expire)), expire, WriteOutcome.Gone)
        assertTrue(after.outbox.isEmpty())
        assertNull(message)
    }

    @Test
    fun conflictRecomputesTheMarkFromTheCurrentField() {
        val refreshed = Fields.square(9, 0.0, 0.0, visible = listOf(Fields.RED, 11L)).copy(updatedAt = "t2")
        val (after, _) = Outbox.apply(GameState(outbox = listOf(mark)), mark, WriteOutcome.Conflict(refreshed))
        val again = after.outbox.single() as PendingWrite.MarkField
        assertEquals(listOf(Fields.RED, 11L, Fields.BLUE), again.visibleTeamIds)
        assertEquals("t2", again.updatedAt)
        assertEquals(mark.key, again.key)
    }

    @Test
    fun conflictIsDoneWhenTheTeamAlreadySeesTheFieldOrItIsGone() {
        val seen = Fields.square(9, 0.0, 0.0, visible = listOf(Fields.BLUE))
        val public = Fields.square(9, 0.0, 0.0, visible = emptyList())
        assertTrue(Outbox.apply(GameState(outbox = listOf(mark)), mark, WriteOutcome.Conflict(seen)).first.outbox.isEmpty())
        assertTrue(Outbox.apply(GameState(outbox = listOf(mark)), mark, WriteOutcome.Conflict(public)).first.outbox.isEmpty())
        assertTrue(Outbox.apply(GameState(outbox = listOf(mark)), mark, WriteOutcome.Conflict(null)).first.outbox.isEmpty())
    }

    @Test
    fun rejectedWriteIsDroppedWithItsMessage() {
        val (after, message) = Outbox.apply(GameState(outbox = listOf(minefield)), minefield, WriteOutcome.Rejected("No"))
        assertTrue(after.outbox.isEmpty())
        assertEquals("No", message)
    }

    @Test
    fun limitsCountPendingWritesAndZeroMeansUnlimited() {
        val settings = SapperSettings(maxMinefields = 3, maxNoMinesMarkers = 0)
        val state = GameState(counters = Counters(minefields = 2), outbox = listOf(minefield))
        assertEquals(0, Limits.minefieldsLeft(settings, state))
        assertFalse(Limits.allows(Limits.minefieldsLeft(settings, state)))
        assertNull(Limits.noMinesLeft(settings, state))
        assertTrue(Limits.allows(Limits.noMinesLeft(settings, state)))
        assertEquals(10, Limits.noMinesLeft(SapperSettings(), GameState()))
    }

    @Test
    fun toneLengthAndTwoBeepPattern() {
        val beep = Tone.samples(Tone.BEEP_MILLIS)
        val gap = Tone.samples(Tone.GAP_MILLIS)
        assertEquals(6_615, beep)
        assertEquals(beep, Tone.pattern(1).size)
        val two = Tone.pattern(2)
        assertEquals(2 * beep + gap, two.size)
        assertTrue((beep until beep + gap).all { two[it].toInt() == 0 })
        assertTrue(two.slice(0 until beep).any { it > 1_000 })
    }
}
