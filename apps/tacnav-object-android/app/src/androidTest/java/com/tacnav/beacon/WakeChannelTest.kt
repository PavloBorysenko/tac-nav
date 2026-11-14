package com.tacnav.beacon

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.beacon.messaging.WakeChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeChannelTest {
    private val manager = ApplicationProvider.getApplicationContext<BeaconApplication>()
        .getSystemService(NotificationManager::class.java)

    @Test
    fun wakeChannelIsSilentAndReplacesTheLoudOne() {
        manager.createNotificationChannel(
            NotificationChannel(WakeChannel.LEGACY_ID, "Old", NotificationManager.IMPORTANCE_HIGH),
        )

        WakeChannel.ensure(manager, "Deliveries")

        val channel = manager.getNotificationChannel(WakeChannel.ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertNull(manager.getNotificationChannel(WakeChannel.LEGACY_ID))
    }
}
