package com.tacnav.mortar.domain

import com.tacnav.mortar.network.Coordinate
import com.tacnav.mortar.network.Geometry
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class SensorSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val ageSeconds: Double,
    val trueHeadingDegrees: Double,
    val elevationDegrees: Double,
    val triggerEpochMillis: Long,
)

sealed interface ShotBlock {
    val reason: String
}

data class ValidShot(val snapshot: SensorSnapshot, val rangeMetres: Double) : ShotBlock {
    override val reason: String = ""
}

data class BlockedShot(override val reason: String) : ShotBlock

enum class ShotPhase {
    IN_FLIGHT,
    DUE,
    SEARCHED,
    IMPACT_CREATED,
    MESSAGES_SENT,
    FAILED,
    COMPLETED,
}

data class HitFact(
    val objectId: Long,
    val distanceMetres: Double,
    val probability: Double,
    val roll: Double,
)

data class ShotRecord(
    val shotId: String,
    val snapshot: SensorSnapshot,
    val rangeMetres: Double,
    val impactLatitude: Double,
    val impactLongitude: Double,
    val dueAtElapsedMillis: Long,
    val dueAtEpochMillis: Long,
    val phase: ShotPhase = ShotPhase.IN_FLIGHT,
    val rolls: Map<Long, Double> = emptyMap(),
    val facts: List<HitFact> = emptyList(),
    val hits: List<Long> = emptyList(),
    val failure: String? = null,
)

data class PreciseGuidance(
    val targetDistanceMetres: Double,
    val requiredBearingDegrees: Double,
    val requiredElevationDegrees: Double?,
    val turnSign: String,
    val tiltSign: String,
    val turnMetres: Double,
    val alongMetres: Double,
    val outOfRange: Boolean,
)

data class ApproximateGuidance(
    val leftRight: String,
    val addDrop: String,
    val inZone: Boolean,
    val crossMetres: Double? = null,
    val alongMetres: Double? = null,
)

object Ballistics {
    fun rangeMetres(elevationDegrees: Double, settings: GameplaySettings): Double {
        if (elevationDegrees < settings.minElevationDegrees ||
            elevationDegrees > settings.maxElevationDegrees
        ) {
            throw IllegalArgumentException("Elevation is outside the firing range.")
        }
        val span = settings.maxElevationDegrees - settings.minElevationDegrees
        val t = (elevationDegrees - settings.minElevationDegrees) / span
        return settings.maxDistanceMetres -
            t * (settings.maxDistanceMetres - settings.minDistanceMetres)
    }

    fun elevationForRange(distanceMetres: Double, settings: GameplaySettings): Double? {
        if (distanceMetres < settings.minDistanceMetres ||
            distanceMetres > settings.maxDistanceMetres
        ) {
            return null
        }
        val span = settings.maxDistanceMetres - settings.minDistanceMetres
        val t = (settings.maxDistanceMetres - distanceMetres) / span
        return settings.minElevationDegrees +
            t * (settings.maxElevationDegrees - settings.minElevationDegrees)
    }
}

object SensorGate {
    fun accept(snapshot: SensorSnapshot, settings: GameplaySettings): ShotBlock {
        if (snapshot.accuracyMetres > settings.maxLocationAccuracyMetres) {
            return BlockedShot("Insufficient GPS accuracy.")
        }
        if (snapshot.ageSeconds > settings.maxLocationAgeSeconds) {
            return BlockedShot("GPS fix is too old.")
        }
        if (snapshot.elevationDegrees < settings.minElevationDegrees ||
            snapshot.elevationDegrees > settings.maxElevationDegrees
        ) {
            return BlockedShot("Elevation is outside the firing range.")
        }
        if (!snapshot.latitude.isFinite() || !snapshot.longitude.isFinite()) {
            return BlockedShot("Location is unavailable.")
        }
        if (!snapshot.trueHeadingDegrees.isFinite()) {
            return BlockedShot("Heading is unavailable.")
        }
        val range = Ballistics.rangeMetres(snapshot.elevationDegrees, settings)
        return ValidShot(snapshot, range)
    }
}

