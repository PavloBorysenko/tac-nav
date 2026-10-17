package com.tacnav.mortar.session

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.os.Looper
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tacnav.mortar.MortarApplication
import com.tacnav.mortar.R
import com.tacnav.mortar.domain.AcousticTrigger
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.sensors.LocationAge
import com.tacnav.mortar.sensors.PhonePose
import com.tacnav.mortar.ui.ShotFeedback
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FiringSessionService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var pose: PhonePose
    private lateinit var engine: FiringEngine
    private var loop: Job? = null
    private var audio: Job? = null
    private var torchJob: Job? = null
    private var lastLocation: Location? = null
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastLocation = location
            pose.updateLocation(location)
            (application as MortarApplication).publishLocation(location)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MortarApplication
        pose = PhonePose(getSystemService(SENSOR_SERVICE) as SensorManager)
        engine = app.firingEngine
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_FIRE -> fire(button = true)
            ACTION_ARM -> armAcoustic()
            ACTION_DISARM -> audio?.cancel()
            else -> startSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        audio?.cancel()
        torchJob?.cancel()
        locationClient.removeLocationUpdates(locationCallback)
        setTorch(false)
        pose.stop()
        (application as MortarApplication).repository.setSession(false)
        super.onDestroy()
    }

    private fun startSession() {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.session_title))
            .setContentText(getString(R.string.session_body))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        pose.start()
        startLocationUpdates()
        val app = application as MortarApplication
        app.repository.setSession(true)
        app.bindApi()
        loop?.cancel()
        loop = scope.launch {
            val started = SystemClock.elapsedRealtime()
            while (isActive) {
                val state = app.repository.state.value
                if (state != null) {
                    pose.updateCalibration(state.calibration)
                    engine.resolveDue(
                        elapsedMillis = SystemClock.elapsedRealtime() - started,
                        nowEpochMillis = System.currentTimeMillis(),
                        settings = state.settings,
                        context = state.context,
                        online = isOnline(),
                    )
                    app.publishPose(pose.pose)
                    app.publishShots(engine.currentShots())
                }
                delay(500)
            }
        }
    }

    private fun fire(button: Boolean) {
        val app = application as MortarApplication
        val state = app.repository.state.value ?: return
        val location = lastLocation
        val live = pose.pose
        val heading = live.calibratedHeading
        val elevation = live.calibratedElevation
        if (location == null || heading == null || elevation == null) {
            app.publishError("Location, heading, or elevation is unavailable.")
            return
        }
        val snapshot = SensorSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMetres = location.accuracy.toDouble(),
            ageSeconds = LocationAge.seconds(location),
            trueHeadingDegrees = heading,
            elevationDegrees = elevation,
            triggerEpochMillis = System.currentTimeMillis(),
        )
        runCatching {
            engine.accept(
                snapshot,
                state.settings,
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis(),
            )
        }.onSuccess { shot ->
            ShotFeedback.play(this)
            app.publishShots(engine.currentShots())
            app.publishError(null)
            if (button) pulseTorch()
            if (state.settings.markOwnPositionOnShot) {
                scope.launch {
                    runCatching {
                        engine.publishOwnPosition(shot, state.settings, state.context)
                    }
                }
            }
        }.onFailure {
            app.publishError(it.message)
        }
    }

    private fun armAcoustic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            (application as MortarApplication).publishError(
                "Microphone permission denied. Button firing remains available.",
            )
            return
        }
        val settings = (application as MortarApplication).repository.state.value?.settings ?: return
        val trigger = AcousticTrigger(
            settings.acousticThresholdDbfs,
            settings.acousticImpulseMs,
            settings.acousticDebounceMs,
        )
        audio?.cancel()
        audio = scope.launch {
            val minBuffer = AudioRecord.getMinBufferSize(
                16_000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16_000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
            val buffer = ShortArray(minBuffer / 2)
            recorder.startRecording()
            val started = SystemClock.elapsedRealtime()
            try {
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val rms = sqrt(buffer.take(read).map { it * it }.average())
                        val dbfs = if (rms <= 0) -120.0 else 20 * log10(rms / Short.MAX_VALUE)
                        (application as MortarApplication).publishLevel(dbfs)
                        if (trigger.sample(dbfs, SystemClock.elapsedRealtime() - started)) {
                            fire(button = false)
                        }
                    }
                }
            } finally {
                recorder.release()
            }
        }
    }

    private fun pulseTorch() {
        torchJob?.cancel()
        setTorch(true)
        torchJob = scope.launch {
            delay(5_000)
            setTorch(false)
        }
    }

    private fun setTorch(enabled: Boolean) {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        runCatching {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                if (enabled) {
                    (application as MortarApplication).publishError(
                        "Torch is unavailable, but the shot was accepted.",
                    )
                }
                return
            }
            manager.setTorchMode(cameraId, enabled)
        }.onFailure {
            if (enabled) {
                (application as MortarApplication).publishError(
                    "Torch is busy, but the shot was accepted.",
                )
            }
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(true)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdateAgeMillis(2_000L)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.activeNetwork != null
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.session_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val ACTION_STOP = "com.tacnav.mortar.STOP_SESSION"
        const val ACTION_FIRE = "com.tacnav.mortar.FIRE"
        const val ACTION_ARM = "com.tacnav.mortar.ARM"
        const val ACTION_DISARM = "com.tacnav.mortar.DISARM"
        private const val CHANNEL = "tacnav_session"
        private const val NOTIFICATION_ID = 71
    }
}
