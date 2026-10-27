package com.tacnav.mortar

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tacnav.mortar.data.ProtectedResult
import com.tacnav.mortar.domain.ActivationResult
import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.MapScope
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ServiceConfiguration
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.domain.TeamScope
import com.tacnav.mortar.security.LocalState
import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.security.PinRecord
import com.tacnav.mortar.ui.TacNavTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SessionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun one_firing_mode_shows_no_selector() {
        session(state(GameplaySettings(firingModes = setOf(FiringMode.ACOUSTIC))))
        compose.onNodeWithTag("ready-to-fire").assertExists()
        compose.onAllNodesWithTag("fire-button").assertCountEquals(0)
        compose.onAllNodesWithTag("firing-mode-button").assertCountEquals(0)
        compose.onAllNodesWithTag("aiming-mode-free").assertCountEquals(0)
    }

    @Test
    fun several_modes_switch_without_pin() {
        val selected = AtomicReference<Pair<FiringMode, AimingMode>?>(null)
        val settings = GameplaySettings(
            firingModes = FiringMode.entries.toSet(),
            aimingModes = setOf(AimingMode.PRECISE_COORDINATES, AimingMode.APPROXIMATE_COORDINATES),
        )
        session(state(settings)) { firing, aiming -> selected.set(firing to aiming) }
        compose.onNodeWithTag("fire-button").assertExists()
        compose.onNodeWithTag("firing-mode-acoustic").performClick()
        assertEquals(FiringMode.ACOUSTIC to AimingMode.PRECISE_COORDINATES, selected.get())
        compose.onNodeWithTag("aiming-mode-approximate-coordinates").performClick()
        assertEquals(FiringMode.BUTTON to AimingMode.APPROXIMATE_COORDINATES, selected.get())
        compose.onAllNodesWithTag("aiming-mode-free").assertCountEquals(0)
    }

    @Test
    fun in_flight_shot_shows_countdown() {
        val shot = ShotRecord(
            shotId = "flight",
            snapshot = SensorSnapshot(50.0, 30.0, 4.0, 1.0, 90.0, 60.0, 0),
            rangeMetres = 200.0,
            impactLatitude = 50.0,
            impactLongitude = 30.003,
            dueAtElapsedMillis = Long.MAX_VALUE,
            dueAtEpochMillis = System.currentTimeMillis() + 20_000,
        )
        session(state(GameplaySettings()), shots = listOf(shot))
        compose.onNodeWithText("Shot in flight", substring = true).assertExists()
        compose.onNodeWithText("lands in", substring = true).assertExists()
        compose.onNodeWithText("1 in flight", substring = true).assertExists()
    }

    @Test
    fun empty_firing_set_is_refused_before_pin_check() {
        val submitted = AtomicBoolean(false)
        protectedSettings {
            submitted.set(true)
            ProtectedResult.Saved
        }
        compose.onNodeWithTag("settings-pin").performTextInput("4321")
        compose.onNodeWithText("Button").performScrollTo().performClick()
        compose.onNodeWithTag("save-protected-settings").performScrollTo().performClick()
        assertFalse(submitted.get())
        compose.onNodeWithText("Select at least one firing mode.").performScrollTo().assertExists()
    }

    @Test
    fun wrong_pin_is_reported() {
        protectedSettings { ProtectedResult.Denied(PinAttempt.Rejected(4)) }
        compose.onNodeWithTag("settings-pin").performTextInput("0000")
        compose.onNodeWithTag("save-protected-settings").performScrollTo().performClick()
        compose.onNodeWithText("Incorrect PIN", substring = true).performScrollTo().assertExists()
    }

    private fun session(
        state: LocalState,
        shots: List<ShotRecord> = emptyList(),
        onSelect: (FiringMode, AimingMode) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            TacNavTheme {
                SessionScreen(
                    state = state,
                    shots = shots,
                    onSelectModes = onSelect,
                    onEndSession = {},
                    onFire = {},
                    onArm = {},
                    onCalibrateElevation = {},
                    onOpenCompass = {},
                    onResetCalibration = {},
                    onOpenGameplay = {},
                )
            }
        }
    }

    private fun protectedSettings(onSave: () -> ProtectedResult) {
        compose.setContent {
            TacNavTheme {
                ProtectedSettingsScreen(
                    state = state(GameplaySettings()),
                    onSave = { _, _ -> onSave() },
                    onChangePin = { _, _ -> ProtectedResult.Saved },
                    onBack = {},
                )
            }
        }
    }

    private fun state(settings: GameplaySettings): LocalState {
        val activation = ActivationResult(
            serverUrl = "https://example.test",
            applicationId = 1,
            installationId = "installation",
            bearerCredential = "not-a-real-token",
            maps = listOf(MapScope(10, "Training", listOf(TeamScope(20, "Blue")))),
            defaultMapId = 10,
            defaultTeamId = 20,
            referenceSettings = settings,
            services = ServiceConfiguration(),
        )
        return LocalState(
            activation = activation,
            settings = settings,
            context = ActiveContext(10, 20),
            pin = PinRecord(ByteArray(16), ByteArray(32)),
        )
    }
}
