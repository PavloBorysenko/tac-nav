package com.tacnav.beacon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tacnav.beacon.domain.CatalogIcon
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.domain.ObjectVisibility
import com.tacnav.beacon.security.LocalState
import com.tacnav.beacon.security.PinAttempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PinDialog(
    onVerify: (String) -> PinAttempt,
    onAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = fieldColors(),
                    modifier = Modifier.testTag("pin_field"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !checking,
                colors = textButtonColors(),
                modifier = Modifier.testTag("pin_confirm"),
                onClick = {
                    checking = true
                    scope.launch {
                        val attempt = withContext(Dispatchers.Default) { onVerify(pin) }
                        checking = false
                        when (attempt) {
                            PinAttempt.Accepted -> onAccepted()
                            is PinAttempt.Rejected -> error =
                                "Wrong PIN. ${attempt.attemptsRemaining} attempts remain."
                            is PinAttempt.Throttled -> error =
                                "Too many attempts. Try again in ${attempt.retryAfterMillis / 1_000} s."
                        }
                    }
                },
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = textButtonColors()) { Text("Cancel") }
        },
    )
}

private fun String.metres(): Double? = trim().takeIf(String::isNotEmpty)?.toDouble()

private fun String.whole(): Long? = trim().takeIf(String::isNotEmpty)?.toLong()

private fun Double?.field(): String = when {
    this == null || this == 0.0 -> ""
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, colors = checkboxColors())
        Text(label)
    }
}

@Composable
private fun IconField(
    label: String,
    selected: Long?,
    icons: List<CatalogIcon>,
    error: String?,
    onSelected: (Long?) -> Unit,
    tag: String,
) {
    var open by remember { mutableStateOf(false) }
    val title = selected?.let { id -> icons.firstOrNull { it.id == id }?.title ?: "Icon $id" }
    OutlinedButton(
        onClick = { open = true },
        colors = secondaryButtonColors(),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Text("$label: ${title ?: "Default marker"}")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("$label (${icons.size})") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        Text(
                            error ?: "Default marker",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(null)
                                    open = false
                                }
                                .padding(12.dp),
                        )
                    }
                    items(icons, key = CatalogIcon::id) { icon ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(icon.id)
                                    open = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = icon.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(icon.title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }, colors = textButtonColors()) { Text("Close") }
            },
        )
    }
}

