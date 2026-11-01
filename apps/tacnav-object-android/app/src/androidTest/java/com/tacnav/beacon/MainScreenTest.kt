package com.tacnav.beacon

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.beacon.domain.ActivationResult
import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.MapScope
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.TeamScope
import com.tacnav.beacon.game.Fix
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.SessionSnapshot
import com.tacnav.beacon.security.LocalState
import com.tacnav.beacon.security.PinRecord
import com.tacnav.beacon.ui.MainScreen
import com.tacnav.beacon.ui.MainUiState
import com.tacnav.beacon.ui.TacNavTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHost>()

    private val settings = ObjectSettings(
        objectName = "Antenna",
        hitsToDestroy = 5,
        activationSeconds = 30,
        maxDowntimeMinutes = 20,
        autoActivateEnabled = true,
        autoActivateSeconds = 60,
    )
    private val context = ActiveContext(4, 7)
    private val local = LocalState(
        activation = ActivationResult(
            serverUrl = "https://tacnav.example",
            applicationId = 9,
            installationId = "12",
            bearerCredential = "bearer",
            maps = listOf(MapScope(4, "North field", listOf(TeamScope(7, "Blue")))),
            defaultMapId = 4,
            defaultTeamId = 7,
            referenceSettings = settings,
            settingsPin = "4321",
        ),
        settings = settings,
        context = context,
        pin = PinRecord(ByteArray(1), ByteArray(1)),
    )
    private val session = SessionSnapshot(settings, context, 0.0)
    private var pressed = ""

    private fun show(game: GameState, canActivate: Boolean = true) {
        compose.setContent {
            TacNavTheme {
                MainScreen(
                    ui = MainUiState(local, game, canActivate, true, true, null, emptyList()),
                    onActivate = { pressed = "activate" },
                    onDeactivate = { pressed = "deactivate" },
                    onArm = { pressed = "arm" },
                    onOpenSettings = { pressed = "settings" },
                )
            }
        }
    }

    @Test
    fun waitingOffersActivate() {
        show(GameState("k", hits = 2, lastFix = Fix(50.0, 30.0, 6.0, 1)))
        compose.onNodeWithTag("life_state").assertTextEquals("Waiting")
        compose.onNodeWithTag("hit_count").assertTextEquals("Hits: 2 of 5")
        compose.onNodeWithTag("gps_status").assertTextEquals("GPS ±6 m, limit 15 m")
        compose.onNodeWithTag("activate").assertIsEnabled().performClick()
        assertEquals("activate", pressed)
    }

    @Test
    fun poorGpsDisablesActivate() {
        show(GameState("k", lastFix = Fix(50.0, 30.0, 40.0, 1)), canActivate = false)
        compose.onNodeWithTag("activate").assertIsNotEnabled()
        compose.onNodeWithTag("gps_status").assertTextEquals("GPS ±40 m, limit 15 m")
    }

    @Test
    fun deployingShowsProgressAndCanBeCancelled() {
        show(GameState("k", life = LifeState.DEPLOYING, session = session, deployedMillis = 15_000))
        compose.onNodeWithTag("deploy_progress").assertExists()
        compose.onNodeWithTag("deactivate").performClick()
        assertEquals("deactivate", pressed)
    }

    @Test
    fun activeShowsTimersAndDowntime() {
        show(
            GameState(
                "k",
                life = LifeState.ACTIVE,
                session = session,
                sessionMillis = 754_000,
                downtimeStarted = true,
                downtimeRemainingMillis = 300_000,
                activeStreakMillis = 60_000,
            ),
        )
        compose.onNodeWithTag("life_state").assertTextEquals("Active")
        compose.onNodeWithTag("session_timer").assertTextEquals("Active for 12:34")
        compose.onNodeWithTag("downtime_left").assertTextEquals("Downtime left 05:00")
        compose.onNodeWithTag("full_reset").assertTextEquals("Full reset in 19:00")
    }

    @Test
    fun waitingCountdownNamesReactivation() {
        show(GameState("k", downtimeStarted = true, downtimeRemainingMillis = 90_000))
        compose.onNodeWithTag("downtime_left").assertTextEquals("Time left to reactivate 01:30")
    }

    @Test
    fun disarmedPhoneOffersArm() {
        show(GameState("k", armed = false))
        compose.onNodeWithTag("arm").performClick()
        assertEquals("arm", pressed)
    }

    @Test
    fun destroyedPhoneOffersNoActivation() {
        show(GameState("k", life = LifeState.DESTROYED, hits = 5))
        compose.onNodeWithTag("life_state").assertTextEquals("Destroyed")
        compose.onNodeWithTag("activate").assertDoesNotExist()
        compose.onNodeWithTag("deactivate").assertDoesNotExist()
    }

    @Test
    fun settingsAreOneTapAway() {
        show(GameState("k"))
        compose.onNodeWithTag("settings").performClick()
        assertEquals("settings", pressed)
    }
}
