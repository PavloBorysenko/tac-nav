package com.tacnav.sapper

import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.game.DetectorLevel
import com.tacnav.sapper.game.DetectorMachine
import com.tacnav.sapper.game.Inside
import com.tacnav.sapper.game.SearchMark
import com.tacnav.sapper.game.SearchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {
    private val settings = SapperSettings()

    private fun step(
        previous: DetectorLevel,
        fields: List<com.tacnav.sapper.domain.MapObject>,
        heading: Double?,
        position: com.tacnav.sapper.domain.Coordinate = Fields.HOME,
        team: Long = Fields.BLUE,
    ) = DetectorMachine.step(previous, fields, position, heading, settings, team)

    @Test
    fun stillPhoneSearchesEvery30Seconds() {
        val fix = Fields.fix(epoch = 100_000)
        val last = SearchMark(Fields.HOME, 80_000)
        assertFalse(SearchPolicy.due(last, fix, 100_000))
        assertTrue(SearchPolicy.due(last, fix, 110_000))
        assertTrue(SearchPolicy.due(null, fix, 100_000))
    }

    @Test
    fun walkingPhoneSearchesEvery5Seconds() {
        val last = SearchMark(Fields.HOME, 100_000)
        val moved = Fields.fix(Fields.at(0.0, 8.0), accuracy = 3.0, epoch = 105_000)
        assertTrue(SearchPolicy.due(last, moved, 105_000))
        assertFalse(SearchPolicy.due(last, moved, 104_000))
    }

    @Test
    fun sweepingAcrossAFieldSignalsOnceEachTime() {
        // Field edge 8 metres away to the north.
        val fields = listOf(Fields.square(1, 0.0, 13.0))
        var level = DetectorLevel.NONE
        val signals = mutableListOf<Int>()
        listOf(120.0, 30.0, 0.0, 330.0, 200.0, 120.0, 20.0, 0.0).forEach { heading ->
            val result = step(level, fields, heading)
            level = result.level
            signals += result.signals
        }
        assertEquals(listOf(0, 1, 0, 0, 0, 0, 1, 0), signals)
    }

    @Test
    fun approachingAFieldSignalsOnceThenTwice() {
        val field = Fields.circle(1, Fields.at(0.0, 13.0), 5.0)
        var level = DetectorLevel.NONE
        val signals = mutableListOf<Int>()
        // 8, 6, 4, and 3.5 metres from the edge: far at 8 m, near from 4 m (half the radius is 5 m).
        listOf(0.0, 2.0, 4.0, 4.5).forEach { walked ->
            val result = step(level, listOf(field), 0.0, Fields.at(0.0, walked))
            level = result.level
            signals += result.signals
        }
        assertEquals(listOf(1, 0, 2, 0), signals)
        assertEquals(DetectorLevel.NEAR, level)
    }

    @Test
    fun pointingAtAFieldSignalsOnlyOnce() {
        val fields = listOf(Fields.square(1, 0.0, 13.0))
        var level = DetectorLevel.NONE
        var total = 0
        repeat(20) {
            val result = step(level, fields, 0.0)
            level = result.level
            total += result.signals
        }
        assertEquals(1, total)
    }

    @Test
    fun enteringAFieldSignalsTwiceOnce() {
        val fields = listOf(Fields.square(1, 0.0, 0.0))
        var level = DetectorLevel.NEAR
        val first = step(level, fields, null)
        assertEquals(DetectorLevel.INSIDE, first.level)
        assertEquals(2, first.signals)
        level = first.level
        repeat(10) {
            val again = step(level, fields, (it * 37.0) % 360)
            assertEquals(0, again.signals)
            level = again.level
        }
    }

    @Test
    fun compassJitterAtTheSectorEdgeDoesNotRepeat() {
        // Edge 8 m north; the sector edge is 45 degrees either side of the heading.
        val fields = listOf(Fields.square(1, 0.0, 13.0))
        var level = DetectorLevel.NONE
        val signals = mutableListOf<Int>()
        listOf(44.0, 47.0, 43.0, 46.0, 44.0, 47.0).forEach { heading ->
            val result = step(level, fields, heading)
            level = result.level
            signals += result.signals
        }
        assertEquals(1, signals.sum())
    }

    @Test
    fun ownFieldSignalsButCannotBeMarked() {
        val own = Fields.square(1, 0.0, 0.0, owner = Fields.BLUE, visible = listOf(Fields.BLUE))
        val result = step(DetectorLevel.NONE, listOf(own), 0.0)
        assertEquals(2, result.signals)
        assertNull(result.markTarget)
        assertTrue(result.insideAny)
    }

    @Test
    fun hiddenEnemyFieldCanBeMarked() {
        val enemy = Fields.square(1, 0.0, 0.0)
        assertNotNull(step(DetectorLevel.NONE, listOf(enemy), null).markTarget)
    }

    @Test
    fun neutralStaffFieldTheTeamCannotSeeCanBeMarked() {
        val neutral = Fields.square(1, 0.0, 0.0, owner = null, visible = listOf(Fields.RED))
        assertEquals(1L, step(DetectorLevel.NONE, listOf(neutral), null).markTarget?.id)
    }

    @Test
    fun fieldAlreadyVisibleOrPublicCannotBeMarked() {
        val seen = Fields.square(1, 0.0, 0.0, visible = listOf(Fields.RED, Fields.BLUE))
        val public = Fields.square(2, 0.0, 0.0, visible = emptyList())
        assertNull(step(DetectorLevel.NONE, listOf(seen), null).markTarget)
        assertNull(step(DetectorLevel.NONE, listOf(public), null).markTarget)
    }

    @Test
    fun gpsWanderAtTheEdgeKeepsThePhoneInsideWithoutRepeatedSignals() {
        // 10 m square centred on HOME: the east edge is 5 m east.
        val fields = listOf(Fields.square(1, 0.0, 0.0))
        var level = DetectorLevel.NONE
        var inside = emptySet<Long>()
        val signals = mutableListOf<Int>()
        val seen = mutableListOf<Boolean>()
        listOf(4.0, 7.0, 4.5, 7.5, 6.0, 4.0).forEach { east ->
            val result = DetectorMachine.step(
                level, fields, Fields.at(90.0, east), null, settings, Fields.BLUE, inside, Inside.exitMetres(3.0),
            )
            level = result.level
            inside = result.insideIds
            signals += result.signals
            seen += result.insideAny
        }
        assertEquals(2, signals.sum())
        assertTrue(seen.all { it })
        val left = DetectorMachine.step(
            level, fields, Fields.at(90.0, 9.0), null, settings, Fields.BLUE, inside, Inside.exitMetres(3.0),
        )
        assertFalse(left.insideAny)
    }

    @Test
    fun aFieldIsEnteredOnlyFromInside() {
        val fields = listOf(Fields.square(1, 0.0, 0.0))
        val near = DetectorMachine.step(
            DetectorLevel.NONE, fields, Fields.at(90.0, 6.0), null, settings, Fields.BLUE, emptySet(), 5.0,
        )
        assertFalse(near.insideAny)
        assertTrue(near.insideIds.isEmpty())
    }

    @Test
    fun exitMarginFollowsAccuracyBetweenOneAndFiveMetres() {
        assertEquals(1.0, Inside.exitMetres(0.5), 0.0)
        assertEquals(3.0, Inside.exitMetres(3.0), 0.0)
        assertEquals(5.0, Inside.exitMetres(12.0), 0.0)
    }

    @Test
    fun outsideEveryFieldAllowsNoMines() {
        val result = step(DetectorLevel.NONE, listOf(Fields.square(1, 0.0, 30.0)), 0.0)
        assertFalse(result.insideAny)
    }
}
