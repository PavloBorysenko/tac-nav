package com.tacnav.beacon

import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.Clock
import com.tacnav.beacon.game.Environment
import com.tacnav.beacon.game.Fix
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.GameStore
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.game.ObjectController
import com.tacnav.beacon.game.ObjectMachine
import com.tacnav.beacon.game.SignalKind
import com.tacnav.beacon.game.SignalPort
import com.tacnav.beacon.network.DeviceMailboxApi
import com.tacnav.beacon.network.GeoObjectApi
import com.tacnav.beacon.network.MailboxDelivery
import com.tacnav.beacon.network.ObjectGoneException
import com.tacnav.beacon.network.ObjectRequest
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGeo : GeoObjectApi {
    val objects = linkedMapOf<Long, ObjectRequest>()
    val deleted = mutableListOf<Long>()
    val updates = mutableListOf<Long>()
    var offline = false
    var loseNextResponse = false
    var nextId = 41L

    override suspend fun create(request: ObjectRequest): Long {
        if (offline) throw IOException("offline")
        val existing = objects.entries.firstOrNull {
            it.value.idempotencyKey != null && it.value.idempotencyKey == request.idempotencyKey
        }
        val id = existing?.key ?: nextId++.also { objects[it] = request }
        if (loseNextResponse) {
            loseNextResponse = false
            throw IOException("response lost")
        }
        return id
    }

    override suspend fun update(objectId: Long, request: ObjectRequest): Long {
        if (offline) throw IOException("offline")
        if (objectId !in objects) throw ObjectGoneException()
        objects[objectId] = request
        updates += objectId
        return objectId
    }

    override suspend fun delete(objectId: Long, mapId: Long, teamId: Long) {
        if (offline) throw IOException("offline")
        objects.remove(objectId)
        deleted += objectId
    }
}

private class FakeMailbox : DeviceMailboxApi {
    val pending = mutableListOf<MailboxDelivery>()
    val acknowledged = mutableListOf<String>()
    var failAcknowledge = false
    private var nextId = 1L

    fun add(recipient: Long, event: String, type: String = "hit", source: Long = 9) {
        val id = nextId++
        pending += MailboxDelivery(id, id.toString(), type, recipient, source, event)
    }

    override suspend fun inbox(afterCursor: Long?): List<MailboxDelivery> =
        pending.filter { it.cursor > (afterCursor ?: 0) }

    override suspend fun acknowledge(deliveryReference: String) {
        if (failAcknowledge) throw IOException("lost")
        pending.removeAll { it.deliveryReference == deliveryReference }
        acknowledged += deliveryReference
    }
}

private class Stored : GameStore {
    var state: GameState? = null

    override fun saveGame(state: GameState) {
        this.state = state
    }
}

private class Harness(val settings: ObjectSettings = ObjectSettings(objectName = "Antenna")) {
    val geo = FakeGeo()
    val mailbox = FakeMailbox()
    val store = Stored()
    val played = mutableListOf<SignalKind>()
    var elapsed = 1_000L
    var epoch = 1_700_000_000_000L
    val controller = build()

    fun build(): ObjectController = ObjectController(
        store = store,
        geo = geo,
        mailbox = mailbox,
        signals = object : SignalPort {
            override fun play(kind: SignalKind) {
                played += kind
            }
        },
        machine = ObjectMachine({ 0.5 }, { "next-key" }),
        environment = { Environment(settings, CONTEXT, 0.0) },
        clock = { Clock(elapsed, epoch) },
    ).also { it.load(store.state ?: GameState("first-key")) }

    fun deploy() {
        controller.onFix(Fix(HOME_LATITUDE, HOME_LONGITUDE, 5.0, epoch))
        controller.activate()
    }

    fun pass(millis: Long) {
        elapsed += millis
        epoch += millis
        controller.tick()
    }
}

