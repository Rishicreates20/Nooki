package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The thread's colours, adjusted for light and dark so they keep their meaning and contrast. */
@Immutable
data class ThreadColors(
    val missed: Color,
    val forgot: Color,
    val todo: Color,
    val done: Color,
    val quiet: Color,
)

private val LightThread = ThreadColors(missed = Sindoor, forgot = Color(0xFFB86E00), todo = NeelIndigo, done = Leaf, quiet = CottonLine)
private val DarkThread = ThreadColors(missed = SindoorOnDark, forgot = MarigoldOnDark, todo = IndigoOnDark, done = LeafOnDark, quiet = IndigoLine)

val LocalThreadColors = staticCompositionLocalOf { LightThread }

private val LightColors = lightColorScheme(
    primary = NeelIndigo,
    onPrimary = WashedCotton,
    secondary = Marigold,
    onSecondary = NightIndigo,
    tertiary = Leaf,
    error = Sindoor,
    background = WashedCotton,
    onBackground = NeelIndigo,
    surface = CottonSurface,
    onSurface = NeelIndigo,
    surfaceVariant = Color(0xFFE2E5F5),
    onSurfaceVariant = IndigoMist,
    outline = CottonLine,
)

private val DarkColors = darkColorScheme(
    primary = IndigoOnDark,
    onPrimary = NightIndigo,
    secondary = Marigold,
    onSecondary = NightIndigo,
    tertiary = LeafOnDark,
    error = SindoorOnDark,
    background = NightIndigo,
    onBackground = WashedCotton,
    surface = NightSurface,
    onSurface = WashedCotton,
    surfaceVariant = Color(0xFF252A5C),
    onSurfaceVariant = Color(0xFFB4B9DE),
    outline = IndigoLine,
)

@Composable
fun NookiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalThreadColors provides if (darkTheme) DarkThread else LightThread) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
