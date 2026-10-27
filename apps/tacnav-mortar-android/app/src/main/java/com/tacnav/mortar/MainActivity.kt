package com.tacnav.mortar

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.tacnav.mortar.activation.ActivationPayload
import com.tacnav.mortar.activation.ActivationPayloadParser
import com.tacnav.mortar.activation.QrScanner
import com.tacnav.mortar.data.MortarRepository
import com.tacnav.mortar.data.ProtectedResult
import com.tacnav.mortar.data.ProtectedSettingsUpdate
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.Ballistics
import com.tacnav.mortar.domain.CalibrationMath
import com.tacnav.mortar.domain.CatalogIcon
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.domain.GuidanceMath
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.network.Coordinate
import com.tacnav.mortar.network.MailboxDelivery
import com.tacnav.mortar.sensors.LivePose
import com.tacnav.mortar.sensors.LocationAge
import com.tacnav.mortar.security.CalibrationState
import com.tacnav.mortar.security.LocalState
import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.session.FiringSessionService
import com.tacnav.mortar.session.PermissionPolicy
import com.tacnav.mortar.session.PermissionState
import com.tacnav.mortar.ui.Diagnostics
import com.tacnav.mortar.ui.Ink
import com.tacnav.mortar.ui.Ivory
import com.tacnav.mortar.ui.Olive
import com.tacnav.mortar.ui.TacNavChrome
import com.tacnav.mortar.ui.TacNavTheme
import com.tacnav.mortar.ui.checkboxColors
import com.tacnav.mortar.ui.fieldColors
import com.tacnav.mortar.ui.primaryButtonColors
import com.tacnav.mortar.ui.radioColors
import com.tacnav.mortar.ui.secondaryButtonColors
import com.tacnav.mortar.ui.textButtonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository: MortarRepository
        get() = (application as MortarApplication).repository

    private val app: MortarApplication
        get() = application as MortarApplication

    private val sessionPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val state = permissionState()
        refreshPermissionWarnings(state)
        if (PermissionPolicy.canStartSession(state)) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FiringSessionService::class.java),
            )
        } else {
            app.publishError(
                PermissionPolicy.sessionWarnings(state, currentFiringModes()).joinToString(" "),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        setContent {
            TacNavTheme {
                val state by repository.state.collectAsState()
                val shots by app.shots.collectAsState()
                val sessionError by app.sessionError.collectAsState()
                val acousticLevel by app.acousticLevel.collectAsState()
                val location by app.location.collectAsState()
                val pose by app.pose.collectAsState()
                val online by app.online.collectAsState()
                val deliveries by app.deliveries.collectAsState()
                val warnings by app.permissionWarnings.collectAsState()
                MortarApp(
                    state = state,
                    repository = repository,
                    shots = shots,
                    sessionError = sessionError,
                    acousticLevel = acousticLevel,
                    location = location,
                    pose = pose,
                    online = online,
                    deliveries = deliveries,
                    permissionWarnings = warnings,
                    onActivate = ::activate,
                    onStartSession = ::startSession,
                    onStopSession = ::stopSession,
                    onFire = ::fire,
                    onArm = ::armAcoustic,
                    onGiveDirection = ::giveDirection,
                    onReactivate = ::reactivate,
                )
            }
        }
        if (repository.state.value != null) registerCurrentFcmToken()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionWarnings(permissionState())
        val current = repository.state.value ?: return
        current.pendingFcmToken?.let { token ->
            lifecycleScope.launch { runCatching { repository.registerFcmToken(token) } }
        }
        app.syncMailbox()
    }

    private fun activate(
        payload: ActivationPayload,
        onFinished: (String?) -> Unit,
    ) {
        val tokenTask = runCatching { FirebaseMessaging.getInstance().token }.getOrNull()
        if (tokenTask == null) {
            completeActivation(payload, null, onFinished)
            return
        }
        tokenTask.addOnCompleteListener { task ->
            // A missing Play Services token must not block activation; onNewToken
            // or the next resume registers it later.
            val token = if (task.isSuccessful) task.result else null
            completeActivation(payload, token, onFinished)
        }
    }

    private fun completeActivation(
        payload: ActivationPayload,
        token: String?,
        onFinished: (String?) -> Unit,
    ) {
        lifecycleScope.launch {
            runCatching {
                repository.activate(payload, null)
            }.onSuccess {
                app.resetLocalSession()
                onFinished(null)
                if (!token.isNullOrBlank()) {
                    runCatching { repository.registerFcmToken(token) }
                }
                registerNotifications()
            }.onFailure {
                onFinished(it.message ?: getString(R.string.activation_invalid))
            }
        }
    }

    private fun reactivate() {
        stopSession()
        app.resetLocalSession()
        repository.logout()
    }

    private fun registerCurrentFcmToken() {
        runCatching { FirebaseMessaging.getInstance().token }.getOrNull()
            ?.addOnSuccessListener { token ->
                lifecycleScope.launch {
                    runCatching { repository.registerFcmToken(token) }
                }
            }
    }

    private fun startSession() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        sessionPermissions.launch(permissions.toTypedArray())
    }

    private fun stopSession() {
        startService(
            Intent(this, FiringSessionService::class.java)
                .setAction(FiringSessionService.ACTION_STOP),
        )
    }

    private fun fire() {
        startService(
            Intent(this, FiringSessionService::class.java)
                .setAction(FiringSessionService.ACTION_FIRE),
        )
    }

    private fun armAcoustic() {
        startService(
            Intent(this, FiringSessionService::class.java)
                .setAction(FiringSessionService.ACTION_ARM),
        )
    }

    private suspend fun giveDirection(
        latitude: Double,
        longitude: Double,
        heading: Double,
        accuracyMetres: Double,
    ): Double {
        val current = repository.state.value ?: error("Not activated.")
        app.bindApi()
        return app.firingEngine.publishDirectionLine(
            latitude,
            longitude,
            heading,
            accuracyMetres,
            current.settings,
            current.context,
        )
    }

    private fun permissionState(): PermissionState = PermissionState(
        preciseLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        microphone = granted(Manifest.permission.RECORD_AUDIO),
        camera = granted(Manifest.permission.CAMERA),
        notifications = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        playServices = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS,
    )

    private fun refreshPermissionWarnings(state: PermissionState) {
        app.publishPermissionWarnings(
            PermissionPolicy.sessionWarnings(state, currentFiringModes()),
        )
    }

    private fun currentFiringModes(): Set<FiringMode> =
        repository.state.value?.settings?.firingModes ?: emptySet()

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun registerNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 37
    }
}

