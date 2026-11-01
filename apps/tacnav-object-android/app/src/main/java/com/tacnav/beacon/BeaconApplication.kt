package com.tacnav.beacon

import android.app.Application
import android.os.SystemClock
import com.tacnav.beacon.activation.HttpActivationApi
import com.tacnav.beacon.game.Clock
import com.tacnav.beacon.game.Environment
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.ObjectController
import com.tacnav.beacon.game.ObjectMachine
import com.tacnav.beacon.network.AuthorizationLostException
import com.tacnav.beacon.network.HttpTacNavApi
import com.tacnav.beacon.security.BeaconRepository
import com.tacnav.beacon.security.SecureStateStore
import com.tacnav.beacon.session.AndroidSignals
import com.tacnav.beacon.session.Compass
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BeaconApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { SecureStateStore(this) }
    private val random = SecureRandom()
    private val machine = ObjectMachine(random::nextDouble, ::newWreckKey)
    private val mutableOnline = MutableStateFlow(true)

    val compass by lazy { Compass(this) }
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    /** True while the activity is on screen; the service may stop otherwise. */
    @Volatile
    var visible: Boolean = false

    /** Set by the service. While it runs, ticks already account for elapsed time. */
    @Volatile
    var serviceRunning: Boolean = false

    val repository: BeaconRepository by lazy {
        BeaconRepository(store, HttpActivationApi())
    }

    private val api by lazy {
        HttpTacNavApi(
            session = {
                repository.state.value?.let { it.activation to it.context }
            },
        )
    }

    val controller: ObjectController by lazy {
        ObjectController(
            store = store,
            geo = api,
            mailbox = api,
            signals = AndroidSignals(this, applicationScope),
            machine = machine,
            environment = {
                repository.state.value?.let {
                    Environment(it.settings, it.context, compass.pose.heading)
                }
            },
            clock = { Clock(SystemClock.elapsedRealtime(), System.currentTimeMillis()) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        controller.load(if (repository.state.value == null) null else loadOrNewGame())
    }

    private fun loadOrNewGame(): GameState = store.loadGame() ?: GameState(newWreckKey())

    private fun newWreckKey(): String = UUID.randomUUID().toString()

    /**
     * Called after a successful activation, which always starts a new game.
     *
     * Reactivating the same application keeps its unsent wreck and deletes, which
     * the new credential can still finish. Another application cannot.
     */
    fun startNewGame(previousApplicationId: Long?) {
        val activated = repository.state.value?.activation?.applicationId
        if (activated == previousApplicationId && controller.state.value != null) {
            controller.reset()
            return
        }
        val fresh = GameState(newWreckKey())
        store.saveGame(fresh)
        controller.load(fresh)
    }

    /** Called when this phone gives up its activation. */
    fun forgetGame() {
        controller.load(null)
    }

    fun publishOnline(value: Boolean) {
        mutableOnline.value = value
    }

    /** Reads and applies pending hits; a failure only delays them. */
    fun syncMailbox() {
        if (repository.state.value == null) return
        applicationScope.launch {
            runCatching { controller.syncMailbox() }.onFailure { error ->
                if (error is AuthorizationLostException) controller.report(error.message)
            }
            runCatching { controller.syncMap() }
        }
    }
}
