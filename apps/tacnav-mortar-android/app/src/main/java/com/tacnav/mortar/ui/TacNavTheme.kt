package com.tacnav.mortar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tacnav.mortar.R

val Khaki = Color(0xFFD4CBB3)
val Olive = Color(0xFF364428)
val Ink = Color(0xFF1A1A1A)
val Ivory = Color(0xFFFFF9E8)
val Murky = Color(0x266A5244)

@Composable
fun TacNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Olive,
            onPrimary = Ivory,
            background = Khaki,
            onBackground = Ink,
            surface = Khaki,
            onSurface = Ink,
            error = Color(0xFF7A1F1F),
        ),
        content = content,
    )
}

@Composable
fun TacNavChrome(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.topographic),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Khaki.copy(alpha = 0.82f)),
        )
        content()
    }
}

@Composable
fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Olive,
    contentColor = Ivory,
    disabledContainerColor = Olive.copy(alpha = 0.35f),
    disabledContentColor = Ivory.copy(alpha = 0.6f),
)