private enum class Screen {
    INITIATION,
    SESSION,
    GAMEPLAY,
    PROTECTED,
    COMPASS,
    DIAGNOSTICS,
}

@Composable
fun MortarApp(
    state: LocalState?,
    repository: MortarRepository,
    shots: List<ShotRecord> = emptyList(),
    sessionError: String? = null,
    acousticLevel: Double = -120.0,
    location: Location? = null,
    pose: LivePose = LivePose(),
    online: Boolean = true,
    deliveries: List<MailboxDelivery> = emptyList(),
    permissionWarnings: List<String> = emptyList(),
    onActivate: (ActivationPayload, (String?) -> Unit) -> Unit,
    onStartSession: () -> Unit = {},
    onStopSession: () -> Unit = {},
    onFire: () -> Unit = {},
    onArm: () -> Unit = {},
    onGiveDirection: suspend (Double, Double, Double, Double) -> Double = { _, _, heading, _ -> heading },
    onReactivate: () -> Unit = repository::logout,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.INITIATION) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.INITIATION) }
    var calibrationNote by rememberSaveable { mutableStateOf<String?>(null) }
    if (state == null) {
        ActivationScreen(onActivate)
        return
    }
    val view = LocalView.current
    DisposableEffect(state.activeSession) {
        view.keepScreenOn = state.activeSession
        onDispose { view.keepScreenOn = false }
    }
    if (state.activeSession && screen == Screen.INITIATION) {
        screen = Screen.SESSION
    }
    if (!state.activeSession && screen == Screen.SESSION) {
        screen = Screen.INITIATION
    }
    when (screen) {
        Screen.INITIATION -> InitiationScreen(
            state = state,
            warnings = permissionWarnings,
            sessionError = sessionError,
            onDeploy = onStartSession,
            onOpenGameplay = {
                settingsReturn = Screen.INITIATION
                screen = Screen.GAMEPLAY
            },
            onReactivate = onReactivate,
            onOpenProtected = {
                settingsReturn = Screen.INITIATION
                screen = Screen.PROTECTED
            },
            onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
        )
        Screen.SESSION -> SessionScreen(
            state = state,
            shots = shots,
            sessionError = sessionError,
            acousticLevel = acousticLevel,
            location = location,
            pose = pose,
            online = online,
            deliveries = deliveries,
            warnings = permissionWarnings,
            onSelectModes = repository::selectPlayerModes,
            calibrationNote = calibrationNote,
            onEndSession = {
                onStopSession()
                screen = Screen.INITIATION
            },
            onFire = onFire,
            onArm = onArm,
            onCalibrateElevation = {
                val measured = pose.elevation
                val reference = state.settings.elevationReferenceDegrees
                calibrationNote = if (measured == null) {
                    "Elevation is unavailable."
                } else {
                    repository.saveCalibration(
                        state.calibration.copy(
                            elevationOffsetDegrees = CalibrationMath.elevationOffset(
                                reference,
                                measured,
                            ),
                        ),
                    )
                    "Elevation now reads ${reference.toInt()}° at this tube position."
                }
            },
            onOpenCompass = { screen = Screen.COMPASS },
            onResetCalibration = {
                repository.saveCalibration(CalibrationState())
                calibrationNote = "Calibration cleared."
            },
            onOpenGameplay = {
                settingsReturn = Screen.SESSION
                screen = Screen.GAMEPLAY
            },
        )
        Screen.GAMEPLAY -> GameplaySettingsScreen(
            state = state,
            onSave = { firing, aiming ->
                repository.selectPlayerModes(firing, aiming)
                screen = settingsReturn
            },
            onBack = { screen = settingsReturn },
        )
        Screen.PROTECTED -> ProtectedSettingsScreen(
            state = state,
            repository = repository,
            onSave = repository::saveProtected,
            onChangePin = repository::changePin,
            onBack = { screen = settingsReturn },
        )
        Screen.COMPASS -> CompassCalibrationScreen(
            calibration = state.calibration,
            pose = pose,
            location = location,
            onGiveDirection = onGiveDirection,
            onSave = { next, note ->
                repository.saveCalibration(next)
                calibrationNote = note
                screen = Screen.SESSION
            },
            onBack = { screen = Screen.SESSION },
        )
        Screen.DIAGNOSTICS -> DiagnosticsScreen(
            shots = shots,
            deliveries = deliveries,
            online = online,
            pendingFcmToken = state.pendingFcmToken != null,
            onBack = { screen = Screen.INITIATION },
        )
    }
}