class ObjectControllerTest {
    @Test
    fun hitIsCountedAndSignalled() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 5))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        assertEquals(1, rig.controller.state.value!!.hits)
        assertEquals(listOf(SignalKind.HIT), rig.played)
        assertEquals(listOf("1"), rig.mailbox.acknowledged)
    }

    @Test
    fun hitDuringDeploymentCountsAndDeploymentContinues() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", activationSeconds = 30, hitsToDestroy = 3))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        val state = rig.controller.state.value!!
        assertEquals(1, state.hits)
        assertEquals(LifeState.DEPLOYING, state.life)
    }

    @Test
    fun sameEventDeliveredTwiceCountsOnce() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        assertEquals(1, rig.controller.state.value!!.hits)
        assertEquals(2, rig.mailbox.acknowledged.size)
    }

    @Test
    fun sameEventIdFromAnotherApplicationIsAnotherHit() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1", source = 9)
        rig.mailbox.add(41, "shot-1", source = 10)
        rig.controller.syncMailbox()
        assertEquals(2, rig.controller.state.value!!.hits)
    }

    @Test
    fun crashBeforeAcknowledgementDoesNotCountTwice() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.mailbox.failAcknowledge = true
        runCatching { rig.controller.syncMailbox() }
        assertEquals(1, rig.store.state!!.hits)

        rig.mailbox.failAcknowledge = false
        val restarted = rig.build()
        restarted.syncMailbox()
        assertEquals(1, restarted.state.value!!.hits)
        assertTrue(rig.mailbox.pending.isEmpty())
    }

    @Test
    fun lateDeliveryAfterDeactivationCounts() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.controller.deactivate()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        assertEquals(1, rig.controller.state.value!!.hits)
    }

    @Test
    fun deactivationDoesNotHeal() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.mailbox.add(41, "shot-2")
        rig.controller.syncMailbox()
        rig.controller.deactivate()
        rig.controller.syncMap()
        rig.controller.onFix(Fix(HOME_LATITUDE, HOME_LONGITUDE, 5.0, rig.epoch))
        rig.controller.activate()
        assertEquals(2, rig.controller.state.value!!.hits)
    }

    @Test
    fun hitsForAnEarlierLifeAreIgnored() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.controller.reset()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        assertEquals(0, rig.controller.state.value!!.hits)
        assertTrue(rig.mailbox.pending.isEmpty())
    }

    @Test
    fun otherEventTypesAreAcknowledgedAndIgnored() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "ping-1", type = "ping")
        rig.controller.syncMailbox()
        assertEquals(0, rig.controller.state.value!!.hits)
        assertTrue(rig.mailbox.pending.isEmpty())
    }

    @Test
    fun indestructibleObjectCountsButSurvives() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 0))
        rig.deploy()
        rig.controller.syncMap()
        repeat(10) { rig.mailbox.add(41, "shot-$it") }
        rig.controller.syncMailbox()
        val state = rig.controller.state.value!!
        assertEquals(10, state.hits)
        assertEquals(LifeState.ACTIVE, state.life)
        assertFalse(SignalKind.DESTROYED in rig.played)
    }

    @Test
    fun lastHitDestroysAndReplacesTheObjectWithAWreck() = runTest {
        val rig = Harness(ObjectSettings(objectName = "Antenna", hitsToDestroy = 2, destroyedIconId = 13))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.mailbox.add(41, "shot-2")
        rig.controller.syncMailbox()
        assertEquals(LifeState.DESTROYED, rig.controller.state.value!!.life)
        assertEquals(
            listOf(SignalKind.HIT, SignalKind.HIT, SignalKind.DESTROYED),
            rig.played,
        )
        rig.controller.syncMap()
        val wreck = rig.geo.objects.values.single()
        assertEquals("Antenna destroyed", wreck.title)
        assertEquals(listOf(41L), rig.geo.deleted)
    }

    @Test
    fun destroyedPhoneIgnoresFurtherHits() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        rig.mailbox.add(41, "shot-2")
        rig.controller.syncMailbox()
        assertEquals(1, rig.controller.state.value!!.hits)
    }

    @Test
    fun destructionOfflineSignalsAtOnceAndPublishesOnReturn() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.geo.offline = true
        rig.controller.syncMailbox()
        assertTrue(SignalKind.DESTROYED in rig.played)
        rig.controller.syncMap()
        assertEquals(1, rig.geo.objects.size)
        assertTrue(41L in rig.geo.objects)

        val fixed = rig.controller.state.value!!.wreck
        rig.geo.offline = false
        rig.pass(5_000)
        rig.controller.syncMap()
        assertNull(rig.controller.state.value!!.wreck)
        assertEquals("A destroyed", rig.geo.objects.values.single().title)
        assertEquals(fixed!!.key, rig.geo.objects.values.single().idempotencyKey)
    }

    @Test
    fun lostCreateResponseDoesNotDuplicateTheWreck() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        rig.geo.loseNextResponse = true
        rig.controller.syncMap()
        rig.pass(5_000)
        rig.controller.syncMap()
        assertEquals(1, rig.geo.objects.values.count { it.title == "A destroyed" })
        assertNull(rig.controller.state.value!!.wreck)
    }

    @Test
    fun wreckSurvivesAProcessDeath() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.geo.offline = true
        rig.controller.syncMailbox()

        rig.geo.offline = false
        val restarted = rig.build()
        restarted.syncMap()
        assertEquals("A destroyed", rig.geo.objects.values.single().title)
    }

    @Test
    fun resetBeforeASecondDestructionGivesASecondWreck() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", hitsToDestroy = 1))
        rig.deploy()
        rig.controller.syncMap()
        rig.mailbox.add(41, "shot-1")
        rig.controller.syncMailbox()
        rig.controller.syncMap()
        val firstWreck = rig.geo.objects.keys.single()

        rig.controller.reset()
        rig.controller.onFix(Fix(HOME_LATITUDE, HOME_LONGITUDE, 5.0, rig.epoch))
        rig.controller.activate()
        rig.controller.syncMap()
        val second = rig.geo.objects.keys.single { it != firstWreck }
        rig.mailbox.add(second, "shot-2")
        rig.controller.syncMailbox()
        rig.controller.syncMap()
        assertEquals(2, rig.geo.objects.values.count { it.title == "A destroyed" })
    }

    @Test
    fun updateOfAVanishedObjectCreatesItAgain() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.geo.objects.clear()
        rig.pass(30_000)
        rig.controller.syncMap()
        assertEquals(1, rig.geo.objects.size)
        val id = rig.controller.state.value!!.objectId
        assertTrue(id != 41L)
        assertEquals(setOf(41L, id), rig.controller.state.value!!.lifeObjectIds)
    }

    @Test
    fun deletesRetryUntilTheyReachTheServer() = runTest {
        val rig = Harness()
        rig.deploy()
        rig.controller.syncMap()
        rig.geo.offline = true
        rig.controller.deactivate()
        rig.controller.syncMap()
        assertEquals(setOf(41L), rig.controller.state.value!!.pendingDeletes)
        rig.geo.offline = false
        rig.pass(5_000)
        rig.controller.syncMap()
        assertTrue(rig.controller.state.value!!.pendingDeletes.isEmpty())
        assertTrue(rig.geo.objects.isEmpty())
    }

    @Test
    fun backgroundWorkFollowsTheState() = runTest {
        val rig = Harness(ObjectSettings(objectName = "A", maxDowntimeMinutes = 20))
        assertFalse(rig.controller.needsBackground())
        rig.deploy()
        assertTrue(rig.controller.needsBackground())
        rig.controller.syncMap()
        rig.controller.deactivate()
        rig.controller.syncMap()
        assertTrue(rig.controller.needsBackground())
    }
}
