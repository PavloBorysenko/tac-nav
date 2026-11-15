package com.tacnav.mortar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tacnav.mortar.data.ProtectedResult
import com.tacnav.mortar.domain.ActivationResult
import com.tacnav.mortar.domain.ActiveContext
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.MapScope
import com.tacnav.mortar.domain.ServiceConfiguration
import com.tacnav.mortar.domain.TeamScope
import com.tacnav.mortar.security.LocalState
import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.security.PinRecord
import com.tacnav.mortar.ui.TacNavTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ProtectedPathsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun calibration_path_does_not_request_pin() {
        val opened = AtomicBoolean(false)
        compose.setContent {
            TacNavTheme {
                SessionScreen(
                    state = state(),
                    onEndSession = {},
                    onFire = {},
                    onArm = {},
                    onCalibrateElevation = { opened.set(true) },
                    onOpenCompass = {},
                    onResetCalibration = {},
                    onOpenGameplay = {},
                )
            }
        }

        compose.onNodeWithTag("calibration").performClick()

        assertTrue(opened.get())
    }

    @Test
    fun pin_is_asked_once_when_opening_protected_settings() {
        val checkedPin = AtomicReference<String?>(null)
        val unlocked = AtomicBoolean(false)
        compose.setContent {
            TacNavTheme {
                ProtectedPinScreen(
                    onUnlock = { pin ->
                        checkedPin.set(pin)
                        PinAttempt.Accepted
                    },
                    onUnlocked = { unlocked.set(true) },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("settings-pin").assertExists().performTextInput("4321")
        compose.onNodeWithTag("unlock-protected-settings").performClick()
        compose.waitUntil(5_000) { unlocked.get() }

        assertEquals("4321", checkedPin.get())
    }

    @Test
    fun unlocked_protected_settings_save_without_pin() {
        val submitted = AtomicBoolean(false)
        compose.setContent {
            TacNavTheme {
                ProtectedSettingsScreen(
                    state = state(),
                    onSave = {
                        submitted.set(true)
                        ProtectedResult.Saved
                    },
                    onChangePin = { ProtectedResult.Saved },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("settings-pin").assertDoesNotExist()
        compose.onNodeWithTag("save-protected-settings").performScrollTo().performClick()

        assertTrue(submitted.get())
    }

    private fun state(): LocalState {
        val settings = GameplaySettings()
        val activation = ActivationResult(
            serverUrl = "https://example.test",
            applicationId = 1,
            installationId = "installation",
            bearerCredential = "not-a-real-token",
            maps = listOf(
                MapScope(10, "Training", listOf(TeamScope(20, "Blue"))),
            ),
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
