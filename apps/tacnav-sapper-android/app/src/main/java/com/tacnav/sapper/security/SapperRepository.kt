package com.tacnav.sapper.security

import com.tacnav.sapper.activation.ActivationApi
import com.tacnav.sapper.activation.ActivationPayload
import com.tacnav.sapper.activation.HttpActivationApi
import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.CatalogIcon
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.ScopeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Identity, protected settings, and the PIN. Game progress lives in the controller. */
class SapperRepository(
    private val store: SecureStateStore,
    private val api: ActivationApi,
    private val pinPolicy: PinPolicy = PinPolicy(),
) {
    private val mutableState = MutableStateFlow(store.load())

    /** Before the first activation the PIN is the default, and its failures are counted in memory. */
    private val defaultPin by lazy { pinPolicy.create(HttpActivationApi.DEFAULT_PIN) }
    private var defaultThrottle = PinThrottleState()

    val state: StateFlow<LocalState?> = mutableState.asStateFlow()

    /**
     * Every activation replaces settings and the PIN with the server reference and
     * clears counters and progress. A refused activation changes nothing.
     */
    suspend fun activate(payload: ActivationPayload): LocalState {
        val activation = api.activate(payload)
        store.clear()
        val state = LocalState(
            activation = activation,
            settings = activation.referenceSettings,
            context = ActiveContext(activation.defaultMapId, activation.defaultTeamId),
            pin = pinPolicy.create(activation.settingsPin),
        )
        store.save(state)
        mutableState.value = state
        return state
    }

    fun verifyPin(pin: String): PinAttempt {
        val current = mutableState.value
        if (current == null) {
            val (attempt, throttle) = pinPolicy.verify(pin, defaultPin, defaultThrottle)
            defaultThrottle = throttle
            return attempt
        }
        val (attempt, throttle) = pinPolicy.verify(pin, current.pin, current.pinThrottle)
        persist(current.copy(pinThrottle = throttle))
        return attempt
    }

    fun changePin(pin: String) {
        val current = mutableState.value ?: return
        persist(current.copy(pin = pinPolicy.create(pin), pinThrottle = PinThrottleState()))
    }

    fun saveSettings(settings: SapperSettings) {
        val current = mutableState.value ?: return
        persist(current.copy(settings = settings.validated()))
    }

    fun selectContext(mapId: Long, teamId: Long) {
        val current = mutableState.value ?: return
        persist(
            current.copy(context = ScopeRules.select(current.activation.maps, mapId, teamId)),
        )
    }

    suspend fun icons(): List<CatalogIcon> {
        val current = mutableState.value ?: return emptyList()
        return api.fetchIconCatalog(current.activation)
    }

    /** Reset connection: forget the credential, settings, progress, and counters. */
    fun resetConnection() {
        store.clear()
        mutableState.value = null
        defaultThrottle = PinThrottleState()
    }

    private fun persist(state: LocalState) {
        store.save(state)
        mutableState.value = state
    }
}
