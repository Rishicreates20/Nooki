package com.example.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.pet.PetMood
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val Fur = Color(0xFFEC8F2A)
private val FurDark = Color(0xFFD9771F)
private val EarInner = Color(0xFFF6A7A0)
private val Muzzle = Color(0xFFFFE2BF)
private val Blush = Color(0xCCF27C8A)
private val Ink = Color(0xFF241D4D)
private val Bell = Color(0xFF59C3EA)
private val BellShine = Color(0xFFD8F3FC)

/**
 * Nooki as the app icon draws her, with her face driven by [mood]. Everything is drawn on a 108 unit
 * grid, the same grid as the launcher icon, so the two can never drift apart.
 *
 * Blinking and a soft ear twitch are the only things that move on their own. Everything else answers
 * a tap or a real change of mood.
 */
@Composable
fun NookiFace(
    mood: PetMood,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    reaction: Int = 0
) {
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var blink by remember { mutableFloatStateOf(0f) }
    val still = LocalInspectionMode.current

    fun bounce() {
        scope.launch {
            hop.animateTo(-7f, spring(stiffness = Spring.StiffnessHigh))
            hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    LaunchedEffect(mood, reaction) { if (reaction > 0) bounce() }

    LaunchedEffect(mood, still) {
        if (still || mood == PetMood.SLEEPING) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2_800L, 6_500L))
            blink = 1f
            delay(120L)
            blink = 0f
        }
    }

    val tapModifier = if (onTap != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            bounce()
            onTap()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .semantics { contentDescription = "Nooki, ${mood.label.lowercase()}" }
            .then(tapModifier)
            .graphicsLayer { translationY = hop.value * density }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val u = size.minDimension / 108f
            drawNooki(mood, u, blink)
        }
    }
}

private fun DrawScope.drawNooki(mood: PetMood, u: Float, blink: Float) {
    val earDroop = when (mood) {
        PetMood.SLEEPING -> 12f
        PetMood.OVERWHELMED -> 18f
        PetMood.ALERT -> -3f
        PetMood.HAPPY -> 0f
    }
    fun p(x: Float, y: Float) = Offset(x * u, y * u)

    // Collar bell, where the thread starts.
    drawCircle(Bell, radius = 5.2f * u, center = p(54f, 83f))
    drawCircle(BellShine, radius = 1.7f * u, center = p(52.2f, 81.2f))

    rotate(degrees = -7f, pivot = p(54f, 58f)) {
        rotate(degrees = -earDroop, pivot = p(42f, 46f)) {
            drawPath(leftEar(u, outer = true), FurDark)
            drawPath(leftEar(u, outer = false), EarInner)
        }
        rotate(degrees = earDroop, pivot = p(66f, 46f)) {
            drawPath(mirror(leftEar(u, outer = true), u), FurDark)
            drawPath(mirror(leftEar(u, outer = false), u), EarInner)
        }
        drawHead(mood, u, blink)
    }
}

