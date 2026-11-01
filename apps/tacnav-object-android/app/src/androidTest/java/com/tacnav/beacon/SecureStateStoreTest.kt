package com.tacnav.beacon

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.beacon.domain.ActivationResult
import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.Coordinate
import com.tacnav.beacon.domain.MapScope
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.ObjectVisibility
import com.tacnav.beacon.domain.TeamScope
import com.tacnav.beacon.game.Fix
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.SessionSnapshot
import com.tacnav.beacon.game.WreckPlan
import com.tacnav.beacon.security.GameJson
import com.tacnav.beacon.security.LocalState
import com.tacnav.beacon.security.PinPolicy
import com.tacnav.beacon.security.SecureStateStore
import com.tacnav.beacon.security.SettingsJson
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<BeaconApplication>()
    private val store = SecureStateStore(context)
    private val settings = ObjectSettings(
        objectName = "Antenna",
        description = "Guard it",
        iconId = 12,
        destroyedIconId = 13,
        hitsToDestroy = 2,
        sizeAMetres = 10.0,
        sizeBMetres = 20.5,
        activationSeconds = 30,
        visibility = ObjectVisibility.ALL,
        randomAreaEnabled = true,
        randomAreaRadiusMetres = 100.0,
        autoDeactivateMetres = 30.0,
        maxLocationAccuracyMetres = 12.0,
        autoActivateEnabled = true,
        autoActivateSeconds = 60,
        maxDowntimeMinutes = 20,
        downtimeResetMinutes = 0,
    )
    private val snapshot = SessionSnapshot(settings, ActiveContext(4, 7), 40.0)
    private val fix = Fix(50.0, 30.0, 5.0, 1_700_000_000_000)

    @Before
    @After
    fun clean() {
        store.clear()
    }

    @Test
    fun settingsSurviveTheStoredFormat() {
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(
            ObjectSettings(objectName = "A"),
            SettingsJson.decode(SettingsJson.encode(ObjectSettings(objectName = "A"))),
        )
    }

    @Test
    fun everyDurableGameFieldSurvivesARestart() {
        val game = GameState(
            wreckKey = "key-1",
            life = LifeState.DESTROYED,
            armed = false,
            automaticDeployment = true,
            hits = 3,
            appliedEvents = setOf("9:shot-1", "9:shot-2"),
            lifeObjectIds = setOf(41, 43),
            objectId = 43,
            areaId = 42,
            wreck = WreckPlan("key-1", 50.0, 30.0, 5.0, snapshot),
            pendingDeletes = setOf(41),
            session = snapshot,
            lastSession = snapshot,
            deployedMillis = 1_000,
            sessionMillis = 2_000,
            activeStreakMillis = 3_000,
            anchor = fix,
            position = fix,
            lastFix = fix,
            areaCentre = Coordinate(50.001, 30.001),
            stillOrigin = fix,
            stillMillis = 4_000,
            awayCount = 2,
            downtimeStarted = true,
            downtimeRemainingMillis = 5_000,
            lastActivityEpochMillis = 1_700_000_000_500,
        )
        store.saveGame(game)
        assertEquals(game, store.loadGame())
    }

    @Test
    fun runtimeFieldsAreNotRestored() {
        val game = GameState(
            wreckKey = "k",
            lastElapsedMillis = 1,
            positionDirty = true,
            objectWrittenAtMillis = 2,
            retryAtMillis = 3,
        )
        val restored = GameJson.decode(JSONObject(GameJson.encode(game).toString()))
        assertNull(restored.lastElapsedMillis)
        assertFalse(restored.positionDirty)
        assertNull(restored.objectWrittenAtMillis)
        assertEquals(0L, restored.retryAtMillis)
    }

    @Test
    fun identityAndPinSurviveARestart() {
        val policy = PinPolicy()
        val activation = ActivationResult(
            serverUrl = "https://tacnav.example",
            applicationId = 9,
            installationId = "12",
            bearerCredential = "bearer-value",
            maps = listOf(MapScope(4, "Map", listOf(TeamScope(7, "Team")))),
            defaultMapId = 4,
            defaultTeamId = 7,
            referenceSettings = settings,
            settingsPin = "9876",
        )
        val state = LocalState(activation, settings, ActiveContext(4, 7), policy.create("9876"))
        store.save(state)

        val loaded = SecureStateStore(context).load()!!
        assertEquals(activation, loaded.activation)
        assertEquals(settings, loaded.settings)
        assertEquals(
            com.tacnav.beacon.security.PinAttempt.Accepted,
            policy.verify("9876", loaded.pin, loaded.pinThrottle).first,
        )
    }

    @Test
    fun storedFileIsEncrypted() {
        store.saveGame(GameState("visible-key"))
        val bytes = File(context.noBackupFilesDir, "beacon_game.enc").readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("visible-key"))
    }
}
