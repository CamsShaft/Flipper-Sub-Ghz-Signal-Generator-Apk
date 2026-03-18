package com.subghz.signalgenerator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SubGhzColorScheme = darkColorScheme(
    primary = FlipperOrange,
    onPrimary = Color.Black,
    primaryContainer = FlipperOrangeDark,
    onPrimaryContainer = FlipperOrangeLight,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D60),
    onSecondaryContainer = CyanAccent,
    tertiary = GreenSignal,
    onTertiary = Color.Black,
    error = RedSignal,
    onError = Color.Black,
    background = SurfaceBlack,
    onBackground = TextPrimary,
    surface = SurfaceBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    inverseSurface = TextPrimary,
    inverseOnSurface = SurfaceBlack,
)

@Composable
fun SubGhzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SubGhzColorScheme,
        typography = Typography,
        content = content
    )
}
