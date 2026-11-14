package com.tacnav.sapper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tacnav.sapper.domain.CatalogIcon
import com.tacnav.sapper.security.PinAttempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A button that acts while it is held. Releasing it, or the pointer leaving the
 * screen, calls [onRelease].
 */
@Composable
fun HoldButton(
    label: String,
    enabled: Boolean,
    pressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    color: Color = Olive,
) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val background = when {
        !enabled -> color.copy(alpha = 0.35f)
        pressed -> color.copy(alpha = 0.75f)
        else -> color
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        press()
                        try {
                            tryAwaitRelease()
                        } finally {
                            release()
                        }
                    },
                )
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Progress(fraction: Float, tag: String) {
    LinearProgressIndicator(
        progress = { fraction },
        color = Olive,
        trackColor = Ink.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().height(10.dp).testTag(tag),
    )
}

@Composable
fun BackButton(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        colors = secondaryButtonColors(),
        modifier = Modifier.fillMaxWidth().testTag("back"),
    ) {
        Text("Back")
    }
}

@Composable
fun Notice(text: String, tag: String, error: Boolean = false) {
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else Ink,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

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
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pin_error"))
                }
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

@Composable
fun NumberField(label: String, value: String, onValue: (String) -> Unit, tag: String) {
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
fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, colors = checkboxColors())
        Text(label)
    }
}

@Composable
fun IconField(
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
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
