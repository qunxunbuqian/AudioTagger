package com.yangzhen.audiotagger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF0F3D3A),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEFEAE1),
    onSurfaceVariant = Color(0xFF4D4942),
    outline = Color(0xFF7D766B),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF422006),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF2E1065),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE7E3DC),
    surface = Color(0xFF191F1E),
    onSurface = Color(0xFFE7E3DC),
    surfaceVariant = Color(0xFF2A302E),
    onSurfaceVariant = Color(0xFFC9C4BA),
    outline = Color(0xFF938F86),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AudioTaggerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
