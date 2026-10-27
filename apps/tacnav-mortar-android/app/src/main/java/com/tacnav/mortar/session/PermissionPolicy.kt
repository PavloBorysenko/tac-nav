package com.tacnav.mortar.session

import com.tacnav.mortar.domain.FiringMode

data class PermissionState(
    val preciseLocation: Boolean,
    val microphone: Boolean,
    val camera: Boolean,
    val notifications: Boolean,
    val playServices: Boolean,
)

/** Maps each missing permission to the degraded behavior the session must show. */
object PermissionPolicy {
    const val LOCATION_DENIED =
        "Precise location is not allowed. Firing is blocked until you allow precise location."
    const val MICROPHONE_DENIED =
        "Microphone is not allowed. Acoustic firing is off; button firing remains available."
    const val CAMERA_DENIED = "Camera is not allowed. Use manual activation."
    const val NOTIFICATIONS_DENIED =
        "Notifications are off. Allow them to start an active session and receive deliveries."
    const val PLAY_SERVICES_MISSING =
        "Google Play Services is unavailable. Location and Firebase wake-up will not work."

    fun canStartSession(state: PermissionState): Boolean =
        state.preciseLocation && state.notifications && state.playServices

    fun canUseAcoustic(state: PermissionState): Boolean = state.microphone

    fun sessionWarnings(state: PermissionState, firingModes: Set<FiringMode>): List<String> =
        buildList {
            if (!state.playServices) add(PLAY_SERVICES_MISSING)
            if (!state.preciseLocation) add(LOCATION_DENIED)
            if (!state.notifications) add(NOTIFICATIONS_DENIED)
            if (!state.microphone && FiringMode.ACOUSTIC in firingModes) add(MICROPHONE_DENIED)
        }
}
