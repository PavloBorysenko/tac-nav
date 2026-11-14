package com.tacnav.sapper.activation

import com.tacnav.sapper.domain.ActivationResult
import com.tacnav.sapper.domain.CatalogIcon
import com.tacnav.sapper.domain.MapScope
import com.tacnav.sapper.domain.ServiceConfiguration
import com.tacnav.sapper.domain.TeamScope
import com.tacnav.sapper.network.AuthorizationLostException
import com.tacnav.sapper.security.SettingsJson
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

interface ActivationApi {
    suspend fun activate(payload: ActivationPayload): ActivationResult

    suspend fun fetchIconCatalog(activation: ActivationResult): List<CatalogIcon>
}

class ActivationRejectedException(message: String) : IOException(message)

class HttpActivationApi(
    private val client: OkHttpClient = OkHttpClient(),
) : ActivationApi {
    override suspend fun activate(payload: ActivationPayload): ActivationResult {
        val body = JSONObject()
            .put("code", payload.oneTimeCode)
            .put("package_id", PACKAGE_ID)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(payload.serverUrl + ACTIVATION_PATH)
            .post(body)
            .build()
        val response = client.executeForText(request)
        if (response.code !in 200..299) {
            val message = runCatching {
                JSONObject(response.body).optString("message")
            }.getOrNull().orEmpty()
            throw ActivationRejectedException(
                message.ifBlank { "Activation failed (${response.code})." },
            )
        }
        return parseActivation(payload.serverUrl, JSONObject(response.body)).validated()
    }

    override suspend fun fetchIconCatalog(activation: ActivationResult): List<CatalogIcon> {
        val request = Request.Builder()
            .url(activation.serverUrl + activation.services.iconCatalogPath)
            .header("Authorization", "Bearer ${activation.bearerCredential}")
            .get()
            .build()
        val response = client.executeForText(request)
        if (response.code == 401) throw AuthorizationLostException()
        if (response.code !in 200..299) {
            throw IOException("Icon catalog request failed (${response.code}).")
        }
        val icons = JSONArray(response.body)
        return List(icons.length()) { index ->
            val icon = icons.getJSONObject(index)
            CatalogIcon(
                id = icon.getLong("id"),
                title = icon.getString("title"),
                imageUrl = icon.optString("url"),
            )
        }
    }

    private fun parseActivation(serverUrl: String, root: JSONObject): ActivationResult {
        val reference = root.optJSONObject("reference_settings") ?: JSONObject()
        return ActivationResult(
            serverUrl = serverUrl,
            applicationId = root.getLong("application_id"),
            installationId = root.optString("installation_id"),
            bearerCredential = root.getString("credential"),
            maps = parseMaps(root.getJSONArray("allowed_maps")),
            defaultMapId = root.getLong("default_map_id"),
            defaultTeamId = root.getLong("default_team_id"),
            referenceSettings = SettingsJson.decode(reference),
            settingsPin = reference.optString("settings_pin", DEFAULT_PIN).ifBlank { DEFAULT_PIN },
            services = ServiceConfiguration(),
        )
    }

    private fun parseMaps(scopes: JSONArray): List<MapScope> = List(scopes.length()) { mapIndex ->
        val map = scopes.getJSONObject(mapIndex)
        val teams = map.getJSONArray("teams")
        MapScope(
            id = map.getLong("id"),
            name = map.getString("name"),
            teams = List(teams.length()) { teamIndex ->
                val team = teams.getJSONObject(teamIndex)
                TeamScope(team.getLong("id"), team.getString("name"))
            },
        )
    }

    private data class TextResponse(val code: Int, val body: String)

    private suspend fun OkHttpClient.executeForText(request: Request): TextResponse =
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
        const val PACKAGE_ID = "com.tacnav.sapper"
        const val DEFAULT_PIN = "4321"
        private const val ACTIVATION_PATH = "/wp-json/tacnav-devices/v1/activate"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
