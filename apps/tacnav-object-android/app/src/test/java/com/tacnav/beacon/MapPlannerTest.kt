package com.tacnav.beacon

import com.tacnav.beacon.domain.Geodesy
import com.tacnav.beacon.domain.Geometry
import com.tacnav.beacon.domain.HintArea
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.ObjectVisibility
import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.Shapes
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.MapOp
import com.tacnav.beacon.game.MapPlanner
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPlannerTest {
    private fun Rig.writeNow(): MapOp? = MapPlanner.next(state, elapsed)

    private fun Rig.acceptWrites() {
        while (true) {
            when (val op = writeNow()) {
                is MapOp.WriteObject -> state = machine.objectWritten(state, op.id ?: 41, op.life, elapsed)
                is MapOp.WriteArea -> state = machine.areaWritten(state, op.id ?: 42, elapsed)
                else -> return
            }
        }
    }

    @Test
    fun deployingObjectIsHiddenSearchableAndMarked() {
        val rig = Rig(
            ObjectSettings(
                objectName = "Antenna",
                description = "Guard it",
                activationSeconds = 30,
                visibility = ObjectVisibility.ALL,
            ),
        )
        rig.deploy()
        val op = rig.writeNow() as MapOp.WriteObject
        assertEquals(listOf(CONTEXT.teamId), op.request.visibleTeamIds)
        assertTrue(op.request.searchable)
        assertEquals("Guard it\nDeploying", op.request.description)
        assertEquals(60L, op.request.ttlSeconds)
    }

    @Test
    fun activeObjectUsesTheConfiguredVisibility() {
        val everyone = Rig(ObjectSettings(objectName = "A", visibility = ObjectVisibility.ALL))
        everyone.deploy()
        assertEquals(
            emptyList<Long>(),
            (everyone.writeNow() as MapOp.WriteObject).request.visibleTeamIds,
        )
        val team = Rig(ObjectSettings(objectName = "A"))
        team.deploy()
        assertEquals(
            listOf(CONTEXT.teamId),
            (team.writeNow() as MapOp.WriteObject).request.visibleTeamIds,
        )
    }

    @Test
    fun enteringActiveRewritesVisibilityAtOnce() {
        val rig = Rig(
            ObjectSettings(
                objectName = "A",
                activationSeconds = 5,
                visibility = ObjectVisibility.ALL,
            ),
        )
        rig.deploy()
        rig.acceptWrites()
        assertNull(rig.writeNow())
        rig.tick(5_000)
        val op = rig.writeNow() as MapOp.WriteObject
        assertEquals(41L, op.id)
        assertEquals(emptyList<Long>(), op.request.visibleTeamIds)
    }

    @Test
    fun sessionTimeIsShownInMinutes() {
        val rig = Rig(ObjectSettings(objectName = "A", description = "Guard it"))
        rig.deploy()
        rig.acceptWrites()
        rig.run(12 * 60_000)
        rig.tick(30_000)
        val op = rig.writeNow() as MapOp.WriteObject
        assertEquals("Guard it\nActive 12 min", op.request.description)
    }

    @Test
    fun objectGoneFallsBackToCreate() {
        val rig = Rig()
        rig.deploy()
        rig.acceptWrites()
        rig.state = rig.machine.objectGone(rig.state)
        assertNull((rig.writeNow() as MapOp.WriteObject).id)
    }

    @Test
    fun stillObjectWritesOnlyToKeepItsLease() {
        val rig = Rig()
        rig.deploy()
        var writes = 0
        repeat(120) {
            if (rig.writeNow() != null) writes += 1
            rig.acceptWrites()
            rig.run(1_000)
        }
        assertTrue("writes=$writes", writes in 4..5)
    }

    @Test
    fun movingObjectWritesEverySeveralSeconds() {
        val rig = Rig()
        rig.deploy()
        var writes = 0
        for (second in 1..60) {
            if (rig.writeNow() != null) writes += 1
            rig.acceptWrites()
            rig.run(1_000, north = second * 10.0)
        }
        assertTrue("writes=$writes", writes in 11..13)
    }

    @Test
    fun areaIsPublicNotSearchableAndHasNoIcon() {
        val rig = Rig(
            ObjectSettings(
                objectName = "Antenna",
                iconId = 12,
                visibility = ObjectVisibility.ALL,
                randomAreaEnabled = true,
                randomAreaRadiusMetres = 100.0,
            ),
        )
        rig.deploy()
        val first = rig.writeNow() as MapOp.WriteObject
        assertEquals(listOf(CONTEXT.teamId), first.request.visibleTeamIds)
        rig.state = rig.machine.objectWritten(rig.state, 41, first.life, rig.elapsed)
        val area = rig.writeNow() as MapOp.WriteArea
        assertEquals("Antenna area", area.request.title)
        assertEquals(emptyList<Long>(), area.request.visibleTeamIds)
        assertFalse(area.request.searchable)
        assertNull(area.request.iconId)
        assertEquals("Active 0 min", area.request.description)
        val circle = area.request.geometry as Geometry.Circle
        assertEquals(100.0, circle.radiusMetres, 0.0)
    }

    @Test
    fun areaIsAStableCircleContainingTheObject() {
        val rig = Rig(
            ObjectSettings(objectName = "A", randomAreaEnabled = true, randomAreaRadiusMetres = 100.0),
            draws = { 0.9 },
        )
        rig.deploy()
        val centre = rig.state.areaCentre!!
        assertTrue(Geodesy.distanceMetres(centre, rig.state.position!!.coordinate) < 100.0)
        repeat(20) { step -> rig.fix(north = step * 2.0) }
        assertEquals(centre, rig.state.areaCentre)
    }

    @Test
    fun leavingTheAreaRollsANewOneContainingThePosition() {
        val rig = Rig(
            ObjectSettings(objectName = "A", randomAreaEnabled = true, randomAreaRadiusMetres = 100.0),
        )
        rig.deploy()
        rig.acceptWrites()
        val centre = rig.state.areaCentre!!
        rig.fix(north = 1_000.0)
        val next = rig.state.areaCentre!!
        assertFalse(centre == next)
        assertTrue(Geodesy.distanceMetres(next, rig.state.position!!.coordinate) <= 100.0)
        assertTrue(rig.state.areaDirty)
    }

    @Test
    fun areaDistributionFavoursTheEdge() {
        val random = Random(7)
        val total = 20_000
        val outer = (1..total).count {
            HintArea.offsetMetres(100.0, random.nextDouble()) > 50.0
        }
        val share = outer.toDouble() / total
        // A uniform spread over the area puts 75% in the outer half of the radius.
        assertEquals(0.875, share, 0.01)
    }

    @Test
    fun wreckIsPublicPermanentNotSearchableAndKeyed() {
        val rig = Rig(
            ObjectSettings(
                objectName = "Antenna",
                sizeAMetres = 25.0,
                destroyedIconId = 13,
                hitsToDestroy = 1,
            ),
        )
        rig.deploy()
        rig.acceptWrites()
        rig.step(rig.machine.onDelivery(rig.state, rig.env, "hit", "shot", 41, rig.clock))
        val op = rig.writeNow() as MapOp.CreateWreck
        assertEquals("Antenna destroyed", op.request.title)
        assertEquals(13L, op.request.iconId)
        assertEquals(emptyList<Long>(), op.request.visibleTeamIds)
        assertEquals(0L, op.request.ttlSeconds)
        assertFalse(op.request.searchable)
        assertEquals("key-0", op.request.idempotencyKey)
        assertTrue(op.request.geometry is Geometry.Circle)
    }

    @Test
    fun liveObjectIsDeletedOnlyAfterTheWreckIsAccepted() {
        val rig = Rig(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.acceptWrites()
        rig.step(rig.machine.onDelivery(rig.state, rig.env, "hit", "shot", 41, rig.clock))
        assertTrue(rig.writeNow() is MapOp.CreateWreck)
        rig.state = rig.machine.wreckAccepted(rig.state)
        val delete = rig.writeNow() as MapOp.Delete
        assertEquals(41L, delete.id)
    }

    @Test
    fun failureWaitsBeforeRetrying() {
        val rig = Rig()
        rig.deploy()
        rig.state = rig.machine.writeFailed(rig.state, rig.elapsed)
        assertNull(rig.writeNow())
        rig.tick(5_000)
        assertTrue(rig.writeNow() is MapOp.WriteObject)
    }

    @Test
    fun pointCircleAndRectangleComeFromTheSizeFields() {
        val centre = Coordinate(HOME_LATITUDE, HOME_LONGITUDE)
        val point = Shapes.geometry(ObjectSettings(), centre, 0.0)
        val first = Shapes.geometry(ObjectSettings(sizeAMetres = 25.0), centre, 0.0)
        val second = Shapes.geometry(ObjectSettings(sizeBMetres = 25.0), centre, 0.0)
        val both = Shapes.geometry(ObjectSettings(sizeAMetres = 10.0, sizeBMetres = 20.0), centre, 0.0)
        assertTrue(point is Geometry.Marker)
        assertEquals(25.0, (first as Geometry.Circle).radiusMetres, 0.0)
        assertEquals(25.0, (second as Geometry.Circle).radiusMetres, 0.0)
        assertEquals(4, (both as Geometry.Polygon).points.size)
    }

    @Test
    fun rectangleSidesFollowTheHeadingAtSeveralLatitudes() {
        for (latitude in listOf(0.0, 50.0, 69.0, -35.0)) {
            for (heading in listOf(0.0, 40.0, 90.0, 217.0, 359.0)) {
                val centre = Coordinate(latitude, 30.0)
                val polygon = Shapes.geometry(
                    ObjectSettings(sizeAMetres = 10.0, sizeBMetres = 20.0),
                    centre,
                    heading,
                ) as Geometry.Polygon
                val (frontLeft, frontRight, backRight, backLeft) = polygon.points
                assertEquals(20.0, Geodesy.distanceMetres(frontLeft, frontRight), 0.01)
                assertEquals(10.0, Geodesy.distanceMetres(frontRight, backRight), 0.01)
                assertEquals(20.0, Geodesy.distanceMetres(backRight, backLeft), 0.01)
                val along = Geodesy.bearingDegrees(backLeft, frontLeft)
                val turn = (along - heading + 540.0) % 360.0 - 180.0
                assertEquals(0.0, turn, 0.01)
            }
        }
    }

    @Test
    fun rectangleIsCentredOnThePhone() {
        val centre = Coordinate(HOME_LATITUDE, HOME_LONGITUDE)
        val polygon = Shapes.geometry(
            ObjectSettings(sizeAMetres = 10.0, sizeBMetres = 20.0),
            centre,
            40.0,
        ) as Geometry.Polygon
        val half = Geodesy.distanceMetres(centre, polygon.points[0])
        polygon.points.forEach {
            assertEquals(half, Geodesy.distanceMetres(centre, it), 0.001)
        }
    }

    @Test
    fun headingUsesThePhoneTopWhenFlatAndTheBackWhenUpright() {
        // Flat, top pointing east: device Y maps to world east.
        val flat = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        assertEquals(90.0, Geodesy.headingFromRotation(flat), 0.01)
        // Upright, back facing north: device Z maps to world south.
        val upright = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        assertEquals(0.0, Geodesy.headingFromRotation(upright), 0.01)
    }

    @Test
    fun deploymentIsOffTheMapBeforeActivate() {
        assertEquals(LifeState.WAITING, Rig().state.life)
        assertNull(Rig().writeNow())
    }
}