@Composable
fun ActivationScreen(
    onActivate: (ActivationPayload, (String?) -> Unit) -> Unit,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var cameraGranted by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        scanning = granted
        if (!granted) error = "Camera permission denied. Use manual activation."
    }

    fun submit(payload: ActivationPayload) {
        busy = true
        error = null
        scanning = false
        onActivate(payload) {
            busy = false
            error = it
        }
    }

    TacNavChrome {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tac Nav Mortar", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("Scan the staff QR code or enter its HTTPS server URL and one-time code.", color = Ink)
            if (scanning && cameraGranted) {
                QrScanner(
                    onPayload = ::submit,
                    onError = { error = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
                TextButton(
                    onClick = { scanning = false },
                    colors = textButtonColors(),
                ) { Text("Use manual activation") }
            } else {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server-url"),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("One-time code") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activation-code"),
                )
                Button(
                    enabled = !busy,
                    colors = primaryButtonColors(),
                    onClick = {
                        runCatching {
                            ActivationPayloadParser.fromManual(serverUrl, code)
                        }.onSuccess(::submit).onFailure {
                            error = it.message
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activate-button"),
                ) {
                    Text("Activate")
                }
                OutlinedButton(
                    enabled = !busy,
                    colors = secondaryButtonColors(),
                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scan QR code")
                }
            }
            if (busy) CircularProgressIndicator(color = Olive)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun InitiationScreen(
    state: LocalState,
    warnings: List<String> = emptyList(),
    sessionError: String? = null,
    onDeploy: () -> Unit,
    onOpenGameplay: () -> Unit,
    onReactivate: () -> Unit,
    onOpenProtected: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
) {
    val map = state.activation.maps.single { it.id == state.context.mapId }
    val team = map.teams.single { it.id == state.context.teamId }
    var confirmReactivate by rememberSaveable { mutableStateOf(false) }
    if (confirmReactivate) {
        AlertDialog(
            onDismissRequest = { confirmReactivate = false },
            title = { Text("Reactivate this phone?") },
            text = {
                Text(
                    "Reactivation erases local settings, calibration, the PIN, and pending shots. " +
                        "You will need a new staff QR or one-time code.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReactivate = false
                        onReactivate()
                    },
                    colors = textButtonColors(),
                    modifier = Modifier.testTag("confirm-reactivate"),
                ) { Text("Erase and reactivate") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmReactivate = false },
                    colors = textButtonColors(),
                ) { Text("Cancel") }
            },
        )
    }
    TacNavChrome {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Tac Nav Mortar",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(map.name, modifier = Modifier.weight(1f), color = Ink, fontSize = 16.sp)
                Text(
                    state.settings.mortarName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    team.name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    color = Ink,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            (warnings + listOfNotNull(sessionError)).distinct().forEach {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = onDeploy,
                    colors = primaryButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("start-session"),
                ) {
                    Text("Prepare for battle", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenGameplay,
                    colors = secondaryButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gameplay-settings"),
                ) { Text("Gameplay settings") }
                OutlinedButton(
                    onClick = { confirmReactivate = true },
                    colors = secondaryButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reactivate"),
                ) {
                    Text("Reactivate")
                }
                OutlinedButton(
                    onClick = onOpenProtected,
                    colors = secondaryButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings"),
                ) { Text("Protected settings") }
                TextButton(
                    onClick = onOpenDiagnostics,
                    colors = textButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics"),
                ) { Text("Diagnostics") }
            }
            Spacer(Modifier.weight(0.35f))
        }
    }
}

@Composable
fun SessionScreen(
    state: LocalState,
    shots: List<ShotRecord> = emptyList(),
    sessionError: String? = null,
    acousticLevel: Double = -120.0,
    location: Location? = null,
    pose: LivePose = LivePose(),
    online: Boolean = true,
    deliveries: List<MailboxDelivery> = emptyList(),
    warnings: List<String> = emptyList(),
    onSelectModes: (FiringMode, AimingMode) -> Unit = { _, _ -> },
    onEndSession: () -> Unit,
    onFire: () -> Unit,
    onArm: () -> Unit,
    onCalibrateElevation: () -> Unit,
    onOpenCompass: () -> Unit,
    onResetCalibration: () -> Unit,
    onOpenGameplay: () -> Unit,
    calibrationNote: String? = null,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val elevation = pose.calibratedElevation
    val range = elevation
        ?.let { Ballistics.effectiveElevation(it, state.settings) }
        ?.let { Ballistics.rangeMetres(it, state.settings) }
    val inFlight = shots.count { it.phase == ShotPhase.IN_FLIGHT }
    val waiting = shots.count {
        it.phase != ShotPhase.IN_FLIGHT && it.phase != ShotPhase.COMPLETED
    }
    val messages = buildList {
        shots.takeLast(6).forEach { shot ->
            if (shot.phase == ShotPhase.IN_FLIGHT) {
                add("Shot in flight · ${shot.rangeMetres.toInt()} m · lands in ${Diagnostics.secondsLeft(shot, now)} s")
            } else {
                add("Shot fired · ${shot.rangeMetres.toInt()} m · ${shot.phase.name.lowercase().replace('_', ' ')}")
            }
            shot.failure?.let { add(it) }
        }
        deliveries.takeLast(3).forEach { add(Diagnostics.deliveryLine(it)) }
        addAll(warnings)
        sessionError?.let { add(it) }
        calibrationNote?.let { add(it) }
    }
    TacNavChrome {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        location?.accuracy?.let { "${it.toInt()} m" } ?: "—",
                        color = Ink,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        range?.let { "${it.toInt()} m" } ?: "—",
                        color = Ink,
                        fontSize = 16.sp,
                    )
                }
                Text(
                    buildString {
                        append(if (online) "Online" else "Offline")
                        if (inFlight > 0) append(" · $inFlight in flight")
                        if (waiting > 0) append(" · $waiting resolving")
                        if (!pose.compassReliable) append(" · compass unreliable, recalibrate")
                    },
                    color = Ink.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("session-status"),
                )
                if (state.settings.firingModes.size > 1 || state.settings.aimingModes.size > 1) {
                    InlineModes(state, onSelectModes)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ivory.copy(alpha = 0.55f))
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AimingReadout(state, location, pose, shots, elevation, range)
                }
                Spacer(Modifier.height(12.dp))
                if (state.activeFiringMode == FiringMode.BUTTON) {
                    Button(
                        onClick = onFire,
                        colors = primaryButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("fire-button"),
                    ) { Text("Fire", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = onArm,
                        colors = secondaryButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("ready-to-fire"),
                    ) { Text("Ready to fire  ${"%.0f".format(acousticLevel)} dBFS") }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(top = 10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (messages.isEmpty()) {
                        Text("Waiting for a shot.", color = Ink.copy(alpha = 0.7f))
                    }
                    messages.asReversed().forEach { Text(it, color = Ink, fontSize = 15.sp) }
                }
                Spacer(Modifier.height(56.dp))
            }
            TextButton(
                onClick = onEndSession,
                colors = textButtonColors(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .testTag("end-session"),
            ) { Text("Stand down", color = Ink, fontSize = 13.sp) }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuxCircle(
                    label = "∠ ${state.settings.elevationReferenceDegrees.toInt()}°",
                    tag = "calibration",
                    onClick = onCalibrateElevation,
                )
                AuxCircle(label = "N", tag = "compass-calibration", onClick = onOpenCompass)
                AuxCircle(label = "↺", tag = "reset-calibration", onClick = onResetCalibration)
                AuxCircle(label = "⚙", tag = "session-gameplay", onClick = onOpenGameplay)
            }
        }
    }
}

