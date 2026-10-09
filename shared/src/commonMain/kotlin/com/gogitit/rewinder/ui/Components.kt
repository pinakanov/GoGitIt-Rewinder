package com.gogitit.rewinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Round icon button. glass = translucent white, used on top of the gradient. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    diameter: Dp = 48.dp,
) {
    val base = modifier
        .size(diameter)
        .clip(CircleShape)
        .background(if (glass) Color.White.copy(alpha = 0.14f) else RewinderColors.SurfaceHigh)
    Box(
        modifier = (if (glass) base.border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape) else base)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Big round button in the style of the iOS stopwatch (Lap / Stop).
 * [progress] draws an accent ring, used while Rewind is still locked.
 */
@Composable
fun StopwatchButton(
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: Float? = null,
    diameter: Dp = 88.dp,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            drawCircle(color = background)
            drawCircle(
                color = Color.Black,
                radius = size.minDimension / 2f - 3.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
            if (progress != null) {
                val stroke = 3.dp.toPx()
                drawArc(
                    color = RewinderColors.Accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** White pill, like Locally's "Chat" button. */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.22f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) Color.Black else Color.White.copy(alpha = 0.6f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Mirrored bar waveform from recent input levels (0..1). */
@Composable
fun Waveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.85f),
    bars: Int = 48,
) {
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val recent = levels.takeLast(bars)
        val padded = List(bars - recent.size) { 0f } + recent
        val minHeight = 3.dp.toPx()
        padded.forEachIndexed { i, level ->
            val h = max(minHeight, level * size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
