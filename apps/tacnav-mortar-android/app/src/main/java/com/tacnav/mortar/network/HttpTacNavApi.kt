package com.tacnav.mortar.network

import com.tacnav.mortar.domain.ActivationResult
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
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpTacNavApi(
    private val client: OkHttpClient = OkHttpClient(),
) : GeoObjectApi, DeviceMailboxApi {
    lateinit var activation: ActivationResult
    var mapId: Long = 0
    var teamId: Long = 0

    override suspend fun search(request: GeometrySearchRequest): List<GeometrySearchResult> {
        val body = JSONObject()
            .put("kind", kindOf(request.geometry))
            .put("geometry", encodeGeometry(request.geometry))
            .toString()
            .toRequestBody(JSON)
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/${request.context.mapId}/search")
                .post(body)
                .build(),
        )
        response.requireSuccess("Search failed")
        val results = parseArray(response.body, "results")
        return List(results.length()) { index ->
            val item = results.getJSONObject(index)
            val geometry = item.optJSONObject("geometry") ?: JSONObject()
            GeometrySearchResult(
                objectId = item.getLong("id"),
                kind = item.getString("kind"),
                geometry = decodeGeometry(item.getString("kind"), geometry),
                positionAccuracyMetres = item.optDouble("position_accuracy_m").takeIf {
                    item.has("position_accuracy_m") && !item.isNull("position_accuracy_m")
                },
            )
        }
    }

    override suspend fun create(request: CreateObjectRequest): CreatedObject {
        val body = JSONObject()
            .put("kind", request.kind)
            .put("geometry", encodeGeometry(request.geometry))
            .put("title", request.title)
            .put("visible_team_ids", JSONArray(request.visibleTeamIds))
            .put("ttl_seconds", request.ttlSeconds)
            .put("position_accuracy_m", request.positionAccuracyMetres)
            .put("icon_id", request.iconId ?: 0)
            .put("client_object_id", request.clientObjectId)
            .toString()
            .toRequestBody(JSON)
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/${request.context.mapId}/objects")
                .post(body)
                .build(),
        )
        response.requireSuccess("Impact marker could not be created")
        return createdObject(response.body, request)
    }

    override suspend fun update(objectId: Long, request: CreateObjectRequest): CreatedObject {
        val body = JSONObject()
            .put("kind", request.kind)
            .put("geometry", encodeGeometry(request.geometry))
            .put("title", request.title)
            .put("visible_team_ids", JSONArray(request.visibleTeamIds))
            .put("ttl_seconds", request.ttlSeconds)
            .put("position_accuracy_m", request.positionAccuracyMetres)
            .put("icon_id", request.iconId ?: 0)
            .toString()
            .toRequestBody(JSON)
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/${request.context.mapId}/objects/$objectId")
                .put(body)
                .build(),
        )
        response.requireSuccess("Object could not be updated")
        return createdObject(response.body, request)
    }

    private fun createdObject(raw: String, request: CreateObjectRequest): CreatedObject {
        val item = JSONObject(raw)
        return CreatedObject(
            objectId = item.getLong("id"),
            kind = item.optString("kind", request.kind),
            geometry = request.geometry,
        )
    }

    override suspend fun submit(request: AddressedEventRequest) {
        val body = JSONObject()
            .put("client_event_id", request.clientEventId)
            .put("type", request.type)
            .put("recipient_object_ids", JSONArray(request.recipientObjectIds))
            .put("payload", jsonValue(request.opaquePayload))
            .toString()
            .toRequestBody(JSON)
        val response = client.execute(
            authorized("/wp-json/tacnav-devices/v1/events").post(body).build(),
        )
        response.requireSuccess("Hit delivery failed")
    }

    override suspend fun inbox(afterCursor: Long?): List<MailboxDelivery> {
        val url = buildString {
            append("/wp-json/tacnav-devices/v1/inbox")
            if (afterCursor != null) append("?cursor=$afterCursor")
        }
        val response = client.execute(authorized(url).get().build())
        response.requireSuccess("Inbox request failed")
        val items = parseArray(response.body, "items")
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            MailboxDelivery(
                cursor = item.getLong("id"),
                deliveryReference = item.getLong("id").toString(),
                eventType = item.optString("event_type"),
                opaquePayload = item.optJSONObject("payload")?.let(::mapValue) ?: emptyMap(),
                recipientObjectId = item.optLong("recipient_object_id"),
                sourceApplicationId = item.optLong("source_application_id"),
                sourceTeamId = item.optLong("source_team_id"),
                clientEventId = item.optString("client_event_id"),
            )
        }
    }

    override suspend fun acknowledge(deliveryReference: String) {
        val body = JSONObject()
            .put("delivery_ids", JSONArray().put(deliveryReference.toLongOrNull() ?: 0))
            .toString()
            .toRequestBody(JSON)
        val response = client.execute(
            authorized("/wp-json/tacnav-devices/v1/inbox/ack").post(body).build(),
        )
        response.requireSuccess("Acknowledgement failed")
    }

    private fun TextResponse.requireSuccess(failure: String) {
        if (code == 401) {
            throw AuthorizationLostException()
        }
        if (code !in 200..299) throw IOException("$failure ($code).")
    }

    private fun mapValue(value: JSONObject): Map<String, Any?> =
        value.keys().asSequence().associateWith { key -> plainValue(value.opt(key)) }

    private fun plainValue(value: Any?): Any? = when (value) {
        JSONObject.NULL, null -> null
        is JSONObject -> mapValue(value)
        is JSONArray -> List(value.length()) { plainValue(value.opt(it)) }
        else -> value
    }

    private fun authorized(path: String): Request.Builder {
        val current = activation
        return Request.Builder()
            .url(current.serverUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${current.bearerCredential}")
            .header("X-TacNav-Map", mapId.toString())
            .header("X-TacNav-Team", teamId.toString())
    }

    private fun parseArray(body: String, wrapperKey: String): JSONArray {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return JSONArray()
        }
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        return JSONObject(trimmed).optJSONArray(wrapperKey) ?: JSONArray()
    }

    private fun jsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray -> value
        is Map<*, *> -> JSONObject().also { encoded ->
            value.forEach { (key, item) ->
                encoded.put(key.toString(), jsonValue(item))
            }
        }
        is Collection<*> -> JSONArray().also { encoded ->
            value.forEach { encoded.put(jsonValue(it)) }
        }
        is Boolean, is Number, is String -> value
        else -> value.toString()
    }

    private fun kindOf(geometry: Geometry): String = when (geometry) {
        is Geometry.Marker -> "marker"
        is Geometry.Circle -> "circle"
        is Geometry.Polyline -> "polyline"
        is Geometry.Polygon -> "polygon"
    }

    private fun encodeGeometry(geometry: Geometry): JSONObject = when (geometry) {
        is Geometry.Marker -> JSONObject()
            .put("lat", geometry.latitude)
            .put("lng", geometry.longitude)
        is Geometry.Circle -> JSONObject()
            .put("lat", geometry.latitude)
            .put("lng", geometry.longitude)
            .put("radius", geometry.radiusMetres)
        is Geometry.Polyline -> JSONObject().put("path", pathArray(geometry.points))
        is Geometry.Polygon -> JSONObject().put("path", pathArray(geometry.points))
    }

    private fun decodeGeometry(kind: String, geometry: JSONObject): Geometry = when (kind) {
        "circle" -> Geometry.Circle(
            geometry.getDouble("lat"),
            geometry.getDouble("lng"),
            geometry.getDouble("radius"),
        )
        "polyline" -> Geometry.Polyline(pathPoints(geometry))
        "polygon" -> Geometry.Polygon(pathPoints(geometry))
        else -> Geometry.Marker(geometry.getDouble("lat"), geometry.getDouble("lng"))
    }

    private fun pathArray(points: List<Coordinate>): JSONArray = JSONArray().apply {
        points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
    }

    private fun pathPoints(geometry: JSONObject): List<Coordinate> {
        val path = geometry.optJSONArray("path") ?: return emptyList()
        return List(path.length()) { index ->
            val pair = path.getJSONArray(index)
            Coordinate(pair.getDouble(0), pair.getDouble(1))
        }
    }

    private data class TextResponse(val code: Int, val body: String)

    private suspend fun OkHttpClient.execute(request: Request): TextResponse =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
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
