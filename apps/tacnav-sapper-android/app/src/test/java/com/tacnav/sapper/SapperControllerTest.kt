package com.tacnav.sapper

import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.domain.Plane
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.game.Clock
import com.tacnav.sapper.game.DetectorLevel
import com.tacnav.sapper.game.GameState
import com.tacnav.sapper.game.Mode
import com.tacnav.sapper.game.PendingWrite
import com.tacnav.sapper.game.SapperController
import com.tacnav.sapper.game.Session
import com.tacnav.sapper.network.ConflictException
import com.tacnav.sapper.network.MapApi
import com.tacnav.sapper.network.ObjectGoneException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMapApi : MapApi {
    val fields = linkedMapOf<Long, MapObject>()
    val created = linkedMapOf<String, PendingWrite>()
    val marks = mutableListOf<PendingWrite.MarkField>()
    val expired = mutableListOf<Long>()
    var searches = 0
    var offline = false
    var conflictOnce = false

    override suspend fun search(context: ActiveContext, centre: Coordinate, radiusMetres: Double, role: String?): List<MapObject> {
        if (offline) throw IOException("offline")
        searches += 1
        return fields.values.filter {
            it.role == role && Plane.view(centre, it.geometry).distanceMetres <= radiusMetres
        }
    }

    override suspend fun create(write: PendingWrite): Long {
        if (offline) throw IOException("offline")
        created.putIfAbsent(write.key, write)
        return 100L + created.keys.indexOf(write.key)
    }

    override suspend fun mark(write: PendingWrite.MarkField) {
        if (offline) throw IOException("offline")
        val field = fields[write.fieldId] ?: throw ObjectGoneException()
        if (conflictOnce || field.updatedAt != write.updatedAt) {
            conflictOnce = false
            fields[field.id] = field.copy(visibleTeamIds = field.visibleTeamIds + 99L, updatedAt = "later")
            throw ConflictException()
        }
        marks += write
        fields[field.id] = field.copy(visibleTeamIds = write.visibleTeamIds, updatedAt = "marked")
    }

    override suspend fun expire(write: PendingWrite.ExpireField) {
        if (offline) throw IOException("offline")
        fields.remove(write.fieldId) ?: throw ObjectGoneException()
        expired += write.fieldId
    }
}

private class Rig(var settings: SapperSettings = SapperSettings()) {
    val api = FakeMapApi()
    var saved: GameState? = null
    val played = mutableListOf<Int>()
    var elapsed = 0L
    var epoch = 1_700_000_000_000L
    private var keys = 0
    val controller = SapperController(
        store = { saved = it },
        api = api,
        signals = { played += it },
        session = { Session(settings, ActiveContext(4, Fields.BLUE)) },
        clock = { Clock(elapsed, epoch) },
        newKey = { "key-${++keys}" },
    ).also { it.load(null) }

    fun stand(position: Coordinate = Fields.HOME, accuracy: Double = 3.0, heading: Double? = 0.0) {
        controller.onFix(Fields.fix(position, accuracy, epoch))
        controller.onHeading(heading, true)
    }

    /** Advances one second, refreshes the fix time, and ticks. */
    suspend fun second(position: Coordinate = Fields.HOME, accuracy: Double = 3.0, heading: Double? = 0.0) {
        elapsed += 1_000
        epoch += 1_000
        stand(position, accuracy, heading)
        controller.tick()
    }

    suspend fun seconds(count: Int, position: Coordinate = Fields.HOME, heading: Double? = 0.0) {
        repeat(count) { second(position, heading = heading) }
    }
}

class SapperControllerTest {
    @Test
    fun layingAFieldCreatesOneBlueMinefieldAligned40Degrees() = runTest {
        val rig = Rig()
        rig.controller.enter(Mode.LAY)
        rig.stand(heading = 40.0)
        rig.controller.pressLay()
        rig.seconds(180, heading = 40.0)
        val write = rig.api.created.values.single() as PendingWrite.CreateMinefield
        assertEquals(ActiveContext(4, Fields.BLUE), write.context)
        assertEquals(10.0, com.tacnav.sapper.domain.Geodesy.distanceMetres(write.polygon.points[0], write.polygon.points[1]), 0.05)
        assertEquals(0.0, com.tacnav.sapper.domain.Geodesy.distanceMetres(Plane.centre(write.polygon), Fields.HOME), 0.05)
        assertEquals(1, rig.controller.state.counters.minefields)
        assertEquals(2, rig.controller.view.value.minefieldsLeft)
        assertNull(rig.controller.state.laying)
        assertEquals(listOf(SapperController.LAID_SIGNALS), rig.played)
        assertEquals(1, rig.controller.view.value.laidSerial)
    }

