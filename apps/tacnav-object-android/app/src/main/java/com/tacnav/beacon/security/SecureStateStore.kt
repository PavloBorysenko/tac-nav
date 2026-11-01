package com.tacnav.beacon.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.tacnav.beacon.domain.ActivationResult
import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.MapScope
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.ObjectVisibility
import com.tacnav.beacon.domain.TeamScope
import com.tacnav.beacon.game.Fix
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.GameStore
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.SessionSnapshot
import com.tacnav.beacon.game.WreckPlan
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

data class LocalState(
    val activation: ActivationResult,
    val settings: ObjectSettings,
    val context: ActiveContext,
    val pin: PinRecord,
    val pinThrottle: PinThrottleState = PinThrottleState(),
    val pendingFcmToken: String? = null,
)

/** The server's reference field names double as the stored format. */
object SettingsJson {
    fun encode(value: ObjectSettings): JSONObject = JSONObject()
        .put("object_name", value.objectName)
        .put("description", value.description)
        .put("icon_id", value.iconId ?: 0)
        .put("destroyed_icon_id", value.destroyedIconId ?: 0)
        .put("hits_to_destroy", value.hitsToDestroy)
        .put("size_a_m", value.sizeAMetres ?: JSONObject.NULL)
        .put("size_b_m", value.sizeBMetres ?: JSONObject.NULL)
        .put("activation_seconds", value.activationSeconds)
        .put("visibility", value.visibility.wireValue)
        .put("random_area_enabled", value.randomAreaEnabled)
        .put("random_area_radius_m", value.randomAreaRadiusMetres)
        .put("auto_deactivate_m", value.autoDeactivateMetres)
        .put("max_location_accuracy_m", value.maxLocationAccuracyMetres)
        .put("auto_activate_enabled", value.autoActivateEnabled)
        .put("auto_activate_seconds", value.autoActivateSeconds)
        .put("max_downtime_minutes", value.maxDowntimeMinutes ?: JSONObject.NULL)
        .put("downtime_reset_minutes", value.downtimeResetMinutes ?: JSONObject.NULL)

    fun decode(json: JSONObject): ObjectSettings {
        val defaults = ObjectSettings()
        return ObjectSettings(
            objectName = json.optString("object_name").ifBlank { defaults.objectName },
            description = json.optString("description"),
            iconId = json.optLong("icon_id").takeIf { it > 0 },
            destroyedIconId = json.optLong("destroyed_icon_id").takeIf { it > 0 },
            hitsToDestroy = json.optInt("hits_to_destroy", defaults.hitsToDestroy),
            sizeAMetres = json.nullableDouble("size_a_m"),
            sizeBMetres = json.nullableDouble("size_b_m"),
            activationSeconds = json.optLong("activation_seconds", defaults.activationSeconds),
            visibility = ObjectVisibility.entries
                .firstOrNull { it.wireValue == json.optString("visibility") }
                ?: defaults.visibility,
            randomAreaEnabled = json.optBoolean("random_area_enabled"),
            randomAreaRadiusMetres = json.optDouble("random_area_radius_m", 0.0),
            autoDeactivateMetres = json.optDouble("auto_deactivate_m", 0.0),
            maxLocationAccuracyMetres = json.optDouble(
                "max_location_accuracy_m",
                defaults.maxLocationAccuracyMetres,
            ),
            autoActivateEnabled = json.optBoolean("auto_activate_enabled"),
            autoActivateSeconds = json.optLong("auto_activate_seconds"),
            maxDowntimeMinutes = json.nullableLong("max_downtime_minutes"),
            downtimeResetMinutes = json.nullableLong("downtime_reset_minutes"),
        )
    }
}

class SecureStateStore(context: Context) : GameStore {
    private val stateFile = AtomicFile(File(context.noBackupFilesDir, STATE_FILE))
    private val gameFile = AtomicFile(File(context.noBackupFilesDir, GAME_FILE))

    @Synchronized
    fun load(): LocalState? {
        if (!stateFile.baseFile.exists()) return null
        return runCatching {
            decode(JSONObject(String(decrypt(stateFile.readFully()), Charsets.UTF_8)))
        }.getOrNull()
    }

    @Synchronized
    fun save(state: LocalState) {
        state.settings.validated()
        write(stateFile, encode(state))
    }

    @Synchronized
    fun loadGame(): GameState? {
        if (!gameFile.baseFile.exists()) return null
        return runCatching {
            GameJson.decode(JSONObject(String(decrypt(gameFile.readFully()), Charsets.UTF_8)))
        }.getOrNull()
    }

