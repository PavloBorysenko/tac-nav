package com.tacnav.sapper.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

sealed interface Geometry {
    val kind: String

    data class Marker(val latitude: Double, val longitude: Double) : Geometry {
        override val kind = "marker"
    }

    data class Circle(
        val latitude: Double,
        val longitude: Double,
        val radiusMetres: Double,
    ) : Geometry {
        override val kind = "circle"
    }

    data class Polygon(val points: List<Coordinate>) : Geometry {
        override val kind = "polygon"
    }
}

object Geodesy {
    const val EARTH_RADIUS_METRES = 6_371_008.8

    fun distanceMetres(start: Coordinate, end: Coordinate): Double {
        val latitudeA = Math.toRadians(start.latitude)
        val latitudeB = Math.toRadians(end.latitude)
        val deltaLatitude = latitudeB - latitudeA
        val deltaLongitude = Math.toRadians(end.longitude - start.longitude)
        val term = sin(deltaLatitude / 2).pow(2) +
            cos(latitudeA) * cos(latitudeB) * sin(deltaLongitude / 2).pow(2)
        return 2 * EARTH_RADIUS_METRES * atan2(sqrt(term), sqrt(1 - term))
    }

    fun destination(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        distanceMetres: Double,
    ): Coordinate {
        val angular = distanceMetres / EARTH_RADIUS_METRES
        val bearing = Math.toRadians(bearingDegrees)
        val startLatitude = Math.toRadians(latitude)
        val startLongitude = Math.toRadians(longitude)
        val endLatitude = asin(
            sin(startLatitude) * cos(angular) + cos(startLatitude) * sin(angular) * cos(bearing),
        )
        val endLongitude = startLongitude + atan2(
            sin(bearing) * sin(angular) * cos(startLatitude),
            cos(angular) - sin(startLatitude) * sin(endLatitude),
        )
        return Coordinate(Math.toDegrees(endLatitude), normalizeLongitude(Math.toDegrees(endLongitude)))
    }

    fun normalizeBearing(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    /** Smallest angle between two bearings, from 0 to 180 degrees. */
    fun angleBetween(first: Double, second: Double): Double =
        abs(((first - second) % 360.0 + 540.0) % 360.0 - 180.0)

    private fun normalizeLongitude(degrees: Double): Double =
        ((degrees + 540.0) % 360.0) - 180.0

    /**
     * Compass bearing of the phone top, from a device-to-world rotation matrix.
     *
     * The top edge is used while the phone lies flat or tilts. An upright phone's
     * top edge points at the sky, so the direction out of its back is used instead.
     */
    fun headingFromRotation(rotation: FloatArray): Double {
        val topEast = rotation[1].toDouble()
        val topNorth = rotation[4].toDouble()
        val backEast = -rotation[2].toDouble()
        val backNorth = -rotation[5].toDouble()
        val useTop = hypot(topEast, topNorth) >= hypot(backEast, backNorth)
        val east = if (useTop) topEast else backEast
        val north = if (useTop) topNorth else backNorth
        return normalizeBearing(Math.toDegrees(atan2(east, north)))
    }
}

/** How a field looks from one position. */
data class FieldView(
    /** Zero inside the field, otherwise the distance to its nearest edge. */
    val distanceMetres: Double,
    val inside: Boolean,
    /** True bearing to the nearest point of the field; null inside it. */
    val bearingDegrees: Double?,
    /** Distance to the field's centre, used to choose among overlapping fields. */
    val centreDistanceMetres: Double,
)

/**
 * Geometry in a local metre plane around the phone. An equirectangular
 * projection is exact enough at the tens of metres a sapper works with.
 */
object Plane {
    private data class Point(val east: Double, val north: Double)

    fun view(position: Coordinate, geometry: Geometry): FieldView = when (geometry) {
        is Geometry.Marker -> circleView(position, geometry.latitude, geometry.longitude, 0.0)
        is Geometry.Circle -> circleView(
            position,
            geometry.latitude,
            geometry.longitude,
            geometry.radiusMetres,
        )
        is Geometry.Polygon -> polygonView(position, geometry.points)
    }

