package com.tacnav.sapper.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tacnav.sapper.activation.ActivationPayload
import com.tacnav.sapper.activation.QrScanner
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.game.DetectorLevel
import com.tacnav.sapper.game.Mode
import com.tacnav.sapper.game.SapperActions
import com.tacnav.sapper.game.SapperController
import com.tacnav.sapper.game.SapperView
import kotlinx.coroutines.delay

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun HomeScreen(
    connected: Boolean,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onMode: (Mode) -> Unit,
) {
    Page("Tac Nav Sapper") {
        if (!connected) {
            Text("Scan the organizer's QR code to connect this phone.")
            BigButton("Scan QR", "scan_qr", onScan)
            OutlinedButton(
                onClick = onSettings,
                colors = secondaryButtonColors(),
                modifier = Modifier.fillMaxWidth().testTag("settings"),
            ) {
                Text("Settings")
            }
        } else {
            BigButton("Detect", "mode_detect") { onMode(Mode.DETECT) }
            BigButton("Lay mines", "mode_lay") { onMode(Mode.LAY) }
            BigButton("Clear mines", "mode_clear") { onMode(Mode.CLEAR) }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onSettings,
                colors = secondaryButtonColors(),
                modifier = Modifier.testTag("settings"),
            ) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun BigButton(label: String, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = primaryButtonColors(),
        modifier = Modifier.fillMaxWidth().height(72.dp).testTag(tag),
    ) {
        Text(label, fontSize = 20.sp)
    }
}

@Composable
fun ScanScreen(
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    busy: Boolean,
    error: String?,
    onPayload: (ActivationPayload) -> Unit,
    onBack: () -> Unit,
) {
    var local by remember { mutableStateOf<String?>(null) }
    Page("Scan QR") {
        if (cameraGranted) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                if (!busy) QrScanner(onPayload = onPayload, onError = { local = it })
            }
        } else {
            Text("Allow the camera to scan the activation code.")
            Button(onClick = onRequestCamera, colors = primaryButtonColors()) { Text("Allow camera") }
        }
        if (busy) Text("Activating…")
        (local ?: error)?.let { Notice(it, "scan_error", error = true) }
        BackButton(onBack)
    }
}

/** Warnings shared by every mode screen. */
@Composable
private fun StatusLines(view: SapperView, locationGranted: Boolean, gpsNeeded: Boolean) {
    if (!locationGranted) Notice("Allow precise location. Sapper cannot work without it.", "warn_location", true)
    if (!view.online) Notice("Offline. Last results are still checked; changes are sent when the network returns.", "warn_offline")
    when {
        !view.hasFix -> Notice("Waiting for GPS…", "warn_gps")
        !view.gpsUsable -> Notice(
            if (gpsNeeded) {
                "GPS is not accurate enough. Wait for a better fix."
            } else {
                "GPS is not accurate enough. Detection still signals; marking and No mines wait for a better fix."
            },
            "warn_gps",
        )
    }
    if (!view.headingKnown || !view.compassReliable) {
        Notice("Compass is unreliable. Move the phone in a figure eight.", "warn_compass")
    }
    if (view.pendingWrites > 0) Notice("Waiting to send ${view.pendingWrites} change(s).", "pending")
}

