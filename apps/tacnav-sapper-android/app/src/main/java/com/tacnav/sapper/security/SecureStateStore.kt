package com.tacnav.sapper.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.tacnav.sapper.domain.ActivationResult
import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MapScope
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.TeamScope
import com.tacnav.sapper.game.ClearProgress
import com.tacnav.sapper.game.Counters
import com.tacnav.sapper.game.Fix
import com.tacnav.sapper.game.GameState
import com.tacnav.sapper.game.GameStore
import com.tacnav.sapper.game.Laying
import com.tacnav.sapper.game.PendingWrite
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
    val settings: SapperSettings,
    val context: ActiveContext,
    val pin: PinRecord,
    val pinThrottle: PinThrottleState = PinThrottleState(),
)

/** The server's reference field names double as the stored format. */
object SettingsJson {
    fun encode(value: SapperSettings): JSONObject = JSONObject()
        .put("detect_radius_m", value.detectRadiusMetres)
        .put("detect_sector_deg", value.detectSectorDegrees)
        .put("mark_hold_seconds", value.markHoldSeconds)
        .put("clear_hold_seconds", value.clearHoldSeconds)
        .put("lay_hold_seconds", value.layHoldSeconds)
        .put("minefield_length_m", value.minefieldLengthMetres)
        .put("minefield_width_m", value.minefieldWidthMetres)
        .put("minefield_icon_id", value.minefieldIconId ?: 0)
        .put("no_mines_icon_id", value.noMinesIconId ?: 0)
        .put("max_minefields", value.maxMinefields)
        .put("max_no_mines_markers", value.maxNoMinesMarkers)
        .put("max_location_accuracy_m", value.maxLocationAccuracyMetres)

