package com.digitalsignage.tv.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val DarkTvColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepNavyBackground,
    primaryContainer = BrightBlue,
    onPrimaryContainer = TextPrimary,
    secondary = SkyGlow,
    onSecondary = DeepNavyBackground,
    background = DeepNavyBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardElevated,
    onSurfaceVariant = TextSecondary,
    error = StatusErrorRed,
    onError = TextPrimary
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DigitalSignageTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkTvColorScheme,
        content = content
    )
}