@Composable
fun DetectScreen(
    view: SapperView,
    locationGranted: Boolean,
    actions: SapperActions,
    onBack: () -> Unit,
) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(view.flashSerial) {
        if (view.flashSerial == 0) return@LaunchedEffect
        flashing = true
        delay(300)
        flashing = false
    }
    Page("Detect") {
        StatusLines(view, locationGranted, gpsNeeded = false)
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (flashing) Ivory else Olive)
                .testTag(if (flashing) "flash_on" else "flash_off"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (view.level == DetectorLevel.INSIDE) "Minefield" else "Searching",
                color = if (flashing) Ink else Ivory,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (view.markAvailable) {
            val pulse = rememberInfiniteTransition(label = "mark")
            val alpha by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 0.45f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "mark_alpha",
            )
            HoldButton(
                label = "Hold to mark minefield",
                enabled = view.gpsUsable,
                pressed = view.markPressed,
                onPress = actions::pressMark,
                onRelease = actions::releaseMark,
                tag = "mark_minefield",
                color = Danger,
                modifier = Modifier.alpha(if (view.markPressed) 1f else alpha),
            )
            Progress(view.markFraction, "mark_progress")
        }
        if (view.noMinesOffered) NoMinesButton(view, actions)
        view.message?.let { Notice(it, "message") }
        BackButton(onBack)
    }
}

@Composable
private fun NoMinesButton(view: SapperView, actions: SapperActions) {
    val left = view.noMinesLeft
    OutlinedButton(
        onClick = actions::placeNoMines,
        enabled = view.gpsUsable && left != 0,
        colors = secondaryButtonColors(),
        modifier = Modifier.fillMaxWidth().testTag("no_mines"),
    ) {
        Text(if (left == null) "No mines" else "No mines ($left left)")
    }
    if (left == 0) Notice("No markers left for this activation.", "no_mines_limit")
}

@Composable
fun LayScreen(
    view: SapperView,
    settings: SapperSettings,
    locationGranted: Boolean,
    actions: SapperActions,
    onBack: () -> Unit,
) {
    val left = view.minefieldsLeft
    val locked = view.laying?.locked == true
    Page("Lay mines") {
        StatusLines(view, locationGranted, gpsNeeded = true)
        Text(
            "Hold for ${settings.layHoldSeconds} s in one place. The minefield is " +
                "${settings.minefieldLengthMetres.trimmed()} by ${settings.minefieldWidthMetres.trimmed()} m, " +
                "its length along the phone top.",
        )
        if (left != null) Notice("Minefields left: $left", "minefields_left")
        HoldButton(
            label = if (view.laying == null) "Hold to lay mines" else "Hold to continue",
            enabled = view.gpsUsable && left != 0 && !locked,
            pressed = view.layPressed,
            onPress = actions::pressLay,
            onRelease = actions::releaseLay,
            tag = "lay_hold",
        )
        Progress(view.layFraction, "lay_progress")
        if (left == 0 && view.laying == null) Notice("No minefields left for this activation.", "lay_limit", true)
        view.message?.let { Notice(it, "message") }
        if (view.laying != null) {
            OutlinedButton(
                onClick = actions::resetLaying,
                colors = secondaryButtonColors(),
                modifier = Modifier.fillMaxWidth().testTag("reset_laying"),
            ) {
                Text("Reset laying")
            }
        }
        BackButton(onBack)
    }
}

@Composable
fun ClearScreen(
    view: SapperView,
    locationGranted: Boolean,
    actions: SapperActions,
    onBack: () -> Unit,
) {
    Page("Clear mines") {
        StatusLines(view, locationGranted, gpsNeeded = true)
        when {
            view.safe -> {
                Text(
                    SapperController.SAFE,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E5A1C),
                    modifier = Modifier.testTag("safe"),
                )
                NoMinesButton(view, actions)
            }
            !view.clearSearched -> Notice("Searching for minefields…", "clear_searching")
            view.clearTargetId == null -> Notice("No minefield here.", "clear_none")
            else -> {
                HoldButton(
                    label = "Hold to clear mines",
                    enabled = view.gpsUsable,
                    pressed = view.clearPressed,
                    onPress = actions::pressClear,
                    onRelease = actions::releaseClear,
                    tag = "clear_hold",
                )
                Progress(view.clearFraction, "clear_progress")
            }
        }
        view.message?.takeIf { !view.safe || it != SapperController.SAFE }?.let { Notice(it, "message") }
        BackButton(onBack)
    }
}

private fun Double.trimmed(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
