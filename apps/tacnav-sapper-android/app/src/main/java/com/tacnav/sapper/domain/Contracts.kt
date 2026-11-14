package com.tacnav.sapper.domain

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

/** Field names match the server reference; 0 in a maximum means unlimited. */
data class SapperSettings(
    val detectRadiusMetres: Double = 10.0,
    val detectSectorDegrees: Double = 90.0,
    val markHoldSeconds: Long = 30,
    val clearHoldSeconds: Long = 180,
    val layHoldSeconds: Long = 180,
    val minefieldLengthMetres: Double = 10.0,
    val minefieldWidthMetres: Double = 10.0,
    val minefieldIconId: Long? = null,
    val noMinesIconId: Long? = null,
    val maxMinefields: Int = 3,
    val maxNoMinesMarkers: Int = 10,
    val maxLocationAccuracyMetres: Double = 15.0,
) {
    fun validated(): SapperSettings {
        require(detectRadiusMetres > 0) { "Detection radius must be positive." }
        require(detectSectorDegrees in 1.0..360.0) {
            "The heading sector must be between 1 and 360 degrees."
        }
        require(markHoldSeconds > 0 && clearHoldSeconds > 0 && layHoldSeconds > 0) {
            "Hold times must be positive."
        }
        require(minefieldLengthMetres > 0 && minefieldWidthMetres > 0) {
            "Minefield sides must be positive."
        }
        require(maxMinefields >= 0 && maxNoMinesMarkers >= 0) {
            "Maximums cannot be negative."
        }
        require(maxLocationAccuracyMetres > 0) { "GPS accuracy limit must be positive." }
        return this
    }
}

data class ServiceConfiguration(
    val iconCatalogPath: String = "/wp-json/tacnav-devices/v1/icons",
)

data class ActivationResult(
    val serverUrl: String,
    val applicationId: Long,
    val installationId: String,
    val bearerCredential: String,
    val maps: List<MapScope>,
    val defaultMapId: Long,
    val defaultTeamId: Long,
    val referenceSettings: SapperSettings,
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

/** A map object as the server stores it. */
data class MapObject(
    val id: Long,
    val geometry: Geometry,
    val role: String,
    val ownerTeamId: Long?,
    val visibleTeamIds: List<Long>,
    val updatedAt: String,
)

const val MINEFIELD_ROLE = "minefield"
