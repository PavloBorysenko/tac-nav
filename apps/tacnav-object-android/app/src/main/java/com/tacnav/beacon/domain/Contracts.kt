package com.tacnav.beacon.domain

import kotlin.math.max
import kotlin.math.sqrt

enum class ObjectVisibility(val wireValue: String) {
    TEAM("team"),
    ALL("all"),
}

data class TeamScope(
    val id: Long,
    val name: String,
)

data class MapScope(
    val id: Long,
    val name: String,
    val teams: List<TeamScope>,
)

data class ActiveContext(
    val mapId: Long,
    val teamId: Long,
)

data class CatalogIcon(
    val id: Long,
    val title: String,
    val imageUrl: String,
)

data class ObjectSettings(
    val objectName: String = "Object",
    val description: String = "",
    val iconId: Long? = null,
    val destroyedIconId: Long? = null,
    val hitsToDestroy: Int = 0,
    val sizeAMetres: Double? = null,
    val sizeBMetres: Double? = null,
    val activationSeconds: Long = 0,
    val visibility: ObjectVisibility = ObjectVisibility.TEAM,
    val randomAreaEnabled: Boolean = false,
    val randomAreaRadiusMetres: Double = 0.0,
    val autoDeactivateMetres: Double = 0.0,
    val maxLocationAccuracyMetres: Double = 15.0,
    val autoActivateEnabled: Boolean = false,
    val autoActivateSeconds: Long = 0,
    val maxDowntimeMinutes: Long? = null,
    val downtimeResetMinutes: Long? = null,
) {
    /** Distance from the centre to the object's furthest edge. */
    val extentMetres: Double
        get() {
            val first = sizeAMetres
            val second = sizeBMetres
            return when {
                first != null && second != null -> sqrt(first * first + second * second) / 2
                else -> first ?: second ?: 0.0
            }
        }

    /** Radius within which the phone counts as still. */
    val stillRadiusMetres: Double
        get() = max(autoDeactivateMetres, 2 * maxLocationAccuracyMetres)

    val downtimeMillis: Long?
        get() = maxDowntimeMinutes?.takeIf { it > 0 }?.let { it * 60_000 }

    /** An empty reset time means the allowance value; zero resets at once. */
    val downtimeResetMillis: Long?
        get() = downtimeMillis?.let { allowance ->
            downtimeResetMinutes?.let { it * 60_000 } ?: allowance
        }

    fun validated(): ObjectSettings {
        require(objectName.isNotBlank()) { "Object name is required." }
        require(hitsToDestroy >= 0) { "Hits to destroy cannot be negative." }
        require((sizeAMetres ?: 1.0) > 0 && (sizeBMetres ?: 1.0) > 0) {
            "Object sizes must be positive."
        }
        require(activationSeconds >= 0) { "Deployment seconds cannot be negative." }
        require(maxLocationAccuracyMetres > 0) { "GPS accuracy limit must be positive." }
        if (randomAreaEnabled) {
            require(randomAreaRadiusMetres > 0 && randomAreaRadiusMetres >= extentMetres) {
                "The random area radius must be positive and contain the whole object."
            }
        }
        if (autoActivateEnabled) {
            require(autoActivateSeconds > 0) {
                "Automatic activation needs a positive number of still seconds."
            }
        }
        require(autoDeactivateMetres >= 0) { "Automatic deactivation cannot be negative." }
        if (autoDeactivateMetres > 0) {
            require(autoDeactivateMetres >= 2 * maxLocationAccuracyMetres) {
                "Automatic deactivation must be at least " +
                    "${2 * maxLocationAccuracyMetres} metres, twice the GPS accuracy limit."
            }
        }
        require((maxDowntimeMinutes ?: 0) >= 0 && (downtimeResetMinutes ?: 0) >= 0) {
            "Downtime values cannot be negative."
        }
        return this
    }
}

data class ServiceConfiguration(
    val iconCatalogPath: String = "/wp-json/tacnav-devices/v1/icons",
    val fcmRegistrationPath: String = "/wp-json/tacnav-devices/v1/fcm",
)

data class ActivationResult(
    val serverUrl: String,
    val applicationId: Long,
    val installationId: String,
    val bearerCredential: String,
    val maps: List<MapScope>,
    val defaultMapId: Long,
    val defaultTeamId: Long,
    val referenceSettings: ObjectSettings,
    val settingsPin: String,
    val services: ServiceConfiguration = ServiceConfiguration(),
) {
    fun validated(): ActivationResult {
        require(serverUrl.startsWith("https://")) { "The server URL must use HTTPS." }
        require(bearerCredential.isNotBlank()) { "Activation returned no credential." }
        require(installationId.isNotBlank()) { "Activation returned no installation id." }
        val defaultMap = maps.singleOrNull { it.id == defaultMapId }
            ?: error("The default map is outside the activated scope.")
        require(defaultMap.teams.any { it.id == defaultTeamId }) {
            "The default team is unavailable on the default map."
        }
        referenceSettings.validated()
        return this
    }
}

object ScopeRules {
    fun select(maps: List<MapScope>, mapId: Long, teamId: Long): ActiveContext {
        val map = maps.singleOrNull { it.id == mapId }
            ?: throw IllegalArgumentException("Map is outside the activated scope.")
        require(map.teams.any { it.id == teamId }) {
            "Team is unavailable on the selected map."
        }
        return ActiveContext(mapId, teamId)
    }
}
