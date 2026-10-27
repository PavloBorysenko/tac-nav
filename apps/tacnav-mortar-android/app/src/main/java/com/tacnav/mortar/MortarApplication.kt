package com.tacnav.mortar

import android.app.Application
import android.location.Location
import com.tacnav.mortar.activation.HttpActivationApi
import com.tacnav.mortar.data.MortarRepository
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.messaging.MailboxSync
import com.tacnav.mortar.network.AuthorizationLostException
import com.tacnav.mortar.network.HttpTacNavApi
import com.tacnav.mortar.network.MailboxDelivery
import com.tacnav.mortar.security.SecureStateStore
import com.tacnav.mortar.sensors.LivePose
import com.tacnav.mortar.session.FiringEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MortarApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpApi = HttpTacNavApi()
    val firingEngine = FiringEngine(
        geo = httpApi,
        mailbox = httpApi,
        onChange = ::publishShots,
    )
    private val mailboxSync = MailboxSync(httpApi, ::recordDelivery)

    val repository: MortarRepository by lazy {
        MortarRepository(
            store = SecureStateStore(this),
            api = HttpActivationApi(),
        )
    }

    private val mutableShots = MutableStateFlow<List<ShotRecord>>(emptyList())
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableLevel = MutableStateFlow(-120.0)
    private val mutableLocation = MutableStateFlow<Location?>(null)
    private val mutablePose = MutableStateFlow(LivePose())
    private val mutableOnline = MutableStateFlow(true)
    private val mutableDeliveries = MutableStateFlow<List<MailboxDelivery>>(emptyList())
    private val mutableWarnings = MutableStateFlow<List<String>>(emptyList())

    val shots: StateFlow<List<ShotRecord>> = mutableShots.asStateFlow()
    val sessionError: StateFlow<String?> = mutableError.asStateFlow()
    val acousticLevel: StateFlow<Double> = mutableLevel.asStateFlow()
    val location: StateFlow<Location?> = mutableLocation.asStateFlow()
    val pose: StateFlow<LivePose> = mutablePose.asStateFlow()
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()
    val deliveries: StateFlow<List<MailboxDelivery>> = mutableDeliveries.asStateFlow()
    val permissionWarnings: StateFlow<List<String>> = mutableWarnings.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        firingEngine.restore(repository.loadShots())
        mutableShots.value = firingEngine.currentShots()
    }

    private fun publishShots(value: List<ShotRecord>) {
        mutableShots.value = value
        repository.saveShots(value)
    }

    fun publishPose(value: LivePose) {
        mutablePose.value = value
    }

    fun publishError(value: String?) {
        mutableError.value = value
    }

    fun publishLevel(value: Double) {
        mutableLevel.value = value
    }

    fun publishLocation(value: Location) {
        mutableLocation.value = value
        bindApi()
    }

    fun publishOnline(value: Boolean) {
        mutableOnline.value = value
    }

    fun publishPermissionWarnings(value: List<String>) {
        mutableWarnings.value = value
    }

    fun bindApi() {
        repository.state.value?.let { state ->
            httpApi.activation = state.activation
            httpApi.mapId = state.context.mapId
            httpApi.teamId = state.context.teamId
        }
    }

    /** Reads and acknowledges pending deliveries; failures only delay delivery. */
    fun syncMailbox() {
        if (repository.state.value == null) return
        applicationScope.launch {
            bindApi()
            runCatching { mailboxSync.sync() }.onFailure { error ->
                if (error is AuthorizationLostException) publishError(error.message)
            }
        }
    }

    /** Drops every installation-specific runtime value before reactivation. */
    fun resetLocalSession() {
        firingEngine.clear()
        mailboxSync.reset()
        mutableDeliveries.value = emptyList()
        mutableError.value = null
        mutableLocation.value = null
        mutablePose.value = LivePose()
    }

    private fun recordDelivery(delivery: MailboxDelivery) {
        mutableDeliveries.value = (mutableDeliveries.value + delivery).takeLast(MAX_DELIVERIES)
    }

    private companion object {
        const val MAX_DELIVERIES = 50
    }
}
