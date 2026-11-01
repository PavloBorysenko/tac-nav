package com.tacnav.beacon.security

import com.tacnav.beacon.activation.ActivationApi
import com.tacnav.beacon.activation.ActivationPayload
import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.CatalogIcon
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.ScopeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Identity, protected settings, and the PIN. Game state lives in the controller. */
class BeaconRepository(
    private val store: SecureStateStore,
    private val api: ActivationApi,
    private val pinPolicy: PinPolicy = PinPolicy(),
) {
    private val mutableState = MutableStateFlow(store.load())

    val state: StateFlow<LocalState?> = mutableState.asStateFlow()

    /** Every activation replaces local settings, the PIN, and the game. */
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
        val current = mutableState.value ?: return PinAttempt.Rejected(0)
        val (attempt, throttle) = pinPolicy.verify(pin, current.pin, current.pinThrottle)
        persist(current.copy(pinThrottle = throttle))
        return attempt
    }

    fun changePin(pin: String) {
        val current = mutableState.value ?: return
        persist(current.copy(pin = pinPolicy.create(pin), pinThrottle = PinThrottleState()))
    }

    fun saveSettings(settings: ObjectSettings) {
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

    /** A failed send stays pending, so the next resume retries it. */
    suspend fun registerFcmToken(token: String) {
        val current = mutableState.value ?: return
        persist(current.copy(pendingFcmToken = token))
        api.registerFcmToken(current.activation, token)
        mutableState.value?.let { persist(it.copy(pendingFcmToken = null)) }
    }

    fun logout() {
        store.clear()
        mutableState.value = null
    }

    private fun persist(state: LocalState) {
        store.save(state)
        mutableState.value = state
    }
}