@Composable
private fun AuxCircle(label: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Olive)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ivory, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AimingReadout(
    state: LocalState,
    location: Location?,
    pose: LivePose,
    shots: List<ShotRecord>,
    elevation: Double?,
    range: Double?,
) {
    val heading = pose.calibratedHeading
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (state.activeAimingMode) {
            AimingMode.FREE -> {
                Text(
                    elevation?.let { "${it.toInt()}°" } ?: "—",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Text(
                    range?.let { "${it.toInt()} m" } ?: "aim the tube",
                    fontSize = 28.sp,
                    color = Ink,
                )
                Text(
                    heading?.let { "heading ${it.toInt()}°" } ?: "heading unavailable",
                    fontSize = 20.sp,
                    color = Ink,
                )
                Text(
                    pose.elevation?.let { "sensor ${it.toInt()}°" } ?: "",
                    fontSize = 16.sp,
                    color = Ink.copy(alpha = 0.75f),
                )
            }
            AimingMode.PRECISE_COORDINATES,
            AimingMode.APPROXIMATE_COORDINATES,
            -> {
                CoordinateAiming(state, location, pose, shots)
                LiveAngles(pose, compact = true)
            }
        }
    }
}

@Composable
private fun LiveAngles(pose: LivePose, compact: Boolean) {
    val elevation = pose.calibratedElevation?.toInt()?.let { "$it°" } ?: "—"
    val heading = pose.calibratedHeading?.toInt()?.let { "$it°" } ?: "—"
    Text(
        "$elevation   heading $heading",
        fontSize = if (compact) 16.sp else 20.sp,
        color = Ink,
    )
}

