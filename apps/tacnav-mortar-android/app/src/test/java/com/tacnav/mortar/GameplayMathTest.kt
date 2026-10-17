package com.tacnav.mortar

import com.tacnav.mortar.domain.AcousticTrigger
import com.tacnav.mortar.domain.Ballistics
import com.tacnav.mortar.domain.BlockedShot
import com.tacnav.mortar.domain.CalibrationMath
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.domain.GeometryMath
import com.tacnav.mortar.domain.GuidanceMath
import com.tacnav.mortar.domain.HitMath
import com.tacnav.mortar.domain.SensorGate
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ValidShot
import com.tacnav.mortar.network.Coordinate
import com.tacnav.mortar.network.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayMathTest {
    private val settings = GameplaySettings()

    @Test
    fun `endpoint angles use endpoint ranges`() {
        assertEquals(200.0, Ballistics.rangeMetres(60.0, settings), 0.0001)
        assertEquals(10.0, Ballistics.rangeMetres(85.0, settings), 0.0001)
    }

    @Test
    fun `intermediate angle is interpolated linearly`() {
        assertEquals(105.0, Ballistics.rangeMetres(72.5, settings), 0.0001)
    }

    @Test
    fun `sixteen metre fix is blocked at fifteen metre threshold`() {
        val result = SensorGate.accept(snapshot(accuracy = 16.0), settings)
        assertTrue(result is BlockedShot)
        assertEquals("Insufficient GPS accuracy.", result.reason)
    }

    @Test
    fun `fourteen metre fix is accepted at the default threshold`() {
        val result = SensorGate.accept(snapshot(accuracy = 14.0), settings)
        assertTrue(result is ValidShot)
    }

    @Test
    fun `stale location is blocked`() {
        val result = SensorGate.accept(snapshot(age = 16.0), settings)
        assertTrue(result is BlockedShot)
        assertEquals("GPS fix is too old.", result.reason)
    }

    @Test
    fun `valid snapshot creates a shot`() {
        val result = SensorGate.accept(snapshot(), settings)
        assertTrue(result is ValidShot)
        assertEquals(200.0, (result as ValidShot).rangeMetres, 0.0001)
    }

    @Test
    fun `center probability is certain`() {
        assertEquals(1.0, HitMath.probability(0.0, settings), 0.0)
    }

    @Test
    fun `boundary uses configured edge probability`() {
        assertEquals(0.5, HitMath.probability(5.0, settings), 0.0)
    }

    @Test
    fun `geodesic destination stays on a known fixture`() {
        val point = Geodesy.destination(50.0, 30.0, 90.0, 100.0)
        assertEquals(50.0, point.latitude, 0.001)
        assertEquals(30.0014, point.longitude, 0.0002)
    }

    @Test
    fun `marker circle polyline and polygon distances are local`() {
        val impact = Coordinate(50.0, 30.0)
        assertEquals(
            0.0,
            GeometryMath.nearestDistanceMetres(
                impact,
                Geometry.Marker(50.0, 30.0),
            ),
            0.01,
        )
        assertEquals(
            0.0,
            GeometryMath.nearestDistanceMetres(
                impact,
                Geometry.Circle(50.0, 30.0, 10.0),
            ),
            0.01,
        )
        val line = Geometry.Polyline(
            listOf(Coordinate(50.0, 29.999), Coordinate(50.0, 30.001)),
        )
        assertEquals(0.0, GeometryMath.nearestDistanceMetres(impact, line), 1.0)
        val polygon = Geometry.Polygon(
            listOf(
                Coordinate(49.999, 29.999),
                Coordinate(49.999, 30.001),
                Coordinate(50.001, 30.001),
                Coordinate(50.001, 29.999),
            ),
        )
        assertEquals(0.0, GeometryMath.nearestDistanceMetres(impact, polygon), 0.01)
    }

    @Test
    fun `approximate guidance hides metres until after the shot`() {
        val origin = snapshot()
        val target = Geodesy.destination(origin.latitude, origin.longitude, 90.0, 80.0)
        val before = GuidanceMath.approximate(origin, target, settings, revealMetres = false)
        assertNull(before.crossMetres)
        assertNull(before.alongMetres)
        val after = GuidanceMath.approximate(origin, target, settings, revealMetres = true)
        assertTrue((after.crossMetres ?: 0.0) > 0.0)
    }

    @Test
    fun `known-point offset makes the same aim read the known bearing`() {
        val measured = 128.0
        val known = 42.0
        val offset = CalibrationMath.signedBearingOffset(known, measured)
        assertEquals(known, CalibrationMath.applyHeadingOffset(measured, offset), 0.0001)
        assertEquals(
            0.0,
            CalibrationMath.applyHeadingOffset(90.0, CalibrationMath.signedBearingOffset(0.0, 90.0)),
            0.0001,
        )
    }

    @Test
    fun `known point closer than 25 m is rejected`() {
        val near = Geodesy.destination(50.0, 30.0, 90.0, 10.0)
        assertTrue(CalibrationMath.knownPointTooClose(50.0, 30.0, near.latitude, near.longitude))
        val far = Geodesy.destination(50.0, 30.0, 90.0, 80.0)
        assertFalse(CalibrationMath.knownPointTooClose(50.0, 30.0, far.latitude, far.longitude))
    }

    @Test
    fun `flat phone is not accepted as the 60 degree reference`() {
        assertTrue(CalibrationMath.elevationLooksUnmounted(0.0, 60.0))
        assertFalse(CalibrationMath.elevationLooksUnmounted(55.0, 60.0))
    }

    @Test
    fun `elevation below the horizon is not clamped to the reference`() {
        val offset = CalibrationMath.elevationOffset(60.0, 0.0)
        assertEquals(53.0, CalibrationMath.applyElevationOffset(-7.0, offset), 0.0001)
        assertEquals(67.0, CalibrationMath.applyElevationOffset(7.0, offset), 0.0001)
        assertEquals(-7.0, CalibrationMath.elevationFromPitch(7.0), 0.0001)
        assertEquals(60.0, CalibrationMath.elevationFromPitch(-60.0), 0.0001)
    }

    @Test
    fun `tube heading is the horizontal projection of device Y`() {
        val north = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val east = floatArrayOf(
            0f, 1f, 0f,
            -1f, 0f, 0f,
            0f, 0f, 1f,
        )
        val south = floatArrayOf(
            -1f, 0f, 0f,
            0f, -1f, 0f,
            0f, 0f, 1f,
        )
        assertEquals(0.0, Geodesy.magneticHeadingFromRotation(north), 0.0001)
        assertEquals(90.0, Geodesy.magneticHeadingFromRotation(east), 0.0001)
        assertEquals(180.0, Geodesy.magneticHeadingFromRotation(south), 0.0001)
    }

    @Test
    fun `acoustic impulse fires once then debounce`() {
        val trigger = AcousticTrigger(-20.0, 80, 400)
        assertFalse(trigger.sample(-10.0, 0))
        assertTrue(trigger.sample(-10.0, 80))
        assertFalse(trigger.sample(-10.0, 160))
    }

    private fun snapshot(
        accuracy: Double = 4.0,
        age: Double = 1.0,
    ) = SensorSnapshot(
        latitude = 50.0,
        longitude = 30.0,
        accuracyMetres = accuracy,
        ageSeconds = age,
        trueHeadingDegrees = 0.0,
        elevationDegrees = 60.0,
        triggerEpochMillis = 1_700_000_000_000,
    )
}
