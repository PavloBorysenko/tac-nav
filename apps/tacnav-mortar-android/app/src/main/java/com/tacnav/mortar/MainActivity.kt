package com.tacnav.mortar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
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
import com.tacnav.mortar.sensors.PhonePose
import com.tacnav.mortar.security.CalibrationState
import com.tacnav.mortar.security.LocalState
import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.session.FiringSessionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository: MortarRepository
        get() = (application as MortarApplication).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
                    onRegisterNotifications = ::registerNotifications,
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
    HOME,
    SETTINGS,
    CALIBRATION,
    ICONS,
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
    onRegisterNotifications: () -> Unit,
    onStartSession: () -> Unit = {},
    onStopSession: () -> Unit = {},
    onFire: () -> Unit = {},
    onArm: () -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    if (state == null) {
        ActivationScreen(onActivate)
        return
    }
    when (screen) {
        Screen.HOME -> HomeScreen(
            state = state,
            cachedIconPath = repository.selectedIconPath(),
            shots = shots,
            sessionError = sessionError,
            acousticLevel = acousticLevel,
            location = location,
            pose = pose,
            onModesSelected = repository::selectPlayerModes,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenCalibration = { screen = Screen.CALIBRATION },
            onOpenIcons = { screen = Screen.ICONS },
            onRegisterNotifications = onRegisterNotifications,
            onStartSession = onStartSession,
            onStopSession = onStopSession,
            onFire = onFire,
            onArm = onArm,
            onReactivate = {
                repository.logout()
                screen = Screen.HOME
            },
        )
        Screen.SETTINGS -> ProtectedSettingsScreen(
            state = state,
            onSave = repository::saveProtected,
            onChangePin = repository::changePin,
            onBack = { screen = Screen.HOME },
        )
        Screen.CALIBRATION -> CalibrationScreen(
            settings = state.settings,
            calibration = state.calibration,
            onSave = repository::saveCalibration,
            onBack = { screen = Screen.HOME },
        )
        Screen.ICONS -> IconPickerScreen(repository) { screen = Screen.HOME }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Activate Tac Nav Mortar", style = MaterialTheme.typography.headlineMedium)
        Text("Scan the staff QR code or enter its HTTPS server URL and one-time code.")
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
                onClick = {
                    runCatching {
                        ActivationPayloadParser.fromManual(serverUrl, code)
                    }.onSuccess(::submit).onFailure {
                        error = it.message
                    }
                },
                modifier = Modifier.testTag("activate-button"),
            ) {
                Text("Activate")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            ) {
                Text("Scan QR code")
            }
        }
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: LocalState,
    cachedIconPath: String?,
    shots: List<ShotRecord> = emptyList(),
    sessionError: String? = null,
    acousticLevel: Double = -120.0,
    location: Location? = null,
    pose: LivePose = LivePose(),
    onModesSelected: (FiringMode, AimingMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenIcons: () -> Unit,
    onRegisterNotifications: () -> Unit,
    onStartSession: () -> Unit = {},
    onStopSession: () -> Unit = {},
    onFire: () -> Unit = {},
    onArm: () -> Unit = {},
    onReactivate: () -> Unit,
) {
    val map = state.activation.maps.single { it.id == state.context.mapId }
    val team = map.teams.single { it.id == state.context.teamId }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Tac Nav Mortar") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${map.name} · ${team.name}", style = MaterialTheme.typography.titleMedium)
            PermissionStatus()
            val heading = pose.calibratedHeading
            val elevation = pose.calibratedElevation
            val range = elevation?.let {
                runCatching { Ballistics.rangeMetres(it, state.settings) }.getOrNull()
            }
            Text("GPS accuracy: ${location?.accuracy?.let { "±${it.toInt()} m" } ?: "waiting"}")
            Text("True heading: ${heading?.let { "${it.toInt()}°" } ?: "waiting"}")
            Text("Elevation: ${elevation?.let { "${it.toInt()}°" } ?: "waiting"}")
            Text("Range: ${range?.let { "${it.toInt()} m" } ?: "waiting"}")
            Text("Sensor quality: ${pose.quality}")
            if (state.activeSession) {
                Button(onClick = onStopSession) { Text("Stop session") }
            } else {
                Button(onClick = onStartSession, modifier = Modifier.testTag("start-session")) {
                    Text("Start active session")
                }
            }
            if (state.activeFiringMode == FiringMode.BUTTON) {
                Button(
                    onClick = onFire,
                    enabled = state.activeSession,
                    modifier = Modifier.testTag("fire-button"),
                ) { Text("Fire") }
            } else {
                OutlinedButton(
                    onClick = onArm,
                    enabled = state.activeSession,
                    modifier = Modifier.testTag("ready-to-fire"),
                ) { Text("Ready to fire") }
                Text("Microphone level: ${"%.1f".format(acousticLevel)} dBFS")
            }
            AimingPanel(state, location, pose, shots)
            shots.filter { it.phase == ShotPhase.IN_FLIGHT || it.phase == ShotPhase.DUE }.forEach { shot ->
                Text("In flight ${shot.shotId.take(8)} · ${shot.rangeMetres.toInt()} m")
            }
            shots.forEach { shot ->
                Text("Shot ${shot.shotId.take(8)} ${shot.phase}")
                shot.facts.forEach { fact ->
                    Text(
                        "Object ${fact.objectId}: ${fact.distanceMetres.toInt()} m, " +
                            "P=${"%.2f".format(fact.probability)}, roll=${"%.2f".format(fact.roll)}",
                    )
                }
                shot.failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            sessionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (cachedIconPath != null) {
                AsyncImage(
                    model = cachedIconPath,
                    contentDescription = "Selected impact icon",
                    modifier = Modifier.height(72.dp),
                )
            }
            ModeSelector(
                title = "Firing mode",
                values = state.settings.firingModes,
                selected = state.activeFiringMode,
                label = { if (it == FiringMode.BUTTON) "Button" else "Acoustic" },
                onSelected = { onModesSelected(it, state.activeAimingMode) },
            )
            ModeSelector(
                title = "Aiming mode",
                values = state.settings.aimingModes,
                selected = state.activeAimingMode,
                label = {
                    when (it) {
                        AimingMode.FREE -> "Free"
                        AimingMode.PRECISE_COORDINATES -> "Precise coordinates"
                        AimingMode.APPROXIMATE_COORDINATES -> "Approximate coordinates"
                    }
                },
                onSelected = { onModesSelected(state.activeFiringMode, it) },
            )
            Button(onClick = onOpenCalibration, modifier = Modifier.testTag("calibration")) {
                Text("Quick calibration (no PIN)")
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
                Text("Protected settings")
            }
            OutlinedButton(onClick = onOpenIcons) { Text("Choose impact icon") }
            TextButton(onClick = onRegisterNotifications) { Text("Enable notifications") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Reactivation resets local settings, calibration, and credentials.",
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onReactivate) { Text("Log out or reactivate") }
        }
    }
}

