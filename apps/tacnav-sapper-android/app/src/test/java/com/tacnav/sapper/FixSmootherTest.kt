package com.tacnav.sapper

import com.tacnav.sapper.domain.Geodesy
import com.tacnav.sapper.game.Fix
import com.tacnav.sapper.game.FixSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixSmootherTest {
    private fun fix(east: Double, north: Double, second: Int, accuracy: Double = 4.0): Fix {
        val position = Fields.at(0.0, north, Fields.at(90.0, east))
        return Fix(position.latitude, position.longitude, accuracy, 1_000_000L + second * 1_000L)
    }

    private fun offset(value: Fix, east: Double, north: Double): Double =
        Geodesy.distanceMetres(value.coordinate, Fields.at(0.0, north, Fields.at(90.0, east)))

    @Test
    fun standingPhoneWanderIsDamped() {
        val smoother = FixSmoother()
        val errors = (0 until 60).map { second ->
            val wander = if (second % 2 == 0) 4.0 else -4.0
            offset(smoother.smooth(fix(wander, 0.0, second)), 0.0, 0.0)
        }
        assertTrue(errors.drop(10).all { it < 2.0 })
    }

    @Test
    fun walkingPhoneIsFollowed() {
        val smoother = FixSmoother()
        var last = fix(0.0, 0.0, 0)
        (0..30).forEach { second -> last = smoother.smooth(fix(0.0, 1.4 * second, second)) }
        assertTrue(offset(last, 0.0, 1.4 * 30) < 3.0)
    }

    @Test
    fun accuracyStaysRawAndALongGapStartsAgain() {
        val smoother = FixSmoother()
        smoother.smooth(fix(0.0, 0.0, 0))
        val next = smoother.smooth(fix(4.0, 0.0, 1, accuracy = 6.0))
        assertEquals(6.0, next.accuracyMetres, 0.0)
        val afterGap = smoother.smooth(fix(30.0, 0.0, 30))
        assertEquals(0.0, offset(afterGap, 30.0, 0.0), 0.01)
    }
}
