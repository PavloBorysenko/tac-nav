package com.tacnav.beacon

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tacnav.beacon.session.ObjectService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
    private val app = ApplicationProvider.getApplicationContext<BeaconApplication>()

    @Before
    fun grantLocation() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test
    fun screenStaysAwakeWhileTheActivityIsOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertEquals(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                )
            }
        }
    }

    @Test
    fun serviceStartsFromTheVisibleActivityAndStopsWhenNotNeeded() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ContextCompat.startForegroundService(
                    activity,
                    Intent(activity, ObjectService::class.java),
                )
            }
            assertTrue(waitUntil { app.serviceRunning })
            app.visible = false
            assertTrue(waitUntil { !app.serviceRunning })
            assertFalse(app.controller.needsBackground())
        }
    }
}
