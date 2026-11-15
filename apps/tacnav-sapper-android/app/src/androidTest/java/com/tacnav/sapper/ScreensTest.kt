package com.tacnav.sapper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.sapper.domain.ActivationResult
import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.MapScope
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.TeamScope
import com.tacnav.sapper.game.DetectorLevel
import com.tacnav.sapper.game.Fix
import com.tacnav.sapper.game.Laying
import com.tacnav.sapper.game.Mode
import com.tacnav.sapper.game.SapperActions
import com.tacnav.sapper.game.SapperView
import com.tacnav.sapper.security.LocalState
import com.tacnav.sapper.security.PinAttempt
import com.tacnav.sapper.security.PinRecord
import com.tacnav.sapper.ui.ClearScreen
import com.tacnav.sapper.ui.DetectScreen
import com.tacnav.sapper.ui.HomeScreen
import com.tacnav.sapper.ui.LayScreen
import com.tacnav.sapper.ui.PinDialog
import com.tacnav.sapper.ui.SettingsScreen
import com.tacnav.sapper.ui.TacNavTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class RecordingActions : SapperActions {
    val calls = mutableListOf<String>()
    override fun pressMark() { calls += "pressMark" }
    override fun releaseMark() { calls += "releaseMark" }
    override fun placeNoMines() { calls += "noMines" }
    override fun pressLay() { calls += "pressLay" }
    override fun releaseLay() { calls += "releaseLay" }
    override fun resetLaying() { calls += "resetLaying" }
    override fun pressClear() { calls += "pressClear" }
    override fun releaseClear() { calls += "releaseClear" }
}

