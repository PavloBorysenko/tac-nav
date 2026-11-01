package com.tacnav.beacon

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.beacon.activation.ActivationPayload
import com.tacnav.beacon.activation.ActivationRejectedException
import com.tacnav.beacon.activation.HttpActivationApi
import com.tacnav.beacon.domain.ActivationResult
import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.Geometry
import com.tacnav.beacon.domain.MapScope
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.TeamScope
import com.tacnav.beacon.network.AuthorizationLostException
import com.tacnav.beacon.network.HttpTacNavApi
import com.tacnav.beacon.network.ObjectGoneException
import com.tacnav.beacon.network.ObjectRequest
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
        maps = listOf(MapScope(4, "Map", listOf(TeamScope(7, "Team")))),
        defaultMapId = 4,
        defaultTeamId = 7,
        referenceSettings = ObjectSettings(objectName = "A"),
        settingsPin = "4321",
    )
    private val request = ObjectRequest(
        context = context,
        geometry = Geometry.Circle(50.0, 30.0, 25.0),
        title = "Antenna destroyed",
        description = "Guard it",
        iconId = 13,
        visibleTeamIds = emptyList(),
        ttlSeconds = 0,
        positionAccuracyMetres = 6.5,
        searchable = false,
        idempotencyKey = "wreck-1",
    )

    private fun api(server: FakeServer) = HttpTacNavApi(server.client) { activation to context }

    @Test
    fun createSendsEveryFieldTheServerReads() = runTest {
        val server = FakeServer()
        assertEquals(41L, api(server).create(request))
        val sent = server.lastBody()
        val call = server.requests.last()
        assertEquals("POST", call.method)
        assertEquals("/wp-json/tacnav/v1/maps/4/objects", call.url.encodedPath)
        assertEquals("Bearer bearer-value", call.header("Authorization"))
        assertEquals("4", call.header("X-TacNav-Map"))
        assertEquals("7", call.header("X-TacNav-Team"))
        assertEquals("circle", sent.getString("kind"))
        assertEquals(25.0, sent.getJSONObject("geometry").getDouble("radius"), 0.0)
        assertEquals("Guard it", sent.getString("description"))
        assertEquals(0, sent.getInt("ttl_seconds"))
        assertEquals(13, sent.getInt("icon_id"))
        assertFalse(sent.getBoolean("searchable"))
        assertEquals("wreck-1", sent.getString("idempotency_key"))
    }

    @Test
    fun polygonIsSentAsLatitudeLongitudePairs() = runTest {
        val server = FakeServer()
        api(server).create(
            request.copy(
                geometry = Geometry.Polygon(listOf(Coordinate(50.0, 30.0), Coordinate(50.1, 30.1))),
                idempotencyKey = null,
            ),
        )
        val sent = server.lastBody()
        assertEquals("polygon", sent.getString("kind"))
        assertEquals(50.1, sent.getJSONObject("geometry").getJSONArray("path").getJSONArray(1).getDouble(0), 0.0)
        assertFalse(sent.has("idempotency_key"))
    }

    @Test
    fun updateTargetsTheObjectAndReports404AsGone() = runTest {
        val server = FakeServer()
        api(server).update(41, request)
        assertEquals("PUT", server.requests.last().method)
        assertEquals("/wp-json/tacnav/v1/maps/4/objects/41", server.requests.last().url.encodedPath)

        val gone = FakeServer(404)
        var failure: Throwable? = null
        runCatching { api(gone).update(41, request) }.onFailure { failure = it }
        assertTrue(failure is ObjectGoneException)
    }

    @Test
    fun deleteTreats404AsDone() = runTest {
        val server = FakeServer(404)
        api(server).delete(41, 4, 7)
        assertEquals("DELETE", server.requests.last().method)
    }

    @Test
    fun revokedCredentialIsReportedAsSuch() = runTest {
        var failure: Throwable? = null
        runCatching { api(FakeServer(401)).create(request) }.onFailure { failure = it }
        assertTrue(failure is AuthorizationLostException)
    }

    @Test
    fun inboxParsesDeliveriesAndAcknowledgeSendsTheId() = runTest {
        val server = FakeServer(
            body = """{"items":[{"id":5,"recipient_object_id":41,"event_type":"hit",
                "source_application_id":9,"client_event_id":"shot-1"}],"next_cursor":5}""",
        )
        val delivery = api(server).inbox(3).single()
        assertEquals("cursor=3", server.requests.last().url.query)
        assertEquals("hit", delivery.eventType)
        assertEquals(41L, delivery.recipientObjectId)
        assertEquals("shot-1", delivery.clientEventId)
        assertEquals(9L, delivery.sourceApplicationId)

        api(server).acknowledge(delivery.deliveryReference)
        assertEquals("/wp-json/tacnav-devices/v1/inbox/ack", server.requests.last().url.encodedPath)
        assertEquals(5, server.lastBody().getJSONArray("delivery_ids").getInt(0))
    }

    @Test
    fun activationSendsThePackageAndReadsTheReference() = runTest {
        val server = FakeServer(
            body = """{
                "application_id": 9,
                "installation_id": 12,
                "credential": "bearer-value",
                "application_type": "object",
                "allowed_maps": [{"id": 4, "name": "Map", "teams": [{"id": 7, "name": "Team"}]}],
                "default_map_id": 4,
                "default_team_id": 7,
                "reference_settings": {
                    "object_name": "Antenna",
                    "description": "Guard it",
                    "icon_id": 12,
                    "destroyed_icon_id": 13,
                    "hits_to_destroy": 2,
                    "size_a_m": 10,
                    "size_b_m": null,
                    "activation_seconds": 30,
                    "visibility": "all",
                    "random_area_enabled": true,
                    "random_area_radius_m": 100,
                    "auto_deactivate_m": 30,
                    "max_location_accuracy_m": 15,
                    "auto_activate_enabled": true,
                    "auto_activate_seconds": 60,
                    "max_downtime_minutes": 20,
                    "downtime_reset_minutes": null,
                    "settings_pin": "9876"
                }
            }""",
        )
        val result = HttpActivationApi(server.client)
            .activate(ActivationPayload("https://tacnav.example", "CODE"))
        assertEquals("com.tacnav.beacon", server.lastBody().getString("package_id"))
        assertEquals("9876", result.settingsPin)
        val settings = result.referenceSettings
        assertEquals("Antenna", settings.objectName)
        assertEquals(10.0, settings.sizeAMetres!!, 0.0)
        assertNull(settings.sizeBMetres)
        assertEquals(20L, settings.maxDowntimeMinutes)
        assertNull(settings.downtimeResetMinutes)
    }

    @Test
    fun refusedActivationShowsTheServerMessage() = runTest {
        val server = FakeServer(403, """{"message":"This activation code is for a different application."}""")
        var failure: Throwable? = null
        runCatching {
            HttpActivationApi(server.client)
                .activate(ActivationPayload("https://tacnav.example", "CODE"))
        }.onFailure { failure = it }
        assertTrue(failure is ActivationRejectedException)
        assertEquals("This activation code is for a different application.", failure!!.message)
    }
}
