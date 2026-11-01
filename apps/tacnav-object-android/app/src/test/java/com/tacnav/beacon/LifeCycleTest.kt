package com.tacnav.beacon

import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.ObjectMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeCycleTest {
    @Test
    fun zeroDeploymentSecondsActivateImmediately() {
        val rig = Rig()
        rig.deploy()
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun timedDeploymentRunsForTheConfiguredSeconds() {
        val rig = Rig(ObjectSettings(objectName = "A", activationSeconds = 30))
        rig.deploy()
        assertEquals(LifeState.DEPLOYING, rig.state.life)
        rig.run(29_000)
        assertEquals(LifeState.DEPLOYING, rig.state.life)
        rig.run(2_000)
        assertEquals(LifeState.ACTIVE, rig.state.life)
    }

    @Test
    fun sessionTimerStartsAfterDeployment() {
        val rig = Rig(ObjectSettings(objectName = "A", activationSeconds = 30))
        rig.deploy()
        rig.run(30_000)
        assertEquals(LifeState.ACTIVE, rig.state.life)
        assertEquals(0L, rig.state.sessionMillis)
        rig.run(10_000)
        assertEquals(10_000L, rig.state.sessionMillis)
    }

    @Test
    fun cancellingDeploymentDeletesTheObject() {
        val rig = Rig(ObjectSettings(objectName = "A", activationSeconds = 30))
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.DEPLOYING, rig.elapsed)
        rig.deactivate()
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(setOf(41L), rig.state.pendingDeletes)
        assertNull(rig.state.objectId)
    }

    @Test
    fun deactivationRemovesObjectAndArea() {
        val rig = Rig(
            ObjectSettings(objectName = "A", randomAreaEnabled = true, randomAreaRadiusMetres = 100.0),
        )
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        rig.state = rig.machine.areaWritten(rig.state, 42, rig.elapsed)
        rig.deactivate()
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(setOf(41L, 42L), rig.state.pendingDeletes)
        assertNull(rig.state.areaCentre)
    }

    @Test
    fun writeFinishingAfterDeactivationIsDeleted() {
        val rig = Rig()
        rig.deploy()
        rig.deactivate()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        assertEquals(setOf(41L), rig.state.pendingDeletes)
        assertNull(rig.state.objectId)
    }

    @Test
    fun destroyedPhoneRefusesActivation() {
        val rig = Rig(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        rig.step(rig.machine.onDelivery(rig.state, rig.env, "hit", "shot", 41, rig.clock))
        assertEquals(LifeState.DESTROYED, rig.state.life)
        rig.activate()
        assertEquals(LifeState.DESTROYED, rig.state.life)
        assertFalse(rig.machine.canActivate(rig.state, rig.settings, rig.epoch))
    }

    @Test
    fun rectangleOrientationIsFrozenWhenDeploymentStarts() {
        val rig = Rig(ObjectSettings(objectName = "A", sizeAMetres = 10.0, sizeBMetres = 20.0))
        rig.heading = 40.0
        rig.deploy()
        rig.heading = 200.0
        rig.run(5_000)
        assertEquals(40.0, rig.state.session!!.headingDegrees, 0.0)
    }

    @Test
    fun settingsSavedDuringSessionWaitForTheNextActivation() {
        val rig = Rig(ObjectSettings(objectName = "A", sizeAMetres = 5.0))
        rig.deploy()
        rig.settings = ObjectSettings(objectName = "B", sizeAMetres = 50.0)
        rig.run(5_000)
        assertEquals("A", rig.state.session!!.settings.objectName)
        rig.deactivate()
        rig.fix()
        rig.activate()
        assertEquals("B", rig.state.session!!.settings.objectName)
    }

    @Test
    fun resetClearsHitsAndKeepsAnUnsentWreck() {
        val rig = Rig(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        rig.step(rig.machine.onDelivery(rig.state, rig.env, "hit", "shot", 41, rig.clock))
        val wreck = rig.state.wreck
        assertNotNull(wreck)
        rig.step(rig.machine.reset(rig.state))
        assertEquals(LifeState.WAITING, rig.state.life)
        assertEquals(0, rig.state.hits)
        assertEquals(wreck, rig.state.wreck)
        assertEquals("key-1", rig.state.wreckKey)
        assertEquals("key-0", rig.state.wreck!!.key)
    }

    @Test
    fun resetDeletesTheLiveObjectAndKeepsItsContext() {
        val rig = Rig()
        rig.deploy()
        rig.state = rig.machine.objectWritten(rig.state, 41, LifeState.ACTIVE, rig.elapsed)
        rig.step(rig.machine.reset(rig.state))
        assertEquals(setOf(41L), rig.state.pendingDeletes)
        assertTrue(rig.state.lastSession != null)
    }

    @Test
    fun openingTheApplicationStartsArmed() {
        assertTrue(Rig().state.armed)
    }

    @Test
    fun machineExposesHitEventName() {
        assertEquals("hit", ObjectMachine.HIT_EVENT)
    }
}
