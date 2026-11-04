package com.example.presentation.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.provider.Settings

/** The left rail every item hangs from. */
val RailWidth = 48.dp
val RailCenter = 24.dp
private val StrandWidth = 3.dp

private fun DrawScope.strokeThread(path: Path, brush: Brush) {
    drawPath(path, brush, style = Stroke(width = StrandWidth.toPx(), cap = StrokeCap.Round))
    // The second ply of a twisted thread, like a kalava. Thin enough to read as texture.
    val dash = floatArrayOf(5.dp.toPx(), 8.dp.toPx())
    drawPath(
        path,
        Color.White.copy(alpha = 0.42f),
        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(dash))
    )
}

private fun DrawScope.drawRailSegment(start: Color, end: Color, bendRight: Boolean) {
    val x = RailCenter.toPx()
    val h = size.height
    val sway = (if (bendRight) 2.5.dp else (-2.5).dp).toPx()
    val path = Path().apply {
        moveTo(x, 0f)
        cubicTo(x + sway, h * 0.33f, x - sway, h * 0.66f, x, h)
    }
    strokeThread(path, Brush.verticalGradient(listOf(start, end)))
}

/**
 * A stretch of thread with something hanging from it. Rows meet edge to edge, so one thread appears
 * to run the whole length of the list, changing colour with the meaning of what hangs from it.
 */
@Composable
fun ThreadRow(
    color: Color,
    bendRight: Boolean,
    modifier: Modifier = Modifier,
    endColor: Color = color,
    minHeight: Dp = 0.dp,
    knot: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .drawBehind { drawRailSegment(color, endColor, bendRight) },
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(RailWidth), contentAlignment = Alignment.TopCenter) { knot?.invoke() }
        content()
    }
}

/**
 * A knot on the thread. Tapping unties it: it pulls loose, then [onUntie] runs. For a person using a
 * screen reader this is a plain button with [label].
 */
@Composable
fun Knot(
    color: Color,
    label: String,
    onUntie: () -> Unit,
    modifier: Modifier = Modifier
) {
    var untying by remember { mutableStateOf(false) }
    val pull by animateFloatAsState(
        targetValue = if (untying) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "knot_pull",
        finishedListener = { value -> if (value == 0f) onUntie() }
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp),
                role = Role.Checkbox
            ) { untying = true }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = pull
                    scaleY = pull
                    alpha = pull
                    rotationZ = (1f - pull) * 90f
                }
        ) { drawKnot(color) }
    }
}

/** A knot that only marks a place: a calendar event or a heading. */
@Composable
fun KnotMark(color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawKnot(color) }
    }
}

private fun DrawScope.drawKnot(color: Color) {
    val c = center
    val w = size.width
    rotate(degrees = -28f, pivot = c) {
        drawOval(color, topLeft = Offset(c.x - w * 0.44f, c.y - w * 0.31f), size = Size(w * 0.88f, w * 0.62f))
        // The turns of thread around the knot.
        val wrap = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        val shine = Color.White.copy(alpha = 0.62f)
        listOf(-0.15f, 0.12f).forEach { x ->
            val turn = Path().apply {
                moveTo(c.x + w * x, c.y - w * 0.23f)
                quadraticTo(c.x + w * (x + 0.15f), c.y, c.x + w * x, c.y + w * 0.23f)
            }
            drawPath(turn, shine, style = wrap)
        }
    }
}

private fun animationsEnabled(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/**
 * The one piece of motion the app plays on its own: when it opens, the thread falls from Nooki's
 * collar and swings across to the left rail. Everything else moves only when the person does.
 */
@Composable
fun ThreadSwoop(
    color: Color,
    fromRightInset: Dp,
    startY: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    var played by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { Animatable(if (played || inPreview || !animationsEnabled(context)) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, tween(durationMillis = 820, easing = FastOutSlowInEasing))
        played = true
    }
    Canvas(modifier) {
        val startX = size.width - fromRightInset.toPx()
        val endX = RailCenter.toPx()
        val top = startY.toPx()
        val drop = size.height - top
        // Both control points sit directly above and below their ends, so the thread leaves the bell
        // hanging straight down and reaches the rail the same way.
        val full = Path().apply {
            moveTo(startX, top)
            cubicTo(startX, top + drop * 0.85f, endX, top + drop * 0.30f, endX, size.height)
        }
        val measure = PathMeasure()
        measure.setPath(full, false)
        val partial = Path()
        measure.getSegment(0f, measure.length * reveal.value, partial, true)
        strokeThread(partial, Brush.linearGradient(listOf(color, color)))
    }
}
