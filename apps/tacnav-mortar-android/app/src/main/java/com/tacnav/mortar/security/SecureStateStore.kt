package com.tacnav.mortar.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.tacnav.mortar.domain.ActivationResult
import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.HitFact
import com.tacnav.mortar.domain.MapScope
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ServiceConfiguration
import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.domain.TeamScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CalibrationState(
    val directionOffsetDegrees: Double? = null,
    val elevationOffsetDegrees: Double? = null,
)

data class LocalState(
    val activation: ActivationResult,
    val settings: GameplaySettings,
    val context: ActiveContext,
    val pin: PinRecord,
    val pinThrottle: PinThrottleState = PinThrottleState(),
    val calibration: CalibrationState = CalibrationState(),
    val activeFiringMode: FiringMode = settings.firingModes.first(),
    val activeAimingMode: AimingMode = settings.aimingModes.first(),
    val pendingFcmToken: String? = null,
    val activeSession: Boolean = false,
)

class SecureStateStore(
    context: Context,
    private val pinPolicy: PinPolicy = PinPolicy(),
) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, STATE_FILE))
    private val shotFile = AtomicFile(File(context.noBackupFilesDir, SHOTS_FILE))
    private val iconCache = File(context.noBackupFilesDir, ICON_CACHE_FILE)

    @Synchronized
    fun load(): LocalState? {
        if (!atomicFile.baseFile.exists()) return null
        return runCatching {
            decode(JSONObject(String(decrypt(atomicFile.readFully()), Charsets.UTF_8)))
        }.getOrNull()
    }

    @Synchronized
    fun replaceActivation(activation: ActivationResult): LocalState {
        clear()
        val state = LocalState(
            activation = activation.validated(),
            settings = activation.referenceSettings.validated(),
            context = ActiveContext(activation.defaultMapId, activation.defaultTeamId),
            pin = pinPolicy.create(PinPolicy.DEFAULT_PIN),
        )
        save(state)
        return state
    }

    @Synchronized
    fun save(state: LocalState) {
        state.settings.validated()
        val bytes = encrypt(encode(state).toString().toByteArray(Charsets.UTF_8))
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    fun clear() {
        atomicFile.delete()
        shotFile.delete()
        iconCache.delete()
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    @Synchronized
    fun loadShots(): List<ShotRecord> {
        if (!shotFile.baseFile.exists()) return emptyList()
        return runCatching {
            decodeShots(JSONObject(String(decrypt(shotFile.readFully()), Charsets.UTF_8)))
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveShots(shots: List<ShotRecord>) {
        val bytes = encrypt(encodeShots(shots).toString().toByteArray(Charsets.UTF_8))
        val stream = shotFile.startWrite()
        try {
            stream.write(bytes)
            shotFile.finishWrite(stream)
        } catch (error: Throwable) {
            shotFile.failWrite(stream)
            throw error
        }
    }

    fun cacheSelectedIcon(bytes: ByteArray) {
        iconCache.writeBytes(bytes)
    }

    fun clearSelectedIcon() {
        iconCache.delete()
    }

    fun selectedIconFile(): File? = iconCache.takeIf(File::exists)

    private fun encrypt(clearText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(clearText)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "Encrypted state is incomplete." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, blob.copyOfRange(0, IV_SIZE)),
        )
        return cipher.doFinal(blob.copyOfRange(IV_SIZE, blob.size))
    }

    private fun secretKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun encode(state: LocalState): JSONObject = JSONObject()
        .put("activation", encodeActivation(state.activation))
        .put("settings", encodeSettings(state.settings))
        .put(
            "context",
            JSONObject()
                .put("map_id", state.context.mapId)
                .put("team_id", state.context.teamId),
        )
        .put("pin_salt", state.pin.salt.base64())
        .put("pin_hash", state.pin.hash.base64())
        .put("pin_failures", state.pinThrottle.failedAttempts)
        .put("pin_blocked_until", state.pinThrottle.blockedUntilEpochMillis)
        .put(
            "calibration",
            JSONObject()
                .putNullable("direction", state.calibration.directionOffsetDegrees)
                .putNullable("elevation", state.calibration.elevationOffsetDegrees),
        )
        .put("active_firing_mode", state.activeFiringMode.wireValue)
        .put("active_aiming_mode", state.activeAimingMode.wireValue)
        .putNullable("pending_fcm_token", state.pendingFcmToken)
        .put("active_session", state.activeSession)

    private fun encodeActivation(value: ActivationResult): JSONObject = JSONObject()
        .put("server_url", value.serverUrl)
        .put("application_id", value.applicationId)
        .put("installation_id", value.installationId)
        .put("credential", value.bearerCredential)
        .put(
            "maps",
            JSONArray().apply {
                value.maps.forEach { map ->
                    put(
                        JSONObject()
                            .put("id", map.id)
                            .put("name", map.name)
                            .put(
                                "teams",
                                JSONArray().apply {
                                    map.teams.forEach { team ->
                                        put(
                                            JSONObject()
                                                .put("id", team.id)
                                                .put("name", team.name),
                                        )
                                    }
                                },
                            ),
                    )
                }
            },
        )
        .put("default_map_id", value.defaultMapId)
        .put("default_team_id", value.defaultTeamId)
        .put("reference", encodeSettings(value.referenceSettings))
        .put(
            "services",
            JSONObject()
                .put("icons", value.services.iconCatalogPath)
                .put("fcm", value.services.fcmRegistrationPath),
        )

    private fun encodeSettings(value: GameplaySettings): JSONObject = JSONObject()
        .put("firing", JSONArray(value.firingModes.map(FiringMode::wireValue)))
        .put("aiming", JSONArray(value.aimingModes.map(AimingMode::wireValue)))
        .put("radius", value.approximateRadiusMetres)
        .put("elevation", value.elevationReferenceDegrees)
        .putNullable("icon_id", value.impactIconId)
        .put("mortar_name", value.mortarName)
        .put("min_elevation", value.minElevationDegrees)
        .put("max_elevation", value.maxElevationDegrees)
        .put("max_distance", value.maxDistanceMetres)
        .put("min_distance", value.minDistanceMetres)
        .put("damage_radius", value.damageRadiusMetres)
        .put("edge_probability", value.edgeProbability)
        .put("flight_seconds", value.flightDurationSeconds)
        .put("ttl_seconds", value.impactTtlSeconds)
        .put("gps_accuracy", value.maxLocationAccuracyMetres)
        .put("gps_age", value.maxLocationAgeSeconds)
        .put("acoustic_threshold", value.acousticThresholdDbfs)
        .put("acoustic_impulse", value.acousticImpulseMs)
        .put("acoustic_debounce", value.acousticDebounceMs)
        .put("mark_own_position", value.markOwnPositionOnShot)

    private fun decode(root: JSONObject): LocalState {
        val activation = decodeActivation(root.getJSONObject("activation"))
        val context = root.getJSONObject("context")
        val calibration = root.getJSONObject("calibration")
        return LocalState(
            activation = activation,
            settings = decodeSettings(root.getJSONObject("settings")),
            context = ActiveContext(
                context.getLong("map_id"),
                context.getLong("team_id"),
            ),
            pin = PinRecord(
                root.getString("pin_salt").base64Bytes(),
                root.getString("pin_hash").base64Bytes(),
            ),
            pinThrottle = PinThrottleState(
                root.optInt("pin_failures"),
                root.optLong("pin_blocked_until"),
            ),
            calibration = CalibrationState(
                calibration.nullableDouble("direction"),
                calibration.nullableDouble("elevation"),
            ),
            activeFiringMode = root.optString(
                "active_firing_mode",
                activation.referenceSettings.firingModes.first().wireValue,
            ).let { wire -> FiringMode.entries.single { it.wireValue == wire } },
            activeAimingMode = root.optString(
                "active_aiming_mode",
                activation.referenceSettings.aimingModes.first().wireValue,
            ).let { wire -> AimingMode.entries.single { it.wireValue == wire } },
            pendingFcmToken = root.nullableString("pending_fcm_token"),
            activeSession = false,
        )
    }

    private fun decodeActivation(value: JSONObject): ActivationResult {
        val mapsArray = value.getJSONArray("maps")
        val maps = buildList {
            for (mapIndex in 0 until mapsArray.length()) {
                val map = mapsArray.getJSONObject(mapIndex)
                val teamsArray = map.getJSONArray("teams")
                val teams = buildList {
                    for (teamIndex in 0 until teamsArray.length()) {
                        val team = teamsArray.getJSONObject(teamIndex)
                        add(TeamScope(team.getLong("id"), team.getString("name")))
                    }
                }
                add(MapScope(map.getLong("id"), map.getString("name"), teams))
            }
        }
        val services = value.getJSONObject("services")
        return ActivationResult(
            serverUrl = value.getString("server_url"),
            applicationId = value.getLong("application_id"),
            installationId = value.getString("installation_id"),
            bearerCredential = value.getString("credential"),
            maps = maps,
            defaultMapId = value.getLong("default_map_id"),
            defaultTeamId = value.getLong("default_team_id"),
            referenceSettings = decodeSettings(value.getJSONObject("reference")),
            services = ServiceConfiguration(
                services.getString("icons"),
                services.getString("fcm"),
            ),
        )
    }

    private fun decodeSettings(value: JSONObject): GameplaySettings = GameplaySettings(
        firingModes = value.getJSONArray("firing").stringValues()
            .map { wire -> FiringMode.entries.single { it.wireValue == wire } }
            .toSet(),
        aimingModes = value.getJSONArray("aiming").stringValues()
            .map { wire -> AimingMode.entries.single { it.wireValue == wire } }
            .toSet(),
        approximateRadiusMetres = value.getDouble("radius"),
        elevationReferenceDegrees = value.getDouble("elevation"),
        impactIconId = value.optLong("icon_id").takeIf {
            value.has("icon_id") && !value.isNull("icon_id")
        },
        mortarName = value.optString("mortar_name", "Mortar 1"),
        minElevationDegrees = value.optDouble("min_elevation", 60.0),
        maxElevationDegrees = value.optDouble("max_elevation", 85.0),
        maxDistanceMetres = value.optDouble("max_distance", 200.0),
        minDistanceMetres = value.optDouble("min_distance", 10.0),
        damageRadiusMetres = value.optDouble("damage_radius", 5.0),
        edgeProbability = value.optDouble("edge_probability", 0.5),
        flightDurationSeconds = value.optLong("flight_seconds", 20),
        impactTtlSeconds = value.optLong("ttl_seconds", 30),
        maxLocationAccuracyMetres = value.optDouble("gps_accuracy", 15.0).let { accuracy ->
            if (accuracy == 10.0 || accuracy == 20.0) 15.0 else accuracy
        },
        maxLocationAgeSeconds = value.optLong("gps_age", 15).let { age ->
            if (age == 5L) 15L else age
        },
        acousticThresholdDbfs = value.optDouble("acoustic_threshold", -20.0),
        acousticImpulseMs = value.optLong("acoustic_impulse", 80),
        acousticDebounceMs = value.optLong("acoustic_debounce", 400),
        markOwnPositionOnShot = value.optBoolean("mark_own_position", false),
    ).validated()

    private fun encodeShots(shots: List<ShotRecord>): JSONObject = JSONObject().put(
        "shots",
        JSONArray().apply {
            shots.forEach { shot ->
                put(
                    JSONObject()
                        .put("shot_id", shot.shotId)
                        .put("range", shot.rangeMetres)
                        .put("impact_lat", shot.impactLatitude)
                        .put("impact_lng", shot.impactLongitude)
                        .put("due_elapsed", shot.dueAtElapsedMillis)
                        .put("due_epoch", shot.dueAtEpochMillis)
                        .put("phase", shot.phase.name)
                        .put(
                            "rolls",
                            JSONObject().apply {
                                shot.rolls.forEach { (id, roll) -> put(id.toString(), roll) }
                            },
                        )
                        .put(
                            "facts",
                            JSONArray().apply {
                                shot.facts.forEach { fact ->
                                    put(
                                        JSONObject()
                                            .put("id", fact.objectId)
                                            .put("distance", fact.distanceMetres)
                                            .put("probability", fact.probability)
                                            .put("roll", fact.roll),
                                    )
                                }
                            },
                        )
                        .put("hits", JSONArray(shot.hits))
                        .putNullable("failure", shot.failure)
                        .put(
                            "snapshot",
                            JSONObject()
                                .put("lat", shot.snapshot.latitude)
                                .put("lng", shot.snapshot.longitude)
                                .put("accuracy", shot.snapshot.accuracyMetres)
                                .put("age", shot.snapshot.ageSeconds)
                                .put("heading", shot.snapshot.trueHeadingDegrees)
                                .put("elevation", shot.snapshot.elevationDegrees)
                                .put("trigger", shot.snapshot.triggerEpochMillis),
                        ),
                )
            }
        },
    )

    private fun decodeShots(root: JSONObject): List<ShotRecord> {
        val array = root.optJSONArray("shots") ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val snapshot = item.getJSONObject("snapshot")
            val rollsJson = item.optJSONObject("rolls") ?: JSONObject()
            val factsJson = item.optJSONArray("facts") ?: JSONArray()
            val hitsJson = item.optJSONArray("hits") ?: JSONArray()
            ShotRecord(
                shotId = item.getString("shot_id"),
                snapshot = SensorSnapshot(
                    snapshot.getDouble("lat"),
                    snapshot.getDouble("lng"),
                    snapshot.getDouble("accuracy"),
                    snapshot.getDouble("age"),
                    snapshot.getDouble("heading"),
                    snapshot.getDouble("elevation"),
                    snapshot.getLong("trigger"),
                ),
                rangeMetres = item.getDouble("range"),
                impactLatitude = item.getDouble("impact_lat"),
                impactLongitude = item.getDouble("impact_lng"),
                dueAtElapsedMillis = item.getLong("due_elapsed"),
                dueAtEpochMillis = item.getLong("due_epoch"),
                phase = ShotPhase.valueOf(item.getString("phase")),
                rolls = rollsJson.keys().asSequence().associate { key ->
                    key.toLong() to rollsJson.getDouble(key)
                },
                facts = List(factsJson.length()) { factIndex ->
                    val fact = factsJson.getJSONObject(factIndex)
                    HitFact(
                        fact.getLong("id"),
                        fact.getDouble("distance"),
                        fact.getDouble("probability"),
                        fact.getDouble("roll"),
                    )
                },
                hits = List(hitsJson.length()) { hitIndex -> hitsJson.getLong(hitIndex) },
                failure = item.nullableString("failure"),
            )
        }
    }

    private fun JSONArray.stringValues(): List<String> =
        List(length()) { index -> getString(index) }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.nullableDouble(name: String): Double? =
        if (isNull(name)) null else getDouble(name)

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.base64Bytes(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tacnav_mortar_local_state"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val STATE_FILE = "mortar-state.enc"
        const val SHOTS_FILE = "mortar-shots.enc"
        const val ICON_CACHE_FILE = "selected-impact-icon"
    }
}