@Composable
fun SettingsScreen(
    local: LocalState,
    sessionRunning: Boolean,
    loadIcons: suspend () -> List<CatalogIcon>,
    onSave: (ObjectSettings, Long, Long, String?) -> Unit,
    onReset: () -> Unit,
    onReactivate: () -> Unit,
    onBack: () -> Unit,
) {
    val current = local.settings
    var name by rememberSaveable { mutableStateOf(current.objectName) }
    var description by rememberSaveable { mutableStateOf(current.description) }
    var iconId by rememberSaveable { mutableStateOf(current.iconId) }
    var destroyedIconId by rememberSaveable { mutableStateOf(current.destroyedIconId) }
    var hits by rememberSaveable { mutableStateOf(current.hitsToDestroy.toString()) }
    var sizeA by rememberSaveable { mutableStateOf(current.sizeAMetres.field()) }
    var sizeB by rememberSaveable { mutableStateOf(current.sizeBMetres.field()) }
    var deploy by rememberSaveable { mutableStateOf(current.activationSeconds.toString()) }
    var visibleToAll by rememberSaveable {
        mutableStateOf(current.visibility == ObjectVisibility.ALL)
    }
    var area by rememberSaveable { mutableStateOf(current.randomAreaEnabled) }
    var areaRadius by rememberSaveable { mutableStateOf(current.randomAreaRadiusMetres.field()) }
    var gpsLimit by rememberSaveable { mutableStateOf(current.maxLocationAccuracyMetres.field()) }
    var deactivate by rememberSaveable { mutableStateOf(current.autoDeactivateMetres.field()) }
    var automatic by rememberSaveable { mutableStateOf(current.autoActivateEnabled) }
    var stillSeconds by rememberSaveable { mutableStateOf(current.autoActivateSeconds.toString()) }
    var downtime by rememberSaveable {
        mutableStateOf(current.maxDowntimeMinutes?.toString().orEmpty())
    }
    var downtimeReset by rememberSaveable {
        mutableStateOf(current.downtimeResetMinutes?.toString().orEmpty())
    }
    var mapId by rememberSaveable { mutableStateOf(local.context.mapId) }
    var teamId by rememberSaveable { mutableStateOf(local.context.teamId) }
    var newPin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmReactivate by remember { mutableStateOf(false) }
    var icons by remember { mutableStateOf(emptyList<CatalogIcon>()) }
    var iconError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { loadIcons() }
            .onSuccess { icons = it }
            .onFailure { iconError = "The icon list needs a connection." }
    }

    val maps = local.activation.maps
    val selectedMap = maps.firstOrNull { it.id == mapId }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (sessionRunning) {
            Text("Changes apply from the next activation.")
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Object name") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("object_name"),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
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
        IconField("Icon", iconId, icons, iconError, { iconId = it }, "icon_field")
        IconField(
            "Destroyed icon",
            destroyedIconId,
            icons,
            iconError,
            { destroyedIconId = it },
            "destroyed_icon_field",
        )
        NumberField("Hits to destroy (0 = never)", hits, { hits = it }, "hits_to_destroy")
        NumberField("Size along the phone top (m)", sizeA, { sizeA = it }, "size_a")
        NumberField("Size across the phone (m)", sizeB, { sizeB = it }, "size_b")
        Text("Both empty: a point. One filled: a circle with that radius. Both filled: a rectangle.")
        NumberField("Deployment seconds (0 = at once)", deploy, { deploy = it }, "deploy_seconds")
        CheckRow("Visible to everyone", visibleToAll) { visibleToAll = it }
        CheckRow("Random area", area) { area = it }
        NumberField("Random area radius (m)", areaRadius, { areaRadius = it }, "area_radius")
        NumberField("GPS accuracy limit (m)", gpsLimit, { gpsLimit = it }, "gps_limit")
        NumberField("Automatic deactivation (m)", deactivate, { deactivate = it }, "auto_deactivate")
        Text("Must be at least twice the GPS accuracy limit, or GPS drift causes false triggers.")
        CheckRow("Automatic activation", automatic) { automatic = it }
        NumberField("Still seconds", stillSeconds, { stillSeconds = it }, "still_seconds")
        NumberField("Downtime allowance (minutes)", downtime, { downtime = it }, "downtime")
        NumberField("Downtime reset (minutes)", downtimeReset, { downtimeReset = it }, "downtime_reset")
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            colors = primaryButtonColors(),
            modifier = Modifier.fillMaxWidth().testTag("save_settings"),
            onClick = {
                runCatching {
                    ObjectSettings(
                        objectName = name.trim(),
                        description = description.trim(),
                        iconId = iconId,
                        destroyedIconId = destroyedIconId,
                        hitsToDestroy = (hits.whole() ?: 0).toInt(),
                        sizeAMetres = sizeA.metres()?.takeIf { it > 0 },
                        sizeBMetres = sizeB.metres()?.takeIf { it > 0 },
                        activationSeconds = deploy.whole() ?: 0,
                        visibility = if (visibleToAll) {
                            ObjectVisibility.ALL
                        } else {
                            ObjectVisibility.TEAM
                        },
                        randomAreaEnabled = area,
                        randomAreaRadiusMetres = areaRadius.metres() ?: 0.0,
                        autoDeactivateMetres = deactivate.metres() ?: 0.0,
                        maxLocationAccuracyMetres = gpsLimit.metres() ?: 15.0,
                        autoActivateEnabled = automatic,
                        autoActivateSeconds = stillSeconds.whole() ?: 0,
                        maxDowntimeMinutes = downtime.whole()?.takeIf { it > 0 },
                        downtimeResetMinutes = downtimeReset.whole(),
                    ).validated()
                }.onSuccess { settings ->
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
        OutlinedButton(
            onClick = onBack,
            colors = secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_back"),
        ) {
            Text("Back")
        }
        Button(
            onClick = { confirmReset = true },
            colors = dangerButtonColors(),
            modifier = Modifier.fillMaxWidth().testTag("protected_reset"),
        ) {
            Text("Reset object")
        }
        OutlinedButton(
            onClick = { confirmReactivate = true },
            colors = secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Activate again with a new code")
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            text = "This clears hits, destroyed state, and downtime. Wrecks stay on the map.",
            onConfirm = {
                confirmReset = false
                onReset()
                onBack()
            },
            onDismiss = { confirmReset = false },
        )
    }
    if (confirmReactivate) {
        ConfirmDialog(
            text = "This phone leaves the game until a new staff code activates it.",
            onConfirm = {
                confirmReactivate = false
                onReactivate()
            },
            onDismiss = { confirmReactivate = false },
        )
    }
}

@Composable
private fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = textButtonColors(),
                modifier = Modifier.testTag("confirm_yes"),
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = textButtonColors()) { Text("Cancel") }
        },
    )
}