object Geodesy {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun destination(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        distanceMetres: Double,
    ): Coordinate {
        val bearing = Math.toRadians(bearingDegrees)
        val angular = distanceMetres / EARTH_RADIUS_M
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing),
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return Coordinate(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lon2)))
    }

    fun distanceMetres(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearingDegrees(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    fun localOffset(
        originLat: Double,
        originLng: Double,
        headingDegrees: Double,
        targetLat: Double,
        targetLng: Double,
    ): Pair<Double, Double> {
        val east = Geodesy.distanceMetres(originLat, originLng, originLat, targetLng) *
            if (targetLng >= originLng) 1.0 else -1.0
        val north = Geodesy.distanceMetres(originLat, originLng, targetLat, originLng) *
            if (targetLat >= originLat) 1.0 else -1.0
        val heading = Math.toRadians(headingDegrees)
        val along = north * cos(heading) + east * sin(heading)
        val cross = east * cos(heading) - north * sin(heading)
        return cross to along
    }

    fun normalizeBearing(value: Double): Double {
        var degrees = value % 360.0
        if (degrees < 0) degrees += 360.0
        return degrees
    }

    /**
     * Magnetic heading of the aim axis from an Android rotation matrix.
     *
     * World frame is east / north / up. The more horizontal of the top edge (Y)
     * and through-screen (−Z) axes is used so a steep mount stays stable and a
     * dip below the horizon does not reverse azimuth.
     */
    fun magneticHeadingFromRotation(rotation: FloatArray): Double {
        if (rotation.size < 9) {
            return Double.NaN
        }
        val topEast = rotation[1].toDouble()
        val topNorth = rotation[4].toDouble()
        val throughEast = -rotation[2].toDouble()
        val throughNorth = -rotation[5].toDouble()
        val topH = hypot(topEast, topNorth)
        val throughH = hypot(throughEast, throughNorth)
        val east: Double
        val north: Double
        if (throughH > topH) {
            east = throughEast
            north = throughNorth
        } else {
            east = topEast
            north = topNorth
        }
        if (!east.isFinite() || !north.isFinite() || (east == 0.0 && north == 0.0)) {
            return Double.NaN
        }
        return normalizeBearing(Math.toDegrees(atan2(east, north)))
    }

    private fun normalizeLongitude(value: Double): Double {
        var longitude = value
        while (longitude > 180) longitude -= 360
        while (longitude < -180) longitude += 360
        return longitude
    }
}

object CalibrationMath {
    const val MIN_KNOWN_POINT_METRES = 25.0
    const val DIRECTION_LINE_METRES = 100.0

    fun directionLine(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        lengthMetres: Double = DIRECTION_LINE_METRES,
    ): Geometry.Polyline {
        val end = Geodesy.destination(latitude, longitude, bearingDegrees, lengthMetres)
        return Geometry.Polyline(
            listOf(Coordinate(latitude, longitude), end),
        )
    }

    fun elevationFromPitch(pitchDegrees: Double): Double = -pitchDegrees

    fun elevationOffset(referenceDegrees: Double, measuredDegrees: Double): Double =
        referenceDegrees - measuredDegrees

    fun applyElevationOffset(measuredDegrees: Double, offsetDegrees: Double?): Double =
        measuredDegrees + (offsetDegrees ?: 0.0)

    fun signedBearingOffset(knownBearingDegrees: Double, measuredTrueHeadingDegrees: Double): Double {
        var delta = (knownBearingDegrees - measuredTrueHeadingDegrees) % 360.0
        if (delta > 180.0) {
            delta -= 360.0
        }
        if (delta < -180.0) {
            delta += 360.0
        }
        return delta
    }

    fun applyHeadingOffset(trueHeadingDegrees: Double, offsetDegrees: Double?): Double =
        Geodesy.normalizeBearing(trueHeadingDegrees + (offsetDegrees ?: 0.0))

    fun knownPointTooClose(
        originLatitude: Double,
        originLongitude: Double,
        knownLatitude: Double,
        knownLongitude: Double,
    ): Boolean = Geodesy.distanceMetres(
        originLatitude,
        originLongitude,
        knownLatitude,
        knownLongitude,
    ) < MIN_KNOWN_POINT_METRES

    fun parseCoordinate(value: String): Double? {
        val parsed = value.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() }
    }
}

