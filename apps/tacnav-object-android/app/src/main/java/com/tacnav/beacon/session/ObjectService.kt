package com.tacnav.beacon.session

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tacnav.beacon.BeaconApplication
import com.tacnav.beacon.MainActivity
import com.tacnav.beacon.R
import com.tacnav.beacon.game.Fix
import com.tacnav.beacon.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the game while the application is open or needs to stay awake.
 *
 * Android allows a location service to start only from a visible action, so the
 * activity starts this and it then keeps running with the screen off.
 */
class ObjectService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app: BeaconApplication
        get() = application as BeaconApplication
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var loop: Job? = null
    private var lastNotified = ""
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            app.compass.updateLocation(location)
            app.controller.onFix(
                Fix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMetres = location.accuracy.toDouble(),
                    epochMillis = location.time,
                ),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loop == null) startSession()
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        locationClient.removeLocationUpdates(locationCallback)
        app.compass.stop()
        app.serviceRunning = false
        super.onDestroy()
    }

    private fun startSession() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) || !startInForeground()) {
            stopSelf()
            return
        }
        app.controller.restore()
        app.serviceRunning = true
        app.compass.start()
        startLocationUpdates()
        loop = scope.launch { runLoop() }
    }

    private fun startInForeground(): Boolean = runCatching {
        createChannel()
        val notification = buildNotification(getString(R.string.app_name))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }.isSuccess

    private suspend fun runLoop() {
        var wasOnline = false
        var lastMailbox = 0L
        var lastNotification = 0L
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            val online = isOnline()
            app.publishOnline(online)
            app.controller.tick()
            if (online) {
                scope.launch { runCatching { app.controller.syncMap() } }
                if (!wasOnline || now - lastMailbox >= MAILBOX_MILLIS) {
                    app.syncMailbox()
                    lastMailbox = now
                }
            }
            wasOnline = online
            if (now - lastNotification >= NOTIFICATION_MILLIS) {
                updateNotification()
                lastNotification = now
            }
            if (!app.visible && !app.controller.needsBackground()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            delay(TICK_MILLIS)
        }
    }

    private fun updateNotification() {
        val state = app.controller.state.value ?: return
        val settings = app.repository.state.value?.settings ?: return
        val text = StatusText.summary(state, settings)
        if (text == lastNotified) return
        lastNotified = text
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_MILLIS)
            .setMinUpdateIntervalMillis(LOCATION_MILLIS / 2)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdateAgeMillis(LOCATION_MILLIS * 2)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun isOnline(): Boolean =
        getSystemService(ConnectivityManager::class.java).activeNetwork != null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.session_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private companion object {
        const val CHANNEL = "tacnav_session"
        const val NOTIFICATION_ID = 71
        const val TICK_MILLIS = 500L
        const val LOCATION_MILLIS = 1_000L
        const val MAILBOX_MILLIS = 10_000L
        const val NOTIFICATION_MILLIS = 15_000L
    }
}
