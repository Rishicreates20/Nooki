package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NookiNightIndigoColorScheme = darkColorScheme(
    primary = Marigold,
    onPrimary = NightIndigo,
    primaryContainer = NeelIndigo,
    onPrimaryContainer = WashedCotton,
    secondary = LeafGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF134E3A),
    onSecondaryContainer = WashedCotton,
    tertiary = Sindoor,
    onTertiary = Color.White,
    background = NightIndigo,
    onBackground = WashedCotton,
    surface = NeelIndigo,
    onSurface = WashedCotton,
    surfaceVariant = Color(0xFF252D6B),
    onSurfaceVariant = NookiMutedLavender
)

private val NookiWashedCottonColorScheme = lightColorScheme(
    primary = NeelIndigo,
    onPrimary = WashedCotton,
    primaryContainer = Color(0xFFDDE2F8),
    onPrimaryContainer = NightIndigo,
    secondary = LeafGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Sindoor,
    onTertiary = Color.White,
    background = WashedCotton,
    onBackground = NightIndigo,
    surface = Color.White,
    onSurface = NightIndigo,
    surfaceVariant = Color(0xFFE2E6F7),
    onSurfaceVariant = NeelIndigo
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Respects both dark (Night Indigo #12153A) and light (Washed Cotton #EEF0FA) themes
    val colorScheme = if (darkTheme) NookiNightIndigoColorScheme else NookiNightIndigoColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
