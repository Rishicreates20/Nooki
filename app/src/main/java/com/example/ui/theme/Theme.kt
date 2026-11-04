package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NookiDarkColorScheme = darkColorScheme(
    primary = NookiAmberLight,
    onPrimary = NookiDeepInk,
    primaryContainer = Color(0xFF452708),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = NookiSkyInfo,
    onSecondary = NookiDeepInk,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = NookiCoralAccent,
    onTertiary = NookiDeepInk,
    background = NookiDarkBackground,
    onBackground = NookiCreamText,
    surface = NookiDarkSurface,
    onSurface = NookiCreamText,
    surfaceVariant = NookiDarkSurfaceVariant,
    onSurfaceVariant = NookiMutedLavender
)

private val NookiLightColorScheme = lightColorScheme(
    primary = NookiAmberPrimary,
    onPrimary = Color.White,
    primaryContainer = NookiLightSurfaceVariant,
    onPrimaryContainer = NookiDeepInk,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    tertiary = Color(0xFFE11D48),
    background = NookiLightBackground,
    onBackground = NookiDeepInk,
    surface = NookiLightSurface,
    onSurface = NookiDeepInk,
    surfaceVariant = Color(0xFFF3E8FF),
    onSurfaceVariant = Color(0xFF4C1D95)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Default to cozy twilight dark desk theme for Nooki
    val colorScheme = if (darkTheme) NookiDarkColorScheme else NookiDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
