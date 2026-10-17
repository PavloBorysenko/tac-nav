package com.tacnav.mortar.activation

import com.tacnav.mortar.domain.ActivationResult
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.CatalogIcon
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.MapScope
import com.tacnav.mortar.domain.ServiceConfiguration
import com.tacnav.mortar.domain.TeamScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ActivationApi {
    suspend fun activate(payload: ActivationPayload): ActivationResult

    suspend fun registerFcmToken(
        activation: ActivationResult,
        token: String,
    )

    suspend fun fetchIconCatalog(activation: ActivationResult): List<CatalogIcon>

    suspend fun downloadIcon(
        icon: CatalogIcon,
    ): ByteArray
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
                message.ifBlank {
                    if (response.code == 409 || response.code == 410) {
                        "This activation code is invalid, expired, or already used."
                    } else {
                        "Activation failed (${response.code})."
                    }
                },
            )
        }
        return parseActivation(payload.serverUrl, JSONObject(response.body)).validated()
    }

    override suspend fun registerFcmToken(
        activation: ActivationResult,
        token: String,
    ) {
        val body = JSONObject()
            .put("installation_id", activation.installationId)
            .put("token", token)
            .toString()
            .toRequestBody(JSON)
        val request = authorizedRequest(
            activation,
            activation.services.fcmRegistrationPath,
        ).post(body).build()
        val response = client.executeForText(request)
        if (response.code !in 200..299) {
            throw IOException("Firebase registration was refused (${response.code}).")
        }
    }

    override suspend fun fetchIconCatalog(
        activation: ActivationResult,
    ): List<CatalogIcon> {
        val request = authorizedRequest(
            activation,
            activation.services.iconCatalogPath,
        ).get().build()
        val response = client.executeForText(request)
        if (response.code !in 200..299) {
            throw IOException("Icon catalog request failed (${response.code}).")
        }
        val icons = org.json.JSONArray(response.body)
        return buildList {
            for (index in 0 until icons.length()) {
                val icon = icons.getJSONObject(index)
                add(
                    CatalogIcon(
                        id = icon.getLong("id"),
                        title = icon.getString("title"),
                        imageUrl = icon.optString("url").ifBlank {
                            icon.optString("image_url")
                        },
                    ),
                )
            }
        }
    }

    override suspend fun downloadIcon(
        icon: CatalogIcon,
    ): ByteArray {
        val request = Request.Builder()
            .url(icon.imageUrl)
            .build()
        return client.executeForBytes(request)
    }

    private fun authorizedRequest(
        activation: ActivationResult,
        path: String,
    ): Request.Builder = Request.Builder()
        .url(activation.serverUrl + path)
        .header("Authorization", "Bearer ${activation.bearerCredential}")
        .header("X-TacNav-Application", activation.applicationId.toString())

    private fun parseActivation(serverUrl: String, root: JSONObject): ActivationResult {
        val maps = if (root.has("allowed_maps")) {
            val scopes = root.getJSONArray("allowed_maps")
            buildList {
                for (mapIndex in 0 until scopes.length()) {
                    val map = scopes.getJSONObject(mapIndex)
                    val teamArray = map.getJSONArray("teams")
                    val teams = buildList {
                        for (teamIndex in 0 until teamArray.length()) {
                            val team = teamArray.getJSONObject(teamIndex)
                            add(TeamScope(team.getLong("id"), team.getString("name")))
                        }
                    }
                    add(MapScope(map.getLong("id"), map.getString("name"), teams))
                }
            }
        } else {
            val teamIds = root.optJSONArray("allowed_team_ids").orEmptyLongs()
            val teams = teamIds.map { TeamScope(it, "Team $it") }
            root.optJSONArray("allowed_map_ids").orEmptyLongs().map { mapId ->
                MapScope(mapId, "Map $mapId", teams)
            }
        }
        val reference = root.optJSONObject("reference_settings") ?: JSONObject()
        val services = root.optJSONObject("services") ?: JSONObject()
        val defaults = GameplaySettings()
        return ActivationResult(
            serverUrl = serverUrl,
            applicationId = root.getLong("application_id"),
            installationId = root.optString("installation_id").ifBlank {
                root.optLong("installation_id").toString()
            },
            bearerCredential = root.getString("credential"),
            maps = maps,
            defaultMapId = root.getLong("default_map_id"),
            defaultTeamId = root.getLong("default_team_id"),
            referenceSettings = GameplaySettings(
                firingModes = reference.stringSet("firing_modes")
                    .mapNotNull { value ->
                        FiringMode.entries.singleOrNull { it.wireValue == value }
                    }
                    .toSet()
                    .ifEmpty { setOf(FiringMode.BUTTON) },
                aimingModes = reference.stringSet("aiming_modes")
                    .mapNotNull { value ->
                        AimingMode.entries.singleOrNull { it.wireValue == value }
                    }
                    .toSet()
                    .ifEmpty { setOf(AimingMode.FREE) },
                approximateRadiusMetres =
                    reference.optDouble("approximate_radius_m", 30.0),
                elevationReferenceDegrees =
                    reference.optDouble("elevation_reference_degrees", 60.0),
                impactIconId = reference.optLong("impact_icon_id").takeIf { it > 0 },
                mortarName = reference.optString("mortar_name", defaults.mortarName)
                    .ifBlank { defaults.mortarName },
                minElevationDegrees = reference.optDouble("min_elevation_degrees", 60.0),
                maxElevationDegrees = reference.optDouble("max_elevation_degrees", 85.0),
                maxDistanceMetres = reference.optDouble("max_distance_m", 200.0),
                minDistanceMetres = reference.optDouble("min_distance_m", 10.0),
                damageRadiusMetres = reference.optDouble("damage_radius_m", 5.0),
                edgeProbability = reference.optDouble("edge_probability", 0.5),
                flightDurationSeconds = reference.optLong("flight_duration_seconds", 20),
                impactTtlSeconds = reference.optLong("impact_ttl_seconds", 30),
                maxLocationAccuracyMetres = reference.optDouble("max_location_accuracy_m", 15.0),
                maxLocationAgeSeconds = reference.optLong("max_location_age_seconds", 15),
                acousticThresholdDbfs = reference.optDouble("acoustic_threshold_dbfs", -20.0),
                acousticImpulseMs = reference.optLong("acoustic_impulse_ms", 80),
                acousticDebounceMs = reference.optLong("acoustic_debounce_ms", 400),
                markOwnPositionOnShot = reference.optBoolean("mark_own_position_on_shot", false),
            ),
            services = ServiceConfiguration(
                iconCatalogPath = services.optString(
                    "icon_catalog_path",
                    "/wp-json/tacnav-devices/v1/icons",
                ),
                fcmRegistrationPath = services.optString(
                    "fcm_registration_path",
                    "/wp-json/tacnav-devices/v1/fcm",
                ),
            ),
        )
    }

    private fun org.json.JSONArray?.orEmptyLongs(): List<Long> {
        if (this == null) return emptyList()
        return List(length()) { index -> getLong(index) }
    }

    private fun JSONObject.stringSet(name: String): Set<String> {
        val values = optJSONArray(name) ?: return emptySet()
        return buildSet {
            for (index in 0 until values.length()) {
                add(values.getString(index))
            }
        }
    }

    private data class TextResponse(val code: Int, val body: String)

    private suspend fun OkHttpClient.executeForText(request: Request): TextResponse =
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

    private suspend fun OkHttpClient.executeForBytes(request: Request): ByteArray =
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
                            if (it.code !in 200..299) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IOException("Icon download failed (${it.code})."),
                                    )
                                }
                            } else if (continuation.isActive) {
                                continuation.resume(it.body?.bytes() ?: ByteArray(0))
                            }
                        }
                    }
                },
            )
        }

    private companion object {
        const val PACKAGE_ID = "com.tacnav.mortar"
        const val ACTIVATION_PATH = "/wp-json/tacnav-devices/v1/activate"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
