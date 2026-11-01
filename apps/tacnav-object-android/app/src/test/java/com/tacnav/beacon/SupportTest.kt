package com.tacnav.beacon

import com.tacnav.beacon.activation.ActivationPayloadParser
import com.tacnav.beacon.domain.ActivationResult
import com.tacnav.beacon.domain.MapScope
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.ScopeRules
import com.tacnav.beacon.domain.TeamScope
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.SignalKind
import com.tacnav.beacon.messaging.WakeRouter
import com.tacnav.beacon.security.PinAttempt
import com.tacnav.beacon.security.PinPolicy
import com.tacnav.beacon.security.PinThrottleState
import com.tacnav.beacon.session.SignalRunner
import com.tacnav.beacon.ui.StatusText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationPayloadTest {
    @Test
    fun qrJsonGivesServerAndCode() {
        val payload = ActivationPayloadParser.parse(
            """{"server_url":"https:\/\/tacnav.example\/","code":"ABC123"}""",
        )
        assertEquals("https://tacnav.example", payload.serverUrl)
        assertEquals("ABC123", payload.oneTimeCode)
    }

    @Test
    fun manualEntryUsesTheSameRules() {
        val payload = ActivationPayloadParser.fromManual(" https://tacnav.example/ ", " ABC123 ")
        assertEquals("https://tacnav.example", payload.serverUrl)
        assertEquals("ABC123", payload.oneTimeCode)
    }

    @Test
    fun plainHttpIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            ActivationPayloadParser.fromManual("http://tacnav.example", "ABC123")
        }
    }

    @Test
    fun missingCodeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            ActivationPayloadParser.fromManual("https://tacnav.example", " ")
        }
    }
}

class SettingsRulesTest {
    private val named = ObjectSettings(objectName = "A")

    @Test
    fun deactivationDistanceMustBeTwiceTheGpsLimit() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            named.copy(autoDeactivateMetres = 20.0).validated()
        }
        assertTrue(error.message!!.contains("30.0"))
        named.copy(autoDeactivateMetres = 30.0).validated()
    }

    @Test
    fun areaMustContainTheObject() {
        assertThrows(IllegalArgumentException::class.java) {
            named.copy(
                sizeAMetres = 30.0,
                randomAreaEnabled = true,
                randomAreaRadiusMetres = 10.0,
            ).validated()
        }
    }

    @Test
    fun automaticActivationNeedsSeconds() {
        assertThrows(IllegalArgumentException::class.java) {
            named.copy(autoActivateEnabled = true).validated()
        }
    }

    @Test
    fun emptyNameIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectSettings(objectName = " ").validated()
        }
    }

    @Test
    fun emptyResetMeansTheAllowanceAndZeroMeansAtOnce() {
        assertEquals(1_200_000L, named.copy(maxDowntimeMinutes = 20).downtimeResetMillis)
        assertEquals(
            0L,
            named.copy(maxDowntimeMinutes = 20, downtimeResetMinutes = 0).downtimeResetMillis,
        )
        assertNull(named.downtimeResetMillis)
    }

    @Test
    fun stillRadiusIsTheLargerOfDistanceAndTwiceTheLimit() {
        assertEquals(30.0, named.stillRadiusMetres, 0.0)
        assertEquals(80.0, named.copy(autoDeactivateMetres = 80.0).stillRadiusMetres, 0.0)
    }

    @Test
    fun scopeSelectionStaysInsideTheActivation() {
        val maps = listOf(MapScope(1, "Map", listOf(TeamScope(2, "Team"))))
        assertEquals(1L, ScopeRules.select(maps, 1, 2).mapId)
        assertThrows(IllegalArgumentException::class.java) { ScopeRules.select(maps, 9, 2) }
        assertThrows(IllegalArgumentException::class.java) { ScopeRules.select(maps, 1, 9) }
    }

    @Test
    fun activationResultNeedsHttpsAndValidDefaults() {
        val valid = ActivationResult(
            serverUrl = "https://tacnav.example",
            applicationId = 1,
            installationId = "9",
            bearerCredential = "bearer",
            maps = listOf(MapScope(1, "Map", listOf(TeamScope(2, "Team")))),
            defaultMapId = 1,
            defaultTeamId = 2,
            referenceSettings = named,
            settingsPin = "4321",
        )
        valid.validated()
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(serverUrl = "http://tacnav.example").validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(defaultTeamId = 9).validated()
        }
    }
}

class PinTest {
    private var now = 1_000L
    private val policy = PinPolicy(now = { now })

