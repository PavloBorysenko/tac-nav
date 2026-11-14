package com.tacnav.sapper

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.sapper.activation.ActivationPayload
import com.tacnav.sapper.activation.ActivationRejectedException
import com.tacnav.sapper.activation.HttpActivationApi
import com.tacnav.sapper.domain.ActivationResult
import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MapScope
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.TeamScope
import com.tacnav.sapper.game.PendingWrite
import com.tacnav.sapper.network.AuthorizationLostException
import com.tacnav.sapper.network.ConflictException
import com.tacnav.sapper.network.HttpMapApi
import com.tacnav.sapper.network.ObjectGoneException
import com.tacnav.sapper.network.RejectedException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Stands in for the WordPress routes without a network. */
private class FakeServer(
    private val code: Int = 200,
    private val body: String = "{\"id\":41}",
) : Interceptor {
    val requests = mutableListOf<Request>()

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

    fun lastBody(): JSONObject {
        val buffer = Buffer()
        requests.last().body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        requests += chain.request()
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

@RunWith(AndroidJUnit4::class)
class HttpContractTest {
    private val context = ActiveContext(4, 7)
    private val activation = ActivationResult(
        serverUrl = "https://tacnav.example",
        applicationId = 9,
        installationId = "12",
        bearerCredential = "bearer-value",
        maps = listOf(MapScope(4, "Map", listOf(TeamScope(7, "Blue"), TeamScope(8, "Red")))),
        defaultMapId = 4,
        defaultTeamId = 7,
        referenceSettings = SapperSettings(),
        settingsPin = "4321",
    )

    private fun api(server: FakeServer) = HttpMapApi(server.client) { activation }

    private val square = Geometry.Polygon(
        listOf(Coordinate(50.0, 30.0), Coordinate(50.0, 30.001), Coordinate(50.001, 30.001), Coordinate(50.001, 30.0)),
    )

    @Test
    fun activationSendsTheSapperPackageAndReadsTheReference() = runTest {
        val server = FakeServer(
            body = """
                {"application_id":9,"installation_id":"12","credential":"secret",
                 "allowed_maps":[{"id":4,"name":"Map","teams":[{"id":7,"name":"Blue"}]}],
                 "default_map_id":4,"default_team_id":7,
                 "reference_settings":{"detect_radius_m":15,"detect_sector_deg":60,"mark_hold_seconds":20,
                   "clear_hold_seconds":120,"lay_hold_seconds":90,"minefield_length_m":12,"minefield_width_m":8,
                   "minefield_icon_id":5,"no_mines_icon_id":0,"max_minefields":0,"max_no_mines_markers":4,
                   "max_location_accuracy_m":10,"settings_pin":"98765"}}
            """.trimIndent(),
        )
        val result = HttpActivationApi(server.client).activate(ActivationPayload("https://tacnav.example", "CODE"))
        val sent = server.lastBody()
        assertEquals("https://tacnav.example/wp-json/tacnav-devices/v1/activate", server.requests.last().url.toString())
        assertEquals("com.tacnav.sapper", sent.getString("package_id"))
        assertEquals("CODE", sent.getString("code"))
        val reference = result.referenceSettings
        assertEquals(15.0, reference.detectRadiusMetres, 0.0)
        assertEquals(60.0, reference.detectSectorDegrees, 0.0)
        assertEquals(20L, reference.markHoldSeconds)
        assertEquals(90L, reference.layHoldSeconds)
        assertEquals(12.0, reference.minefieldLengthMetres, 0.0)
        assertEquals(5L, reference.minefieldIconId)
        assertNull(reference.noMinesIconId)
        assertEquals(0, reference.maxMinefields)
        assertEquals(4, reference.maxNoMinesMarkers)
        assertEquals("98765", result.settingsPin)
    }

    @Test
    fun refusedActivationShowsTheServerMessage() = runTest {
        val server = FakeServer(403, "{\"message\":\"This code belongs to another application.\"}")
        try {
            HttpActivationApi(server.client).activate(ActivationPayload("https://tacnav.example", "CODE"))
            fail("Activation must be refused.")
        } catch (error: ActivationRejectedException) {
            assertEquals("This code belongs to another application.", error.message)
        }
    }

    @Test
    fun searchAsksForMinefieldsInACircleAndReadsTeamFacts() = runTest {
        val server = FakeServer(
            body = """
                [{"id":31,"kind":"polygon","geometry":{"path":[[50,30],[50,30.001],[50.001,30.001]]},
                  "role":"minefield","owner_team_id":null,"visible_team_ids":[8],"updated_at":"2026-11-08 10:00:00"},
                 {"id":32,"kind":"circle","geometry":{"lat":50,"lng":30,"radius":7},
                  "role":"minefield","owner_team_id":7,"visible_team_ids":[],"updated_at":"t"}]
            """.trimIndent(),
        )
        val found = api(server).search(context, Coordinate(50.0, 30.0), 20.0, "minefield")
        val call = server.requests.last()
        val sent = server.lastBody()
        assertEquals("POST", call.method)
        assertEquals("/wp-json/tacnav/v1/maps/4/search", call.url.encodedPath)
        assertEquals("Bearer bearer-value", call.header("Authorization"))
        assertEquals("4", call.header("X-TacNav-Map"))
        assertEquals("7", call.header("X-TacNav-Team"))
        assertEquals("circle", sent.getString("kind"))
        assertEquals(20.0, sent.getJSONObject("geometry").getDouble("radius"), 0.0)
        assertEquals("minefield", sent.getString("role"))
        assertEquals(2, found.size)
        assertNull(found[0].ownerTeamId)
        assertEquals(listOf(8L), found[0].visibleTeamIds)
        assertEquals("2026-11-08 10:00:00", found[0].updatedAt)
        assertEquals(3, (found[0].geometry as Geometry.Polygon).points.size)
        assertEquals(7L, found[1].ownerTeamId)
        assertTrue(found[1].visibleTeamIds.isEmpty())
        assertEquals(7.0, (found[1].geometry as Geometry.Circle).radiusMetres, 0.0)
    }

    @Test
    fun layingCreatesAKeyedTeamMinefield() = runTest {
        val server = FakeServer()
        val id = api(server).create(PendingWrite.CreateMinefield("lay-1", context, square, 13))
        val call = server.requests.last()
        val sent = server.lastBody()
        assertEquals(41L, id)
        assertEquals("POST", call.method)
        assertEquals("/wp-json/tacnav/v1/maps/4/objects", call.url.encodedPath)
        assertEquals("polygon", sent.getString("kind"))
        assertEquals(4, sent.getJSONObject("geometry").getJSONArray("path").length())
        assertEquals("Minefield", sent.getString("title"))
        assertEquals("minefield", sent.getString("role"))
        assertEquals(13, sent.getInt("icon_id"))
        assertEquals(7, sent.getJSONArray("visible_team_ids").getInt(0))
        assertEquals(1, sent.getJSONArray("visible_team_ids").length())
        assertEquals(0, sent.getInt("ttl_seconds"))
        assertEquals("lay-1", sent.getString("idempotency_key"))
    }

    @Test
    fun noMinesCreatesAKeyedOrdinaryMarker() = runTest {
        val server = FakeServer()
        api(server).create(PendingWrite.CreateNoMines("tap-1", context, Coordinate(50.0, 30.0), 4.0, null))
        val sent = server.lastBody()
        assertEquals("marker", sent.getString("kind"))
        assertEquals(50.0, sent.getJSONObject("geometry").getDouble("lat"), 0.0)
        assertEquals("No mines", sent.getString("title"))
        assertFalse(sent.has("role"))
        assertEquals(0, sent.getInt("icon_id"))
        assertEquals(0, sent.getInt("ttl_seconds"))
        assertEquals("tap-1", sent.getString("idempotency_key"))
    }

    @Test
    fun markPatchesOnlyVisibilityWithTheStampItRead() = runTest {
        val server = FakeServer()
        api(server).mark(PendingWrite.MarkField("m-1", context, 31, Coordinate(50.0, 30.0), listOf(8, 7), "2026-11-08 10:00:00"))
        val call = server.requests.last()
        val sent = server.lastBody()
        assertEquals("PATCH", call.method)
        assertEquals("/wp-json/tacnav/v1/maps/4/objects/31", call.url.encodedPath)
        assertEquals(setOf("visible_team_ids", "updated_at"), sent.keys().asSequence().toSet())
        assertEquals(8, sent.getJSONArray("visible_team_ids").getInt(0))
        assertEquals(7, sent.getJSONArray("visible_team_ids").getInt(1))
        assertEquals("2026-11-08 10:00:00", sent.getString("updated_at"))
    }

    @Test
    fun clearingSendsOnlyAOneSecondLifetime() = runTest {
        val server = FakeServer()
        api(server).expire(PendingWrite.ExpireField("c-1", context, 31))
        val call = server.requests.last()
        val sent = server.lastBody()
        assertEquals("PATCH", call.method)
        assertEquals(setOf("ttl_seconds"), sent.keys().asSequence().toSet())
        assertEquals(1, sent.getInt("ttl_seconds"))
    }

    @Test
    fun statusCodesMapToOutcomes() = runTest {
        val expire = PendingWrite.ExpireField("c-1", context, 31)
        assertFailure<ObjectGoneException> { api(FakeServer(404)).expire(expire) }
        assertFailure<ConflictException> { api(FakeServer(409)).expire(expire) }
        assertFailure<AuthorizationLostException> { api(FakeServer(401)).expire(expire) }
        assertFailure<RejectedException> { api(FakeServer(400, "{\"message\":\"Bad role\"}")).expire(expire) }
        assertFailure<java.io.IOException> { api(FakeServer(503)).expire(expire) }
    }

    private suspend inline fun <reified T : Throwable> assertFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}.")
        } catch (error: Throwable) {
            assertTrue("Got ${error::class.simpleName}", error is T)
        }
    }
}
