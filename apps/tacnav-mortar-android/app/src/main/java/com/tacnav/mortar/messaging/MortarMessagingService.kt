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

class MortarMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val application = application as MortarApplication
        application.applicationScope.launch {
            runCatching { application.repository.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val deliveryReference = message.data["delivery_reference"] ?: return
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
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fcm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_DELIVERY_REFERENCE, deliveryReference)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
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

    companion object {
        const val EXTRA_DELIVERY_REFERENCE = "delivery_reference"
        private const val CHANNEL_ID = "tacnav-deliveries"
    }
}
