package com.tacnav.mortar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import com.tacnav.mortar.activation.ActivationPayload
import com.tacnav.mortar.activation.ActivationPayloadParser
import com.tacnav.mortar.activation.QrScanner
import com.tacnav.mortar.data.MortarRepository
import com.tacnav.mortar.data.ProtectedResult
import com.tacnav.mortar.data.ProtectedSettingsUpdate
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.Ballistics
import com.tacnav.mortar.domain.CatalogIcon
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.domain.GuidanceMath
import com.tacnav.mortar.domain.SensorSnapshot
import com.tacnav.mortar.domain.ShotPhase
import com.tacnav.mortar.domain.ShotRecord
import com.tacnav.mortar.network.Coordinate
import com.tacnav.mortar.sensors.LivePose
import com.tacnav.mortar.sensors.LocationAge
import com.tacnav.mortar.sensors.PhonePose
import com.tacnav.mortar.security.CalibrationState
import com.tacnav.mortar.security.LocalState
import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.session.FiringSessionService
import com.tacnav.mortar.ui.Ink
import com.tacnav.mortar.ui.Ivory
import com.tacnav.mortar.ui.Olive
import com.tacnav.mortar.ui.TacNavChrome
import com.tacnav.mortar.ui.TacNavTheme
import com.tacnav.mortar.ui.primaryButtonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository: MortarRepository
        get() = (application as MortarApplication).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TacNavTheme {
                val state by repository.state.collectAsState()
                val app = application as MortarApplication
                val shots by app.shots.collectAsState()
                val sessionError by app.sessionError.collectAsState()
                val acousticLevel by app.acousticLevel.collectAsState()
                val location by app.location.collectAsState()
                val pose by app.pose.collectAsState()
                MortarApp(
                    state = state,
                    repository = repository,
                    shots = shots,
                    sessionError = sessionError,
                    acousticLevel = acousticLevel,
                    location = location,
                    pose = pose,
                    onActivate = ::activate,
                    onStartSession = ::startSession,
                    onStopSession = ::stopSession,
                    onFire = ::fire,
                    onArm = ::armAcoustic,
                )
            }
        }
        if (repository.state.value != null) registerCurrentFcmToken()
    }

    private fun activate(
        payload: ActivationPayload,
        onFinished: (String?) -> Unit,
    ) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
            lifecycleScope.launch {
                runCatching {
                    repository.activate(payload, tokenTask.result)
                }.onSuccess {
                    onFinished(null)
                    registerNotifications()
                }.onFailure {
                    onFinished(it.message ?: getString(R.string.activation_invalid))
                }
            }
        }
    }

    private fun registerCurrentFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                runCatching { repository.registerFcmToken(token) }
            }
        }
    }

    private fun startSession() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            SESSION_PERMISSION_REQUEST,
        )
        ContextCompat.startForegroundService(
            this,
            Intent(this, FiringSessionService::class.java),
        )
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
        const val SESSION_PERMISSION_REQUEST = 38
    }
}

