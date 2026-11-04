package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.domain.model.NookiAppTheme

/**
 * CompositionLocal providing the currently selected [NookiAppTheme] across the UI tree,
 * including the 3D particle renderer and surface components.
 */
val LocalNookiAppTheme = staticCompositionLocalOf { NookiAppTheme.MIDNIGHT_COZY }

fun NookiAppTheme.toColorScheme(): ColorScheme {
    val bg = Color(backgroundDeepHex)
    val surfaceColor = Color(surfaceCardHex)
    val surfaceElevated = Color(surfaceElevatedHex)
    val primaryAccent = Color(primaryAccentHex)
    val secondaryAccent = Color(secondaryAccentHex)
    val tertiaryAlert = Color(tertiaryAlertHex)
    val textPrimary = Color(textPrimaryHex)
    val textMuted = Color(textMutedHex)

    return darkColorScheme(
        primary = primaryAccent,
        onPrimary = bg,
        primaryContainer = surfaceElevated,
        onPrimaryContainer = textPrimary,
        secondary = secondaryAccent,
        onSecondary = bg,
        secondaryContainer = surfaceColor,
        onSecondaryContainer = textPrimary,
        tertiary = tertiaryAlert,
        onTertiary = Color.White,
        background = bg,
        onBackground = textPrimary,
        surface = surfaceColor,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textMuted
    )
}

@Composable
fun MyApplicationTheme(
    appTheme: NookiAppTheme = NookiAppTheme.MIDNIGHT_COZY,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = appTheme.toColorScheme()

    CompositionLocalProvider(LocalNookiAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