@Composable
private fun CoordinateAiming(
    state: LocalState,
    location: Location?,
    pose: LivePose,
    shots: List<ShotRecord>,
) {
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    val lat = latitude.toDoubleOrNull()
    val lng = longitude.toDoubleOrNull()
    OutlinedTextField(
        latitude,
        { latitude = it },
        label = { Text("Target latitude") },
        colors = fieldColors(),
    )
    OutlinedTextField(
        longitude,
        { longitude = it },
        label = { Text("Target longitude") },
        colors = fieldColors(),
    )
    val heading = pose.calibratedHeading
    val elevation = pose.calibratedElevation
    if (lat == null || lng == null || location == null || heading == null || elevation == null) {
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
    val target = Coordinate(lat, lng)
    if (state.activeAimingMode == AimingMode.PRECISE_COORDINATES) {
        val guidance = GuidanceMath.precise(snapshot, target, state.settings)
        Text("${guidance.targetDistanceMetres.toInt()} m", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("${guidance.turnSign} ${guidance.turnMetres.toInt()} m", fontSize = 24.sp, color = Ink)
        Text("${guidance.tiltSign} ${guidance.alongMetres.toInt()} m", fontSize = 24.sp, color = Ink)
        Text(
            "bearing ${guidance.requiredBearingDegrees.toInt()}° · " +
                (guidance.requiredElevationDegrees?.let { "elevation ${it.toInt()}°" } ?: "target out of range"),
            fontSize = 18.sp,
            color = Ink,
        )
        Text(
            guidance.predictedImpact?.let {
                "impact %.5f, %.5f · miss %d m".format(
                    java.util.Locale.US,
                    it.latitude,
                    it.longitude,
                    guidance.missMetres?.toInt() ?: 0,
                )
            } ?: "tube elevation is outside the firing range",
            fontSize = 14.sp,
            color = Ink.copy(alpha = 0.8f),
        )
    } else {
        // Approximate mode reveals nothing exact until the latest shot has landed.
        val latest = shots.lastOrNull { it.phase != ShotPhase.FAILED }
        val landed = latest?.takeIf { it.phase != ShotPhase.IN_FLIGHT }
        if (landed == null) {
            Text(
                if (latest != null) "Waiting for impact." else "Fire to see the correction.",
                fontSize = 22.sp,
                color = Ink,
            )
        } else {
            val correction = GuidanceMath.afterLanding(landed, target, state.settings)
            Text(
                "${correction.leftRight} ${correction.crossMetres.toInt()} m",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            Text(
                "${correction.addDrop} ${correction.alongMetres.toInt()} m",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            Text(
                if (correction.inZone) {
                    "inside the ${state.settings.approximateRadiusMetres.toInt()} m zone"
                } else {
                    "outside the ${state.settings.approximateRadiusMetres.toInt()} m zone"
                },
                fontSize = 16.sp,
                color = Ink,
            )
        }
    }
}

@Composable
fun GameplaySettingsScreen(
    state: LocalState,
    onSave: (FiringMode, AimingMode) -> Unit,
    onBack: () -> Unit,
) {
    var firing by remember { mutableStateOf(state.activeFiringMode) }
    var aiming by remember { mutableStateOf(state.activeAimingMode) }
    TacNavChrome {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Gameplay settings", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text("These choices stay inside the modes allowed for this mortar.", color = Ink)
            ModeSelector("Firing", state.settings.firingModes, firing, ::firingLabel) { firing = it }
            ModeSelector("Aiming", state.settings.aimingModes, aiming, ::aimingLabel) { aiming = it }
            Button(
                onClick = { onSave(firing, aiming) },
                colors = primaryButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            TextButton(onClick = onBack, colors = textButtonColors()) { Text("Back") }
        }
    }
}

@Composable
fun ProtectedSettingsScreen(
    state: LocalState,
    repository: MortarRepository? = null,
    onSave: (String, ProtectedSettingsUpdate) -> ProtectedResult,
    onChangePin: (String, String) -> ProtectedResult,
    onBack: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var firingModes by remember { mutableStateOf(state.settings.firingModes) }
    var aimingModes by remember { mutableStateOf(state.settings.aimingModes) }
    var radius by rememberSaveable { mutableStateOf(state.settings.approximateRadiusMetres.toString()) }
    var elevation by rememberSaveable { mutableStateOf(state.settings.elevationReferenceDegrees.toString()) }
    var mortarName by rememberSaveable { mutableStateOf(state.settings.mortarName) }
    var minElevation by rememberSaveable { mutableStateOf(state.settings.minElevationDegrees.toString()) }
    var maxElevation by rememberSaveable { mutableStateOf(state.settings.maxElevationDegrees.toString()) }
    var maxDistance by rememberSaveable { mutableStateOf(state.settings.maxDistanceMetres.toString()) }
    var minDistance by rememberSaveable { mutableStateOf(state.settings.minDistanceMetres.toString()) }
    var damageRadius by rememberSaveable { mutableStateOf(state.settings.damageRadiusMetres.toString()) }
    var edgeProbability by rememberSaveable { mutableStateOf(state.settings.edgeProbability.toString()) }
    var flightSeconds by rememberSaveable { mutableStateOf(state.settings.flightDurationSeconds.toString()) }
    var ttlSeconds by rememberSaveable { mutableStateOf(state.settings.impactTtlSeconds.toString()) }
    var gpsAccuracy by rememberSaveable { mutableStateOf(state.settings.maxLocationAccuracyMetres.toString()) }
    var gpsAge by rememberSaveable { mutableStateOf(state.settings.maxLocationAgeSeconds.toString()) }
    var acousticThreshold by rememberSaveable { mutableStateOf(state.settings.acousticThresholdDbfs.toString()) }
    var acousticImpulse by rememberSaveable { mutableStateOf(state.settings.acousticImpulseMs.toString()) }
    var acousticDebounce by rememberSaveable { mutableStateOf(state.settings.acousticDebounceMs.toString()) }
    var markOwnPosition by remember { mutableStateOf(state.settings.markOwnPositionOnShot) }
    var mapId by rememberSaveable { mutableStateOf(state.context.mapId) }
    var teamId by rememberSaveable { mutableStateOf(state.context.teamId) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMap = state.activation.maps.single { it.id == mapId }
    val icons = repository?.icons?.collectAsState()?.value ?: emptyList()
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        repository?.let { runCatching { it.refreshIcons() } }
    }

    TacNavChrome {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Protected settings", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text("Enter the local PIN to save. The initial PIN is 4321.", color = Ink)
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors(),
                modifier = Modifier.testTag("settings-pin"),
            )
            Text("Allowed firing modes", style = MaterialTheme.typography.titleMedium, color = Ink)
            FiringMode.entries.forEach { mode ->
                CheckboxRow(firingLabel(mode), mode in firingModes) { checked ->
                    firingModes = firingModes.toggle(mode, checked)
                }
            }
            Text("Allowed aiming modes", style = MaterialTheme.typography.titleMedium, color = Ink)
            AimingMode.entries.forEach { mode ->
                CheckboxRow(aimingLabel(mode), mode in aimingModes) { checked ->
                    aimingModes = aimingModes.toggle(mode, checked)
                }
            }
            OutlinedTextField(radius, { radius = it }, label = { Text("Approximate radius (metres)") }, colors = fieldColors(), modifier = Modifier.testTag("approximate-radius"))
            OutlinedTextField(elevation, { elevation = it }, label = { Text("Elevation reference (degrees)") }, colors = fieldColors(), modifier = Modifier.testTag("elevation-reference"))
            OutlinedTextField(mortarName, { mortarName = it }, label = { Text("Mortar name") }, colors = fieldColors())
            OutlinedTextField(minElevation, { minElevation = it }, label = { Text("Minimum elevation (degrees)") }, colors = fieldColors())
            OutlinedTextField(maxElevation, { maxElevation = it }, label = { Text("Maximum elevation (degrees)") }, colors = fieldColors())
            OutlinedTextField(maxDistance, { maxDistance = it }, label = { Text("Maximum distance (metres)") }, colors = fieldColors())
            OutlinedTextField(minDistance, { minDistance = it }, label = { Text("Minimum distance (metres)") }, colors = fieldColors())
            OutlinedTextField(damageRadius, { damageRadius = it }, label = { Text("Damage radius (metres)") }, colors = fieldColors())
            OutlinedTextField(edgeProbability, { edgeProbability = it }, label = { Text("Edge probability") }, colors = fieldColors())
            OutlinedTextField(flightSeconds, { flightSeconds = it }, label = { Text("Flight duration (seconds)") }, colors = fieldColors())
            OutlinedTextField(ttlSeconds, { ttlSeconds = it }, label = { Text("Impact lifetime (seconds)") }, colors = fieldColors())
            OutlinedTextField(gpsAccuracy, { gpsAccuracy = it }, label = { Text("Maximum GPS error (metres)") }, colors = fieldColors())
            OutlinedTextField(gpsAge, { gpsAge = it }, label = { Text("Maximum GPS age (seconds)") }, colors = fieldColors())
            OutlinedTextField(acousticThreshold, { acousticThreshold = it }, label = { Text("Acoustic threshold (dBFS)") }, colors = fieldColors())
            OutlinedTextField(acousticImpulse, { acousticImpulse = it }, label = { Text("Acoustic impulse (ms)") }, colors = fieldColors())
            OutlinedTextField(acousticDebounce, { acousticDebounce = it }, label = { Text("Acoustic debounce (ms)") }, colors = fieldColors())
            CheckboxRow(
                "Mark this mortar on the map for 30 seconds after each shot",
                markOwnPosition,
            ) { markOwnPosition = it }
            Text("Map", style = MaterialTheme.typography.titleMedium, color = Ink)
            state.activation.maps.forEach { map ->
                RadioRow(map.name, map.id == mapId) {
                    mapId = map.id
                    teamId = map.teams.first().id
                }
            }
            Text("Team", style = MaterialTheme.typography.titleMedium, color = Ink)
            selectedMap.teams.forEach { team ->
                RadioRow(team.name, team.id == teamId) { teamId = team.id }
            }
            Text("Impact icon", style = MaterialTheme.typography.titleMedium, color = Ink)
            icons.forEach { icon ->
                IconRow(icon, icon.id == state.settings.impactIconId) {
                    scope.launch {
                        repository?.let { store ->
                            message = protectedMessage(store.selectIcon(pin, icon))
                        }
                    }
                }
            }
            Button(
                modifier = Modifier.testTag("save-protected-settings"),
                colors = primaryButtonColors(),
                onClick = {
                    val settings = runCatching {
                        state.settings.copy(
                            firingModes = firingModes,
                            aimingModes = aimingModes,
                            approximateRadiusMetres = radius.toDouble(),
                            elevationReferenceDegrees = elevation.toDouble(),
                            mortarName = mortarName,
                            minElevationDegrees = minElevation.toDouble(),
                            maxElevationDegrees = maxElevation.toDouble(),
                            maxDistanceMetres = maxDistance.toDouble(),
                            minDistanceMetres = minDistance.toDouble(),
                            damageRadiusMetres = damageRadius.toDouble(),
                            edgeProbability = edgeProbability.toDouble(),
                            flightDurationSeconds = flightSeconds.toLong(),
                            impactTtlSeconds = ttlSeconds.toLong(),
                            maxLocationAccuracyMetres = gpsAccuracy.toDouble(),
                            maxLocationAgeSeconds = gpsAge.toLong(),
                            acousticThresholdDbfs = acousticThreshold.toDouble(),
                            acousticImpulseMs = acousticImpulse.toLong(),
                            acousticDebounceMs = acousticDebounce.toLong(),
                            markOwnPositionOnShot = markOwnPosition,
                        ).validated()
                    }.getOrElse {
                        message = it.message
                        return@Button
                    }
                    when (val result = onSave(pin, ProtectedSettingsUpdate(settings, mapId, teamId))) {
                        ProtectedResult.Saved -> onBack()
                        else -> message = protectedMessage(result)
                    }
                },
            ) { Text("Save protected settings") }
            Text("Change PIN", style = MaterialTheme.typography.titleMedium, color = Ink)
            OutlinedTextField(
                newPin,
                { newPin = it },
                label = { Text("New PIN") },
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors(),
            )
            OutlinedButton(
                onClick = { message = protectedMessage(onChangePin(pin, newPin)) },
                colors = secondaryButtonColors(),
            ) {
                Text("Change PIN")
            }
            message?.let { Text(it, color = Ink) }
            TextButton(onClick = onBack, colors = textButtonColors()) { Text("Back") }
        }
    }
}

@Composable
fun CompassCalibrationScreen(
    calibration: CalibrationState,
    pose: LivePose,
    location: Location?,
    onGiveDirection: suspend (Double, Double, Double, Double) -> Double,
    onSave: (CalibrationState, String) -> Unit,
    onBack: () -> Unit,
) {
    var knownLat by rememberSaveable { mutableStateOf("") }
    var knownLng by rememberSaveable { mutableStateOf("") }
    var givenBearing by rememberSaveable { mutableStateOf<Double?>(null) }
    var giving by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lat = CalibrationMath.parseCoordinate(knownLat)
    val lng = CalibrationMath.parseCoordinate(knownLng)
    val heading = pose.trueHeading
    val fix = location
    val linePreview = givenBearing?.let { known ->
        heading?.let { measured ->
            CalibrationMath.applyHeadingOffset(
                measured,
                CalibrationMath.signedBearingOffset(known, measured),
            )
        }
    }
    val preview = if (lat != null && lng != null && heading != null && fix != null) {
        val distance = Geodesy.distanceMetres(fix.latitude, fix.longitude, lat, lng)
        val known = Geodesy.bearingDegrees(fix.latitude, fix.longitude, lat, lng)
        Triple(distance, known, CalibrationMath.applyHeadingOffset(heading, CalibrationMath.signedBearingOffset(known, heading)))
    } else {
        null
    }
    TacNavChrome {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Compass calibration", style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(
                "Give direction draws a ${CalibrationMath.DIRECTION_LINE_METRES.toInt()} m line on the map from this GPS fix. It is visible only to this team for 30 seconds. Check that line, turn the phone onto it, then save.",
                color = Ink,
            )
            Text(
                "Heading ${heading?.toInt() ?: "n/a"}°, GPS ${fix?.accuracy?.toInt() ?: "n/a"} m, quality ${pose.quality}",
                color = Ink,
            )
            givenBearing?.let { known ->
                Text(
                    "Map line is at ${known.toInt()}°. Aim along it${
                        linePreview?.let { "; this aim will then read ${it.toInt()}°" } ?: ""
                    }.",
                    color = Ink,
                )
            }
            Button(
                colors = primaryButtonColors(),
                enabled = !giving,
                onClick = {
                    if (heading == null || fix == null) {
                        message = "Wait for a live GPS fix and heading before giving a direction."
                        return@Button
                    }
                    giving = true
                    val origin = fix
                    val measured = heading
                    scope.launch {
                        runCatching {
                            onGiveDirection(
                                origin.latitude,
                                origin.longitude,
                                measured,
                                origin.accuracy.toDouble(),
                            )
                        }.onSuccess { known ->
                            givenBearing = known
                            message = "A ${CalibrationMath.DIRECTION_LINE_METRES.toInt()} m line is on the map at ${known.toInt()}°. Aim along it, then save."
                        }.onFailure {
                            message = it.message ?: "The direction line could not be drawn."
                        }
                        giving = false
                    }
                },
            ) { Text("Give direction") }
            Button(
                colors = secondaryButtonColors(),
                onClick = {
                    val known = givenBearing
                    if (known == null || heading == null) {
                        message = "Give a direction and aim along that map line first."
                        return@Button
                    }
                    onSave(
                        calibration.copy(
                            directionOffsetDegrees = CalibrationMath.signedBearingOffset(known, heading),
                        ),
                        "This aim now reads the map-line bearing.",
                    )
                },
            ) { Text("Save this direction") }
            Text("Or use a known point", color = Ink, fontWeight = FontWeight.SemiBold)
            Text(
                "Keep the phone aimed at the known point and tap Save. Heading is taken at that moment.",
                color = Ink,
            )
            OutlinedTextField(
                knownLat,
                { knownLat = it },
                label = { Text("Known-point latitude") },
                colors = fieldColors(),
            )
            OutlinedTextField(
                knownLng,
                { knownLng = it },
                label = { Text("Known-point longitude") },
                colors = fieldColors(),
            )
            preview?.let { (distance, known, after) ->
                Text(
                    "Point is ${distance.toInt()} m away at ${known.toInt()}°. Save will make this aim read ${after.toInt()}°.",
                    color = Ink,
                )
            }
            Button(
                colors = primaryButtonColors(),
                onClick = {
                    val knownLatValue = lat
                    val knownLngValue = lng
                    if (knownLatValue == null || knownLngValue == null || heading == null || fix == null) {
                        message = "Aim at the known point after a live GPS fix is available."
                        return@Button
                    }
                    if (CalibrationMath.knownPointTooClose(
                            fix.latitude,
                            fix.longitude,
                            knownLatValue,
                            knownLngValue,
                        )
                    ) {
                        message = "The known point is too close to this GPS fix. Enter the target’s coordinates, not the mortar’s."
                        return@Button
                    }
                    val known = Geodesy.bearingDegrees(
                        fix.latitude,
                        fix.longitude,
                        knownLatValue,
                        knownLngValue,
                    )
                    onSave(
                        calibration.copy(
                            directionOffsetDegrees = CalibrationMath.signedBearingOffset(known, heading),
                        ),
                        "This aim now reads the bearing to the known point.",
                    )
                },
            ) { Text("Save compass offset") }
            message?.let { Text(it, color = Ink) }
            TextButton(onClick = onBack, colors = textButtonColors()) { Text("Back") }
        }
    }
}

