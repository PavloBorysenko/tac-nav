package com.tacnav.beacon.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
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
    private const val EARTH_RADIUS_METRES = 6_371_008.8

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

    fun bearingDegrees(start: Coordinate, end: Coordinate): Double {
        val latitudeA = Math.toRadians(start.latitude)
        val latitudeB = Math.toRadians(end.latitude)
        val deltaLongitude = Math.toRadians(end.longitude - start.longitude)
        val bearing = atan2(
            sin(deltaLongitude) * cos(latitudeB),
            cos(latitudeA) * sin(latitudeB) - sin(latitudeA) * cos(latitudeB) * cos(deltaLongitude),
        )
        return normalizeBearing(Math.toDegrees(bearing))
    }

    fun normalizeBearing(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

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

object Shapes {
    /** Empty sizes give a point, one size a circle, and two sizes a rectangle. */
    fun geometry(
        settings: ObjectSettings,
        centre: Coordinate,
        headingDegrees: Double,
    ): Geometry {
        val along = settings.sizeAMetres
        val across = settings.sizeBMetres
        if (along != null && across != null) {
            return rectangle(centre, along, across, headingDegrees)
        }
        val radius = along ?: across
        return if (radius != null) {
            Geometry.Circle(centre.latitude, centre.longitude, radius)
        } else {
            Geometry.Marker(centre.latitude, centre.longitude)
        }
    }

    private fun rectangle(
        centre: Coordinate,
        alongMetres: Double,
        acrossMetres: Double,
        headingDegrees: Double,
    ): Geometry.Polygon {
        val heading = Math.toRadians(headingDegrees)
        val halfAlong = alongMetres / 2
        val halfAcross = acrossMetres / 2
        val corners = listOf(
            halfAlong to -halfAcross,
            halfAlong to halfAcross,
            -halfAlong to halfAcross,
            -halfAlong to -halfAcross,
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
}

object HintArea {
    /**
     * Distance of the object from the area centre.
     *
     * The cube root gives a density that rises towards the edge. A square root
     * would be uniform over the area, which leaves the centre as likely as the rim.
     */
    fun offsetMetres(radiusMetres: Double, draw: Double): Double = radiusMetres * cbrt(draw)

    fun centre(
        position: Coordinate,
        radiusMetres: Double,
        distanceDraw: Double,
        bearingDraw: Double,
    ): Coordinate = Geodesy.destination(
        position.latitude,
        position.longitude,
        bearingDraw * 360.0,
        offsetMetres(radiusMetres, distanceDraw),
    )
}
