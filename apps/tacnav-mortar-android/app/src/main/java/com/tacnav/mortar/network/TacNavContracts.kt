package com.tacnav.mortar.network

import com.tacnav.mortar.domain.ActiveContext

sealed interface Geometry {
    data class Marker(val latitude: Double, val longitude: Double) : Geometry
    data class Circle(
        val latitude: Double,
        val longitude: Double,
        val radiusMetres: Double,
    ) : Geometry

    data class Polyline(val points: List<Coordinate>) : Geometry
    data class Polygon(val points: List<Coordinate>) : Geometry
}

data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

data class GeometrySearchRequest(
    val context: ActiveContext,
    val geometry: Geometry,
)

data class GeometrySearchResult(
    val objectId: Long,
    val kind: String,
    val geometry: Geometry,
    val positionAccuracyMetres: Double?,
)

data class CreateObjectRequest(
    val context: ActiveContext,
    val clientObjectId: String,
    val kind: String,
    val geometry: Geometry,
    val title: String,
    val iconId: Long?,
    val visibleTeamIds: List<Long>,
    val ttlSeconds: Long?,
    val positionAccuracyMetres: Double?,
)

data class CreatedObject(
    val objectId: Long,
    val kind: String,
    val geometry: Geometry,
)

data class AddressedEventRequest(
    val context: ActiveContext,
    val clientEventId: String,
    val type: String,
    val recipientObjectIds: List<Long>,
    val opaquePayload: Map<String, Any?>,
)

data class MailboxDelivery(
    val cursor: Long,
    val deliveryReference: String,
    val eventType: String,
    val opaquePayload: Map<String, Any?>,
    val recipientObjectId: Long = 0,
    val sourceApplicationId: Long = 0,
    val sourceTeamId: Long = 0,
    val clientEventId: String = "",
)

/** The bearer was revoked or replaced by a newer activation of this application. */
class AuthorizationLostException : java.io.IOException(
    "This phone's activation was revoked or replaced. Reactivate it with a new staff code.",
)

interface GeoObjectApi {
    suspend fun search(request: GeometrySearchRequest): List<GeometrySearchResult>
    suspend fun create(request: CreateObjectRequest): CreatedObject
    suspend fun update(objectId: Long, request: CreateObjectRequest): CreatedObject
}

interface DeviceMailboxApi {
    suspend fun submit(request: AddressedEventRequest)
    suspend fun inbox(afterCursor: Long?): List<MailboxDelivery>
    suspend fun acknowledge(deliveryReference: String)
}
