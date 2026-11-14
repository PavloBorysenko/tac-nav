package com.tacnav.sapper.network

import com.tacnav.sapper.domain.ActivationResult
import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MINEFIELD_ROLE
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.game.PendingWrite
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

/** The bearer was revoked or replaced by a newer activation of this application. */
class AuthorizationLostException : IOException(
    "This phone's activation was revoked or replaced. Activate it again with a new staff code.",
)

/** The object no longer exists on the server. */
class ObjectGoneException : IOException("The map object no longer exists.")

/** Someone else changed the object since it was read. */
class ConflictException : IOException("The map object changed since it was read.")

/** The server refused the write for good; retrying cannot help. */
class RejectedException(message: String) : IOException(message)

interface MapApi {
    suspend fun search(
        context: ActiveContext,
        centre: Coordinate,
        radiusMetres: Double,
        role: String?,
    ): List<MapObject>

    /** Creates a minefield or a No mines marker and returns its id. */
    suspend fun create(write: PendingWrite): Long

    /** Adds the team to a field's visibility, refused when the field changed since it was read. */
    suspend fun mark(write: PendingWrite.MarkField)

    /** Expires a field now. */
    suspend fun expire(write: PendingWrite.ExpireField)
}

class HttpMapApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val activation: () -> ActivationResult?,
) : MapApi {

    override suspend fun search(
        context: ActiveContext,
        centre: Coordinate,
        radiusMetres: Double,
        role: String?,
    ): List<MapObject> {
        val body = JSONObject()
            .put("kind", "circle")
            .put(
                "geometry",
                JSONObject()
                    .put("lat", centre.latitude)
                    .put("lng", centre.longitude)
                    .put("radius", radiusMetres),
            )
            .apply { role?.let { put("role", it) } }
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/${context.mapId}/search", context)
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
        response.requireSuccess("Search failed")
        val items = JSONArray(response.body)
        return List(items.length()) { parseObject(items.getJSONObject(it)) }
    }

    override suspend fun create(write: PendingWrite): Long {
        val body = when (write) {
            is PendingWrite.CreateMinefield -> JSONObject()
                .put("kind", "polygon")
                .put("geometry", JSONObject().put("path", pathArray(write.polygon.points)))
                .put("title", "Minefield")
                .put("role", MINEFIELD_ROLE)
                .put("icon_id", write.iconId ?: 0)
            is PendingWrite.CreateNoMines -> JSONObject()
                .put("kind", "marker")
                .put(
                    "geometry",
                    JSONObject()
                        .put("lat", write.position.latitude)
                        .put("lng", write.position.longitude),
                )
                .put("title", "No mines")
                .put("position_accuracy_m", write.accuracyMetres)
                .put("icon_id", write.iconId ?: 0)
            else -> throw IllegalArgumentException("Not a create.")
        }
            .put("description", "")
            .put("visible_team_ids", JSONArray().put(write.context.teamId))
            .put("ttl_seconds", 0)
            .put("idempotency_key", write.key)
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/${write.context.mapId}/objects", write.context)
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
        response.requireSuccess("Create failed")
        return JSONObject(response.body).getLong("id")
    }

    override suspend fun mark(write: PendingWrite.MarkField) {
        patch(
            write.context,
            write.fieldId,
            JSONObject()
                .put("visible_team_ids", JSONArray(write.visibleTeamIds))
                .put("updated_at", write.updatedAt),
        )
    }

    override suspend fun expire(write: PendingWrite.ExpireField) {
        patch(write.context, write.fieldId, JSONObject().put("ttl_seconds", 1))
    }

    private suspend fun patch(context: ActiveContext, id: Long, body: JSONObject) {
        val response = client.execute(
            authorized("/wp-json/tacnav/v1/maps/${context.mapId}/objects/$id", context)
                .patch(body.toString().toRequestBody(JSON))
                .build(),
        )
        response.requireSuccess("Update failed")
    }

    private fun authorized(path: String, context: ActiveContext): Request.Builder {
        val current = activation() ?: throw IOException("This phone is not activated.")
        return Request.Builder()
            .url(current.serverUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${current.bearerCredential}")
            .header("X-TacNav-Map", context.mapId.toString())
            .header("X-TacNav-Team", context.teamId.toString())
    }

    private fun TextResponse.requireSuccess(failure: String) {
        when {
            code in 200..299 -> return
            code == 401 -> throw AuthorizationLostException()
            code == 404 -> throw ObjectGoneException()
            code == 409 -> throw ConflictException()
            code in 400..499 && code != 408 && code != 429 -> {
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw RejectedException(message?.ifBlank { null } ?: "$failure ($code).")
            }
            else -> throw IOException("$failure ($code).")
        }
    }

    private fun pathArray(points: List<Coordinate>): JSONArray = JSONArray().apply {
        points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
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

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun parseObject(item: JSONObject): MapObject {
            val geometry = item.getJSONObject("geometry")
            val shape = when (item.getString("kind")) {
                "circle" -> Geometry.Circle(
                    geometry.getDouble("lat"),
                    geometry.getDouble("lng"),
                    geometry.getDouble("radius"),
                )
                "polygon", "polyline" -> {
                    val path = geometry.getJSONArray("path")
                    Geometry.Polygon(
                        List(path.length()) { index ->
                            val point = path.getJSONArray(index)
                            Coordinate(point.getDouble(0), point.getDouble(1))
                        },
                    )
                }
                else -> Geometry.Marker(geometry.getDouble("lat"), geometry.getDouble("lng"))
            }
            val visible = item.optJSONArray("visible_team_ids") ?: JSONArray()
            return MapObject(
                id = item.getLong("id"),
                geometry = shape,
                role = item.optString("role"),
                ownerTeamId = if (item.isNull("owner_team_id")) null else item.optLong("owner_team_id"),
                visibleTeamIds = List(visible.length(), visible::getLong),
                updatedAt = item.optString("updated_at"),
            )
        }
    }
}
