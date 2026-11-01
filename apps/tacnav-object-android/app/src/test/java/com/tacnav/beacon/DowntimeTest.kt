package com.tacnav.beacon

import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.SignalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DowntimeTest {
    private val allowance = ObjectSettings(objectName = "A", maxDowntimeMinutes = 20)
    private val minute = 60_000L

    @Test
    fun nothingCountsBeforeTheFirstActivationEnds() {
        val rig = Rig(allowance)
        rig.run(30 * minute)
        assertEquals(LifeState.WAITING, rig.state.life)
        assertFalse(rig.state.downtimeStarted)
        rig.deploy()
        rig.run(30 * minute)
        assertFalse(rig.state.downtimeStarted)
    }

    @Test
    fun countdownStartsFullOnFirstLeavingActive() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        assertEquals(20 * minute, rig.state.downtimeRemainingMillis)
        rig.run(5 * minute)
        assertEquals(15 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun countdownRunsDuringDeployment() {
        val rig = Rig(allowance.copy(activationSeconds = 120))
        rig.deploy()
        rig.run(2 * minute + 1_000)
        rig.deactivate()
        rig.run(minute)
        rig.deploy()
        val before = rig.state.downtimeRemainingMillis!!
        rig.run(minute)
        assertEquals(before - minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun activePausesWithoutResetting() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute)
        rig.deploy()
        rig.run(10 * minute)
        assertEquals(5 * minute, rig.state.downtimeRemainingMillis)
        rig.deactivate()
        rig.run(2 * minute)
        assertEquals(3 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun unbrokenActiveTimeRestoresTheAllowance() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute)
        rig.deploy()
        rig.run(20 * minute)
        assertEquals(20 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun interruptedActivityDoesNotAddUp() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute)
        rig.deploy()
        rig.run(12 * minute)
        rig.deactivate()
        rig.deploy()
        rig.run(12 * minute)
        assertEquals(5 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun separateResetTimeIsHonoured() {
        val rig = Rig(allowance.copy(downtimeResetMinutes = 5))
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute)
        rig.deploy()
        rig.run(5 * minute)
        assertEquals(20 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun zeroResetRestoresAtOnce() {
        val rig = Rig(allowance.copy(downtimeResetMinutes = 0))
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute)
        rig.deploy()
        assertEquals(20 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun zeroDestroysEvenAnIndestructibleObject() {
        val rig = Rig(allowance.copy(hitsToDestroy = 0))
        rig.deploy()
        rig.deactivate()
        rig.run(20 * minute)
        assertEquals(LifeState.DESTROYED, rig.state.life)
        assertTrue(SignalKind.DESTROYED in rig.signals)
        assertNotNull(rig.state.wreck)
    }

    @Test
    fun runningOutWhileWaitingForStillnessDestroys() {
        val rig = Rig(allowance.copy(autoActivateEnabled = true, autoActivateSeconds = 999_999))
        rig.deploy()
        rig.deactivate()
        rig.step(rig.machine.arm(rig.state))
        rig.run(20 * minute)
        assertEquals(LifeState.DESTROYED, rig.state.life)
    }

    @Test
    fun poorGpsDoesNotStopTheCountdown() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(20 * minute, accuracy = 80.0)
        assertEquals(LifeState.DESTROYED, rig.state.life)
    }

    @Test
    fun emptyAllowanceNeverCounts() {
        val rig = Rig()
        rig.deploy()
        rig.deactivate()
        rig.run(600 * minute)
        assertEquals(LifeState.WAITING, rig.state.life)
        assertNull(rig.state.downtimeRemainingMillis)
    }

    @Test
    fun warningsSoundAtFiveAndOneMinute() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute - 1_000)
        assertEquals(emptyList<SignalKind>(), rig.signals)
        rig.run(2_000)
        assertEquals(listOf(SignalKind.WARNING), rig.signals)
        rig.run(4 * minute)
        assertEquals(listOf(SignalKind.WARNING, SignalKind.WARNING), rig.signals)
    }

    @Test
    fun warningIsSkippedWhenTheCountdownStartsBelowItsMark() {
        val rig = Rig(allowance.copy(maxDowntimeMinutes = 3))
        rig.deploy()
        rig.deactivate()
        rig.run(90_000)
        assertEquals(emptyList<SignalKind>(), rig.signals)
        rig.run(31_000)
        assertEquals(listOf(SignalKind.WARNING), rig.signals)
    }

    @Test
    fun restartDeductsTheGapFromTheAllowance() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(10 * minute)
        rig.deploy()
        rig.powerOff(2 * minute)
        rig.restore()
        assertEquals(LifeState.ACTIVE, rig.state.life)
        assertEquals(8 * minute, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun restartCountsTheSessionAcrossTheGap() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.run(3 * minute)
        rig.powerOff(2 * minute)
        rig.restore()
        assertEquals(5 * minute, rig.state.sessionMillis)
    }

    @Test
    fun restartLosesProgressTowardFullReset() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(15 * minute)
        rig.deploy()
        rig.run(15 * minute)
        rig.powerOff(10_000)
        rig.restore()
        rig.run(10 * minute)
        assertEquals(5 * minute - 10_000, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun longPowerOffDestroysOnTheNextOpen() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.run(minute)
        rig.powerOff(60 * minute)
        rig.restore()
        assertEquals(LifeState.DESTROYED, rig.state.life)
        assertTrue(SignalKind.DESTROYED in rig.signals)
        assertNotNull(rig.state.wreck)
    }

    @Test
    fun powerOffWhileWaitingCountsToo() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(minute)
        rig.powerOff(19 * minute + 1_000)
        rig.restore()
        assertEquals(LifeState.DESTROYED, rig.state.life)
    }

    @Test
    fun restartWithoutAnAllowanceJustContinues() {
        val rig = Rig()
        rig.deploy()
        rig.run(minute)
        rig.powerOff(600 * minute)
        rig.restore()
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun restartMidDeploymentReturnsToWaiting() {
        val rig = Rig(ObjectSettings(objectName = "A", activationSeconds = 30))
        rig.deploy()
        rig.state = rig.machine.objectWritten(
            rig.state,
            41,
            LifeState.DEPLOYING,
            rig.elapsed,
        )
        rig.powerOff(5_000)
        rig.restore()
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(setOf(41L), rig.state.pendingDeletes)
    }

    @Test
    fun phoneMovedWhileOffDeactivatesOnTheNextFixes() {
        val rig = Rig(ObjectSettings(objectName = "A", autoDeactivateMetres = 30.0))
        rig.deploy()
        rig.powerOff(minute)
        rig.restore()
        repeat(3) { rig.fix(north = 200.0) }
        assertEquals(LifeState.WAITING, rig.state.life)
    }

    @Test
    fun restoredAliveProcessDoesNotDoubleCountTheGap() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(minute)
        rig.powerOff(minute, rebooted = false)
        rig.restore()
        rig.tick(1_000)
        assertEquals(18 * minute - 1_000, rig.state.downtimeRemainingMillis)
    }

    @Test
    fun clockRunningBackwardsNeverAddsTime() {
        val rig = Rig(allowance)
        rig.deploy()
        rig.deactivate()
        rig.run(minute)
        val before = rig.state.downtimeRemainingMillis
        rig.powerOff(-5 * minute)
        rig.restore()
        assertEquals(before, rig.state.downtimeRemainingMillis)
    }
}