    @Test
    fun correctPinOpensAndWrongPinDoesNot() {
        val record = policy.create("9876")
        assertEquals(PinAttempt.Accepted, policy.verify("9876", record, PinThrottleState()).first)
        assertTrue(policy.verify("0000", record, PinThrottleState()).first is PinAttempt.Rejected)
    }

    @Test
    fun repeatedFailuresDelayFurtherAttempts() {
        val record = policy.create("9876")
        var throttle = PinThrottleState()
        repeat(4) { throttle = policy.verify("0000", record, throttle).second }
        val (fifth, blocked) = policy.verify("0000", record, throttle)
        assertTrue(fifth is PinAttempt.Throttled)
        val during = policy.verify("9876", record, blocked).first
        assertTrue(during is PinAttempt.Throttled)
        now += 31_000
        assertEquals(PinAttempt.Accepted, policy.verify("9876", record, blocked).first)
    }

    @Test
    fun shortOrNonNumericPinIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { policy.create("123") }
        assertThrows(IllegalArgumentException::class.java) { policy.create("12a4") }
    }
}

class WakeRouterTest {
    @Test
    fun wakeReadsTheMailboxThenNotifies() {
        val order = mutableListOf<String>()
        WakeRouter.route(
            mapOf(WakeRouter.DATA_DELIVERY_REFERENCE to "42"),
            sync = { order += "sync" },
            notify = { order += "notify $it" },
        )
        assertEquals(listOf("sync", "notify 42"), order)
    }

    @Test
    fun unrelatedPushDoesNothing() {
        var touched = false
        WakeRouter.route(emptyMap(), sync = { touched = true }, notify = { touched = true })
        assertEquals(false, touched)
    }
}

class StatusTextTest {
    private val settings = ObjectSettings(objectName = "A", hitsToDestroy = 5, maxDowntimeMinutes = 20)

    @Test
    fun clockShowsMinutesAndHours() {
        assertEquals("00:01", StatusText.clock(1))
        assertEquals("12:34", StatusText.clock(754_000))
        assertEquals("1:00:05", StatusText.clock(3_605_000))
    }

    @Test
    fun hitCountShowsTheLimitWhenThereIsOne() {
        val state = GameState("k", hits = 2)
        assertEquals("2 of 5", StatusText.hits(state, settings))
        assertEquals("2", StatusText.hits(state, settings.copy(hitsToDestroy = 0)))
    }

    @Test
    fun downtimeTextFollowsTheState() {
        val off = GameState("k", downtimeStarted = true, downtimeRemainingMillis = 300_000)
        assertEquals("Time left to reactivate 05:00", StatusText.downtime(off, settings))
        val active = off.copy(life = LifeState.ACTIVE, activeStreakMillis = 60_000)
        assertEquals("Downtime left 05:00", StatusText.downtime(active, settings))
        assertEquals("Full reset in 19:00", StatusText.fullReset(active, settings))
        assertNull(StatusText.downtime(GameState("k"), settings))
        assertNull(StatusText.downtime(off, settings.copy(maxDowntimeMinutes = null)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SignalRunnerTest {
    @Test
    fun destructionLightsTheTorchForFiveSeconds() = runTest {
        val torch = mutableListOf<Boolean>()
        val runner = SignalRunner(this, {}, {}, { torch += it }, {})
        runner.play(SignalKind.DESTROYED)
        assertEquals(listOf(true), torch)
        advanceTimeBy(4_999)
        assertEquals(listOf(true), torch)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(true, false), torch)
    }

    @Test
    fun hitDoesNotTouchTheTorch() = runTest {
        val torch = mutableListOf<Boolean>()
        val runner = SignalRunner(this, {}, {}, { torch += it }, {})
        runner.play(SignalKind.HIT)
        assertTrue(torch.isEmpty())
    }

    @Test
    fun torchFailureDoesNotStopTheOtherSignals() = runTest {
        val sounds = mutableListOf<SignalKind>()
        val vibrations = mutableListOf<SignalKind>()
        val runner = SignalRunner(
            scope = this,
            sound = { sounds += it },
            vibrate = { vibrations += it },
            torch = { error("busy") },
            notify = {},
        )
        runner.play(SignalKind.DESTROYED)
        advanceTimeBy(6_000)
        assertEquals(listOf(SignalKind.DESTROYED), sounds)
        assertEquals(listOf(SignalKind.DESTROYED), vibrations)
    }

    @Test
    fun soundFailureDoesNotStopVibration() = runTest {
        var vibrated = false
        val runner = SignalRunner(this, { error("muted") }, { vibrated = true }, {}, {})
        runner.play(SignalKind.HIT)
        assertTrue(vibrated)
    }
}
