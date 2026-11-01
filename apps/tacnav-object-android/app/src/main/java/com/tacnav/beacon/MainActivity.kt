package com.tacnav.beacon

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.tacnav.beacon.activation.ActivationPayload
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.security.BeaconRepository
import com.tacnav.beacon.session.ObjectService
import com.tacnav.beacon.ui.ActivationScreen
import com.tacnav.beacon.ui.MainScreen
import com.tacnav.beacon.ui.MainUiState
import com.tacnav.beacon.ui.PinDialog
import com.tacnav.beacon.ui.SettingsScreen
import com.tacnav.beacon.ui.TacNavTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app: BeaconApplication
        get() = application as BeaconApplication

    private val repository: BeaconRepository
        get() = app.repository

    private val cameraGranted = mutableStateOf(false)

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { cameraGranted.value = it }

    private val sessionPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startSessionService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        // Bound to this window, so it holds in every state and ends on leaving.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraGranted.value = granted(Manifest.permission.CAMERA)
        setContent { TacNavTheme { BeaconRoot() } }
    }

    override fun onStart() {
        super.onStart()
        app.visible = true
        if (repository.state.value != null) requestSession()
    }

    override fun onResume() {
        super.onResume()
        val current = repository.state.value ?: return
        current.pendingFcmToken?.let { token ->
            lifecycleScope.launch { runCatching { repository.registerFcmToken(token) } }
        }
        app.syncMailbox()
    }

    override fun onStop() {
        app.visible = false
        super.onStop()
    }

    @Composable
    private fun BeaconRoot() {
        val local by repository.state.collectAsState()
        val game by app.controller.state.collectAsState()
        val message by app.controller.message.collectAsState()
        val online by app.online.collectAsState()
        var activating by remember { mutableStateOf(false) }
        var activationError by remember { mutableStateOf<String?>(null) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var reactivating by rememberSaveable { mutableStateOf(false) }
        var asking by remember { mutableStateOf(false) }
        var frame by remember { mutableStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                frame += 1
            }
        }

        val state = local
        val current = game
        if (state == null || current == null || reactivating) {
            ActivationScreen(
                busy = activating,
                error = activationError,
                cameraGranted = cameraGranted.value,
                onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                onActivate = { payload ->
                    activating = true
                    activationError = null
                    activate(payload) { failure ->
                        activating = false
                        activationError = failure
                        if (failure == null) reactivating = false
                    }
                },
                startScanning = reactivating,
                onCancel = if (reactivating) {
                    {
                        reactivating = false
                        activationError = null
                    }
                } else {
                    null
                },
            )
            return
        }
        if (settingsOpen) {
            SettingsScreen(
                local = state,
                sessionRunning = current.life == LifeState.DEPLOYING ||
                    current.life == LifeState.ACTIVE,
                loadIcons = repository::icons,
                onSave = ::saveSettings,
                onReset = app.controller::reset,
                onReactivate = {
                    settingsOpen = false
                    reactivate()
                },
                onBack = { settingsOpen = false },
            )
            return
        }
        // Recomposes once a second so GPS age is judged against the current time.
        val canActivate = frame >= 0 && app.controller.canActivate()
        MainScreen(
            ui = MainUiState(
                local = state,
                game = current,
                canActivate = canActivate,
                online = online,
                compassReliable = app.compass.pose.reliable,
                message = message,
                warnings = warnings(),
            ),
            onActivate = {
                app.controller.clearMessage()
                app.controller.activate()
            },
            onDeactivate = {
                app.controller.clearMessage()
                app.controller.deactivate()
            },
            onArm = app.controller::arm,
            onOpenSettings = { asking = true },
            onScanQr = {
                if (!cameraGranted.value) cameraPermission.launch(Manifest.permission.CAMERA)
                reactivating = true
            },
        )
        if (asking) {
            PinDialog(
                onVerify = repository::verifyPin,
                onAccepted = {
                    asking = false
                    settingsOpen = true
                },
                onDismiss = { asking = false },
            )
        }
    }

    private fun saveSettings(
        settings: ObjectSettings,
        mapId: Long,
        teamId: Long,
        newPin: String?,
    ) {
        repository.selectContext(mapId, teamId)
        repository.saveSettings(settings)
        newPin?.let(repository::changePin)
    }

    private fun warnings(): List<String> = buildList {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            add("Allow precise location. The object cannot activate without it.")
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            add("Allow notifications to see the object's status with the screen off.")
        }
    }

    private fun activate(payload: ActivationPayload, onFinished: (String?) -> Unit) {
        val tokenTask = runCatching { FirebaseMessaging.getInstance().token }.getOrNull()
        if (tokenTask == null) {
            completeActivation(payload, null, onFinished)
            return
        }
        tokenTask.addOnCompleteListener { task ->
            // A missing Play Services token must not block activation; onNewToken
            // or the next resume registers it later.
            completeActivation(payload, if (task.isSuccessful) task.result else null, onFinished)
        }
    }

    private fun completeActivation(
        payload: ActivationPayload,
        token: String?,
        onFinished: (String?) -> Unit,
    ) {
        val previous = repository.state.value?.activation?.applicationId
        lifecycleScope.launch {
            runCatching { repository.activate(payload) }
                .onSuccess {
                    app.startNewGame(previous)
                    onFinished(null)
                    if (!token.isNullOrBlank()) {
                        runCatching { repository.registerFcmToken(token) }
                    }
                    requestSession()
                }
                .onFailure { onFinished(it.message ?: "Activation failed.") }
        }
    }

    private fun reactivate() {
        stopService(Intent(this, ObjectService::class.java))
        app.forgetGame()
        repository.logout()
    }

    private fun requestSession() {
        val missing = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot(::granted)
        if (missing.isEmpty()) {
            startSessionService()
        } else {
            sessionPermissions.launch(missing.toTypedArray())
        }
    }

    private fun startSessionService() {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ContextCompat.startForegroundService(this, Intent(this, ObjectService::class.java))
        } else if (!app.serviceRunning) {
            app.controller.restore()
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