    fun decode(json: JSONObject): SapperSettings {
        val defaults = SapperSettings()
        return SapperSettings(
            detectRadiusMetres = json.optDouble("detect_radius_m", defaults.detectRadiusMetres),
            detectSectorDegrees = json.optDouble("detect_sector_deg", defaults.detectSectorDegrees),
            markHoldSeconds = json.optLong("mark_hold_seconds", defaults.markHoldSeconds),
            clearHoldSeconds = json.optLong("clear_hold_seconds", defaults.clearHoldSeconds),
            layHoldSeconds = json.optLong("lay_hold_seconds", defaults.layHoldSeconds),
            minefieldLengthMetres = json.optDouble("minefield_length_m", defaults.minefieldLengthMetres),
            minefieldWidthMetres = json.optDouble("minefield_width_m", defaults.minefieldWidthMetres),
            minefieldIconId = json.optLong("minefield_icon_id").takeIf { it > 0 },
            noMinesIconId = json.optLong("no_mines_icon_id").takeIf { it > 0 },
            maxMinefields = json.optInt("max_minefields", defaults.maxMinefields),
            maxNoMinesMarkers = json.optInt("max_no_mines_markers", defaults.maxNoMinesMarkers),
            maxLocationAccuracyMetres = json.optDouble(
                "max_location_accuracy_m",
                defaults.maxLocationAccuracyMetres,
            ),
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

    /** Removes the credential, settings, progress, and counters. */
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

    private fun decode(json: JSONObject): LocalState = LocalState(
        activation = decodeActivation(json.getJSONObject("activation")),
        settings = SettingsJson.decode(json.getJSONObject("settings")),
        context = ActiveContext(json.getLong("map_id"), json.getLong("team_id")),
        pin = PinRecord(json.getString("pin_salt").bytes(), json.getString("pin_hash").bytes()),
        pinThrottle = PinThrottleState(
            json.optInt("pin_failures"),
            json.optLong("pin_blocked_until"),
        ),
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
        const val STATE_FILE = "sapper_state.enc"
        const val GAME_FILE = "sapper_game.enc"
        const val KEY_ALIAS = "tacnav_sapper_state"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** Counters, holds, and pending writes; runtime fields such as press times stay out. */
object GameJson {
    fun encode(state: GameState): JSONObject = JSONObject()
        .put("minefields", state.counters.minefields)
        .put("no_mines_markers", state.counters.noMinesMarkers)
        .put("laying", state.laying?.let(::encodeLaying) ?: JSONObject.NULL)
        .put(
            "clear_progress",
            JSONArray().apply {
                state.clearProgress.forEach {
                    put(
                        JSONObject()
                            .put("field_id", it.fieldId)
                            .put("geometry", encodeGeometry(it.geometry))
                            .put("held_ms", it.heldMillis),
                    )
                }
            },
        )
        .put("outbox", JSONArray().apply { state.outbox.forEach { put(encodeWrite(it)) } })

    fun decode(json: JSONObject): GameState {
        val progress = json.optJSONArray("clear_progress") ?: JSONArray()
        val outbox = json.optJSONArray("outbox") ?: JSONArray()
        return GameState(
            counters = Counters(json.optInt("minefields"), json.optInt("no_mines_markers")),
            laying = json.optJSONObject("laying")?.let(::decodeLaying),
            clearProgress = List(progress.length()) { index ->
                val entry = progress.getJSONObject(index)
                ClearProgress(
                    entry.getLong("field_id"),
                    decodeGeometry(entry.getJSONObject("geometry")),
                    entry.getLong("held_ms"),
                )
            },
            outbox = List(outbox.length()) { decodeWrite(outbox.getJSONObject(it)) },
        )
    }

    private fun encodeLaying(value: Laying): JSONObject = JSONObject()
        .put("id", value.id)
        .put("start", encodeFix(value.start))
        .put("heading", value.headingDegrees)
        .put("held_ms", value.heldMillis)
        .put("locked", value.locked)

    private fun decodeLaying(json: JSONObject): Laying = Laying(
        json.getString("id"),
        decodeFix(json.getJSONObject("start")),
        json.getDouble("heading"),
        json.getLong("held_ms"),
        json.optBoolean("locked"),
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

    private fun encodeWrite(write: PendingWrite): JSONObject {
        val json = JSONObject()
            .put("key", write.key)
            .put("map_id", write.context.mapId)
            .put("team_id", write.context.teamId)
        return when (write) {
            is PendingWrite.CreateMinefield -> json
                .put("type", "minefield")
                .put("geometry", encodeGeometry(write.polygon))
                .put("icon_id", write.iconId ?: 0)
            is PendingWrite.CreateNoMines -> json
                .put("type", "no_mines")
                .put("lat", write.position.latitude)
                .put("lng", write.position.longitude)
                .put("accuracy", write.accuracyMetres)
                .put("icon_id", write.iconId ?: 0)
            is PendingWrite.MarkField -> json
                .put("type", "mark")
                .put("field_id", write.fieldId)
                .put("lat", write.centre.latitude)
                .put("lng", write.centre.longitude)
                .put("visible", JSONArray(write.visibleTeamIds))
                .put("updated_at", write.updatedAt)
            is PendingWrite.ExpireField -> json
                .put("type", "expire")
                .put("field_id", write.fieldId)
        }
    }

    private fun decodeWrite(json: JSONObject): PendingWrite {
        val key = json.getString("key")
        val context = ActiveContext(json.getLong("map_id"), json.getLong("team_id"))
        val icon = json.optLong("icon_id").takeIf { it > 0 }
        return when (json.getString("type")) {
            "minefield" -> PendingWrite.CreateMinefield(
                key,
                context,
                decodeGeometry(json.getJSONObject("geometry")) as Geometry.Polygon,
                icon,
            )
            "no_mines" -> PendingWrite.CreateNoMines(
                key,
                context,
                Coordinate(json.getDouble("lat"), json.getDouble("lng")),
                json.getDouble("accuracy"),
                icon,
            )
            "mark" -> {
                val visible = json.getJSONArray("visible")
                PendingWrite.MarkField(
                    key,
                    context,
                    json.getLong("field_id"),
                    Coordinate(json.getDouble("lat"), json.getDouble("lng")),
                    List(visible.length(), visible::getLong),
                    json.getString("updated_at"),
                )
            }
            else -> PendingWrite.ExpireField(key, context, json.getLong("field_id"))
        }
    }

    private fun encodeGeometry(geometry: Geometry): JSONObject = when (geometry) {
        is Geometry.Marker -> JSONObject()
            .put("kind", "marker")
            .put("lat", geometry.latitude)
            .put("lng", geometry.longitude)
        is Geometry.Circle -> JSONObject()
            .put("kind", "circle")
            .put("lat", geometry.latitude)
            .put("lng", geometry.longitude)
            .put("radius", geometry.radiusMetres)
        is Geometry.Polygon -> JSONObject()
            .put("kind", "polygon")
            .put(
                "path",
                JSONArray().apply {
                    geometry.points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
                },
            )
    }

    private fun decodeGeometry(json: JSONObject): Geometry = when (json.getString("kind")) {
        "circle" -> Geometry.Circle(json.getDouble("lat"), json.getDouble("lng"), json.getDouble("radius"))
        "polygon" -> {
            val path = json.getJSONArray("path")
            Geometry.Polygon(
                List(path.length()) { index ->
                    val point = path.getJSONArray(index)
                    Coordinate(point.getDouble(0), point.getDouble(1))
                },
            )
        }
        else -> Geometry.Marker(json.getDouble("lat"), json.getDouble("lng"))
    }
}

private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.bytes(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
