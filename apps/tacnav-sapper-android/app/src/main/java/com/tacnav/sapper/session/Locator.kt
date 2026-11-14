package com.tacnav.sapper.session

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tacnav.sapper.game.Fix

/** Fused high-accuracy fixes once a second, only while the activity is resumed. */
class Locator(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (Location, Fix) -> Unit) {
        if (callback != null) return
        val next = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onLocation(
                    location,
                    Fix(location.latitude, location.longitude, location.accuracy.toDouble(), location.time),
                )
            }
        }
        callback = next
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000)
            .setMinUpdateIntervalMillis(1_000)
            .build()
        runCatching { client.requestLocationUpdates(request, next, Looper.getMainLooper()) }
            .onFailure { callback = null }
    }

    fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }
}