    fun centre(geometry: Geometry): Coordinate = when (geometry) {
        is Geometry.Marker -> Coordinate(geometry.latitude, geometry.longitude)
        is Geometry.Circle -> Coordinate(geometry.latitude, geometry.longitude)
        is Geometry.Polygon -> Coordinate(
            geometry.points.map(Coordinate::latitude).average(),
            geometry.points.map(Coordinate::longitude).average(),
        )
    }

    private fun circleView(
        position: Coordinate,
        latitude: Double,
        longitude: Double,
        radius: Double,
    ): FieldView {
        val centre = local(position, Coordinate(latitude, longitude))
        val distance = hypot(centre.east, centre.north)
        val inside = distance <= radius
        return FieldView(
            distanceMetres = if (inside) 0.0 else distance - radius,
            inside = inside,
            bearingDegrees = if (inside) null else bearing(centre),
            centreDistanceMetres = distance,
        )
    }

    private fun polygonView(position: Coordinate, points: List<Coordinate>): FieldView {
        val ring = points.map { local(position, it) }
        val centre = Point(ring.map(Point::east).average(), ring.map(Point::north).average())
        val centreDistance = hypot(centre.east, centre.north)
        if (ring.size < 3) {
            return FieldView(centreDistance, false, bearing(centre), centreDistance)
        }
        if (contains(ring)) {
            return FieldView(0.0, true, null, centreDistance)
        }
        var nearest = ring.first()
        var best = Double.MAX_VALUE
        ring.indices.forEach { index ->
            val candidate = nearestOnSegment(ring[index], ring[(index + 1) % ring.size])
            val distance = hypot(candidate.east, candidate.north)
            if (distance < best) {
                best = distance
                nearest = candidate
            }
        }
        return FieldView(best, false, bearing(nearest), centreDistance)
    }

    /** Ray casting from the origin, which is the phone. */
    private fun contains(ring: List<Point>): Boolean {
        var inside = false
        var previous = ring.last()
        ring.forEach { current ->
            if ((current.north > 0) != (previous.north > 0)) {
                val crossing = current.east +
                    (0 - current.north) * (previous.east - current.east) / (previous.north - current.north)
                if (crossing > 0) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun nearestOnSegment(start: Point, end: Point): Point {
        val dx = end.east - start.east
        val dy = end.north - start.north
        val length = dx * dx + dy * dy
        if (length == 0.0) return start
        val t = (-(start.east * dx + start.north * dy) / length).coerceIn(0.0, 1.0)
        return Point(start.east + t * dx, start.north + t * dy)
    }

    private fun local(origin: Coordinate, point: Coordinate): Point {
        val metresPerRadian = Geodesy.EARTH_RADIUS_METRES
        val north = Math.toRadians(point.latitude - origin.latitude) * metresPerRadian
        val east = Math.toRadians(point.longitude - origin.longitude) *
            cos(Math.toRadians(origin.latitude)) * metresPerRadian
        return Point(east, north)
    }

    private fun bearing(point: Point): Double =
        Geodesy.normalizeBearing(Math.toDegrees(atan2(point.east, point.north)))
}

object Shapes {
    /** A rectangle centred on [centre], its length along [headingDegrees]. */
    fun rectangle(
        centre: Coordinate,
        lengthMetres: Double,
        widthMetres: Double,
        headingDegrees: Double,
    ): Geometry.Polygon {
        val heading = Math.toRadians(headingDegrees)
        val halfLength = lengthMetres / 2
        val halfWidth = widthMetres / 2
        val corners = listOf(
            halfLength to -halfWidth,
            halfLength to halfWidth,
            -halfLength to halfWidth,
            -halfLength to -halfWidth,
        )
        return Geometry.Polygon(
            corners.map { (along, across) ->
                val north = along * cos(heading) - across * sin(heading)
                val east = along * sin(heading) + across * cos(heading)
                Geodesy.destination(
                    centre.latitude,
                    centre.longitude,
                    Math.toDegrees(atan2(east, north)),
                    hypot(north, east),
                )
            },
        )
    }

    fun shorterSide(settings: SapperSettings): Double =
        min(settings.minefieldLengthMetres, settings.minefieldWidthMetres)
}
