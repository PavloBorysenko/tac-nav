package com.tacnav.beacon.game

import com.tacnav.beacon.network.AuthorizationLostException
import com.tacnav.beacon.network.DeviceMailboxApi
import com.tacnav.beacon.network.GeoObjectApi
import com.tacnav.beacon.network.MailboxDelivery
import com.tacnav.beacon.network.ObjectGoneException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface GameStore {
    fun saveGame(state: GameState)
}

interface SignalPort {
    fun play(kind: SignalKind)
}

/**
 * Runs the machine against the outside world.
 *
 * Game state is saved before a signal plays or a delivery is acknowledged, so a
 * process death at any point resumes without losing or repeating a hit.
 */
class ObjectController(
    private val store: GameStore,
    private val geo: GeoObjectApi,
    private val mailbox: DeviceMailboxApi,
    private val signals: SignalPort,
    private val machine: ObjectMachine,
    private val environment: () -> Environment?,
    private val clock: () -> Clock,
) {
    private val mutableState = MutableStateFlow<GameState?>(null)
    private val mutableMessage = MutableStateFlow<String?>(null)
    private val mailboxLock = Mutex()
    private val mapLock = Mutex()
    private var savedKey: List<Any?>? = null
    private var savedAtElapsed = 0L

    val state: StateFlow<GameState?> = mutableState.asStateFlow()
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    fun load(state: GameState?) {
        synchronized(this) {
            mutableState.value = state
            savedKey = state?.durableKey()
        }
    }

    fun report(value: String?) {
        mutableMessage.value = value
    }

    fun clearMessage() {
        mutableMessage.value = null
    }

    fun canActivate(): Boolean {
        val current = mutableState.value ?: return false
        val env = environment() ?: return false
        return machine.canActivate(current, env.settings, clock().epochMillis)
    }

    fun activate() = transition { state, env, now -> machine.activate(state, env, now) }

    fun deactivate() = transition { state, env, _ -> machine.deactivate(state, env) }

    fun arm() = transition { state, _, _ -> machine.arm(state) }

    fun reset() = transition { state, _, _ -> machine.reset(state) }

    fun tick() = transition { state, env, now -> machine.tick(state, env, now) }

    fun onFix(fix: Fix) = transition { state, env, now -> machine.onFix(state, env, fix, now) }

    fun restore() = transition { state, env, now -> machine.restore(state, env, now) }

    private fun transition(step: (GameState, Environment, Clock) -> Transition) {
        val result = synchronized(this) {
            val current = mutableState.value ?: return
            val env = environment() ?: return
            val next = step(current, env, clock())
            commit(next.state)
            next
        }
        result.message?.let { mutableMessage.value = it }
        result.signals.forEach(signals::play)
    }

    private fun update(change: (GameState) -> GameState) {
        synchronized(this) {
            val current = mutableState.value ?: return
            commit(change(current))
        }
    }

    private fun commit(next: GameState) {
        val key = next.durableKey()
        val elapsed = clock().elapsedMillis
        if (key != savedKey || elapsed - savedAtElapsed >= SAVE_MILLIS) {
            store.saveGame(next)
            savedKey = key
            savedAtElapsed = elapsed
        }
        mutableState.value = next
    }

    /** Applies each delivery durably before acknowledging it. */
    suspend fun syncMailbox() {
        mailboxLock.withLock {
            var cursor: Long? = null
            while (true) {
                val batch = mailbox.inbox(cursor)
                if (batch.isEmpty()) break
                batch.forEach { delivery ->
                    apply(delivery)
                    mailbox.acknowledge(delivery.deliveryReference)
                }
                cursor = batch.last().cursor
                if (batch.size < PAGE_SIZE) break
            }
        }
    }

    private fun apply(delivery: MailboxDelivery) {
        val eventId = "${delivery.sourceApplicationId}:" +
            delivery.clientEventId.ifBlank { delivery.deliveryReference }
        transition { state, env, now ->
            machine.onDelivery(
                state,
                env,
                delivery.eventType,
                eventId,
                delivery.recipientObjectId,
                now,
            )
        }
    }

    /** Runs every map write that is due now, one at a time. */
    suspend fun syncMap() {
        if (!mapLock.tryLock()) return
        try {
            repeat(MAX_WRITES_PER_PASS) {
                val current = mutableState.value ?: return
                val now = clock().elapsedMillis
                val op = MapPlanner.next(current, now) ?: return
                if (!run(op, now)) return
            }
        } finally {
            mapLock.unlock()
        }
    }

    private suspend fun run(op: MapOp, now: Long): Boolean = try {
        when (op) {
            is MapOp.CreateWreck -> {
                geo.create(op.request)
                update(machine::wreckAccepted)
            }
            is MapOp.Delete -> {
                geo.delete(op.id, op.mapId, op.teamId)
                update { machine.deleted(it, op.id) }
            }
            is MapOp.WriteObject -> writeObject(op, now)
            is MapOp.WriteArea -> writeArea(op, now)
        }
        true
    } catch (error: AuthorizationLostException) {
        mutableMessage.value = error.message
        update { machine.writeFailed(it, now) }
        false
    } catch (_: IOException) {
        update { machine.writeFailed(it, now) }
        false
    }

    private suspend fun writeObject(op: MapOp.WriteObject, now: Long) {
        try {
            val id = op.id?.let { geo.update(it, op.request) } ?: geo.create(op.request)
            update { machine.objectWritten(it, id, op.life, now) }
        } catch (_: ObjectGoneException) {
            update(machine::objectGone)
        }
    }

    private suspend fun writeArea(op: MapOp.WriteArea, now: Long) {
        try {
            val id = op.id?.let { geo.update(it, op.request) } ?: geo.create(op.request)
            update { machine.areaWritten(it, id, now) }
        } catch (_: ObjectGoneException) {
            update(machine::areaGone)
        }
    }

    /** Whether anything needs the phone awake with the application closed. */
    fun needsBackground(): Boolean {
        val current = mutableState.value ?: return false
        val env = environment() ?: return false
        if (current.wreck != null || current.pendingDeletes.isNotEmpty()) return true
        return when (current.life) {
            LifeState.DEPLOYING, LifeState.ACTIVE -> true
            LifeState.WAITING -> current.downtimeStarted ||
                (env.settings.autoActivateEnabled && current.armed)
            LifeState.DESTROYED -> false
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val SAVE_MILLIS = 5_000L
        const val MAX_WRITES_PER_PASS = 6
    }
}
