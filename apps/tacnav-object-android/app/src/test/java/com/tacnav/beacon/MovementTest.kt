package com.tacnav.beacon

import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.ObjectMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementTest {
    private val automatic = ObjectSettings(
        objectName = "A",
        autoActivateEnabled = true,
        autoActivateSeconds = 60,
    )

    @Test
    fun poorFixBlocksActivation() {
        val rig = Rig()
        rig.fix(accuracy = 40.0)
        assertFalse(rig.machine.canActivate(rig.state, rig.settings, rig.epoch))
        rig.activate()
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(ObjectMachine.GPS_REFUSED, rig.message)
    }

    @Test
    fun staleFixBlocksActivation() {
        val rig = Rig()
        rig.fix()
        rig.tick(16_000)
        assertFalse(rig.machine.canActivate(rig.state, rig.settings, rig.epoch))
    }

    @Test
    fun degradedFixCancelsDeployment() {
        val rig = Rig(ObjectSettings(objectName = "A", activationSeconds = 30))
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.DEPLOYING, rig.elapsed)
        rig.fix(accuracy = 40.0)
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(setOf(41L), rig.state.pendingDeletes)
        assertEquals(ObjectMachine.GPS_LOST, rig.message)
    }

    @Test
    fun poorFixWhileActiveChangesNothing() {
        val rig = Rig(ObjectSettings(objectName = "A", autoDeactivateMetres = 30.0))
        rig.deploy()
        val position = rig.state.position
        repeat(5) { rig.fix(north = 500.0, accuracy = 60.0) }
        assertEquals(LifeState.ACTIVE, rig.state.life)
        assertEquals(position, rig.state.position)
        assertEquals(0, rig.state.awayCount)
    }

    @Test
    fun movingObjectFollowsThePhone() {
        val rig = Rig()
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        rig.fix(north = 20.0)
        assertTrue(rig.state.positionDirty)
    }

    @Test
    fun movementWithinFixAccuracyIsNotPublished() {
        val rig = Rig()
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        rig.fix(north = 3.0, accuracy = 5.0)
        assertFalse(rig.state.positionDirty)
    }

    @Test
    fun stillPhoneActivatesItself() {
        val rig = Rig(automatic)
        rig.run(59_000)
        assertEquals(LifeState.WAITING, rig.state.life)
        rig.run(3_000)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun carriedPhoneStaysOff() {
        val rig = Rig(automatic)
        for (step in 1..120) rig.run(1_000, north = step * 40.0)
        assertEquals(LifeState.WAITING, rig.state.life)
    }

    @Test
    fun movementInsideTheStillRadiusDoesNotRestartTheTimer() {
        val rig = Rig(automatic)
        for (step in 1..62) rig.run(1_000, north = (step % 2) * 25.0)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun poorGpsPausesTheStillTimerWithoutResettingIt() {
        val rig = Rig(automatic)
        rig.run(30_000)
        val waited = rig.state.stillMillis
        rig.tick(20_000)
        assertEquals(waited, rig.state.stillMillis)
        rig.run(35_000)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun movingDuringAutomaticDeploymentCancelsAndWaitsAgain() {
        val rig = Rig(automatic.copy(activationSeconds = 30))
        rig.run(61_000)
        assertEquals(LifeState.DEPLOYING, rig.state.life)
        rig.fix(north = 200.0)
        assertEquals(LifeState.WAITING, rig.state.life)
        assertTrue(rig.state.armed)
        rig.run(62_000, north = 200.0)
        assertEquals(LifeState.DEPLOYING, rig.state.life)
    }

    @Test
    fun movingDuringButtonDeploymentFollowsThePhone() {
        val rig = Rig(ObjectSettings(objectName = "A", activationSeconds = 30))
        rig.deploy()
        rig.fix(north = 200.0)
        assertEquals(LifeState.DEPLOYING, rig.state.life)
        assertTrue(rig.state.positionDirty)
    }

    @Test
    fun movedPhoneDeactivatesAfterSeveralFixes() {
        val rig = Rig(ObjectSettings(objectName = "A", autoDeactivateMetres = 30.0))
        rig.deploy()
        rig.fix(north = 100.0)
        rig.fix(north = 100.0)
        assertEquals(LifeState.ACTIVE, rig.state.life)
        rig.fix(north = 100.0)
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(ObjectMachine.MOVED_AWAY, rig.message)
    }

    @Test
    fun jitterWithinAccuracyDoesNotDeactivate() {
        val rig = Rig(ObjectSettings(objectName = "A", autoDeactivateMetres = 30.0))
        rig.deploy()
        repeat(10) { rig.fix(north = 38.0, accuracy = 5.0) }
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun oneJumpInterruptedByAGoodFixDoesNotDeactivate() {
        val rig = Rig(ObjectSettings(objectName = "A", autoDeactivateMetres = 30.0))
        rig.deploy()
        rig.fix(north = 100.0)
        rig.fix(north = 100.0)
        rig.fix(north = 0.0)
        rig.fix(north = 100.0)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun movedPhoneInButtonModeWaitsForAPress() {
        val rig = Rig(ObjectSettings(objectName = "A", autoDeactivateMetres = 30.0))
        rig.deploy()
        repeat(3) { rig.fix(north = 100.0) }
        rig.run(120_000, north = 100.0)
        assertEquals(LifeState.WAITING, rig.state.life)
    }

    @Test
    fun movedPhoneInAutomaticModeDeploysAtTheNewPosition() {
        val rig = Rig(automatic.copy(autoDeactivateMetres = 30.0))
        rig.run(62_000)
        assertEquals(LifeState.ACTIVE, rig.state.life)
        repeat(3) { rig.fix(north = 200.0) }
        assertEquals(LifeState.WAITING, rig.state.life)
        rig.run(62_000, north = 200.0)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun manualStopDisarmsUntilArmIsPressed() {
        val rig = Rig(automatic)
        rig.run(62_000)
        rig.deactivate()
        assertFalse(rig.state.armed)
        rig.run(120_000)
        assertEquals(LifeState.WAITING, rig.state.life)
        rig.step(rig.machine.arm(rig.state))
        rig.run(62_000)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun activateButtonWorksInAutomaticMode() {
        val rig = Rig(automatic)
        rig.deploy()
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }
}