    @Synchronized
    override fun saveGame(state: GameState) {
        write(gameFile, GameJson.encode(state))
    }

    @Synchronized
    fun clear() {
        stateFile.delete()
        gameFile.delete()
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun write(file: AtomicFile, json: JSONObject) {
        val bytes = encrypt(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

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
        .put("settings", SettingsJson.encode(state.settings))
        .put("map_id", state.context.mapId)
        .put("team_id", state.context.teamId)
        .put("pin_salt", state.pin.salt.base64())
        .put("pin_hash", state.pin.hash.base64())
        .put("pin_failures", state.pinThrottle.failedAttempts)
        .put("pin_blocked_until", state.pinThrottle.blockedUntilEpochMillis)
        .put("pending_fcm_token", state.pendingFcmToken ?: JSONObject.NULL)

    private fun decode(json: JSONObject): LocalState = LocalState(
        activation = decodeActivation(json.getJSONObject("activation")),
        settings = SettingsJson.decode(json.getJSONObject("settings")),
        context = ActiveContext(json.getLong("map_id"), json.getLong("team_id")),
        pin = PinRecord(json.getString("pin_salt").bytes(), json.getString("pin_hash").bytes()),
        pinThrottle = PinThrottleState(
            json.optInt("pin_failures"),
            json.optLong("pin_blocked_until"),
        ),
        pendingFcmToken = json.nullableString("pending_fcm_token"),
    )

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
                                        put(JSONObject().put("id", team.id).put("name", team.name))
                                    }
                                },
                            ),
                    )
                }
            },
        )
        .put("default_map_id", value.defaultMapId)
        .put("default_team_id", value.defaultTeamId)
        .put("reference", SettingsJson.encode(value.referenceSettings))
        .put("settings_pin", value.settingsPin)

    private fun decodeActivation(json: JSONObject): ActivationResult {
        val maps = json.getJSONArray("maps")
        return ActivationResult(
            serverUrl = json.getString("server_url"),
            applicationId = json.getLong("application_id"),
            installationId = json.getString("installation_id"),
            bearerCredential = json.getString("credential"),
            maps = List(maps.length()) { mapIndex ->
                val map = maps.getJSONObject(mapIndex)
                val teams = map.getJSONArray("teams")
                MapScope(
                    map.getLong("id"),
                    map.getString("name"),
                    List(teams.length()) { teamIndex ->
                        val team = teams.getJSONObject(teamIndex)
                        TeamScope(team.getLong("id"), team.getString("name"))
                    },
                )
            },
            defaultMapId = json.getLong("default_map_id"),
            defaultTeamId = json.getLong("default_team_id"),
            referenceSettings = SettingsJson.decode(json.getJSONObject("reference")),
            settingsPin = json.getString("settings_pin"),
        )
    }

    private companion object {
        const val STATE_FILE = "beacon_state.enc"
        const val GAME_FILE = "beacon_game.enc"
        const val KEY_ALIAS = "tacnav_beacon_state"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** Runtime-only fields stay out, so a restored process writes to the map at once. */
object GameJson {
    fun encode(state: GameState): JSONObject = JSONObject()
        .put("wreck_key", state.wreckKey)
        .put("life", state.life.name)
        .put("armed", state.armed)
        .put("automatic_deployment", state.automaticDeployment)
        .put("hits", state.hits)
        .put("applied_events", JSONArray(state.appliedEvents.toList()))
        .put("life_object_ids", JSONArray(state.lifeObjectIds.toList()))
        .put("object_id", state.objectId ?: JSONObject.NULL)
        .put("area_id", state.areaId ?: JSONObject.NULL)
        .put("wreck", state.wreck?.let(::encodeWreck) ?: JSONObject.NULL)
        .put("pending_deletes", JSONArray(state.pendingDeletes.toList()))
        .put("session", state.session?.let(::encodeSnapshot) ?: JSONObject.NULL)
        .put("last_session", state.lastSession?.let(::encodeSnapshot) ?: JSONObject.NULL)
        .put("deployed_millis", state.deployedMillis)
        .put("session_millis", state.sessionMillis)
        .put("active_streak_millis", state.activeStreakMillis)
        .put("anchor", state.anchor?.let(::encodeFix) ?: JSONObject.NULL)
        .put("position", state.position?.let(::encodeFix) ?: JSONObject.NULL)
        .put("last_fix", state.lastFix?.let(::encodeFix) ?: JSONObject.NULL)
        .put("area_lat", state.areaCentre?.latitude ?: JSONObject.NULL)
        .put("area_lng", state.areaCentre?.longitude ?: JSONObject.NULL)
        .put("still_origin", state.stillOrigin?.let(::encodeFix) ?: JSONObject.NULL)
        .put("still_millis", state.stillMillis)
        .put("away_count", state.awayCount)
        .put("downtime_started", state.downtimeStarted)
        .put("downtime_remaining_millis", state.downtimeRemainingMillis ?: JSONObject.NULL)
        .put("last_activity_epoch_millis", state.lastActivityEpochMillis)

    fun decode(json: JSONObject): GameState = GameState(
        wreckKey = json.getString("wreck_key"),
        life = LifeState.valueOf(json.getString("life")),
        armed = json.optBoolean("armed", true),
        automaticDeployment = json.optBoolean("automatic_deployment"),
        hits = json.optInt("hits"),
        appliedEvents = json.getJSONArray("applied_events").let { events ->
            List(events.length(), events::getString).toSet()
        },
        lifeObjectIds = json.getJSONArray("life_object_ids").longs(),
        objectId = json.nullableLong("object_id"),
        areaId = json.nullableLong("area_id"),
        wreck = json.optJSONObject("wreck")?.let(::decodeWreck),
        pendingDeletes = json.getJSONArray("pending_deletes").longs(),
        session = json.optJSONObject("session")?.let(::decodeSnapshot),
        lastSession = json.optJSONObject("last_session")?.let(::decodeSnapshot),
        deployedMillis = json.optLong("deployed_millis"),
        sessionMillis = json.optLong("session_millis"),
        activeStreakMillis = json.optLong("active_streak_millis"),
        anchor = json.optJSONObject("anchor")?.let(::decodeFix),
        position = json.optJSONObject("position")?.let(::decodeFix),
        lastFix = json.optJSONObject("last_fix")?.let(::decodeFix),
        areaCentre = json.nullableDouble("area_lat")?.let { latitude ->
            json.nullableDouble("area_lng")?.let { Coordinate(latitude, it) }
        },
        stillOrigin = json.optJSONObject("still_origin")?.let(::decodeFix),
        stillMillis = json.optLong("still_millis"),
        awayCount = json.optInt("away_count"),
        downtimeStarted = json.optBoolean("downtime_started"),
        downtimeRemainingMillis = json.nullableLong("downtime_remaining_millis"),
        lastActivityEpochMillis = json.optLong("last_activity_epoch_millis"),
    )

    private fun encodeFix(fix: Fix): JSONObject = JSONObject()
        .put("lat", fix.latitude)
        .put("lng", fix.longitude)
        .put("accuracy", fix.accuracyMetres)
        .put("epoch", fix.epochMillis)

    private fun decodeFix(json: JSONObject): Fix = Fix(
        json.getDouble("lat"),
        json.getDouble("lng"),
        json.getDouble("accuracy"),
        json.getLong("epoch"),
    )

    private fun encodeSnapshot(value: SessionSnapshot): JSONObject = JSONObject()
        .put("settings", SettingsJson.encode(value.settings))
        .put("map_id", value.context.mapId)
        .put("team_id", value.context.teamId)
        .put("heading", value.headingDegrees)

    private fun decodeSnapshot(json: JSONObject): SessionSnapshot = SessionSnapshot(
        SettingsJson.decode(json.getJSONObject("settings")),
        ActiveContext(json.getLong("map_id"), json.getLong("team_id")),
        json.getDouble("heading"),
    )

    private fun encodeWreck(value: WreckPlan): JSONObject = JSONObject()
        .put("key", value.key)
        .put("lat", value.latitude)
        .put("lng", value.longitude)
        .put("accuracy", value.accuracyMetres)
        .put("snapshot", encodeSnapshot(value.snapshot))

    private fun decodeWreck(json: JSONObject): WreckPlan = WreckPlan(
        json.getString("key"),
        json.getDouble("lat"),
        json.getDouble("lng"),
        json.getDouble("accuracy"),
        decodeSnapshot(json.getJSONObject("snapshot")),
    )
}

private fun JSONObject.nullableDouble(name: String): Double? =
    if (isNull(name)) null else optDouble(name).takeUnless(Double::isNaN)

private fun JSONObject.nullableLong(name: String): Long? =
    if (isNull(name)) null else optLong(name)

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name)

private fun JSONArray.longs(): Set<Long> = List(length(), ::getLong).toSet()

private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.bytes(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
