package com.tacnav.mortar

import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.network.AddressedEventRequest
import com.tacnav.mortar.network.CreateObjectRequest
import com.tacnav.mortar.network.CreatedObject
import com.tacnav.mortar.network.DeviceMailboxApi
import com.tacnav.mortar.network.GeoObjectApi
import com.tacnav.mortar.network.Geometry
import com.tacnav.mortar.network.GeometrySearchRequest
import com.tacnav.mortar.network.GeometrySearchResult
import com.tacnav.mortar.network.MailboxDelivery
import com.tacnav.mortar.session.FiringEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiringEngineTest {
    private val settings = GameplaySettings()
    private val context = ActiveContext(10, 20)

    @Test
    fun `two overlapping shots remain independent`() {
        val ids = ArrayDeque(listOf("one", "two"))
        val engine = FiringEngine(FakeGeo(), FakeMailbox(), { 0.1 }, { ids.removeFirst() })
        engine.accept(snapshot(), settings, 0, 1_000)
        engine.accept(snapshot(), settings, 1_000, 2_000)
        val shots = engine.currentShots()
        assertEquals(2, shots.size)
        assertEquals("one", shots[0].shotId)
        assertEquals("two", shots[1].shotId)
        assertEquals(ShotPhase.IN_FLIGHT, shots[0].phase)
        assertEquals(ShotPhase.IN_FLIGHT, shots[1].phase)
    }

    @Test
    fun `offline due shot waits then uses later search results`() = runBlocking {
        val geo = FakeGeo()
        val mailbox = FakeMailbox()
        val engine = FiringEngine(geo, mailbox, { 0.1 }, { "shot-a" })
        engine.accept(snapshot(), settings, 0, 1_000)
        engine.resolveDue(30_000, 40_000, settings, context, online = false)
        assertEquals(ShotPhase.DUE, engine.currentShots().single().phase)
        val impact = Geodesy.destination(50.0, 30.0, 90.0, 200.0)
        geo.results = listOf(
            GeometrySearchResult(
                7,
                "marker",
                Geometry.Marker(impact.latitude, impact.longitude),
                null,
            ),
        )
        engine.resolveDue(30_000, 40_000, settings, context, online = true)
        val shot = engine.currentShots().single()
        assertEquals(ShotPhase.COMPLETED, shot.phase)
        assertEquals(listOf(7L), shot.hits)
        assertEquals("shot-a", mailbox.submitted?.clientEventId)
        assertTrue(geo.created)
    }

    @Test
    fun `create failure keeps the roll and does not search again`() = runBlocking {
        val geo = FakeGeo()
        val impact = Geodesy.destination(50.0, 30.0, 90.0, 200.0)
        geo.results = listOf(
            GeometrySearchResult(
                9,
                "marker",
                Geometry.Marker(impact.latitude, impact.longitude),
                null,
            ),
        )
        geo.failCreate = true
        val engine = FiringEngine(geo, FakeMailbox(), { 0.25 }, { "shot-b" })
        engine.accept(snapshot(), settings, 0, 1_000)
        engine.resolveDue(30_000, 40_000, settings, context, online = true)
        val first = engine.currentShots().single()
        assertEquals(ShotPhase.SEARCHED, first.phase)
        assertEquals(0.25, first.rolls[9L])
        geo.searches = 0
        geo.failCreate = false
        engine.resolveDue(30_000, 40_000, settings, context, online = true)
        assertEquals(0, geo.searches)
        assertEquals(0.25, engine.currentShots().single().rolls[9L])
        assertEquals(ShotPhase.COMPLETED, engine.currentShots().single().phase)
    }

    private fun snapshot() = SensorSnapshot(
        latitude = 50.0,
        longitude = 30.0,
        accuracyMetres = 4.0,
        ageSeconds = 1.0,
        trueHeadingDegrees = 90.0,
        elevationDegrees = 60.0,
        triggerEpochMillis = 1_700_000_000_000,
    )

    private class FakeGeo : GeoObjectApi {
        var results: List<GeometrySearchResult> = emptyList()
        var created = false
        var failCreate = false
        var searches = 0

        override suspend fun search(request: GeometrySearchRequest): List<GeometrySearchResult> {
            searches += 1
            return results
        }

        override suspend fun create(request: CreateObjectRequest): CreatedObject {
            if (failCreate) error("create failed")
            created = true
            return CreatedObject(1, request.kind, request.geometry)
        }
    }

    private class FakeMailbox : DeviceMailboxApi {
        var submitted: AddressedEventRequest? = null

        override suspend fun submit(request: AddressedEventRequest) {
            submitted = request
        }

        override suspend fun inbox(afterCursor: Long?): List<MailboxDelivery> = emptyList()

        override suspend fun acknowledge(deliveryReference: String) = Unit
    }
}