@Composable
private fun AimingPanel(
    state: LocalState,
    location: Location?,
    pose: LivePose,
    shots: List<ShotRecord>,
) {
    if (state.activeAimingMode == AimingMode.FREE) {
        Text("Free aiming uses the live tube pose. Range is linear between the configured angles.")
        return
    }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    val lat = latitude.toDoubleOrNull()
    val lng = longitude.toDoubleOrNull()
    OutlinedTextField(latitude, { latitude = it }, label = { Text("Target latitude") })
    OutlinedTextField(longitude, { longitude = it }, label = { Text("Target longitude") })
    val heading = pose.calibratedHeading
    val elevation = pose.calibratedElevation
    if (lat != null && lng != null && location != null && heading != null && elevation != null) {
        val snapshot = SensorSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMetres = location.accuracy.toDouble(),
            ageSeconds = ((System.currentTimeMillis() - location.time) / 1000.0).coerceAtLeast(0.0),
            trueHeadingDegrees = heading,
            elevationDegrees = elevation,
            triggerEpochMillis = System.currentTimeMillis(),
        )
        val target = Coordinate(lat, lng)
        if (state.activeAimingMode == AimingMode.PRECISE_COORDINATES) {
            val guidance = GuidanceMath.precise(snapshot, target, state.settings)
            Text("Target ${guidance.targetDistanceMetres.toInt()} m at ${guidance.requiredBearingDegrees.toInt()}°")
            Text("Turn ${guidance.turnSign} ${guidance.turnMetres.toInt()} m, ${guidance.tiltSign} ${guidance.alongMetres.toInt()} m")
            Text("Required elevation ${guidance.requiredElevationDegrees?.toInt() ?: "n/a"}°")
            if (guidance.outOfRange) Text("Target is outside the configured range.")
        } else {
            val latest = shots.lastOrNull { it.phase != ShotPhase.FAILED }
            val reveal = latest != null
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
            val guidance = GuidanceMath.approximate(origin, target, state.settings, reveal)
            Text("${guidance.leftRight} / ${guidance.addDrop}")
            Text(if (guidance.inZone) "Predicted impact is inside the guidance radius." else "Outside the guidance radius.")
            if (reveal) {
                Text("Correction ${guidance.crossMetres?.toInt()} m / ${guidance.alongMetres?.toInt()} m")
            }
        }
        Text("A misaligned shot still uses the actual sensors.")
    }
}

