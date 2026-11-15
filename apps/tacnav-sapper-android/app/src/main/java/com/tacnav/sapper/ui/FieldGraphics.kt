package com.tacnav.sapper.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val SkullRed = Color(0xFFD32F2F)
private val ScopeDark = Color(0xFF1C2416)
private val ScopeGreen = Color(0xFF8BC34A)

/**
 * A thick hold progress bar with the percentage written on it.
 */
@Composable
fun Progress(fraction: Float, tag: String, color: Color = Olive) {
    val value = fraction.coerceIn(0f, 1f)
    val percent = (value * 100).toInt()
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.copy(alpha = 0.15f))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(value)
                .background(color),
        )
        Text(
            "$percent%",
            color = if (value >= 0.5f) Ivory else Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("${tag}_percent"),
        )
    }
}

/** A red skull drawn in place, so it needs no icon library. */
@Composable
fun Skull(modifier: Modifier = Modifier, color: Color = SkullRed, holes: Color = Ivory) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = 0.36f * w, center = Offset(0.5f * w, 0.42f * h))
        drawRoundRect(
            color,
            topLeft = Offset(0.3f * w, 0.55f * h),
            size = Size(0.4f * w, 0.32f * h),
            cornerRadius = CornerRadius(0.06f * w),
        )
        drawCircle(holes, radius = 0.1f * w, center = Offset(0.36f * w, 0.44f * h))
        drawCircle(holes, radius = 0.1f * w, center = Offset(0.64f * w, 0.44f * h))
        val nose = Path().apply {
            moveTo(0.5f * w, 0.56f * h)
            lineTo(0.45f * w, 0.66f * h)
            lineTo(0.55f * w, 0.66f * h)
            close()
        }
        drawPath(nose, holes)
        listOf(0.42f, 0.5f, 0.58f).forEach { x ->
            drawLine(
                holes,
                start = Offset(x * w, 0.74f * h),
                end = Offset(x * w, 0.87f * h),
                strokeWidth = 0.03f * w,
            )
        }
    }
}

/** Shown on every screen that knows the phone stands inside a minefield. */
@Composable
fun MinefieldWarning() {
    val pulse = rememberInfiniteTransition(label = "skull")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "skull_scale",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .padding(16.dp)
            .testTag("inside_minefield"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Skull(Modifier.size(72.dp).scale(scale).testTag("skull"), holes = Ink)
        Text(
            "You are in a minefield",
            color = SkullRed,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A naval-style mine: a ball with spikes. */
@Composable
fun Mine(modifier: Modifier = Modifier, color: Color = Ivory) {
    Canvas(modifier) {
        val c = center
        val r = 0.28f * size.minDimension
        repeat(8) { index ->
            val angle = index * PI / 4
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color,
                start = Offset(c.x + dx * r, c.y + dy * r),
                end = Offset(c.x + dx * r * 1.55f, c.y + dy * r * 1.55f),
                strokeWidth = 0.07f * size.minDimension,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color, radius = r, center = c)
    }
}

/**
 * The detector display. Waves leave the phone's top edge, which is the way the
 * detector looks. It never shows where a field is.
 */
@Composable
fun DetectorScope(flashing: Boolean, label: String, tag: String) {
    val waves = rememberInfiniteTransition(label = "waves")
    val phase by waves.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
        label = "wave_phase",
    )
    val background = if (flashing) ScopeGreen else ScopeDark
    val ink = if (flashing) Ink else ScopeGreen
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .testTag(tag),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val origin = Offset(size.width / 2, size.height - 24.dp.toPx())
            val reach = size.height - 48.dp.toPx()
            drawArc(
                ink.copy(alpha = 0.12f),
                startAngle = -120f,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(origin.x - reach, origin.y - reach),
                size = Size(2 * reach, 2 * reach),
            )
            repeat(3) { index ->
                val p = (phase + index / 3f) % 1f
                val radius = p * reach
                drawArc(
                    ink.copy(alpha = 1f - p),
                    startAngle = -120f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(origin.x - radius, origin.y - radius),
                    size = Size(2 * radius, 2 * radius),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val phone = Size(18.dp.toPx(), 28.dp.toPx())
            drawRoundRect(
                ink,
                topLeft = Offset(origin.x - phone.width / 2, origin.y - phone.height / 2),
                size = phone,
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text("MINE DETECTOR", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(label, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Full-screen confirmation shown for a few seconds after a laying completes. */
@Composable
fun LaidConfirmation(onDismiss: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "laid")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "laid_scale",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.75f))
            .padding(24.dp)
            .testTag("laid_confirmation"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Olive)
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Mine(Modifier.size(120.dp).scale(scale))
            Text("Minefield laid", color = Ivory, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss, colors = textButtonColors()) {
                Text("OK", color = Ivory, fontSize = 18.sp)
            }
        }
    }
}
