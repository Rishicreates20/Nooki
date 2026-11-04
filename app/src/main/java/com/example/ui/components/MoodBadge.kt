package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.example.pet.PetMood

/**
 * A badge whose silhouette morphs between moods using androidx.graphics.shapes:
 * a soft pebble when sleeping, a rounded flower when happy, a perky rounded triangle when alert,
 * and a jittery many-pointed star when overwhelmed.
 */
@Composable
fun MoodBadge(
    mood: PetMood,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    var from by remember { mutableStateOf(mood) }
    var to by remember { mutableStateOf(mood) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(mood) {
        if (mood == to) return@LaunchedEffect
        from = to
        to = mood
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }

    val morph = remember(from, to) { Morph(from.polygon(), to.polygon()) }
    val tint by animateColorAsState(color, spring(stiffness = Spring.StiffnessMediumLow), label = "badge_tint")

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = (1f - progress.value) * -40f }
            .clip(MorphShape(morph, progress.value))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mood.glyph,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { rotationZ = (1f - progress.value) * 40f }
        )
    }
}

private val PetMood.glyph: String
    get() = when (this) {
        PetMood.SLEEPING -> "z"
        PetMood.HAPPY -> "♪"
        PetMood.ALERT -> "!"
        PetMood.OVERWHELMED -> "!!"
    }

private fun PetMood.polygon(): RoundedPolygon = when (this) {
    PetMood.SLEEPING -> RoundedPolygon(numVertices = 7, rounding = CornerRounding(0.9f, smoothing = 1f))
    PetMood.HAPPY -> RoundedPolygon.star(
        numVerticesPerRadius = 6,
        innerRadius = 0.78f,
        rounding = CornerRounding(0.32f, smoothing = 0.6f),
        innerRounding = CornerRounding(0.2f)
    )
    PetMood.ALERT -> RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.42f, smoothing = 0.4f))
    PetMood.OVERWHELMED -> RoundedPolygon.star(
        numVerticesPerRadius = 11,
        innerRadius = 0.72f,
        rounding = CornerRounding(0.08f),
        innerRounding = CornerRounding(0.04f)
    )
}

/** Shape backed by a [Morph] at [progress]; polygons are authored in the unit circle. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val matrix = android.graphics.Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, android.graphics.Path())
        matrix.reset()
        matrix.setScale(size.width / 2f, size.height / 2f)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}