@Composable
private fun InlineModes(
    state: LocalState,
    onSelectModes: (FiringMode, AimingMode) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.settings.firingModes.size > 1) {
            ModeChips(
                values = FiringMode.entries.filter { it in state.settings.firingModes },
                selected = state.activeFiringMode,
                label = ::firingLabel,
                tag = "firing-mode",
            ) { onSelectModes(it, state.activeAimingMode) }
        }
        if (state.settings.aimingModes.size > 1) {
            ModeChips(
                values = AimingMode.entries.filter { it in state.settings.aimingModes },
                selected = state.activeAimingMode,
                label = ::aimingLabel,
                tag = "aiming-mode",
            ) { onSelectModes(state.activeFiringMode, it) }
        }
    }
}

@Composable
private fun <T> ModeChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    tag: String,
    onSelected: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) Olive else Ivory.copy(alpha = 0.6f))
                    .border(1.dp, Olive, RoundedCornerShape(16.dp))
                    .clickable { onSelected(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("$tag-${label(value).lowercase().replace(' ', '-')}"),
            ) {
                Text(label(value), color = if (active) Ivory else Ink, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(
    shots: List<ShotRecord>,
    deliveries: List<MailboxDelivery>,
    online: Boolean,
    pendingFcmToken: Boolean,
    onBack: () -> Unit,
) {
    val now = System.currentTimeMillis()
    TacNavChrome {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                "Network ${if (online) "online" else "offline"} · Firebase token " +
                    if (pendingFcmToken) "waiting to register" else "registered or not needed",
                color = Ink,
            )
            Text("Shots", style = MaterialTheme.typography.titleMedium, color = Ink)
            if (shots.isEmpty()) Text("No shots recorded.", color = Ink)
            shots.asReversed().forEach { shot ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Ink.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                ) {
                    Diagnostics.shotLines(shot, now).forEach { Text(it, color = Ink, fontSize = 13.sp) }
                }
            }
            Text("Mailbox", style = MaterialTheme.typography.titleMedium, color = Ink)
            if (deliveries.isEmpty()) Text("No deliveries received in this run.", color = Ink)
            deliveries.asReversed().forEach {
                Text(Diagnostics.deliveryLine(it), color = Ink, fontSize = 13.sp)
            }
            TextButton(onClick = onBack, colors = textButtonColors()) { Text("Back") }
        }
    }
}

