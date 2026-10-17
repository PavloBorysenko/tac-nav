package com.tacnav.mortar.sensors

import android.location.Location
import android.os.SystemClock

object LocationAge {
    fun seconds(
        location: Location,
        nowElapsedNanos: Long = SystemClock.elapsedRealtimeNanos(),
        nowWallMillis: Long = System.currentTimeMillis(),
    ): Double {
        return if (location.elapsedRealtimeNanos > 0L) {
            ((nowElapsedNanos - location.elapsedRealtimeNanos) / 1_000_000_000.0).coerceAtLeast(0.0)
        } else {
            ((nowWallMillis - location.time) / 1000.0).coerceAtLeast(0.0)
        }
    }
}
