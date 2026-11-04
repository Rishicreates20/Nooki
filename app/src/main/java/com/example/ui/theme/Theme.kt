package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NookiDark = darkColorScheme(
    primary = Marigold,
    onPrimary = Neel,
    primaryContainer = Marigold,
    onPrimaryContainer = Neel,
    secondary = IndigoBright,
    onSecondary = NeelNight,
    secondaryContainer = NeelRaised,
    onSecondaryContainer = Cotton,
    tertiary = LeafBright,
    onTertiary = NeelNight,
    error = SindoorBright,
    onError = NeelNight,
    background = NeelNight,
    onBackground = Cotton,
    surface = NeelNight,
    onSurface = Cotton,
    surfaceVariant = NeelRaised,
    onSurfaceVariant = DarkNookiColors.muted,
    surfaceContainerLow = NeelRaised,
    surfaceContainer = NeelRaised,
    surfaceContainerHigh = NeelRaised,
    surfaceContainerHighest = NeelLine,
    outline = NeelLine,
    outlineVariant = NeelLine,
    scrim = Color(0xCC090B22)
)

private val NookiLight = lightColorScheme(
    primary = Marigold,
    onPrimary = Neel,
    primaryContainer = Marigold,
    onPrimaryContainer = Neel,
    secondary = IndigoThread,
    onSecondary = Color.White,
    secondaryContainer = CottonRaised,
    onSecondaryContainer = Neel,
    tertiary = Leaf,
    onTertiary = Color.White,
    error = Sindoor,
    onError = Color.White,
    background = Cotton,
    onBackground = Neel,
    surface = Cotton,
    onSurface = Neel,
    surfaceVariant = CottonRaised,
    onSurfaceVariant = LightNookiColors.muted,
    surfaceContainerLow = CottonRaised,
    surfaceContainer = CottonRaised,
    surfaceContainerHigh = CottonRaised,
    surfaceContainerHighest = CottonLine,
    outline = Color(0xFFB9BDD9),
    outlineVariant = CottonLine,
    scrim = Color(0x991C2254)
)

private val NookiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NookiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalNookiColors provides if (darkTheme) DarkNookiColors else LightNookiColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) NookiDark else NookiLight,
            typography = Typography,
            shapes = NookiShapes,
            content = content
        )
    }
}
