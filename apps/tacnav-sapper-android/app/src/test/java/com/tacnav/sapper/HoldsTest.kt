package com.tacnav.sapper

import com.tacnav.sapper.domain.Geodesy
import com.tacnav.sapper.domain.Plane
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.game.ClearHold
import com.tacnav.sapper.game.ClearingMachine
import com.tacnav.sapper.game.LayHold
import com.tacnav.sapper.game.LayingMachine
import com.tacnav.sapper.game.MarkHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldsTest {
    private val settings = SapperSettings()
    private val field = Fields.square(1, 0.0, 0.0)

    @Test
    fun releasingMarkAfter20Of30SecondsKeepsTheProgress() {
        var hold = MarkHold().press(field, 0)
        repeat(20) { second ->
            hold = hold.advance((second + 1) * 1_000L, field, true, settings).first
        }
        assertEquals(20_000L, hold.heldMillis(field.id))
        hold = hold.release()
        hold = hold.press(field, 30_000)
        assertEquals(20_000L, hold.heldMillis(field.id))
        var marked: com.tacnav.sapper.domain.MapObject? = null
        repeat(10) { second ->
            val (next, done) = hold.advance(31_000L + second * 1_000L, field, true, settings)
            hold = next
            if (done != null) marked = done
        }
        assertEquals(field, marked)
        assertEquals(0L, hold.heldMillis(field.id))
    }

    @Test
    fun losingGpsOrTheFieldStopsTheMarkAndKeepsTheProgress() {
        var hold = MarkHold().press(field, 0)
        hold = hold.advance(10_000, field, true, settings).first
        hold = hold.advance(11_000, field, false, settings).first
        assertFalse(hold.pressed)
        assertEquals(10_000L, hold.heldMillis(field.id))
        hold = hold.press(field, 12_000).advance(15_000, field, true, settings).first
        hold = hold.advance(16_000, null, true, settings).first
        assertFalse(hold.pressed)
        assertEquals(13_000L, hold.heldMillis(field.id))
    }

    @Test
    fun markProgressIsKeptPerField() {
        val other = Fields.square(2, 0.0, 30.0)
        var hold = MarkHold().press(field, 0).advance(5_000, field, true, settings).first.release()
        hold = hold.press(other, 6_000).advance(8_000, other, true, settings).first.release()
        assertEquals(5_000L, hold.heldMillis(field.id))
        assertEquals(2_000L, hold.heldMillis(other.id))
        assertEquals(2_000L, hold.forget(listOf(field.id)).heldMillis(other.id))
        assertEquals(0L, hold.forget(listOf(field.id)).heldMillis(field.id))
    }

    @Test
    fun completedMarkKeepsEveryListedTeam() {
        var hold = MarkHold().press(field, 0)
        val (_, marked) = hold.advance(30_000, field, true, settings)
        assertEquals(field, marked)
        assertEquals(listOf(Fields.RED, Fields.BLUE), MarkHold.union(field.visibleTeamIds, Fields.BLUE))
        assertEquals(listOf(Fields.RED), MarkHold.union(listOf(Fields.RED), Fields.RED))
        hold = hold.release()
        assertFalse(hold.pressed)
    }

    @Test
    fun firstPressRecordsTheStartFixAndHeading() {
        val (hold, refusal) = LayingMachine.press(LayHold(), Fields.fix(), true, 40.0, 0, true) { "lay-1" }
        assertNull(refusal)
        val laying = hold.laying!!
        assertEquals("lay-1", laying.id)
        assertEquals(40.0, laying.headingDegrees, 0.0)
        assertEquals(Fields.HOME.latitude, laying.start.latitude, 0.0)
    }

    @Test
    fun releaseAndResumeContinueTheProgress() {
        var hold = LayingMachine.press(LayHold(), Fields.fix(), true, 40.0, 0, true) { "lay-1" }.first
        hold = LayingMachine.advance(hold, 120_000, Fields.fix(), true, settings).first
        hold = LayingMachine.release(hold)
        assertEquals(120_000L, hold.laying!!.heldMillis)
        // Later, at the same spot, with a different heading: the first heading is kept.
        hold = LayingMachine.press(hold, Fields.fix(), true, 200.0, 500_000, true) { "other" }.first
        hold = LayingMachine.advance(hold, 530_000, Fields.fix(), true, settings).first
        val resumed = hold.laying!!
        assertEquals("lay-1", resumed.id)
        assertEquals(40.0, resumed.headingDegrees, 0.0)
        assertEquals(150_000L, resumed.heldMillis)
    }

    @Test
    fun walkingAwayLocksLayingUntilReset() {
        var hold = LayingMachine.press(LayHold(), Fields.fix(accuracy = 0.5), true, 0.0, 0, true) { "lay-1" }.first
        val away = Fields.fix(Fields.at(90.0, 12.0), accuracy = 0.5)
        hold = LayingMachine.advance(hold, 1_000, away, true, settings).first
        assertTrue(hold.laying!!.locked)
        assertFalse(hold.pressed)
        val (again, refusal) = LayingMachine.press(hold, away, true, 0.0, 2_000, true) { "x" }
        assertEquals(LayingMachine.MOVED_AWAY, refusal)
        assertFalse(again.pressed)
        assertNull(LayingMachine.reset().laying)
    }

    @Test
    fun accuracyIsAllowedForBeforeLocking() {
        val laying = LayingMachine.press(LayHold(), Fields.fix(accuracy = 4.0), true, 0.0, 0, true) { "l" }
            .first.laying!!
        // 12 m away, but 4 + 4 m of accuracy leaves 4 m, within half of 10 m.
        assertFalse(LayingMachine.movedAway(laying, Fields.fix(Fields.at(90.0, 12.0), accuracy = 4.0), settings))
    }

    @Test
    fun completedLayingGivesARectangleOnTheStartFix() {
        var hold = LayingMachine.press(LayHold(), Fields.fix(), true, 40.0, 0, true) { "lay-1" }.first
        val (after, done) = LayingMachine.advance(hold, 180_000, Fields.fix(), true, settings)
        assertNull(after.laying)
        assertNotNull(done)
        val polygon = LayingMachine.geometry(done!!, settings)
        assertEquals(0.0, Geodesy.distanceMetres(Plane.centre(polygon), Fields.HOME), 0.05)
        assertEquals(10.0, Geodesy.distanceMetres(polygon.points[0], polygon.points[1]), 0.05)
        hold = after
        assertFalse(hold.pressed)
    }

    @Test
    fun noMinefieldsLeftRefusesLaying() {
        val (hold, refusal) = LayingMachine.press(LayHold(), Fields.fix(), true, 0.0, 0, false) { "l" }
        assertEquals(LayingMachine.NO_MINEFIELDS_LEFT, refusal)
        assertNull(hold.laying)
    }

    @Test
    fun clearingTargetsTheNearestCentreWhenInsideSeveralFields() {
        val near = Fields.square(1, 90.0, 2.0)
        val far = Fields.square(2, 270.0, 4.0)
        assertEquals(1L, ClearingMachine.target(listOf(far, near), Fields.HOME)?.id)
        assertNull(ClearingMachine.target(emptyList(), Fields.HOME))
    }

    @Test
    fun clearingProgressIsKeptPerField() {
        val first = Fields.square(1, 0.0, 0.0)
        val second = Fields.square(2, 0.0, 0.0)
        var hold = ClearingMachine.press(ClearHold(), first, true, 0).first
        hold = ClearingMachine.advance(hold, 90_000, first, true, settings).first
        hold = ClearingMachine.release(hold)
        hold = ClearingMachine.press(hold, second, true, 100_000).first
        hold = ClearingMachine.advance(hold, 130_000, second, true, settings).first
        hold = ClearingMachine.release(hold)
        hold = ClearingMachine.press(hold, first, true, 200_000).first
        hold = ClearingMachine.advance(hold, 210_000, first, true, settings).first
        assertEquals(100_000L, hold.progress[1]!!.heldMillis)
        assertEquals(30_000L, hold.progress[2]!!.heldMillis)
        assertEquals(100_000f / 180_000f, ClearingMachine.fraction(hold, 1, settings), 1e-4f)
    }

    @Test
    fun completedClearingReturnsTheFieldAndDropsItsProgress() {
        var hold = ClearingMachine.press(ClearHold(), field, true, 0).first
        val (after, cleared) = ClearingMachine.advance(hold, 180_000, field, true, settings)
        assertEquals(field, cleared)
        assertTrue(after.progress.isEmpty())
        hold = after
        assertFalse(hold.pressed)
    }

    @Test
    fun vanishedFieldLosesItsProgressButADistantOneKeepsIt() {
        val here = Fields.square(1, 0.0, 0.0)
        val distant = Fields.square(2, 0.0, 60.0)
        var hold = ClearingMachine.press(ClearHold(), here, true, 0).first
        hold = ClearingMachine.advance(hold, 50_000, here, true, settings).first
        hold = ClearingMachine.release(hold)
        hold = ClearingMachine.press(hold, distant, true, 60_000).first
        hold = ClearingMachine.advance(hold, 70_000, distant, true, settings).first
        hold = ClearingMachine.release(hold)
        val searched = ClearingMachine.afterSearch(hold, emptyList(), Fields.HOME, 5.0)
        assertNull(searched.progress[1])
        assertNotNull(searched.progress[2])
    }

    @Test
    fun noTargetRefusesClearing() {
        assertEquals(ClearingMachine.NO_FIELD, ClearingMachine.press(ClearHold(), null, true, 0).second)
    }
}
