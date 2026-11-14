package com.tacnav.mortar.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tacnav.mortar.MainActivity
import com.tacnav.mortar.MortarApplication
import com.tacnav.mortar.R
import kotlinx.coroutines.launch

/**
 * Wake-up pushes post a visible notification so high-priority delivery is not
 * deprioritized, but it must stay silent: a hit on an impact marker must not
 * give the mortar away. Channel sound cannot change after creation, so the old
 * loud channel is deleted and a new low-importance one is used.
 */
object WakeChannel {
    const val ID = "tacnav-wake"
    const val LEGACY_ID = "tacnav-deliveries"

    fun ensure(manager: NotificationManager, name: CharSequence) {
        manager.deleteNotificationChannel(LEGACY_ID)
        manager.createNotificationChannel(
            NotificationChannel(ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            },
        )
    }
}

class MortarMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val application = application as MortarApplication
        application.applicationScope.launch {
            runCatching { application.repository.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val deliveryReference = message.data[DATA_DELIVERY_REFERENCE] ?: return
        // The push only carries a reference; the mailbox is the source of truth.
        (application as MortarApplication).syncMailbox()
        showWakeNotification(deliveryReference)
    }

    private fun showWakeNotification(deliveryReference: String) {
        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        WakeChannel.ensure(manager, getString(R.string.fcm_channel_name))
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_DELIVERY_REFERENCE, deliveryReference)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, WakeChannel.ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.fcm_wake_title))
            .setContentText(getString(R.string.fcm_wake_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(deliveryReference.hashCode(), notification)
    }

    companion object {
        const val EXTRA_DELIVERY_REFERENCE = "delivery_reference"

        /** Data key sent by tacnav-devices `Firebase::wake_delivery()`. */
        const val DATA_DELIVERY_REFERENCE = "delivery_ref"
    }
}