object GeometryMath {
    fun nearestDistanceMetres(impact: Coordinate, geometry: Geometry): Double = when (geometry) {
        is Geometry.Marker -> Geodesy.distanceMetres(
            impact.latitude,
            impact.longitude,
            geometry.latitude,
            geometry.longitude,
        )
        is Geometry.Circle -> max(
            0.0,
            Geodesy.distanceMetres(
                impact.latitude,
                impact.longitude,
                geometry.latitude,
                geometry.longitude,
            ) - geometry.radiusMetres,
        )
        is Geometry.Polyline -> nearestOnPath(impact, geometry.points, closed = false)
        is Geometry.Polygon -> {
            val boundary = nearestOnPath(impact, geometry.points, closed = true)
            if (contains(impact, geometry.points)) 0.0 else boundary
        }
    }

    private fun nearestOnPath(
        impact: Coordinate,
        points: List<Coordinate>,
        closed: Boolean,
    ): Double {
        if (points.isEmpty()) return Double.POSITIVE_INFINITY
        if (points.size == 1) {
            return Geodesy.distanceMetres(
                impact.latitude,
                impact.longitude,
                points[0].latitude,
                points[0].longitude,
            )
        }
        val origin = points[0]
        val impactLocal = toLocal(origin, impact)
        val locals = points.map { toLocal(origin, it) }
        var best = Double.POSITIVE_INFINITY
        val last = if (closed) locals.size - 1 else locals.size - 2
        for (index in 0..max(0, last)) {
            val next = locals[(index + 1) % locals.size]
            best = min(best, distanceToSegment(impactLocal, locals[index], next))
        }
        return best
    }

    private fun contains(impact: Coordinate, points: List<Coordinate>): Boolean {
        if (points.size < 3) return false
        var inside = false
        var previous = points.last()
        for (current in points) {
            val intersects = (current.latitude > impact.latitude) !=
                (previous.latitude > impact.latitude) &&
                impact.longitude < (previous.longitude - current.longitude) *
                (impact.latitude - current.latitude) /
                (previous.latitude - current.latitude) + current.longitude
            if (intersects) inside = !inside
            previous = current
        }
        return inside
    }

    private fun toLocal(origin: Coordinate, point: Coordinate): Pair<Double, Double> {
        val east = Geodesy.distanceMetres(
            origin.latitude,
            origin.longitude,
            origin.latitude,
            point.longitude,
        ) * if (point.longitude >= origin.longitude) 1.0 else -1.0
        val north = Geodesy.distanceMetres(
            origin.latitude,
            origin.longitude,
            point.latitude,
            origin.longitude,
        ) * if (point.latitude >= origin.latitude) 1.0 else -1.0
        return east to north
    }

    private fun distanceToSegment(
        point: Pair<Double, Double>,
        start: Pair<Double, Double>,
        end: Pair<Double, Double>,
    ): Double {
        val dx = end.first - start.first
        val dy = end.second - start.second
        val length = hypot(dx, dy)
        if (length == 0.0) return hypot(point.first - start.first, point.second - start.second)
        val t = min(
            1.0,
            max(
                0.0,
                ((point.first - start.first) * dx + (point.second - start.second) * dy) /
                    (length * length),
            ),
        )
        return hypot(point.first - (start.first + t * dx), point.second - (start.second + t * dy))
    }
}

