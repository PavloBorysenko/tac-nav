package com.tacnav.beacon.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tacnav.beacon.BeaconApplication
import com.tacnav.beacon.MainActivity
import com.tacnav.beacon.R
import kotlinx.coroutines.launch

/** The push only carries a reference; the mailbox is the source of truth. */
object WakeRouter {
    /** Data key sent by tacnav-devices `Firebase::wake_delivery()`. */
    const val DATA_DELIVERY_REFERENCE = "delivery_ref"

    fun route(data: Map<String, String>, sync: () -> Unit, notify: (String) -> Unit) {
        val reference = data[DATA_DELIVERY_REFERENCE] ?: return
        sync()
        notify(reference)
    }
}

class BeaconMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val application = application as BeaconApplication
        application.applicationScope.launch {
            runCatching { application.repository.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        WakeRouter.route(
            message.data,
            sync = (application as BeaconApplication)::syncMailbox,
            notify = ::showWakeNotification,
        )
    }

    private fun showWakeNotification(deliveryReference: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fcm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.fcm_wake_title))
            .setContentText(getString(R.string.fcm_wake_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(deliveryReference.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "tacnav-deliveries"
    }
}