@Composable
private fun <T> ModeSelector(
    title: String,
    values: Set<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    if (values.size <= 1) {
        Text("$title: ${label(values.first())}", color = Ink)
        return
    }
    Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
    values.forEach { value ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelected(value) },
        ) {
            RadioButton(
                selected = value == selected,
                onClick = { onSelected(value) },
                colors = radioColors(),
            )
            Text(label(value), modifier = Modifier.padding(top = 12.dp), color = Ink)
        }
    }
}

@Composable
private fun IconRow(icon: CatalogIcon, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick, colors = radioColors())
        AsyncImage(model = icon.imageUrl, contentDescription = null, modifier = Modifier.height(48.dp))
        Text(icon.title, color = Ink)
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) },
    ) {
        Checkbox(checked, onCheckedChange = onChecked, colors = checkboxColors())
        Text(label, modifier = Modifier.padding(top = 12.dp), color = Ink)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        RadioButton(selected, onClick, colors = radioColors())
        Text(label, modifier = Modifier.padding(top = 12.dp), color = Ink)
    }
}

private fun <T> Set<T>.toggle(value: T, enabled: Boolean): Set<T> =
    if (enabled) this + value else this - value

private fun firingLabel(mode: FiringMode): String =
    if (mode == FiringMode.BUTTON) "Button" else "Acoustic"

private fun aimingLabel(mode: AimingMode): String = when (mode) {
    AimingMode.FREE -> "Free"
    AimingMode.PRECISE_COORDINATES -> "Precise coordinates"
    AimingMode.APPROXIMATE_COORDINATES -> "Approximate coordinates"
}

private fun protectedMessage(result: ProtectedResult): String = when (result) {
    ProtectedResult.Saved -> "Saved."
    is ProtectedResult.Invalid -> result.message
    is ProtectedResult.Denied -> when (val attempt = result.attempt) {
        PinAttempt.Accepted -> "Saved."
        is PinAttempt.Rejected ->
            "Incorrect PIN. ${attempt.attemptsRemaining} attempts remain before a delay."
        is PinAttempt.Throttled ->
            "Too many failed attempts. Try again in ${attempt.retryAfterMillis / 1_000 + 1} seconds."
    }
}
