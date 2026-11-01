package com.tacnav.beacon.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tacnav.beacon.R
import com.tacnav.beacon.game.SignalKind
import com.tacnav.beacon.game.SignalPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sound, vibration, and torch for one game signal.
 *
 * Each output is isolated: a busy torch or a muted phone must never get in the
 * way of the others, because destruction has already happened by this point.
 */
class SignalRunner(
    private val scope: CoroutineScope,
    private val sound: (SignalKind) -> Unit,
    private val vibrate: (SignalKind) -> Unit,
    private val torch: (Boolean) -> Unit,
    private val notify: (SignalKind) -> Unit,
    private val torchMillis: Long = TORCH_MILLIS,
) : SignalPort {
    private var torchJob: Job? = null

    override fun play(kind: SignalKind) {
        runCatching { sound(kind) }
        runCatching { vibrate(kind) }
        runCatching { notify(kind) }
        if (kind == SignalKind.DESTROYED) pulseTorch()
    }

    private fun pulseTorch() {
        torchJob?.cancel()
        runCatching { torch(true) }
        torchJob = scope.launch {
            delay(torchMillis)
            runCatching { torch(false) }
        }
    }

    companion object {
        const val TORCH_MILLIS = 5_000L
    }
}

/** Alarm-stream output, so a signal sounds with media volume at zero. */
class AndroidSignals(
    private val context: Context,
    scope: CoroutineScope,
    private val runner: SignalRunner = SignalRunner(
        scope = scope,
        sound = AlarmSound(context)::play,
        vibrate = { kind -> vibrate(context, kind) },
        torch = { enabled -> setTorch(context, enabled) },
        notify = { kind -> notifyAlert(context, kind) },
    ),
) : SignalPort by runner

private class AlarmSound(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var restoreVolume: Int? = null

    fun play(kind: SignalKind) {
        // Posted, so the runner's guard cannot catch a failure here.
        handler.post { runCatching { start(kind) } }
    }

    private fun start(kind: SignalKind) {
        stop()
        val audio = context.getSystemService(AudioManager::class.java)
        restoreVolume = runCatching {
            val before = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
            before
        }.getOrNull()
        val next = MediaPlayer()
        next.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        context.resources.openRawResourceFd(soundOf(kind)).use { file ->
            next.setDataSource(file.fileDescriptor, file.startOffset, file.length)
        }
        next.setOnCompletionListener { stop() }
        next.prepare()
        next.start()
        player = next
    }

    private fun stop() {
        player?.release()
        player = null
        restoreVolume?.let { volume ->
            runCatching {
                context.getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            }
        }
        restoreVolume = null
    }

    private fun soundOf(kind: SignalKind): Int = when (kind) {
        SignalKind.HIT -> R.raw.signal_hit
        SignalKind.WARNING -> R.raw.signal_warning
        SignalKind.DESTROYED -> R.raw.signal_destroyed
    }
}

private fun vibrate(context: Context, kind: SignalKind) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }
    val pattern = when (kind) {
        SignalKind.HIT -> longArrayOf(0, 200, 100, 200)
        SignalKind.WARNING -> longArrayOf(0, 400, 200, 400)
        SignalKind.DESTROYED -> longArrayOf(0, 900, 200, 900, 200, 900, 200, 900, 200, 500)
    }
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}

private fun setTorch(context: Context, enabled: Boolean) {
    val manager = context.getSystemService(CameraManager::class.java)
    val cameraId = manager.cameraIdList.firstOrNull { id ->
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: return
    manager.setTorchMode(cameraId, enabled)
}

private const val ALERT_CHANNEL = "tacnav_alerts"
private const val ALERT_NOTIFICATION = 72

private fun notifyAlert(context: Context, kind: SignalKind) {
    val text = when (kind) {
        SignalKind.HIT -> return
        SignalKind.WARNING -> "The object must be reactivated soon or it will be destroyed."
        SignalKind.DESTROYED -> "The object was destroyed."
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            ALERT_CHANNEL,
            context.getString(R.string.alert_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ),
    )
    manager.notify(
        ALERT_NOTIFICATION,
        NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build(),
    )
}
