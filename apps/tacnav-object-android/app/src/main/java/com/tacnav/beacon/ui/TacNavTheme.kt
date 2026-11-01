package com.tacnav.beacon.ui

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Khaki = Color(0xFFD4CBB3)
val Olive = Color(0xFF364428)
val Ink = Color(0xFF1A1A1A)
val Ivory = Color(0xFFFFF9E8)
val Danger = Color(0xFF7A1F1F)

@Composable
fun TacNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Olive,
            onPrimary = Ivory,
            secondary = Olive,
            onSecondary = Ivory,
            tertiary = Olive,
            onTertiary = Ivory,
            background = Khaki,
            onBackground = Ink,
            surface = Khaki,
            onSurface = Ink,
            surfaceVariant = Color(0xFFC4B79A),
            onSurfaceVariant = Ink,
            outline = Ink.copy(alpha = 0.55f),
            outlineVariant = Ink.copy(alpha = 0.35f),
            error = Danger,
            onError = Ivory,
            inverseSurface = Olive,
            inverseOnSurface = Ivory,
            inversePrimary = Khaki,
            scrim = Ink,
        ),
        content = content,
    )
}

@Composable
fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Olive,
    contentColor = Ivory,
    disabledContainerColor = Olive.copy(alpha = 0.35f),
    disabledContentColor = Ivory.copy(alpha = 0.6f),
)

@Composable
fun dangerButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Danger,
    contentColor = Ivory,
)

@Composable
fun secondaryButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = Ink,
    disabledContentColor = Ink.copy(alpha = 0.4f),
)

@Composable
fun textButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = Ink,
    disabledContentColor = Ink.copy(alpha = 0.4f),
)

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    disabledTextColor = Ink.copy(alpha = 0.45f),
    focusedLabelColor = Olive,
    unfocusedLabelColor = Ink,
    disabledLabelColor = Ink.copy(alpha = 0.45f),
    cursorColor = Olive,
    focusedBorderColor = Olive,
    unfocusedBorderColor = Ink.copy(alpha = 0.55f),
    disabledBorderColor = Ink.copy(alpha = 0.25f),
)

@Composable
fun checkboxColors() = CheckboxDefaults.colors(
    checkedColor = Olive,
    uncheckedColor = Ink,
    checkmarkColor = Ivory,
)