object HitMath {
    fun probability(distanceMetres: Double, settings: GameplaySettings): Double {
        if (distanceMetres <= 0.0) return 1.0
        if (distanceMetres >= settings.damageRadiusMetres) {
            return if (distanceMetres == settings.damageRadiusMetres) {
                settings.edgeProbability
            } else {
                0.0
            }
        }
        return 1.0 - (1.0 - settings.edgeProbability) *
            distanceMetres / settings.damageRadiusMetres
    }
}

object GuidanceMath {
    fun precise(
        origin: SensorSnapshot,
        target: Coordinate,
        settings: GameplaySettings,
    ): PreciseGuidance {
        val distance = Geodesy.distanceMetres(
            origin.latitude,
            origin.longitude,
            target.latitude,
            target.longitude,
        )
        val bearing = Geodesy.bearingDegrees(
            origin.latitude,
            origin.longitude,
            target.latitude,
            target.longitude,
        )
        val predicted = Geodesy.destination(
            origin.latitude,
            origin.longitude,
            origin.trueHeadingDegrees,
            Ballistics.rangeMetres(
                origin.elevationDegrees.coerceIn(
                    settings.minElevationDegrees,
                    settings.maxElevationDegrees,
                ),
                settings,
            ),
        )
        val (cross, along) = Geodesy.localOffset(
            origin.latitude,
            origin.longitude,
            origin.trueHeadingDegrees,
            target.latitude,
            target.longitude,
        )
        val predictedAlong = Geodesy.localOffset(
            origin.latitude,
            origin.longitude,
            origin.trueHeadingDegrees,
            predicted.latitude,
            predicted.longitude,
        ).second
        return PreciseGuidance(
            targetDistanceMetres = distance,
            requiredBearingDegrees = bearing,
            requiredElevationDegrees = Ballistics.elevationForRange(distance, settings),
            turnSign = if (cross < 0) "LEFT" else "RIGHT",
            tiltSign = if (along > predictedAlong) "ADD" else "DROP",
            turnMetres = kotlin.math.abs(cross),
            alongMetres = kotlin.math.abs(along - predictedAlong),
            outOfRange = distance < settings.minDistanceMetres ||
                distance > settings.maxDistanceMetres,
        )
    }

    fun approximate(
        origin: SensorSnapshot,
        target: Coordinate,
        settings: GameplaySettings,
        revealMetres: Boolean,
    ): ApproximateGuidance {
        val precise = precise(origin, target, settings)
        val predicted = Geodesy.destination(
            origin.latitude,
            origin.longitude,
            origin.trueHeadingDegrees,
            Ballistics.rangeMetres(
                origin.elevationDegrees.coerceIn(
                    settings.minElevationDegrees,
                    settings.maxElevationDegrees,
                ),
                settings,
            ),
        )
        val miss = Geodesy.distanceMetres(
            predicted.latitude,
            predicted.longitude,
            target.latitude,
            target.longitude,
        )
        return ApproximateGuidance(
            leftRight = precise.turnSign,
            addDrop = precise.tiltSign,
            inZone = miss <= settings.approximateRadiusMetres,
            crossMetres = if (revealMetres) precise.turnMetres else null,
            alongMetres = if (revealMetres) precise.alongMetres else null,
        )
    }
}

class AcousticTrigger(
    private val thresholdDbfs: Double,
    private val impulseMs: Long,
    private val debounceMs: Long,
) {
    private var aboveSince: Long? = null
    private var lastFireAt: Long? = null

    fun sample(dbfs: Double, elapsedMs: Long): Boolean {
        val previous = lastFireAt
        if (previous != null && elapsedMs - previous < debounceMs) {
            aboveSince = null
            return false
        }
        if (dbfs < thresholdDbfs) {
            aboveSince = null
            return false
        }
        val started = aboveSince ?: elapsedMs.also { aboveSince = it }
        if (elapsedMs - started >= impulseMs) {
            lastFireAt = elapsedMs
            aboveSince = null
            return true
        }
        return false
    }
}