private fun DrawScope.drawHead(mood: PetMood, u: Float, blink: Float) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    drawOval(Fur, topLeft = p(30.5f, 37.5f), size = Size(47f * u, 41f * u))
    drawOval(Muzzle, topLeft = p(43.5f, 59.7f), size = Size(21f * u, 13.6f * u))
    drawOval(Blush, topLeft = p(35.8f, 61.3f), size = Size(7.4f * u, 4.4f * u))
    drawOval(Blush, topLeft = p(64.8f, 61.3f), size = Size(7.4f * u, 4.4f * u))
    drawOval(Color(0xFF7A3B2E), topLeft = p(51.3f, 60.7f), size = Size(5.4f * u, 3.8f * u))

    val line = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
    fun eye(cx: Float, cy: Float, r: Float) {
        drawOval(Ink, topLeft = p(cx - r, cy - r * 1.18f), size = Size(2 * r * u, 2.36f * r * u))
        drawCircle(Color.White, radius = r * 0.36f * u, center = p(cx + r * 0.3f, cy - r * 0.45f))
    }
    fun closedEye(cx: Float, cy: Float) {
        val arc = Path().apply {
            moveTo((cx - 3.6f) * u, cy * u)
            quadraticTo(cx * u, (cy + 3.2f) * u, (cx + 3.6f) * u, cy * u)
        }
        drawPath(arc, Ink, style = line)
    }

    when {
        mood == PetMood.SLEEPING || blink > 0.5f -> {
            closedEye(44.5f, 56f)
            closedEye(63.5f, 56f)
        }
        mood == PetMood.ALERT -> {
            eye(44.5f, 55.5f, 3.7f)
            eye(63.5f, 55.5f, 3.7f)
        }
        mood == PetMood.OVERWHELMED -> {
            eye(44.5f, 56.5f, 2.8f)
            eye(63.5f, 56.5f, 2.8f)
            val browL = Path().apply { moveTo(39.5f * u, 50f * u); lineTo(46.5f * u, 47f * u) }
            val browR = Path().apply { moveTo(68.5f * u, 50f * u); lineTo(61.5f * u, 47f * u) }
            drawPath(browL, Ink, style = line)
            drawPath(browR, Ink, style = line)
        }
        else -> {
            eye(44.5f, 56f, 3.1f)
            val wink = Path().apply {
                moveTo(59.2f * u, 57f * u)
                quadraticTo(63.5f * u, 52.4f * u, 67.8f * u, 57f * u)
            }
            drawPath(wink, Ink, style = line)
        }
    }

    val mouth = Path()
    when (mood) {
        PetMood.ALERT -> drawOval(Ink, topLeft = p(52f, 66.2f), size = Size(4f * u, 4f * u))
        PetMood.OVERWHELMED -> {
            mouth.moveTo(48.6f * u, 69f * u)
            mouth.quadraticTo(51.3f * u, 66.4f * u, 54f * u, 68.6f * u)
            mouth.quadraticTo(56.7f * u, 70.6f * u, 59.4f * u, 67.8f * u)
            drawPath(mouth, Ink, style = line)
        }
        else -> {
            mouth.moveTo(50.6f * u, 68.2f * u)
            mouth.quadraticTo(54f * u, 71.2f * u, 57.4f * u, 68.2f * u)
            drawPath(mouth, Ink, style = line)
        }
    }

    if (mood == PetMood.OVERWHELMED) {
        val drop = Path().apply {
            moveTo(75f * u, 42f * u)
            quadraticTo(79.5f * u, 49f * u, 75f * u, 50.5f * u)
            quadraticTo(70.5f * u, 49f * u, 75f * u, 42f * u)
        }
        drawPath(drop, Bell)
    }
    if (mood == PetMood.SLEEPING) {
        val zed = Stroke(width = 1.8f * u, cap = StrokeCap.Round)
        fun z(x: Float, y: Float, s: Float) {
            val path = Path().apply {
                moveTo(x * u, y * u)
                lineTo((x + s) * u, y * u)
                lineTo(x * u, (y + s) * u)
                lineTo((x + s) * u, (y + s) * u)
            }
            drawPath(path, Ink.copy(alpha = 0.55f), style = zed)
        }
        z(80f, 34f, 5f)
        z(89f, 24f, 3.6f)
    }
}

private fun leftEar(u: Float, outer: Boolean): Path = Path().apply {
    if (outer) {
        moveTo(35f * u, 46f * u)
        cubicTo(30f * u, 37f * u, 28f * u, 27f * u, 31f * u, 21.5f * u)
        cubicTo(33.5f * u, 17.5f * u, 38.5f * u, 18.5f * u, 42f * u, 22f * u)
        cubicTo(46f * u, 26f * u, 49.5f * u, 31.5f * u, 51f * u, 37f * u)
    } else {
        moveTo(37.5f * u, 41f * u)
        cubicTo(34.5f * u, 35f * u, 33.5f * u, 28.5f * u, 35f * u, 25f * u)
        cubicTo(36.5f * u, 22.5f * u, 39.5f * u, 23.5f * u, 41.5f * u, 26f * u)
        cubicTo(44f * u, 29f * u, 45.5f * u, 32f * u, 46f * u, 35f * u)
    }
    close()
}

/** The right ear is the left ear mirrored across the middle of the 108 unit grid. */
private fun mirror(path: Path, u: Float): Path = Path().apply {
    addPath(path)
    transform(
        androidx.compose.ui.graphics.Matrix().apply {
            translate(108f * u, 0f)
            scale(-1f, 1f)
        }
    )
}