private enum class Screen {
    INITIATION,
    SESSION,
    GAMEPLAY,
    PROTECTED,
    COMPASS,
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
    onActivate: (ActivationPayload, (String?) -> Unit) -> Unit,
    onStartSession: () -> Unit = {},
    onStopSession: () -> Unit = {},
    onFire: () -> Unit = {},
    onArm: () -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(Screen.INITIATION) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.INITIATION) }
    if (state == null) {
        ActivationScreen(onActivate)
        return
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
            onDeploy = {
                onStartSession()
                screen = Screen.SESSION
            },
            onOpenGameplay = {
                settingsReturn = Screen.INITIATION
                screen = Screen.GAMEPLAY
            },
            onReactivate = repository::logout,
            onOpenProtected = {
                settingsReturn = Screen.INITIATION
                screen = Screen.PROTECTED
            },
        )
        Screen.SESSION -> SessionScreen(
            state = state,
            shots = shots,
            sessionError = sessionError,
            acousticLevel = acousticLevel,
            location = location,
            pose = pose,
            onEndSession = {
                onStopSession()
                screen = Screen.INITIATION
            },
            onFire = onFire,
            onArm = onArm,
            onCalibrateElevation = {
                val measured = pose.elevation ?: return@SessionScreen
                repository.saveCalibration(
                    state.calibration.copy(
                        elevationOffsetDegrees = state.settings.elevationReferenceDegrees - measured,
                    ),
                )
            },
            onOpenCompass = { screen = Screen.COMPASS },
            onResetCalibration = { repository.saveCalibration(CalibrationState()) },
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
            onSave = {
                repository.saveCalibration(it)
                screen = Screen.SESSION
            },
            onBack = { screen = Screen.SESSION },
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
                TextButton(onClick = { scanning = false }) { Text("Use manual activation") }
            } else {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server-url"),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("One-time code") },
                    singleLine = true,
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
    onDeploy: () -> Unit,
    onOpenGameplay: () -> Unit,
    onReactivate: () -> Unit,
    onOpenProtected: () -> Unit,
) {
    val map = state.activation.maps.single { it.id == state.context.mapId }
    val team = map.teams.single { it.id == state.context.teamId }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gameplay-settings"),
                ) { Text("Gameplay settings") }
                OutlinedButton(onClick = onReactivate, modifier = Modifier.fillMaxWidth()) {
                    Text("Reactivate")
                }
                OutlinedButton(
                    onClick = onOpenProtected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings"),
                ) { Text("Protected settings") }
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
    onEndSession: () -> Unit,
    onFire: () -> Unit,
    onArm: () -> Unit,
    onCalibrateElevation: () -> Unit,
    onOpenCompass: () -> Unit,
    onResetCalibration: () -> Unit,
    onOpenGameplay: () -> Unit,
) {
    val elevation = pose.calibratedElevation
    val range = elevation?.let {
        runCatching { Ballistics.rangeMetres(it, state.settings) }.getOrNull()
    }
    val messages = buildList {
        shots.takeLast(6).forEach { shot ->
            add("Shot fired · ${shot.rangeMetres.toInt()} m · ${shot.phase.name.lowercase().replace('_', ' ')}")
            shot.failure?.let { add(it) }
        }
        sessionError?.let { add(it) }
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
                Spacer(Modifier.height(12.dp))
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
                    heading?.let { "${it.toInt()}°" } ?: "",
                    fontSize = 20.sp,
                    color = Ink.copy(alpha = 0.75f),
                )
            }
            AimingMode.PRECISE_COORDINATES,
            AimingMode.APPROXIMATE_COORDINATES,
            -> CoordinateAiming(state, location, pose, shots)
        }
    }
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
    OutlinedTextField(latitude, { latitude = it }, label = { Text("Target latitude") })
    OutlinedTextField(longitude, { longitude = it }, label = { Text("Target longitude") })
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
            guidance.requiredElevationDegrees?.let { "${it.toInt()}°" } ?: "out of range",
            fontSize = 22.sp,
            color = Ink,
        )
    } else {
        val latest = shots.lastOrNull { it.phase != ShotPhase.FAILED }
        val origin = if (latest != null) {
            snapshot.copy(
                latitude = latest.snapshot.latitude,
                longitude = latest.snapshot.longitude,
                trueHeadingDegrees = latest.snapshot.trueHeadingDegrees,
                elevationDegrees = latest.snapshot.elevationDegrees,
            )
        } else {
            snapshot
        }
        val guidance = GuidanceMath.approximate(origin, target, state.settings, latest != null)
        Text("${guidance.leftRight} / ${guidance.addDrop}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (latest != null) {
            Text(
                "${guidance.crossMetres?.toInt()} m / ${guidance.alongMetres?.toInt()} m",
                fontSize = 22.sp,
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
            TextButton(onClick = onBack) { Text("Back") }
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
                modifier = Modifier.testTag("settings-pin"),
            )
            Text("Allowed firing modes", style = MaterialTheme.typography.titleMedium)
            FiringMode.entries.forEach { mode ->
                CheckboxRow(firingLabel(mode), mode in firingModes) { checked ->
                    firingModes = firingModes.toggle(mode, checked)
                }
            }
            Text("Allowed aiming modes", style = MaterialTheme.typography.titleMedium)
            AimingMode.entries.forEach { mode ->
                CheckboxRow(aimingLabel(mode), mode in aimingModes) { checked ->
                    aimingModes = aimingModes.toggle(mode, checked)
                }
            }
            OutlinedTextField(radius, { radius = it }, label = { Text("Approximate radius (metres)") }, modifier = Modifier.testTag("approximate-radius"))
            OutlinedTextField(elevation, { elevation = it }, label = { Text("Elevation reference (degrees)") }, modifier = Modifier.testTag("elevation-reference"))
            OutlinedTextField(mortarName, { mortarName = it }, label = { Text("Mortar name") })
            OutlinedTextField(minElevation, { minElevation = it }, label = { Text("Minimum elevation (degrees)") })
            OutlinedTextField(maxElevation, { maxElevation = it }, label = { Text("Maximum elevation (degrees)") })
            OutlinedTextField(maxDistance, { maxDistance = it }, label = { Text("Maximum distance (metres)") })
            OutlinedTextField(minDistance, { minDistance = it }, label = { Text("Minimum distance (metres)") })
            OutlinedTextField(damageRadius, { damageRadius = it }, label = { Text("Damage radius (metres)") })
            OutlinedTextField(edgeProbability, { edgeProbability = it }, label = { Text("Edge probability") })
            OutlinedTextField(flightSeconds, { flightSeconds = it }, label = { Text("Flight duration (seconds)") })
            OutlinedTextField(ttlSeconds, { ttlSeconds = it }, label = { Text("Impact lifetime (seconds)") })
            OutlinedTextField(gpsAccuracy, { gpsAccuracy = it }, label = { Text("Maximum GPS error (metres)") })
            OutlinedTextField(gpsAge, { gpsAge = it }, label = { Text("Maximum GPS age (seconds)") })
            OutlinedTextField(acousticThreshold, { acousticThreshold = it }, label = { Text("Acoustic threshold (dBFS)") })
            OutlinedTextField(acousticImpulse, { acousticImpulse = it }, label = { Text("Acoustic impulse (ms)") })
            OutlinedTextField(acousticDebounce, { acousticDebounce = it }, label = { Text("Acoustic debounce (ms)") })
            Text("Map", style = MaterialTheme.typography.titleMedium)
            state.activation.maps.forEach { map ->
                RadioRow(map.name, map.id == mapId) {
                    mapId = map.id
                    teamId = map.teams.first().id
                }
            }
            Text("Team", style = MaterialTheme.typography.titleMedium)
            selectedMap.teams.forEach { team ->
                RadioRow(team.name, team.id == teamId) { teamId = team.id }
            }
            Text("Impact icon", style = MaterialTheme.typography.titleMedium)
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
            Text("Change PIN", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(newPin, { newPin = it }, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation())
            OutlinedButton(onClick = { message = protectedMessage(onChangePin(pin, newPin)) }) {
                Text("Change PIN")
            }
            message?.let { Text(it) }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun CompassCalibrationScreen(
    calibration: CalibrationState,
    onSave: (CalibrationState) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pose by remember { mutableStateOf(LivePose()) }
    var location by remember { mutableStateOf<Location?>(null) }
    var knownLat by rememberSaveable { mutableStateOf("") }
    var knownLng by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val phone = PhonePose(
            context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager,
        )
        phone.start()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener {
                    location = it
                    phone.updateLocation(it)
                }
        }
        try {
            while (isActive) {
                pose = phone.pose
                delay(200)
            }
        } finally {
            phone.stop()
        }
    }
    TacNavChrome {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Compass calibration", style = MaterialTheme.typography.titleLarge, color = Ink)
            Text("Aim the tube at a known map point. Nearby metal can throw the heading off.", color = Ink)
            Text("Magnetic ${pose.magneticHeading?.toInt() ?: "n/a"}°, quality ${pose.quality}", color = Ink)
            OutlinedTextField(knownLat, { knownLat = it }, label = { Text("Known-point latitude") })
            OutlinedTextField(knownLng, { knownLng = it }, label = { Text("Known-point longitude") })
            Button(
                colors = primaryButtonColors(),
                onClick = {
                    val lat = knownLat.toDoubleOrNull()
                    val lng = knownLng.toDoubleOrNull()
                    val heading = pose.trueHeading
                    val fix = location
                    if (lat == null || lng == null || heading == null || fix == null) {
                        message = "Aim at the known point after a GPS fix is available."
                        return@Button
                    }
                    val known = Geodesy.bearingDegrees(fix.latitude, fix.longitude, lat, lng)
                    onSave(
                        calibration.copy(
                            directionOffsetDegrees = Geodesy.normalizeBearing(known - heading).let {
                                if (it > 180) it - 360.0 else it
                            },
                        ),
                    )
                },
            ) { Text("Save compass offset") }
            message?.let { Text(it) }
            TextButton(onClick = onBack) { Text("Back") }
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
            RadioButton(selected = value == selected, onClick = { onSelected(value) })
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
        RadioButton(selected, onClick)
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
        Checkbox(checked, onCheckedChange = onChecked)
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
        RadioButton(selected, onClick)
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
