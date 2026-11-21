package com.step365.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lavender = Color(0xFFCCB6FF)
val SoftViolet = Color(0xFF9B85C7)
val CompletedGreen = Color(0xFF92D9AB)
val MissedRed = Color(0xFFE9A2AB)
val MutedText = Color(0xFFBFB8CD)
val PageBackground = Color(0xFF111017)
val CardBackground = Color(0xFF1D1A27)

private val StepColors = darkColorScheme(
    primary = Lavender,
    onPrimary = Color(0xFF302143),
    primaryContainer = Color(0xFF3A2D51),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = SoftViolet,
    background = PageBackground,
    onBackground = Color(0xFFF5F0FF),
    surface = CardBackground,
    onSurface = Color(0xFFF5F0FF),
    surfaceVariant = Color(0xFF292432),
    onSurfaceVariant = MutedText,
    outline = Color(0xFF786E88),
    error = MissedRed
)

@Composable
fun Step365Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StepColors, typography = Typography(), content = content)
}
