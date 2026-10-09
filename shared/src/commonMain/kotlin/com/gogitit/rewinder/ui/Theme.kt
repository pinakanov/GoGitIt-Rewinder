package com.gogitit.rewinder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Pure black surfaces and a green to teal glow, after Locally (LM Studio). */
object RewinderColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)
    val Accent = Color(0xFF8DC26F)

    val StartTint = Color(0xFF0F2A14)
    val StartText = Color(0xFF4CD964)
    val StopTint = Color(0xFF3A1310)
    val StopText = Color(0xFFFF453A)
}

val HomeGradient: Brush = Brush.verticalGradient(
    0.00f to Color(0xFF8DC26F),
    0.30f to Color(0xFF5C9A6C),
    0.55f to Color(0xFF2B5652),
    0.80f to Color(0xFF0E1414),
    1.00f to Color(0xFF000000),
)

@Composable
fun RewinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RewinderColors.Accent,
            onPrimary = Color.Black,
            background = RewinderColors.Background,
            onBackground = RewinderColors.TextPrimary,
            surface = RewinderColors.Surface,
            onSurface = RewinderColors.TextPrimary,
        ),
        content = content,
    )
}
