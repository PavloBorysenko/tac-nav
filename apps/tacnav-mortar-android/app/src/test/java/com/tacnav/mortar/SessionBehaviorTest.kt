package com.tacnav.mortar

import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.BlockedShot
import com.tacnav.mortar.domain.CalibrationMath
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.domain.GuidanceMath
import com.tacnav.mortar.domain.SensorGate
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.messaging.MailboxSync
import com.tacnav.mortar.network.AddressedEventRequest
import com.tacnav.mortar.network.Coordinate
import com.tacnav.mortar.network.CreateObjectRequest
import com.tacnav.mortar.network.CreatedObject
import com.tacnav.mortar.network.DeviceMailboxApi
import com.tacnav.mortar.network.GeoObjectApi
import com.tacnav.mortar.network.Geometry
import com.tacnav.mortar.network.GeometrySearchRequest
import com.tacnav.mortar.network.GeometrySearchResult
import com.tacnav.mortar.network.MailboxDelivery
import com.tacnav.mortar.session.FiringEngine
import com.tacnav.mortar.session.PermissionPolicy
import com.tacnav.mortar.session.PermissionState
import com.tacnav.mortar.session.TorchPulse
import com.tacnav.mortar.session.TorchResult
import com.tacnav.mortar.ui.Diagnostics
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionBehaviorTest {
    private val settings = GameplaySettings()
    private val context = ActiveContext(10, 20)

    @Test
    fun `search runs before the public impact marker is created`() = runBlocking {
        val geo = RecordingGeo()
        val engine = FiringEngine(geo, RecordingMailbox(), { 0.1 }, { "shot-order" })
        engine.accept(snapshot(), settings.copy(impactIconId = 77, impactTtlSeconds = 30), 0, 1_000)
        engine.resolveDue(0, 60_000, settings.copy(impactIconId = 77, impactTtlSeconds = 30), context, online = true)
        assertEquals(listOf("search", "create"), geo.calls)
        val created = geo.lastCreate!!
        assertEquals(emptyList<Long>(), created.visibleTeamIds)
        assertEquals(30L, created.ttlSeconds)
        assertEquals(77L, created.iconId)
        assertEquals("Mortar 1 impact", created.title)
        assertEquals("marker", created.kind)
    }

    @Test
    fun `lost hit response retries the same event without rerolling`() = runBlocking {
        val impact = Geodesy.destination(50.0, 30.0, 90.0, 200.0)
        val geo = RecordingGeo(
            listOf(
                result(1, impact),
                result(2, Geodesy.destination(impact.latitude, impact.longitude, 0.0, 2.0)),
            ),
        )
        val mailbox = RecordingMailbox(failFirstSubmit = true)
        var rolls = 0
        val engine = FiringEngine(geo, mailbox, { rolls += 1; 0.05 }, { "shot-retry" })
        engine.accept(snapshot(), settings, 0, 1_000)
        engine.resolveDue(0, 60_000, settings, context, online = true)
        assertEquals(ShotPhase.IMPACT_CREATED, engine.currentShots().single().phase)
        engine.resolveDue(0, 61_000, settings, context, online = true)
        assertEquals(ShotPhase.COMPLETED, engine.currentShots().single().phase)
        assertEquals(2, rolls)
        assertEquals(1, geo.searches)
        assertEquals(1, geo.creates)
        assertEquals(2, mailbox.submits.size)
        assertEquals(mailbox.submits[0].clientEventId, mailbox.submits[1].clientEventId)
        assertEquals(mailbox.submits[0].recipientObjectIds, mailbox.submits[1].recipientObjectIds)
    }

    @Test
    fun `process restart resumes from the persisted roll`() = runBlocking {
        val impact = Geodesy.destination(50.0, 30.0, 90.0, 200.0)
        val geo = RecordingGeo(listOf(result(5, impact)))
        geo.failCreate = true
        var persisted: List<ShotRecord> = emptyList()
        val first = FiringEngine(geo, RecordingMailbox(), { 0.42 }, { "shot-restart" }) { persisted = it }
        first.accept(snapshot(), settings, 0, 1_000)
        first.resolveDue(0, 60_000, settings, context, online = true)
        assertEquals(ShotPhase.SEARCHED, persisted.single().phase)

        geo.failCreate = false
        val mailbox = RecordingMailbox()
        val restarted = FiringEngine(geo, mailbox, { error("must not reroll") }, { "unused" })
        restarted.restore(persisted)
        restarted.resolveDue(0, 61_000, settings, context, online = true)
        val shot = restarted.currentShots().single()
        assertEquals(ShotPhase.COMPLETED, shot.phase)
        assertEquals(0.42, shot.rolls[5L]!!, 0.0)
        assertEquals(1, geo.searches)
        assertEquals(listOf(5L), mailbox.submits.single().recipientObjectIds)
    }

    @Test
    fun `entered coordinates never move the impact`() {
        val target = Geodesy.destination(50.0, 30.0, 0.0, 150.0)
        val misaligned = snapshot().copy(trueHeadingDegrees = 90.0, elevationDegrees = 70.0)
        val guidance = GuidanceMath.precise(misaligned, Coordinate(target.latitude, target.longitude), settings)
        val shot = FiringEngine(RecordingGeo(), RecordingMailbox(), { 0.1 }, { "aim" })
            .accept(misaligned, settings, 0, 1_000)
        val expected = Geodesy.destination(50.0, 30.0, 90.0, shot.rangeMetres)
        assertEquals(expected.latitude, shot.impactLatitude, 1e-9)
        assertEquals(expected.longitude, shot.impactLongitude, 1e-9)
        assertEquals(0.0, CalibrationMath.signedBearingOffset(0.0, guidance.requiredBearingDegrees), 0.5)
        assertEquals(expected.latitude, guidance.predictedImpact!!.latitude, 1e-9)
        assertTrue(guidance.missMetres!! > 100.0)
    }

    @Test
    fun `out of range target is reported without blocking a valid shot`() {
        val far = Geodesy.destination(50.0, 30.0, 90.0, 500.0)
        val guidance = GuidanceMath.precise(snapshot(), Coordinate(far.latitude, far.longitude), settings)
        assertTrue(guidance.outOfRange)
        assertEquals(null, guidance.requiredElevationDegrees)
        assertTrue(SensorGate.accept(snapshot(), settings) !is BlockedShot)
    }

    @Test
    fun `landed correction is measured from the shot impact`() {
        val shot = FiringEngine(RecordingGeo(), RecordingMailbox(), { 0.1 }, { "approx" })
            .accept(snapshot(), settings, 0, 1_000)
        val beyond = Geodesy.destination(shot.impactLatitude, shot.impactLongitude, 90.0, 20.0)
        val target = Geodesy.destination(beyond.latitude, beyond.longitude, 180.0, 10.0)
        val correction = GuidanceMath.afterLanding(
            shot.copy(phase = ShotPhase.COMPLETED),
            Coordinate(target.latitude, target.longitude),
            settings,
        )
        assertEquals("RIGHT", correction.leftRight)
        assertEquals("ADD", correction.addDrop)
        assertEquals(10.0, correction.crossMetres, 0.5)
        assertEquals(20.0, correction.alongMetres, 0.5)
        assertTrue(correction.inZone)
    }

    @Test
    fun `half a degree of jitter snaps to the firing endpoints`() {
        val low = SensorGate.accept(snapshot().copy(elevationDegrees = 59.6), settings)
        assertTrue(low is com.tacnav.mortar.domain.ValidShot)
        assertEquals(200.0, (low as com.tacnav.mortar.domain.ValidShot).rangeMetres, 1e-9)
        assertEquals(59.6, low.snapshot.elevationDegrees, 0.0)
        val high = SensorGate.accept(snapshot().copy(elevationDegrees = 85.4), settings)
        assertEquals(10.0, (high as com.tacnav.mortar.domain.ValidShot).rangeMetres, 1e-9)
        assertTrue(SensorGate.accept(snapshot().copy(elevationDegrees = 59.4), settings) is BlockedShot)
        assertTrue(SensorGate.accept(snapshot().copy(elevationDegrees = 85.6), settings) is BlockedShot)
    }

    @Test
    fun `unreliable compass blocks the shot with a reason`() {
        val blocked = SensorGate.accept(snapshot().copy(compassReliable = false), settings)
        assertTrue(blocked is BlockedShot)
        assertTrue((blocked as BlockedShot).reason.contains("Compass"))
    }

    @Test
    fun `declination and saved offset convert magnetic heading`() {
        val trueHeading = Geodesy.trueHeading(350.0, 15.0)!!
        assertEquals(5.0, trueHeading, 1e-9)
        assertEquals(2.0, CalibrationMath.applyHeadingOffset(trueHeading, -3.0), 1e-9)
        assertEquals(350.0, Geodesy.trueHeading(350.0, null)!!, 1e-9)
        assertEquals(null, Geodesy.trueHeading(Double.NaN, 4.0))
    }

    @Test
    fun `repeated button shots extend the torch period`() = runTest {
        val states = mutableListOf<Boolean>()
        val torch = TorchPulse(this, { states += it; TorchResult.OK }, onWarning = {})
        torch.pulse()
        advanceTimeBy(3_000)
        torch.pulse()
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(listOf(true, true), states)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(true, true, false), states)
    }

    @Test
    fun `missing torch warns without throwing`() = runTest {
        val warnings = mutableListOf<String>()
        val torch = TorchPulse(this, { TorchResult.UNAVAILABLE }, onWarning = { warnings += it })
        torch.pulse()
        torch.stop()
        assertEquals(listOf("Torch is unavailable, but the shot was accepted."), warnings)
    }

    @Test
    fun `permission denials map to degraded behavior`() {
        val all = PermissionState(true, true, true, true, true)
        assertTrue(PermissionPolicy.canStartSession(all))
        assertEquals(emptyList<String>(), PermissionPolicy.sessionWarnings(all, FiringMode.entries.toSet()))

        val noLocation = all.copy(preciseLocation = false)
        assertFalse(PermissionPolicy.canStartSession(noLocation))
        assertEquals(listOf(PermissionPolicy.LOCATION_DENIED), PermissionPolicy.sessionWarnings(noLocation, setOf(FiringMode.BUTTON)))

        val noMicrophone = all.copy(microphone = false)
        assertTrue(PermissionPolicy.canStartSession(noMicrophone))
        assertFalse(PermissionPolicy.canUseAcoustic(noMicrophone))
        assertEquals(listOf(PermissionPolicy.MICROPHONE_DENIED), PermissionPolicy.sessionWarnings(noMicrophone, FiringMode.entries.toSet()))
        assertEquals(emptyList<String>(), PermissionPolicy.sessionWarnings(noMicrophone, setOf(FiringMode.BUTTON)))

        assertFalse(PermissionPolicy.canStartSession(all.copy(notifications = false)))
        assertFalse(PermissionPolicy.canStartSession(all.copy(playServices = false)))
        assertTrue(PermissionPolicy.canStartSession(all.copy(camera = false)))
    }

    @Test
    fun `mailbox acknowledges only after local processing`() = runBlocking {
        val inbox = FakeInbox(listOf(delivery(11), delivery(12)))
        val recorded = mutableListOf<String>()
        val sync = MailboxSync(inbox) {
            assertFalse(it.deliveryReference in inbox.acked)
            recorded += it.deliveryReference
        }
        assertEquals(2, sync.sync())
        assertEquals(listOf("11", "12"), recorded)
        assertEquals(listOf("11", "12"), inbox.acked)

        inbox.acked.clear()
        inbox.ackFails = true
        inbox.pending = listOf(delivery(13))
        runCatching { sync.sync() }
        inbox.ackFails = false
        assertEquals(0, sync.sync())
        assertEquals(listOf("11", "12", "13"), recorded)
    }

    @Test
    fun `failed local processing leaves the delivery pending`() = runBlocking {
        val inbox = FakeInbox(listOf(delivery(21)))
        val sync = MailboxSync(inbox) { error("disk full") }
        assertTrue(runCatching { sync.sync() }.isFailure)
        assertEquals(emptyList<String>(), inbox.acked)
    }

    @Test
    fun `diagnostics show roll and delivery state`() {
        val shot = ShotRecord(
            shotId = "abcdef123456",
            snapshot = snapshot(),
            rangeMetres = 200.0,
            impactLatitude = 50.0,
            impactLongitude = 30.003,
            dueAtElapsedMillis = 0,
            dueAtEpochMillis = 0,
            phase = ShotPhase.IMPACT_CREATED,
            rolls = mapOf(4L to 0.25),
            facts = listOf(com.tacnav.mortar.domain.HitFact(4, 1.5, 0.85, 0.25)),
            hits = listOf(4),
            failure = "Hit delivery failed (503).",
        )
        val lines = Diagnostics.shotLines(shot, 0).joinToString("\n")
        assertTrue(lines.contains("roll 0.250 → hit"))
        assertTrue(lines.contains("P 0.85"))
        assertTrue(lines.contains("pending for 1 object(s)"))
        assertTrue(lines.contains("Hit delivery failed (503)."))
        assertFalse(lines.contains("Bearer"))
    }

    private fun result(id: Long, at: Coordinate) =
        GeometrySearchResult(id, "marker", Geometry.Marker(at.latitude, at.longitude), null)

    private fun delivery(id: Long) =
        MailboxDelivery(id, id.toString(), "hit", emptyMap(), recipientObjectId = 3)

    private fun snapshot() = SensorSnapshot(
        latitude = 50.0,
        longitude = 30.0,
        accuracyMetres = 4.0,
        ageSeconds = 1.0,
        trueHeadingDegrees = 90.0,
        elevationDegrees = 60.0,
        triggerEpochMillis = 1_700_000_000_000,
    )

    private class RecordingGeo(
        private val results: List<GeometrySearchResult> = emptyList(),
    ) : GeoObjectApi {
        val calls = mutableListOf<String>()
        var searches = 0
        var creates = 0
        var failCreate = false
        var lastCreate: CreateObjectRequest? = null

        override suspend fun search(request: GeometrySearchRequest): List<GeometrySearchResult> {
            calls += "search"
            searches += 1
            return results
        }

        override suspend fun create(request: CreateObjectRequest): CreatedObject {
            if (failCreate) throw IOException("create failed")
            calls += "create"
            creates += 1
            lastCreate = request
            return CreatedObject(100, request.kind, request.geometry)
        }

        override suspend fun update(objectId: Long, request: CreateObjectRequest): CreatedObject =
            CreatedObject(objectId, request.kind, request.geometry)
    }

    private class RecordingMailbox(private var failFirstSubmit: Boolean = false) : DeviceMailboxApi {
        val submits = mutableListOf<AddressedEventRequest>()

        override suspend fun submit(request: AddressedEventRequest) {
            submits += request
            if (failFirstSubmit) {
                failFirstSubmit = false
                throw IOException("response lost")
            }
        }

        override suspend fun inbox(afterCursor: Long?): List<MailboxDelivery> = emptyList()

        override suspend fun acknowledge(deliveryReference: String) = Unit
    }

    private class FakeInbox(var pending: List<MailboxDelivery>) : DeviceMailboxApi {
        val acked = mutableListOf<String>()
        var ackFails = false

        override suspend fun submit(request: AddressedEventRequest) = Unit

        override suspend fun inbox(afterCursor: Long?): List<MailboxDelivery> =
            pending.filter { afterCursor == null || it.cursor > afterCursor }
                .filter { it.deliveryReference !in acked }

        override suspend fun acknowledge(deliveryReference: String) {
            if (ackFails) throw IOException("ack lost")
            acked += deliveryReference
            pending = pending.filter { it.deliveryReference != deliveryReference }
        }
    }
}
