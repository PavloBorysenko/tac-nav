package com.tacnav.sapper

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tacnav.sapper.activation.ActivationPayload
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.game.Mode
import com.tacnav.sapper.ui.ClearScreen
import com.tacnav.sapper.ui.DetectScreen
import com.tacnav.sapper.ui.HomeScreen
import com.tacnav.sapper.ui.LayScreen
import com.tacnav.sapper.ui.PinDialog
import com.tacnav.sapper.ui.ScanScreen
import com.tacnav.sapper.ui.SettingsScreen
import com.tacnav.sapper.ui.TacNavTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app: SapperApplication
        get() = application as SapperApplication

    private val cameraGranted = mutableStateOf(false)
    private val locationGranted = mutableStateOf(false)
    private var ticker: Job? = null
    private var resumed = false

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { cameraGranted.value = it }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationGranted.value = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (resumed) startSensors()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        // The detector works only on screen, so the display must stay on without touch.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraGranted.value = granted(Manifest.permission.CAMERA)
        locationGranted.value = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        setContent { TacNavTheme { SapperRoot() } }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        startSensors()
    }

    /** Leaving the application stops searching and pauses every hold. */
    override fun onPause() {
        resumed = false
        ticker?.cancel()
        ticker = null
        app.locator.stop()
        app.compass.stop()
        app.controller.pause()
        super.onPause()
    }

    private fun startSensors() {
        if (app.repository.state.value == null) return
        app.compass.start()
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            app.locator.start { location, fix ->
                app.compass.updateLocation(location)
                app.controller.onFix(fix)
            }
        }
        if (ticker == null) {
            ticker = lifecycleScope.launch {
                while (isActive) {
                    val pose = app.compass.pose
                    app.controller.onHeading(pose.heading, pose.reliable)
                    runCatching { app.controller.tick() }
                    delay(1_000)
                }
            }
        }
    }

    private fun openMode(mode: Mode) {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
        app.controller.enter(mode)
        startSensors()
    }

    @Composable
    private fun SapperRoot() {
        val local by app.repository.state.collectAsState()
        val view by app.controller.view.collectAsState()
        var scanning by rememberSaveable { mutableStateOf(false) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var asking by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var activationError by remember { mutableStateOf<String?>(null) }

        val activate: (ActivationPayload) -> Unit = { payload ->
            busy = true
            activationError = null
            lifecycleScope.launch {
                runCatching { app.repository.activate(payload) }
                    .onSuccess {
                        app.startFresh()
                        scanning = false
                        settingsOpen = false
                        startSensors()
                    }
                    .onFailure { activationError = it.message ?: "Activation failed." }
                busy = false
            }
        }

        when {
            scanning -> {
                BackHandler { scanning = false }
                ScanScreen(
                    cameraGranted = cameraGranted.value,
                    onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    busy = busy,
                    error = activationError,
                    onPayload = activate,
                    onBack = {
                        scanning = false
                        activationError = null
                    },
                )
            }
            settingsOpen -> {
                BackHandler { settingsOpen = false }
                SettingsScreen(
                    local = local,
                    loadIcons = app.repository::icons,
                    busy = busy,
                    activationError = activationError,
                    onActivate = activate,
                    onSave = ::saveSettings,
                    onResetConnection = {
                        app.repository.resetConnection()
                        app.startFresh()
                        settingsOpen = false
                    },
                    onBack = {
                        settingsOpen = false
                        activationError = null
                    },
                )
            }
            local == null -> HomeScreen(
                connected = false,
                onScan = {
                    if (!cameraGranted.value) cameraPermission.launch(Manifest.permission.CAMERA)
                    scanning = true
                },
                onSettings = { asking = true },
                onMode = {},
            )
            else -> {
                if (view.mode != Mode.HOME) BackHandler { app.controller.enter(Mode.HOME) }
                val back = { app.controller.enter(Mode.HOME) }
                val settings = local?.settings ?: SapperSettings()
                when (view.mode) {
                    Mode.HOME -> HomeScreen(
                        connected = true,
                        onScan = {},
                        onSettings = { asking = true },
                        onMode = ::openMode,
                    )
                    Mode.DETECT -> DetectScreen(view, locationGranted.value, app.controller, back)
                    Mode.LAY -> LayScreen(view, settings, locationGranted.value, app.controller, back)
                    Mode.CLEAR -> ClearScreen(view, locationGranted.value, app.controller, back)
                }
            }
        }
        if (asking) {
            PinDialog(
                onVerify = app.repository::verifyPin,
                onAccepted = {
                    asking = false
                    settingsOpen = true
                },
                onDismiss = { asking = false },
            )
        }
    }

    private fun saveSettings(settings: SapperSettings, mapId: Long, teamId: Long, newPin: String?) {
        app.repository.selectContext(mapId, teamId)
        app.repository.saveSettings(settings)
        newPin?.let(app.repository::changePin)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
