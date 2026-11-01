package com.tacnav.beacon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tacnav.beacon.activation.ActivationPayload
import com.tacnav.beacon.activation.ActivationPayloadParser
import com.tacnav.beacon.activation.QrScanner
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.LifeState
import com.tacnav.beacon.security.LocalState

/** One frame of what the main screen shows. */
data class MainUiState(
    val local: LocalState,
    val game: GameState,
    val canActivate: Boolean,
    val online: Boolean,
    val compassReliable: Boolean,
    val message: String?,
    val warnings: List<String>,
)

@Composable
fun ActivationScreen(
    busy: Boolean,
    error: String?,
    onRequestCamera: () -> Unit,
    cameraGranted: Boolean,
    onActivate: (ActivationPayload) -> Unit,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var local by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tac Nav Object", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Scan the organizer's QR code, or type the server address and one-time code.")
        if (scanning && cameraGranted) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                QrScanner(
                    onPayload = {
                        scanning = false
                        onActivate(it)
                    },
                    onError = { local = it },
                )
            }
        } else {
            Button(
                onClick = {
                    if (!cameraGranted) onRequestCamera()
                    scanning = true
                },
                enabled = !busy,
                colors = primaryButtonColors(),
                modifier = Modifier.fillMaxWidth().testTag("scan_qr"),
            ) {
                Text("Scan QR code")
            }
        }
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
        (local ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun MainScreen(
    ui: MainUiState,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onArm: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val game = ui.game
    val settings = game.session?.settings ?: ui.local.settings
    val map = ui.local.activation.maps.firstOrNull { it.id == ui.local.context.mapId }
    val team = map?.teams?.firstOrNull { it.id == ui.local.context.teamId }
    val fix = game.lastFix
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(settings.objectName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("${map?.name.orEmpty()} · ${team?.name.orEmpty()}")
        Text(
            StatusText.life(game),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = if (game.life == LifeState.DESTROYED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.testTag("life_state"),
        )
        Text(
            "Hits: ${StatusText.hits(game, settings)}",
            fontSize = 20.sp,
            modifier = Modifier.testTag("hit_count"),
        )
        if (game.life == LifeState.ACTIVE) {
            Text(
                "Active for ${StatusText.clock(game.sessionMillis)}",
                fontSize = 20.sp,
                modifier = Modifier.testTag("session_timer"),
            )
        }
        if (game.life == LifeState.DEPLOYING) {
            val total = (settings.activationSeconds * 1_000).coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (game.deployedMillis.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).testTag("deploy_progress"),
            )
        }
        StatusText.downtime(game, settings)?.let {
            Text(
                it,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("downtime_left"),
            )
        }
        StatusText.fullReset(game, settings)?.let {
            Text(it, modifier = Modifier.testTag("full_reset"))
        }
        if (game.life == LifeState.WAITING && settings.autoActivateEnabled) {
            Text(
                if (game.armed) {
                    "Activates after ${settings.autoActivateSeconds} s without moving " +
                        "(${game.stillMillis / 1_000} s so far)"
                } else {
                    "Automatic activation is off until you press Arm"
                },
                textAlign = TextAlign.Center,
            )
        }
        Text(
            if (fix == null) {
                "Waiting for GPS"
            } else {
                "GPS ±${fix.accuracyMetres.toInt()} m, limit " +
                    "${settings.maxLocationAccuracyMetres.toInt()} m"
            },
            modifier = Modifier.testTag("gps_status"),
        )
        if (settings.sizeAMetres != null && settings.sizeBMetres != null && !ui.compassReliable) {
            Text(
                "The compass is unreliable, so the rectangle may be misaligned.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (!ui.online) {
            Text("No connection. The map updates when it returns.", textAlign = TextAlign.Center)
        }
        ui.warnings.forEach { Text(it, textAlign = TextAlign.Center) }
        ui.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        when (game.life) {
            LifeState.WAITING -> {
                Button(
                    onClick = onActivate,
                    enabled = ui.canActivate,
                    colors = primaryButtonColors(),
                    modifier = Modifier.fillMaxWidth().height(64.dp).testTag("activate"),
                ) {
                    Text("Activate", fontSize = 20.sp)
                }
                if (settings.autoActivateEnabled && !game.armed) {
                    OutlinedButton(
                        onClick = onArm,
                        colors = secondaryButtonColors(),
                        modifier = Modifier.fillMaxWidth().testTag("arm"),
                    ) {
                        Text("Arm")
                    }
                }
            }
            LifeState.DEPLOYING, LifeState.ACTIVE -> Button(
                onClick = onDeactivate,
                colors = dangerButtonColors(),
                modifier = Modifier.fillMaxWidth().height(64.dp).testTag("deactivate"),
            ) {
                Text("Deactivate", fontSize = 20.sp)
            }
            LifeState.DESTROYED -> Text(
                "Reset this object in Settings, or activate it again with a new QR code.",
                textAlign = TextAlign.Center,
            )
        }
        TextButton(
            onClick = onOpenSettings,
            colors = textButtonColors(),
            modifier = Modifier.testTag("settings"),
        ) {
            Text("Settings")
        }
    }
}
