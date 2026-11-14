package com.tacnav.sapper

import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
    private val app = ApplicationProvider.getApplicationContext<SapperApplication>()

    @Test
    fun screenStaysOnWhileTheApplicationIsOpen() {
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
    fun noServiceAndNoBackgroundLocationAreDeclared() {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(
            app.packageName,
            PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS,
        )
        // CameraX and ML Kit declare metadata holders; the application itself declares none,
        // and nothing may run as a foreground service.
        val services = info.services.orEmpty().toList()
        assertTrue(services.none { it.name.startsWith("com.tacnav") })
        if (Build.VERSION.SDK_INT >= 29) assertTrue(services.all { it.foregroundServiceType == 0 })
        val permissions = info.requestedPermissions.orEmpty().toList()
        assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in permissions)
        assertFalse("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.ACCESS_FINE_LOCATION" in permissions)
    }
}
