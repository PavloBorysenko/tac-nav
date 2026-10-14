package com.tacnav.mortar.data

import com.tacnav.mortar.activation.ActivationApi
import com.tacnav.mortar.activation.ActivationPayload
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.CatalogIcon
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.ScopeRules
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.security.CalibrationState
import com.tacnav.mortar.security.LocalState
import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.security.PinPolicy
import com.tacnav.mortar.security.SecureStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProtectedSettingsUpdate(
    val settings: GameplaySettings,
    val mapId: Long,
    val teamId: Long,
)

sealed interface ProtectedResult {
    data object Saved : ProtectedResult
    data class Denied(val attempt: PinAttempt) : ProtectedResult
    data class Invalid(val message: String) : ProtectedResult
}

class MortarRepository(
    private val store: SecureStateStore,
    private val api: ActivationApi,
    private val pinPolicy: PinPolicy = PinPolicy(),
) {
    private val mutableState = MutableStateFlow(store.load())
    private val mutableIcons = MutableStateFlow<List<CatalogIcon>>(emptyList())

    val state: StateFlow<LocalState?> = mutableState.asStateFlow()
    val icons: StateFlow<List<CatalogIcon>> = mutableIcons.asStateFlow()

    suspend fun activate(payload: ActivationPayload, fcmToken: String?): LocalState {
        val activation = api.activate(payload)
        val newState = store.replaceActivation(activation)
        mutableState.value = newState
        if (!fcmToken.isNullOrBlank()) registerFcmToken(fcmToken)
        return mutableState.value ?: newState
    }

    fun logout() {
        store.clear()
        mutableState.value = null
        mutableIcons.value = emptyList()
    }

    fun saveProtected(
        pin: String,
        update: ProtectedSettingsUpdate,
    ): ProtectedResult {
        val current = mutableState.value ?: return ProtectedResult.Invalid("Activate first.")
        val (attempt, throttle) = pinPolicy.verify(pin, current.pin, current.pinThrottle)
        if (attempt !is PinAttempt.Accepted) {
            persist(current.copy(pinThrottle = throttle))
            return ProtectedResult.Denied(attempt)
        }
        return try {
            val settings = update.settings.validated()
            val context = ScopeRules.select(
                current.activation.maps,
                update.mapId,
                update.teamId,
            )
            persist(
                current.copy(
                    settings = settings,
                    context = context,
                    pinThrottle = throttle,
                    activeFiringMode = current.activeFiringMode
                        .takeIf(settings.firingModes::contains)
                        ?: settings.firingModes.first(),
                    activeAimingMode = current.activeAimingMode
                        .takeIf(settings.aimingModes::contains)
                        ?: settings.aimingModes.first(),
                ),
            )
            ProtectedResult.Saved
        } catch (error: IllegalArgumentException) {
            ProtectedResult.Invalid(error.message ?: "Settings are invalid.")
        }
    }

    fun changePin(oldPin: String, newPin: String): ProtectedResult {
        val current = mutableState.value ?: return ProtectedResult.Invalid("Activate first.")
        val (attempt, throttle) = pinPolicy.verify(oldPin, current.pin, current.pinThrottle)
        if (attempt !is PinAttempt.Accepted) {
            persist(current.copy(pinThrottle = throttle))
            return ProtectedResult.Denied(attempt)
        }
        return try {
            persist(
                current.copy(
                    pin = pinPolicy.create(newPin),
                    pinThrottle = throttle,
                ),
            )
            ProtectedResult.Saved
        } catch (error: IllegalArgumentException) {
            ProtectedResult.Invalid(error.message ?: "PIN is invalid.")
        }
    }

    fun selectPlayerModes(
        firingMode: FiringMode,
        aimingMode: AimingMode,
    ) {
        val current = requireNotNull(mutableState.value) { "Activate first." }
        require(firingMode in current.settings.firingModes) { "Firing mode is not allowed." }
        require(aimingMode in current.settings.aimingModes) { "Aiming mode is not allowed." }
        persist(
            current.copy(
                activeFiringMode = firingMode,
                activeAimingMode = aimingMode,
            ),
        )
    }

    fun saveCalibration(calibration: CalibrationState) {
        val current = requireNotNull(mutableState.value) { "Activate first." }
        persist(current.copy(calibration = calibration))
    }

    fun setSession(active: Boolean) {
        val current = mutableState.value ?: return
        persist(current.copy(activeSession = active))
    }

    suspend fun refreshIcons(): List<CatalogIcon> {
        val current = requireNotNull(mutableState.value) { "Activate first." }
        return api.fetchIconCatalog(current.activation).also {
            mutableIcons.value = it
            val latest = mutableState.value ?: return@also
            if (latest.activation.installationId != current.activation.installationId) {
                return@also
            }
            val selectedId = latest.settings.impactIconId
            if (selectedId != null && it.none { icon -> icon.id == selectedId }) {
                store.clearSelectedIcon()
                persist(latest.copy(settings = latest.settings.copy(impactIconId = null)))
            }
        }
    }

    suspend fun selectIcon(pin: String, icon: CatalogIcon): ProtectedResult {
        val current = mutableState.value ?: return ProtectedResult.Invalid("Activate first.")
        val (attempt, throttle) = pinPolicy.verify(pin, current.pin, current.pinThrottle)
        if (attempt !is PinAttempt.Accepted) {
            persist(current.copy(pinThrottle = throttle))
            return ProtectedResult.Denied(attempt)
        }
        val iconBytes = api.downloadIcon(icon)
        val latest = mutableState.value
            ?: return ProtectedResult.Invalid("Activation changed while loading the icon.")
        if (latest.activation.installationId != current.activation.installationId) {
            return ProtectedResult.Invalid("Activation changed while loading the icon.")
        }
        store.cacheSelectedIcon(iconBytes)
        persist(
            latest.copy(
                settings = latest.settings.copy(impactIconId = icon.id),
                pinThrottle = throttle,
            ),
        )
        return ProtectedResult.Saved
    }

    suspend fun registerFcmToken(token: String) {
        val current = mutableState.value ?: return
        persist(current.copy(pendingFcmToken = token))
        api.registerFcmToken(current.activation, token)
        mutableState.value?.let { latest ->
            if (latest.pendingFcmToken == token) {
                persist(latest.copy(pendingFcmToken = null))
            }
        }
    }

    fun selectedIconPath(): String? = store.selectedIconFile()?.absolutePath

    fun loadShots(): List<ShotRecord> = store.loadShots()

    fun saveShots(shots: List<ShotRecord>) {
        store.saveShots(shots)
    }

    private fun persist(state: LocalState) {
        store.save(state)
        mutableState.value = state
    }
}
