package com.tacnav.sapper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tacnav.sapper.activation.ActivationPayload
import com.tacnav.sapper.activation.ActivationPayloadParser
import com.tacnav.sapper.domain.CatalogIcon
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.security.LocalState

@Composable
fun SettingsScreen(
    local: LocalState?,
    loadIcons: suspend () -> List<CatalogIcon>,
    busy: Boolean,
    activationError: String?,
    onActivate: (ActivationPayload) -> Unit,
    onSave: (SapperSettings, Long, Long, String?) -> Unit,
    onResetConnection: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (local != null) {
            GameSettings(local, loadIcons, onSave, onResetConnection, onBack)
        }
        ManualActivation(busy, activationError, onActivate, reactivating = local != null)
        OutlinedButton(
            onClick = onBack,
            colors = secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_back"),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun ManualActivation(
    busy: Boolean,
    error: String?,
    onActivate: (ActivationPayload) -> Unit,
    reactivating: Boolean,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var local by remember { mutableStateOf<String?>(null) }
    Text(if (reactivating) "Activate again" else "Manual activation", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Server URL") },
        singleLine = true,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("server_url"),
    )
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("One-time code") },
        singleLine = true,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("one_time_code"),
    )
    OutlinedButton(
        onClick = {
            runCatching { ActivationPayloadParser.fromManual(serverUrl, code) }
                .onSuccess {
                    local = null
                    onActivate(it)
                }
                .onFailure { local = it.message }
        },
        enabled = !busy,
        colors = secondaryButtonColors(),
        modifier = Modifier.fillMaxWidth().testTag("activate_manual"),
    ) {
        Text(if (busy) "Activating…" else "Activate this phone")
    }
    (local ?: error)?.let { Notice(it, "activation_error", error = true) }
}

@Composable
private fun GameSettings(
    local: LocalState,
    loadIcons: suspend () -> List<CatalogIcon>,
    onSave: (SapperSettings, Long, Long, String?) -> Unit,
    onResetConnection: () -> Unit,
    onBack: () -> Unit,
) {
    val current = local.settings
    var radius by rememberSaveable { mutableStateOf(current.detectRadiusMetres.field()) }
    var sector by rememberSaveable { mutableStateOf(current.detectSectorDegrees.field()) }
    var markHold by rememberSaveable { mutableStateOf(current.markHoldSeconds.toString()) }
    var clearHold by rememberSaveable { mutableStateOf(current.clearHoldSeconds.toString()) }
    var layHold by rememberSaveable { mutableStateOf(current.layHoldSeconds.toString()) }
    var length by rememberSaveable { mutableStateOf(current.minefieldLengthMetres.field()) }
    var width by rememberSaveable { mutableStateOf(current.minefieldWidthMetres.field()) }
    var minefieldIcon by rememberSaveable { mutableStateOf(current.minefieldIconId) }
    var noMinesIcon by rememberSaveable { mutableStateOf(current.noMinesIconId) }
    var maxMinefields by rememberSaveable { mutableStateOf(current.maxMinefields.toString()) }
    var maxMarkers by rememberSaveable { mutableStateOf(current.maxNoMinesMarkers.toString()) }
    var gpsLimit by rememberSaveable { mutableStateOf(current.maxLocationAccuracyMetres.field()) }
    var mapId by rememberSaveable { mutableLongStateOf(local.context.mapId) }
    var teamId by rememberSaveable { mutableLongStateOf(local.context.teamId) }
    var newPin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var icons by remember { mutableStateOf(emptyList<CatalogIcon>()) }
    var iconError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { loadIcons() }
            .onSuccess { icons = it }
            .onFailure { iconError = "The icon list needs a connection." }
    }

    val maps = local.activation.maps
    val selectedMap = maps.firstOrNull { it.id == mapId }

    Text("Map", fontWeight = FontWeight.Bold)
    maps.forEach { map ->
        CheckRow(map.name, map.id == mapId) {
            mapId = map.id
            if (map.teams.none { team -> team.id == teamId }) {
                teamId = map.teams.firstOrNull()?.id ?: teamId
            }
        }
    }
    Text("Team", fontWeight = FontWeight.Bold)
    selectedMap?.teams?.forEach { team ->
        CheckRow(team.name, team.id == teamId) { teamId = team.id }
    }
    NumberField("Detection radius (m)", radius, { radius = it }, "detect_radius")
    NumberField("Heading sector (degrees)", sector, { sector = it }, "detect_sector")
    NumberField("Mark minefield hold (s)", markHold, { markHold = it }, "mark_hold")
    NumberField("Clear mines hold (s)", clearHold, { clearHold = it }, "clear_hold_seconds")
    NumberField("Lay mines hold (s)", layHold, { layHold = it }, "lay_hold_seconds")
    NumberField("Minefield length (m)", length, { length = it }, "minefield_length")
    NumberField("Minefield width (m)", width, { width = it }, "minefield_width")
    IconField("Minefield icon", minefieldIcon, icons, iconError, { minefieldIcon = it }, "minefield_icon")
    IconField("No mines icon", noMinesIcon, icons, iconError, { noMinesIcon = it }, "no_mines_icon")
    NumberField("Minefields per activation (0 = unlimited)", maxMinefields, { maxMinefields = it }, "max_minefields")
    NumberField("No mines markers per activation (0 = unlimited)", maxMarkers, { maxMarkers = it }, "max_markers")
    NumberField("GPS accuracy limit (m)", gpsLimit, { gpsLimit = it }, "gps_limit")
    OutlinedTextField(
        value = newPin,
        onValueChange = { newPin = it },
        label = { Text("New PIN (empty keeps the current one)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("new_pin"),
    )
    error?.let { Notice(it, "settings_error", error = true) }
    Button(
        colors = primaryButtonColors(),
        modifier = Modifier.fillMaxWidth().testTag("save_settings"),
        onClick = {
            runCatching {
                SapperSettings(
                    detectRadiusMetres = radius.number(),
                    detectSectorDegrees = sector.number(),
                    markHoldSeconds = markHold.whole(),
                    clearHoldSeconds = clearHold.whole(),
                    layHoldSeconds = layHold.whole(),
                    minefieldLengthMetres = length.number(),
                    minefieldWidthMetres = width.number(),
                    minefieldIconId = minefieldIcon,
                    noMinesIconId = noMinesIcon,
                    maxMinefields = maxMinefields.whole().toInt(),
                    maxNoMinesMarkers = maxMarkers.whole().toInt(),
                    maxLocationAccuracyMetres = gpsLimit.number(),
                ).validated()
            }.onSuccess { settings ->
                if (newPin.isNotBlank() && (newPin.length < 4 || !newPin.all(Char::isDigit))) {
                    error = "PIN must contain at least four digits."
                    return@onSuccess
                }
                runCatching { onSave(settings, mapId, teamId, newPin.ifBlank { null }) }
                    .onSuccess { onBack() }
                    .onFailure { error = it.message }
            }.onFailure {
                error = if (it is NumberFormatException) "Enter numbers only." else it.message
            }
        },
    ) {
        Text("Save")
    }
    Button(
        onClick = { confirmReset = true },
        colors = dangerButtonColors(),
        modifier = Modifier.fillMaxWidth().testTag("reset_connection"),
    ) {
        Text("Reset connection")
    }
    if (confirmReset) {
        ConfirmDialog(
            text = "This phone forgets its connection, settings, counters, and progress. " +
                "Scan a new QR code to connect again.",
            onConfirm = {
                confirmReset = false
                onResetConnection()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

private fun String.number(): Double = trim().toDouble()

private fun String.whole(): Long = trim().toLong()

private fun Double.field(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
