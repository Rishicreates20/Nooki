package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Named for what they are: indigo-dyed cloth, washed cotton, marigold, sindoor, a green leaf.
val Neel = Color(0xFF1C2254)
val NeelNight = Color(0xFF12153A)
val NeelRaised = Color(0xFF1E2356)
val NeelLine = Color(0xFF32386F)
val Cotton = Color(0xFFEEF0FA)
val CottonRaised = Color(0xFFFAFBFF)
val CottonLine = Color(0xFFD8DBEE)
val Marigold = Color(0xFFFFAE1A)
val Sindoor = Color(0xFFD7302F)
val SindoorBright = Color(0xFFFF7A6B)
val Leaf = Color(0xFF17885B)
val LeafBright = Color(0xFF55D6A2)
val IndigoThread = Color(0xFF4556D0)
val IndigoBright = Color(0xFF9AA6FF)

/** Colours that carry meaning. The thread and every label use these and nothing else. */
@Immutable
data class NookiColors(
    val missed: Color,
    val forgot: Color,
    val toDo: Color,
    val done: Color,
    val comingUp: Color,
    val muted: Color,
    val line: Color,
    val raised: Color
)

val LightNookiColors = NookiColors(
    missed = Sindoor,
    forgot = Color(0xFFB87400),
    toDo = IndigoThread,
    done = Leaf,
    comingUp = Color(0xFF7C82AD),
    muted = Color(0xFF545A85),
    line = CottonLine,
    raised = CottonRaised
)

val DarkNookiColors = NookiColors(
    missed = SindoorBright,
    forgot = Marigold,
    toDo = IndigoBright,
    done = LeafBright,
    comingUp = Color(0xFF7D84BC),
    muted = Color(0xFFADB2DA),
    line = NeelLine,
    raised = NeelRaised
)

val LocalNookiColors = staticCompositionLocalOf { LightNookiColors }

object NookiPalette {
    val colors: NookiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNookiColors.current
}