@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHost>()

    private val actions = RecordingActions()
    private val ready = SapperView(online = true, hasFix = true, gpsUsable = true, headingKnown = true, compassReliable = true)
    private val local = LocalState(
        activation = ActivationResult(
            serverUrl = "https://tacnav.example",
            applicationId = 9,
            installationId = "12",
            bearerCredential = "bearer",
            maps = listOf(MapScope(4, "North field", listOf(TeamScope(7, "Blue")))),
            defaultMapId = 4,
            defaultTeamId = 7,
            referenceSettings = SapperSettings(),
            settingsPin = "4321",
        ),
        settings = SapperSettings(),
        context = ActiveContext(4, 7),
        pin = PinRecord(ByteArray(1), ByteArray(1)),
    )

    private fun exists(tag: String) = compose.onAllNodesWithTagCount(tag) > 0

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun firstStartOffersOnlyScanAndSettings() {
        compose.setContent { TacNavTheme { HomeScreen(false, {}, {}, {}) } }
        assertTrue(exists("scan_qr"))
        assertTrue(exists("settings"))
        assertTrue(!exists("mode_detect") && !exists("mode_lay") && !exists("mode_clear"))
    }

    @Test
    fun activatedPhoneOffersTheThreeModesAndSettings() {
        var opened: Mode? = null
        compose.setContent { TacNavTheme { HomeScreen(true, {}, {}, { opened = it }) } }
        assertTrue(!exists("scan_qr"))
        compose.onNodeWithTag("mode_lay").performClick()
        assertEquals(Mode.LAY, opened)
        assertTrue(exists("mode_detect") && exists("mode_clear") && exists("settings"))
    }

    @Test
    fun wrongPinKeepsSettingsClosed() {
        var opened = false
        compose.setContent {
            TacNavTheme {
                PinDialog(
                    onVerify = { if (it == "4321") PinAttempt.Accepted else PinAttempt.Rejected(4) },
                    onAccepted = { opened = true },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("pin_field").performTextInput("1111")
        compose.onNodeWithTag("pin_confirm").performClick()
        compose.waitUntil(5_000) { exists("pin_error") }
        assertTrue(!opened)
    }

    @Test
    fun settingsBeforeActivationOfferManualActivationOnly() {
        compose.setContent {
            TacNavTheme {
                SettingsScreen(null, { emptyList() }, false, null, {}, { _, _, _, _ -> }, {}, {})
            }
        }
        assertTrue(exists("server_url") && exists("one_time_code") && exists("activate_manual"))
        assertTrue(!exists("reset_connection") && !exists("save_settings"))
    }

    @Test
    fun resetConnectionAsksAndThenResets() {
        var reset = false
        compose.setContent {
            TacNavTheme {
                SettingsScreen(local, { emptyList() }, false, null, {}, { _, _, _, _ -> }, { reset = true }, {})
            }
        }
        compose.onNodeWithTag("reset_connection").performScrollTo().performClick()
        assertTrue(!reset)
        compose.onNodeWithTag("confirm_yes").performClick()
        assertTrue(reset)
    }

    @Test
    fun detectShowsTheMarkHoldInsideAnEnemyField() {
        compose.setContent {
            TacNavTheme {
                DetectScreen(ready.copy(mode = Mode.DETECT, level = DetectorLevel.INSIDE, markAvailable = true), true, actions, {})
            }
        }
        compose.onNodeWithTag("mark_minefield").performTouchInput {
            down(center)
            advanceEventTime(500)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("pressMark", "releaseMark"), actions.calls)
        assertTrue(!exists("no_mines"))
    }

    @Test
    fun detectWithPoorGpsWarnsAndDisablesNoMines() {
        compose.setContent {
            TacNavTheme {
                DetectScreen(ready.copy(mode = Mode.DETECT, gpsUsable = false, noMinesOffered = true, online = false), true, actions, {})
            }
        }
        assertTrue(exists("warn_gps") && exists("warn_offline"))
        compose.onNodeWithTag("no_mines").assertIsNotEnabled()
    }

    @Test
    fun detectShowsTheNoMinesLimit() {
        compose.setContent {
            TacNavTheme {
                DetectScreen(ready.copy(mode = Mode.DETECT, noMinesOffered = true, noMinesLeft = 0), true, actions, {})
            }
        }
        compose.onNodeWithTag("no_mines").assertIsNotEnabled()
        assertTrue(exists("no_mines_limit"))
    }

    @Test
    fun detectFlashesOnASignal() {
        var view by mutableStateOf(ready.copy(mode = Mode.DETECT))
        compose.mainClock.autoAdvance = false
        compose.setContent { TacNavTheme { DetectScreen(view, true, actions, {}) } }
        assertTrue(exists("flash_off"))
        view = view.copy(flashSerial = 1, level = DetectorLevel.FAR)
        compose.mainClock.advanceTimeBy(100)
        assertTrue(exists("flash_on"))
        compose.mainClock.advanceTimeBy(400)
        assertTrue(exists("flash_off"))
    }

    @Test
    fun aMinefieldShowsTheSkullInDetectAndClear() {
        var mode by mutableStateOf(Mode.DETECT)
        var inside by mutableStateOf(false)
        compose.setContent {
            TacNavTheme {
                val view = ready.copy(mode = mode, insideMinefield = inside, clearSearched = true, clearTargetId = 31)
                if (mode == Mode.DETECT) DetectScreen(view, true, actions, {}) else ClearScreen(view, true, actions, {})
            }
        }
        assertTrue(!exists("skull"))
        inside = true
        compose.waitForIdle()
        assertTrue(exists("skull"))
        mode = Mode.CLEAR
        compose.waitForIdle()
        assertTrue(exists("skull"))
    }

    @Test
    fun holdProgressShowsThePercentage() {
        val laying = Laying("lay-1", Fix(50.0, 30.0, 3.0, 0), 40.0, 60_000)
        compose.setContent {
            TacNavTheme {
                LayScreen(ready.copy(mode = Mode.LAY, laying = laying, layFraction = 0.42f), SapperSettings(), true, actions, {})
            }
        }
        compose.onNodeWithText("42%").assertExists()
    }

    @Test
    fun aCompletedLayingIsConfirmedForFiveSeconds() {
        var view by mutableStateOf(ready.copy(mode = Mode.LAY))
        compose.mainClock.autoAdvance = false
        compose.setContent { TacNavTheme { LayScreen(view, SapperSettings(), true, actions, {}) } }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(!exists("laid_confirmation"))
        view = view.copy(laidSerial = 1)
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(exists("laid_confirmation"))
        compose.mainClock.advanceTimeBy(4_500)
        assertTrue(!exists("laid_confirmation"))
    }

    @Test
    fun reopeningLayDoesNotRepeatTheConfirmation() {
        compose.setContent {
            TacNavTheme { LayScreen(ready.copy(mode = Mode.LAY, laidSerial = 2), SapperSettings(), true, actions, {}) }
        }
        compose.waitForIdle()
        assertTrue(!exists("laid_confirmation"))
    }

    @Test
    fun layingLimitDisablesTheHold() {
        compose.setContent {
            TacNavTheme { LayScreen(ready.copy(mode = Mode.LAY, minefieldsLeft = 0), SapperSettings(), true, actions, {}) }
        }
        compose.onNodeWithTag("lay_hold").performTouchInput { down(center); up() }
        assertTrue(actions.calls.isEmpty())
        assertTrue(exists("lay_limit"))
    }

    @Test
    fun layingInProgressOffersReset() {
        val laying = Laying("lay-1", Fix(50.0, 30.0, 3.0, 0), 40.0, 60_000)
        compose.setContent {
            TacNavTheme {
                LayScreen(ready.copy(mode = Mode.LAY, laying = laying, layFraction = 1 / 3f), SapperSettings(), true, actions, {})
            }
        }
        compose.onNodeWithTag("lay_hold").performTouchInput { down(center); advanceEventTime(300); up() }
        compose.onNodeWithTag("reset_laying").performScrollTo().performClick()
        assertEquals(listOf("pressLay", "releaseLay", "resetLaying"), actions.calls)
    }

    @Test
    fun clearShowsSearchingNoFieldHoldAndSafe() {
        var view by mutableStateOf(ready.copy(mode = Mode.CLEAR))
        compose.setContent { TacNavTheme { ClearScreen(view, true, actions, {}) } }
        assertTrue(exists("clear_searching"))
        view = view.copy(clearSearched = true)
        compose.waitForIdle()
        assertTrue(exists("clear_none") && !exists("clear_hold"))
        view = view.copy(clearTargetId = 31)
        compose.waitForIdle()
        compose.onNodeWithTag("clear_hold").assertIsEnabled()
        view = view.copy(safe = true, noMinesOffered = true)
        compose.waitForIdle()
        assertTrue(exists("safe") && exists("no_mines"))
        compose.onNodeWithTag("no_mines").performClick()
        assertEquals(listOf("noMines"), actions.calls)
    }

    @Test
    fun everyModeHasBack() {
        var backs = 0
        var mode by mutableStateOf(Mode.DETECT)
        compose.setContent {
            TacNavTheme {
                when (mode) {
                    Mode.DETECT -> DetectScreen(ready, true, actions) { backs++ }
                    Mode.LAY -> LayScreen(ready, SapperSettings(), true, actions) { backs++ }
                    else -> ClearScreen(ready, true, actions) { backs++ }
                }
            }
        }
        listOf(Mode.DETECT, Mode.LAY, Mode.CLEAR).forEach {
            mode = it
            compose.waitForIdle()
            compose.onNodeWithTag("back").performScrollTo().performClick()
        }
        assertEquals(3, backs)
    }
}
