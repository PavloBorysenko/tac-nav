package com.tacnav.beacon.network

import com.tacnav.beacon.domain.ActivationResult
import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.Geometry
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

data class ObjectRequest(
    val context: ActiveContext,
    val geometry: Geometry,
    val title: String,
    val description: String,
    val iconId: Long?,
    val visibleTeamIds: List<Long>,
    val ttlSeconds: Long,
    val positionAccuracyMetres: Double?,
    val searchable: Boolean,
    val idempotencyKey: String? = null,
)

data class MailboxDelivery(
    val cursor: Long,
    val deliveryReference: String,
    val eventType: String,
    val recipientObjectId: Long,
    val sourceApplicationId: Long,
    val clientEventId: String,
)

/** The bearer was revoked or replaced by a newer activation of this application. */
class AuthorizationLostException : IOException(
    "This phone's activation was revoked or replaced. Reactivate it with a new staff code.",
)

/** The object no longer exists on the server. */
class ObjectGoneException : IOException("The map object no longer exists.")

interface GeoObjectApi {
    suspend fun create(request: ObjectRequest): Long
    suspend fun update(objectId: Long, request: ObjectRequest): Long
    suspend fun delete(objectId: Long, mapId: Long, teamId: Long)
}

interface DeviceMailboxApi {
    suspend fun inbox(afterCursor: Long?): List<MailboxDelivery>
    suspend fun acknowledge(deliveryReference: String)
}

class HttpTacNavApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val session: () -> Pair<ActivationResult, ActiveContext>?,
) : GeoObjectApi, DeviceMailboxApi {

    override suspend fun create(request: ObjectRequest): Long {
        val path = "/wp-json/tacnav/v1/maps/${request.context.mapId}/objects"
        val response = client.execute(
            authorized(path, request.context).post(body(request)).build(),
        )
        response.requireSuccess("Object could not be created")
        return JSONObject(response.body).getLong("id")
    }

    override suspend fun update(objectId: Long, request: ObjectRequest): Long {
        val path = "/wp-json/tacnav/v1/maps/${request.context.mapId}/objects/$objectId"
        val response = client.execute(
            authorized(path, request.context).put(body(request)).build(),
        )
        if (response.code == 404) throw ObjectGoneException()
        response.requireSuccess("Object could not be updated")
        return objectId
    }

    override suspend fun delete(objectId: Long, mapId: Long, teamId: Long) {
        val context = ActiveContext(mapId, teamId)
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/$mapId/objects/$objectId", context)
                .delete()
                .build(),
        )
        if (response.code == 404) return
        response.requireSuccess("Object could not be deleted")
    }

    override suspend fun inbox(afterCursor: Long?): List<MailboxDelivery> {
        val current = session() ?: return emptyList()
        val path = buildString {
            append("/wp-json/tacnav-devices/v1/inbox")
            if (afterCursor != null) append("?cursor=$afterCursor")
        }
        val response = client.execute(authorized(path, current.second).get().build())
        response.requireSuccess("Inbox request failed")
        val items = JSONObject(response.body).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            MailboxDelivery(
                cursor = item.getLong("id"),
                deliveryReference = item.getLong("id").toString(),
                eventType = item.optString("event_type"),
                recipientObjectId = item.optLong("recipient_object_id"),
                sourceApplicationId = item.optLong("source_application_id"),
                clientEventId = item.optString("client_event_id"),
            )
        }
    }

    override suspend fun acknowledge(deliveryReference: String) {
        val current = session() ?: return
        val body = JSONObject()
            .put("delivery_ids", JSONArray().put(deliveryReference.toLongOrNull() ?: 0))
            .toString()
            .toRequestBody(JSON)
        val response = client.execute(
            authorized("/wp-json/tacnav-devices/v1/inbox/ack", current.second).post(body).build(),
        )
        response.requireSuccess("Acknowledgement failed")
    }

    private fun body(request: ObjectRequest) = JSONObject()
        .put("kind", request.geometry.kind)
        .put("geometry", encodeGeometry(request.geometry))
        .put("title", request.title)
        .put("description", request.description)
        .put("visible_team_ids", JSONArray(request.visibleTeamIds))
        .put("ttl_seconds", request.ttlSeconds)
        .put("position_accuracy_m", request.positionAccuracyMetres ?: JSONObject.NULL)
        .put("icon_id", request.iconId ?: 0)
        .put("searchable", request.searchable)
        .apply { request.idempotencyKey?.let { put("idempotency_key", it) } }
        .toString()
        .toRequestBody(JSON)

    private fun encodeGeometry(geometry: Geometry): JSONObject = when (geometry) {
        is Geometry.Marker -> JSONObject()
            .put("lat", geometry.latitude)
            .put("lng", geometry.longitude)
        is Geometry.Circle -> JSONObject()
            .put("lat", geometry.latitude)
            .put("lng", geometry.longitude)
            .put("radius", geometry.radiusMetres)
        is Geometry.Polygon -> JSONObject().put("path", pathArray(geometry.points))
    }

    private fun pathArray(points: List<Coordinate>): JSONArray = JSONArray().apply {
        points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
    }

    private fun authorized(path: String, context: ActiveContext): Request.Builder {
        val activation = session()?.first ?: throw IOException("This phone is not activated.")
        return Request.Builder()
            .url(activation.serverUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${activation.bearerCredential}")
            .header("X-TacNav-Map", context.mapId.toString())
            .header("X-TacNav-Team", context.teamId.toString())
    }

    private fun TextResponse.requireSuccess(failure: String) {
        if (code == 401) throw AuthorizationLostException()
        if (code !in 200..299) throw IOException("$failure ($code).")
    }

    private data class TextResponse(val code: Int, val body: String)

    private suspend fun OkHttpClient.execute(request: Request): TextResponse =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val result = TextResponse(it.code, it.body?.string() ?: "")
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }
                },
            )
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