@Composable
private fun PermissionStatus() {
    val context = LocalContext.current
    val location = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val microphone = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    val notifications = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    if (!location) {
        Text("Precise location is denied. Firing stays blocked until a valid GPS fix is allowed.")
    }
    if (!microphone) {
        Text("Microphone is denied. Acoustic firing is unavailable; button firing can still be used.")
    }
    if (!notifications) {
        Text("Notifications are denied. Mailbox wake-ups will not appear until they are allowed.")
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
        Text("$title: ${label(values.first())}")
        return
    }
    Text(title, style = MaterialTheme.typography.titleSmall)
    values.forEach { value ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelected(value) },
        ) {
            RadioButton(selected = value == selected, onClick = { onSelected(value) })
            Text(label(value), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun ProtectedSettingsScreen(
    state: LocalState,
    onSave: (String, ProtectedSettingsUpdate) -> ProtectedResult,
    onChangePin: (String, String) -> ProtectedResult,
    onBack: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var firingModes by remember { mutableStateOf(state.settings.firingModes) }
    var aimingModes by remember { mutableStateOf(state.settings.aimingModes) }
    var radius by rememberSaveable {
        mutableStateOf(state.settings.approximateRadiusMetres.toString())
    }
    var elevation by rememberSaveable {
        mutableStateOf(state.settings.elevationReferenceDegrees.toString())
    }
    var mortarName by rememberSaveable { mutableStateOf(state.settings.mortarName) }
    var minElevation by rememberSaveable {
        mutableStateOf(state.settings.minElevationDegrees.toString())
    }
    var maxElevation by rememberSaveable {
        mutableStateOf(state.settings.maxElevationDegrees.toString())
    }
    var maxDistance by rememberSaveable {
        mutableStateOf(state.settings.maxDistanceMetres.toString())
    }
    var minDistance by rememberSaveable {
        mutableStateOf(state.settings.minDistanceMetres.toString())
    }
    var damageRadius by rememberSaveable {
        mutableStateOf(state.settings.damageRadiusMetres.toString())
    }
    var edgeProbability by rememberSaveable {
        mutableStateOf(state.settings.edgeProbability.toString())
    }
    var flightSeconds by rememberSaveable {
        mutableStateOf(state.settings.flightDurationSeconds.toString())
    }
    var ttlSeconds by rememberSaveable {
        mutableStateOf(state.settings.impactTtlSeconds.toString())
    }
    var gpsAccuracy by rememberSaveable {
        mutableStateOf(state.settings.maxLocationAccuracyMetres.toString())
    }
    var gpsAge by rememberSaveable {
        mutableStateOf(state.settings.maxLocationAgeSeconds.toString())
    }
    var acousticThreshold by rememberSaveable {
        mutableStateOf(state.settings.acousticThresholdDbfs.toString())
    }
    var acousticImpulse by rememberSaveable {
        mutableStateOf(state.settings.acousticImpulseMs.toString())
    }
    var acousticDebounce by rememberSaveable {
        mutableStateOf(state.settings.acousticDebounceMs.toString())
    }
    var mapId by rememberSaveable { mutableStateOf(state.context.mapId) }
    var teamId by rememberSaveable { mutableStateOf(state.context.teamId) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMap = state.activation.maps.single { it.id == mapId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Protected settings", style = MaterialTheme.typography.headlineSmall)
        Text("Enter the local PIN to save changes. The initial PIN is 4321.")
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
        OutlinedTextField(
            value = radius,
            onValueChange = { radius = it },
            label = { Text("Approximate radius (metres)") },
            modifier = Modifier.testTag("approximate-radius"),
        )
        OutlinedTextField(
            value = elevation,
            onValueChange = { elevation = it },
            label = { Text("Elevation reference (degrees)") },
            modifier = Modifier.testTag("elevation-reference"),
        )
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
        Text("Allowed map", style = MaterialTheme.typography.titleMedium)
        state.activation.maps.forEach { map ->
            RadioRow(map.name, map.id == mapId) {
                mapId = map.id
                teamId = map.teams.first().id
            }
        }
        Text("Allowed team on ${selectedMap.name}", style = MaterialTheme.typography.titleMedium)
        selectedMap.teams.forEach { team ->
            RadioRow(team.name, team.id == teamId) { teamId = team.id }
        }
        Button(
            modifier = Modifier.testTag("save-protected-settings"),
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
                message = protectedMessage(
                    onSave(pin, ProtectedSettingsUpdate(settings, mapId, teamId)),
                )
            },
        ) {
            Text("Save protected settings")
        }
        Text("Change PIN", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it },
            label = { Text("New PIN") },
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedButton(onClick = { message = protectedMessage(onChangePin(pin, newPin)) }) {
            Text("Change PIN")
        }
        message?.let { Text(it) }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun CalibrationScreen(
    settings: GameplaySettings,
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
    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Quick calibration", style = MaterialTheme.typography.titleLarge)
        Text("Calibration is available without the settings PIN. Nearby metal can make one heading inaccurate at other azimuths.")
        Text("Measured elevation ${pose.elevation?.toInt() ?: "n/a"}°, magnetic ${pose.magneticHeading?.toInt() ?: "n/a"}°, quality ${pose.quality}")
        Button(
            onClick = {
                val measured = pose.elevation
                if (measured == null) {
                    message = "Elevation is unavailable."
                    return@Button
                }
                onSave(
                    calibration.copy(
                        elevationOffsetDegrees = settings.elevationReferenceDegrees - measured,
                    ),
                )
                message = "Elevation now reads as ${settings.elevationReferenceDegrees.toInt()}°."
            },
            modifier = Modifier.testTag("save-calibration"),
        ) {
            Text("Calibrate elevation at ${settings.elevationReferenceDegrees.toInt()}°")
        }
        OutlinedTextField(knownLat, { knownLat = it }, label = { Text("Known-point latitude") })
        OutlinedTextField(knownLng, { knownLng = it }, label = { Text("Known-point longitude") })
        Button(
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
                message = "Direction offset saved from the known point."
            },
        ) {
            Text("Calibrate direction to known point")
        }
        message?.let { Text(it) }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun IconPickerScreen(
    repository: MortarRepository,
    onBack: () -> Unit,
) {
    val icons by repository.icons.collectAsState()
    var pin by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        runCatching { repository.refreshIcons() }
            .onFailure { message = "Icon catalog is unavailable. The cached icon remains usable." }
        loading = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Complete impact icon catalog", style = MaterialTheme.typography.headlineSmall)
        Text("Selection is PIN-protected. Icons are not limited to the team palette.")
        OutlinedTextField(
            pin,
            { pin = it },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
        )
        if (loading) CircularProgressIndicator()
        icons.forEach { icon ->
            IconRow(icon) {
                coroutineScope.launch {
                    runCatching { repository.selectIcon(pin, icon) }
                        .onSuccess { message = protectedMessage(it) }
                        .onFailure {
                            message = "Icon could not be cached. The previous icon is unchanged."
                        }
                }
            }
        }
        message?.let { Text(it) }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun IconRow(icon: CatalogIcon, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = icon.imageUrl,
            contentDescription = null,
            modifier = Modifier.height(48.dp),
        )
        Text(icon.title, modifier = Modifier.padding(top = 12.dp))
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
        Text(label, modifier = Modifier.padding(top = 12.dp))
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
        Text(label, modifier = Modifier.padding(top = 12.dp))
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
