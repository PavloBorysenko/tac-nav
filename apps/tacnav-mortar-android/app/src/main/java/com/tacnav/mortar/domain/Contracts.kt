package com.tacnav.mortar.domain

enum class FiringMode(val wireValue: String) {
    BUTTON("button"),
    ACOUSTIC("acoustic"),
}

enum class AimingMode(val wireValue: String) {
    FREE("free"),
    PRECISE_COORDINATES("precise_coordinates"),
    APPROXIMATE_COORDINATES("approximate_coordinates"),
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

data class GameplaySettings(
    val firingModes: Set<FiringMode> = setOf(FiringMode.BUTTON),
    val aimingModes: Set<AimingMode> = setOf(AimingMode.FREE),
    val approximateRadiusMetres: Double = 30.0,
    val elevationReferenceDegrees: Double = 60.0,
    val impactIconId: Long? = null,
    val mortarName: String = "Mortar 1",
    val minElevationDegrees: Double = 60.0,
    val maxElevationDegrees: Double = 85.0,
    val maxDistanceMetres: Double = 200.0,
    val minDistanceMetres: Double = 10.0,
    val damageRadiusMetres: Double = 5.0,
    val edgeProbability: Double = 0.5,
    val flightDurationSeconds: Long = 20,
    val impactTtlSeconds: Long = 30,
    val maxLocationAccuracyMetres: Double = 15.0,
    val maxLocationAgeSeconds: Long = 15,
    val acousticThresholdDbfs: Double = -20.0,
    val acousticImpulseMs: Long = 80,
    val acousticDebounceMs: Long = 400,
    val markOwnPositionOnShot: Boolean = false,
) {
    fun validated(): GameplaySettings {
        require(firingModes.isNotEmpty()) { "Select at least one firing mode." }
        require(aimingModes.isNotEmpty()) { "Select at least one aiming mode." }
        require(approximateRadiusMetres > 0.0) { "Approximate radius must be positive." }
        require(elevationReferenceDegrees in 0.0..90.0) {
            "Elevation reference must be between 0 and 90 degrees."
        }
        require(minElevationDegrees < maxElevationDegrees) {
            "Minimum elevation must be below maximum elevation."
        }
        require(maxDistanceMetres > minDistanceMetres && minDistanceMetres > 0.0) {
            "Distance endpoints must be positive and decreasing with elevation."
        }
        require(damageRadiusMetres > 0.0) { "Damage radius must be positive." }
        require(edgeProbability in 0.0..1.0) { "Edge probability must be between 0 and 1." }
        require(flightDurationSeconds > 0) { "Flight duration must be positive." }
        require(impactTtlSeconds >= 0) { "Impact lifetime cannot be negative." }
        require(maxLocationAccuracyMetres > 0.0) { "GPS accuracy limit must be positive." }
        require(maxLocationAgeSeconds > 0) { "GPS age limit must be positive." }
        require(mortarName.isNotBlank()) { "Mortar name is required." }
        return this
    }

    companion object {
        const val OWN_POSITION_TTL_SECONDS = 30L
    }
}

data class ServiceConfiguration(
    val iconCatalogPath: String = "/wp-json/tacnav/v1/applications/icons",
    val fcmRegistrationPath: String = "/wp-json/tacnav/v1/applications/fcm-token",
)

data class ActivationResult(
    val serverUrl: String,
    val applicationId: Long,
    val installationId: String,
    val bearerCredential: String,
    val maps: List<MapScope>,
    val defaultMapId: Long,
    val defaultTeamId: Long,
    val referenceSettings: GameplaySettings,
    val services: ServiceConfiguration,
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

data class ActiveContext(
    val mapId: Long,
    val teamId: Long,
)

data class CatalogIcon(
    val id: Long,
    val title: String,
    val imageUrl: String,
)

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