    @Test
    fun detectShowsInsideAMinefieldOfAnyTeam() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0, owner = Fields.BLUE, visible = listOf(Fields.BLUE))
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        assertTrue(rig.controller.view.value.insideMinefield)
        assertFalse(rig.controller.view.value.markAvailable)
        rig.seconds(6, position = Fields.at(180.0, 30.0))
        assertFalse(rig.controller.view.value.insideMinefield)
    }

    @Test
    fun clearShowsInsideOnlyWithinTheTargetField() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 7.0)
        rig.controller.enter(Mode.CLEAR)
        rig.second()
        assertEquals(1L, rig.controller.view.value.clearTargetId)
        assertFalse(rig.controller.view.value.insideMinefield)
        rig.second(position = Fields.at(0.0, 7.0))
        assertTrue(rig.controller.view.value.insideMinefield)
    }

    @Test
    fun layingCompletedOfflineAppearsOnceWhenTheNetworkReturns() = runTest {
        val rig = Rig()
        rig.api.offline = true
        rig.controller.enter(Mode.LAY)
        rig.stand()
        rig.controller.pressLay()
        rig.seconds(185)
        assertTrue(rig.api.created.isEmpty())
        assertEquals(1, rig.controller.view.value.pendingWrites)
        assertFalse(rig.controller.view.value.online)
        rig.api.offline = false
        rig.seconds(6)
        assertEquals(1, rig.api.created.size)
        assertEquals(0, rig.controller.view.value.pendingWrites)
        assertEquals(1, rig.controller.state.counters.minefields)
    }

    @Test
    fun layingProgressSurvivesARestart() = runTest {
        val rig = Rig()
        rig.controller.enter(Mode.LAY)
        rig.stand()
        rig.controller.pressLay()
        rig.seconds(120)
        rig.controller.pause()
        val saved = rig.saved!!
        assertEquals(120_000L, saved.laying!!.heldMillis)

        val restarted = Rig()
        restarted.controller.load(saved)
        restarted.controller.enter(Mode.LAY)
        restarted.stand()
        restarted.controller.pressLay()
        restarted.seconds(60)
        assertEquals(1, restarted.api.created.size)
    }

    @Test
    fun lockingThePhoneStopsTheHoldAndKeepsProgress() = runTest {
        val rig = Rig()
        rig.controller.enter(Mode.LAY)
        rig.stand()
        rig.controller.pressLay()
        rig.seconds(30)
        rig.controller.pause()
        rig.seconds(60)
        assertEquals(30_000L, rig.controller.state.laying!!.heldMillis)
        assertFalse(rig.controller.view.value.layPressed)
    }

    @Test
    fun layLimitReachedRefusesLaying() = runTest {
        val rig = Rig(SapperSettings(maxMinefields = 1, layHoldSeconds = 1))
        rig.controller.enter(Mode.LAY)
        rig.stand()
        rig.controller.pressLay()
        rig.seconds(2)
        rig.controller.releaseLay()
        rig.controller.pressLay()
        assertEquals("No minefields left for this activation.", rig.controller.view.value.message)
        assertEquals(0, rig.controller.view.value.minefieldsLeft)
    }

    @Test
    fun poorGpsBlocksWritesButDetectStillSignals() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 13.0)
        rig.controller.enter(Mode.DETECT)
        rig.second(accuracy = 25.0)
        rig.second(accuracy = 25.0)
        assertEquals(listOf(1), rig.played)
        assertFalse(rig.controller.view.value.gpsUsable)
        rig.controller.placeNoMines()
        assertTrue(rig.api.created.isEmpty())
        rig.controller.enter(Mode.LAY)
        rig.stand(accuracy = 25.0)
        rig.controller.pressLay()
        assertNull(rig.controller.state.laying)
    }

    @Test
    fun detectSearchesTwiceTheRadiusForMinefieldsOnly() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 23.0)
        rig.api.fields[2] = Fields.square(2, 0.0, 30.0)
        rig.api.fields[3] = Fields.square(3, 0.0, 3.0).copy(role = "")
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        assertEquals(1, rig.api.searches)
        rig.seconds(29)
        assertEquals(2, rig.api.searches)
        assertEquals(DetectorLevel.NONE, rig.controller.view.value.level)
    }

    @Test
    fun markingAHiddenEnemyFieldAddsTheTeamAndRetriesAfterAConflict() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0)
        rig.api.conflictOnce = true
        rig.controller.enter(Mode.DETECT)
        rig.second()
        rig.second()
        assertEquals(listOf(2), rig.played)
        assertTrue(rig.controller.view.value.markAvailable)
        assertFalse(rig.controller.view.value.noMinesOffered)
        rig.controller.pressMark()
        rig.seconds(30)
        rig.seconds(2)
        val mark = rig.api.marks.single()
        assertEquals(listOf(Fields.RED, 99L, Fields.BLUE), mark.visibleTeamIds)
        assertEquals(listOf(Fields.RED, 99L, Fields.BLUE), rig.api.fields[1]!!.visibleTeamIds)
        assertFalse(rig.controller.view.value.markAvailable)
    }

    @Test
    fun releasingTheMarkContinuesFromTheSavedProgress() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0)
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        rig.controller.pressMark()
        rig.seconds(20)
        rig.controller.releaseMark()
        assertEquals(20_000L, rig.saved!!.markProgress[1L])
        rig.controller.pressMark()
        rig.seconds(9)
        assertTrue(rig.api.marks.isEmpty())
        rig.seconds(3)
        assertEquals(1, rig.api.marks.size)
    }

    @Test
    fun markProgressSurvivesARestart() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0)
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        rig.controller.pressMark()
        rig.seconds(20)
        rig.controller.pause()
        rig.controller.load(rig.saved)
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        assertEquals(2f / 3, rig.controller.view.value.markFraction, 0.01f)
        rig.controller.pressMark()
        rig.seconds(12)
        assertEquals(1, rig.api.marks.size)
    }

    @Test
    fun gpsWanderAtTheEdgeKeepsTheMarkHold() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0)
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2, position = Fields.at(90.0, 4.0))
        rig.controller.pressMark()
        repeat(14) {
            rig.second(position = Fields.at(90.0, 4.0))
            rig.second(position = Fields.at(90.0, 7.0))
            assertTrue(rig.controller.view.value.markAvailable)
        }
        rig.seconds(2, position = Fields.at(90.0, 4.0))
        assertEquals(1, rig.api.marks.size)
    }

    @Test
    fun clearKeepsTheSkullThroughGpsWanderAtTheEdge() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0)
        rig.controller.enter(Mode.CLEAR)
        rig.second(position = Fields.at(90.0, 4.0))
        assertTrue(rig.controller.view.value.insideMinefield)
        rig.second(position = Fields.at(90.0, 7.0))
        assertTrue(rig.controller.view.value.insideMinefield)
        rig.second(position = Fields.at(90.0, 9.0))
        assertFalse(rig.controller.view.value.insideMinefield)
    }

    @Test
    fun noMinesOutsideEveryFieldPlacesOneTeamMarker() = runTest {
        val rig = Rig(SapperSettings(noMinesIconId = 13))
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        assertTrue(rig.controller.view.value.noMinesOffered)
        rig.controller.placeNoMines()
        rig.second()
        val write = rig.api.created.values.single() as PendingWrite.CreateNoMines
        assertEquals(13L, write.iconId)
        assertEquals(Fields.HOME.latitude, write.position.latitude, 1e-9)
        assertEquals(9, rig.controller.view.value.noMinesLeft)
    }

    @Test
    fun noMinesLimitReached() = runTest {
        val rig = Rig(SapperSettings(maxNoMinesMarkers = 1))
        rig.controller.enter(Mode.DETECT)
        rig.seconds(2)
        rig.controller.placeNoMines()
        rig.second()
        rig.controller.placeNoMines()
        rig.second()
        assertEquals(1, rig.api.created.size)
        assertEquals(SapperController.NO_MARKERS_LEFT, rig.controller.view.value.message)
    }

    @Test
    fun clearingAFieldExpiresItAndOffersNoMines() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0, owner = Fields.BLUE, visible = listOf(Fields.BLUE))
        rig.controller.enter(Mode.CLEAR)
        rig.second()
        assertEquals(1L, rig.controller.view.value.clearTargetId)
        rig.controller.pressClear()
        rig.seconds(181)
        assertEquals(listOf(1L), rig.api.expired)
        assertTrue(rig.controller.view.value.safe)
        assertTrue(rig.controller.view.value.noMinesOffered)
    }

    @Test
    fun clearingFarFromEveryFieldOffersNoButton() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 35.0)
        rig.controller.enter(Mode.CLEAR)
        rig.second(accuracy = 6.0)
        assertTrue(rig.controller.view.value.clearSearched)
        assertNull(rig.controller.view.value.clearTargetId)
    }

    @Test
    fun clearingProgressForAFieldClearedElsewhereIsDiscarded() = runTest {
        val rig = Rig()
        rig.api.fields[1] = Fields.square(1, 0.0, 0.0)
        rig.controller.enter(Mode.CLEAR)
        rig.second()
        rig.controller.pressClear()
        rig.seconds(60)
        rig.controller.releaseClear()
        assertNotNull(rig.controller.state.clearProgress.singleOrNull())
        rig.api.fields.clear()
        rig.seconds(31)
        assertTrue(rig.controller.state.clearProgress.isEmpty())
        assertNull(rig.controller.view.value.clearTargetId)
    }

    @Test
    fun aNewActivationStartsWithFullLimitsAndNoProgress() = runTest {
        val rig = Rig(SapperSettings(layHoldSeconds = 1))
        rig.controller.enter(Mode.LAY)
        rig.stand()
        repeat(2) {
            rig.controller.pressLay()
            rig.seconds(2)
            rig.controller.releaseLay()
        }
        assertEquals(2, rig.controller.state.counters.minefields)
        rig.controller.load(GameState())
        assertEquals(3, rig.controller.view.value.minefieldsLeft)
        assertNull(rig.controller.state.laying)
    }
}
