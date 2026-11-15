package com.tacnav.sapper.game

import kotlin.math.max

/**
 * Accuracy-weighted Kalman smoothing of successive fixes.
 *
 * A standing phone's fixes wander by a few metres; the filter averages them so
 * the position stops jumping in and out of a field edge. The position may move
 * at walking speed, so a walking player is followed with about two metres of lag.
 * The reported accuracy stays the raw one, so GPS gates are not loosened.
 */
class FixSmoother(private val speedMetresPerSecond: Double = SPEED_METRES_PER_SECOND) {
    private var latitude = 0.0
    private var longitude = 0.0
    private var varianceSquareMetres = -1.0
    private var lastEpochMillis = 0L

    fun reset() {
        varianceSquareMetres = -1.0
    }

    fun smooth(raw: Fix): Fix {
        val accuracy = max(raw.accuracyMetres, MIN_ACCURACY_METRES)
        val elapsed = raw.epochMillis - lastEpochMillis
        if (varianceSquareMetres < 0 || elapsed < 0 || elapsed > RESET_AFTER_MILLIS) {
            latitude = raw.latitude
            longitude = raw.longitude
            varianceSquareMetres = accuracy * accuracy
            lastEpochMillis = raw.epochMillis
            return raw
        }
        varianceSquareMetres += elapsed / 1_000.0 * speedMetresPerSecond * speedMetresPerSecond
        lastEpochMillis = raw.epochMillis
        val gain = varianceSquareMetres / (varianceSquareMetres + accuracy * accuracy)
        latitude += gain * (raw.latitude - latitude)
        longitude += gain * (raw.longitude - longitude)
        varianceSquareMetres *= 1 - gain
        return raw.copy(latitude = latitude, longitude = longitude)
    }

    companion object {
        const val SPEED_METRES_PER_SECOND = 2.0
        const val MIN_ACCURACY_METRES = 1.0

        /** After a gap this long the next fix starts the filter again. */
        const val RESET_AFTER_MILLIS = Fix.MAX_FIX_AGE_MILLIS
    }
}
