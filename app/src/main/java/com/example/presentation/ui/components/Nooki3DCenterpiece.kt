package com.example.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.NookiPetMood
import com.example.ui.theme.NookiCoralAccent
import com.example.ui.theme.NookiSkyInfo
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive 3D-shaded Nooki Desk-Pet model rendered at the exact center of the viewport.
 * Features:
 * - Multi-layered 3D perspective parallax (Yaw & Pitch) via finger drag or smooth auto-orbit
 * - Volumetric radial & specular 3D shading on the floating desk pedestal, body, snout, ears, paws, and bell
 * - Reactive animations driven by [NookiPetMood]
 */
@Composable
fun Nooki3DCenterpiece(
    mood: NookiPetMood,
    speechText: String,
    isAutoRotateEnabled: Boolean,
    petCombo: Int,
    onPetTap: () -> Unit,
    onToggleAutoRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    // User drag angles in degrees (-45f..45f yaw, -25f..25f pitch)
    var userYaw by remember { mutableFloatStateOf(0f) }
    var userPitch by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "nooki_3d_stage")

    // Smooth 3D orbital angle when auto-rotate is enabled
    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nooki_3d_orbit"
    )

    // Breathing / levitation float
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = mood.breathingSpeedMs,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nooki_3d_breath"
    )

    // Bell shake when ringing
    val bellJingleDeg by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == NookiPetMood.EXCITED_RINGING) 140 else 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nooki_3d_bell"
    )

    val animatedYaw by animateFloatAsState(
        targetValue = if (isAutoRotateEnabled) {
            userYaw + sin(orbitPhase) * 20f
        } else {
            userYaw
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "yaw_spring"
    )

    val animatedPitch by animateFloatAsState(
        targetValue = if (isAutoRotateEnabled) {
            userPitch + cos(orbitPhase) * 8f
        } else {
            userPitch
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pitch_spring"
    )

    val auraColor by animateColorAsState(
        targetValue = mood.primaryAuraColor,
        animationSpec = tween(500),
        label = "aura_color"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Floating 3D Speech Bubble above Nooki
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = mood.displayTitle.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = auraColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = speechText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Centered 3D Model Viewport
        Box(
            modifier = Modifier
                .size(290.dp)
                .testTag("nooki_3d_center_model")
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Gently spring back toward center after manual inspection
                            userYaw *= 0.35f
                            userPitch *= 0.35f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        userYaw = (userYaw + dragAmount.x * 0.25f).coerceIn(-42f, 42f)
                        userPitch = (userPitch - dragAmount.y * 0.20f).coerceIn(-24f, 24f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPetTap() },
                        onDoubleTap = {
                            userYaw = 0f
                            userPitch = 0f
                            onPetTap()
                        }
                    )
                }
                .graphicsLayer {
                    rotationY = animatedYaw * 0.45f
                    rotationX = animatedPitch * 0.45f
                    cameraDistance = 14f * density
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Convert yaw/pitch into 3D parallax pixel offsets for depth layers
                val parallaxX = (animatedYaw / 42f) * 24.dp.toPx()
                val parallaxY = -(animatedPitch / 24f) * 14.dp.toPx()
                val jumpOffset = if (mood == NookiPetMood.EXCITED_RINGING || mood == NookiPetMood.CELEBRATING_XP) {
                    breathProgress * 12.dp.toPx()
                } else {
                    breathProgress * 6.dp.toPx()
                }

                val petCenterY = cy - 6.dp.toPx() + jumpOffset

                // 1. Volumetric 3D Aura Halo behind the 3D model
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor.copy(alpha = 0.42f),
                            auraColor.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(cx - parallaxX * 0.3f, petCenterY),
                        radius = 135.dp.toPx()
                    ),
                    radius = 135.dp.toPx(),
                    center = Offset(cx - parallaxX * 0.3f, petCenterY)
                )

                // 2. Floating 3D Isometric Desk Island Pedestal (Bottom Layer)
                val islandTopY = cy + 84.dp.toPx() + parallaxY * 0.25f
                val islandWidth = 216.dp.toPx()
                val islandHeight = 46.dp.toPx()

                // Pedestal lower 3D bevel/extrusion
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2A1810), Color(0xFF140B07))
                    ),
                    topLeft = Offset(cx - islandWidth / 2f, islandTopY - 6.dp.toPx()),
                    size = Size(islandWidth, islandHeight + 14.dp.toPx())
                )
                // Pedestal top wooden desk surface with 3D radial highlight
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6D4C41),
                            Color(0xFF4E342E),
                            Color(0xFF3E2723)
                        ),
                        center = Offset(cx - 24.dp.toPx() + parallaxX * 0.2f, islandTopY + 12.dp.toPx()),
                        radius = islandWidth * 0.6f
                    ),
                    topLeft = Offset(cx - islandWidth / 2f, islandTopY - 12.dp.toPx()),
                    size = Size(islandWidth, islandHeight)
                )
                // Glowing neon rim around desk mat
                drawOval(
                    color = auraColor.copy(alpha = 0.55f),
                    topLeft = Offset(cx - islandWidth / 2f + 6.dp.toPx(), islandTopY - 9.dp.toPx()),
                    size = Size(islandWidth - 12.dp.toPx(), islandHeight - 6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. Dynamic Ambient Occlusion Ground Shadow (scales with Nooki's height)
                val shadowScale = (1f - (jumpOffset / 45.dp.toPx())).coerceIn(0.75f, 1.15f)
                val shadowW = 132.dp.toPx() * shadowScale
                val shadowH = 26.dp.toPx() * shadowScale
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(cx + parallaxX * 0.25f, islandTopY + 8.dp.toPx()),
                        radius = shadowW / 1.8f
                    ),
                    topLeft = Offset(cx - shadowW / 2f + parallaxX * 0.25f, islandTopY - shadowH / 2f + 8.dp.toPx()),
                    size = Size(shadowW, shadowH)
                )

                // 4. 3D Fluffy Tail (Back parallax layer — shifts opposite to front snout)
                val tailCenterX = cx - parallaxX * 0.85f + 52.dp.toPx()
                val tailCenterY = petCenterY + 34.dp.toPx() - parallaxY * 0.4f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFBBF24),
                            Color(0xFFD97706),
                            Color(0xFF92400E)
                        ),
                        center = Offset(tailCenterX - 6.dp.toPx(), tailCenterY - 6.dp.toPx()),
                        radius = 28.dp.toPx()
                    ),
                    radius = 24.dp.toPx(),
                    center = Offset(tailCenterX, tailCenterY)
                )

                // 5. 3D Sculpted Ears (Mid-back parallax layer)
                val earOffsetX = parallaxX * 0.45f
                val earOffsetY = parallaxY * 0.45f
                val leftEarPath = Path().apply {
                    moveTo(cx - 52.dp.toPx() + earOffsetX, petCenterY - 22.dp.toPx() + earOffsetY)
                    lineTo(cx - 72.dp.toPx() + earOffsetX, petCenterY - 82.dp.toPx() + earOffsetY)
                    lineTo(cx - 18.dp.toPx() + earOffsetX, petCenterY - 54.dp.toPx() + earOffsetY)
                    close()
                }
                val rightEarPath = Path().apply {
                    moveTo(cx + 52.dp.toPx() + earOffsetX, petCenterY - 22.dp.toPx() + earOffsetY)
                    lineTo(cx + 72.dp.toPx() + earOffsetX, petCenterY - 82.dp.toPx() + earOffsetY)
                    lineTo(cx + 18.dp.toPx() + earOffsetX, petCenterY - 54.dp.toPx() + earOffsetY)
                    close()
                }
                drawPath(
                    path = leftEarPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFBBF24), Color(0xFFB45309)),
                        start = Offset(cx - 72.dp.toPx(), petCenterY - 82.dp.toPx()),
                        end = Offset(cx - 20.dp.toPx(), petCenterY - 20.dp.toPx())
                    )
                )
                drawPath(
                    path = rightEarPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFBBF24), Color(0xFFB45309)),
                        start = Offset(cx + 72.dp.toPx(), petCenterY - 82.dp.toPx()),
                        end = Offset(cx + 20.dp.toPx(), petCenterY - 20.dp.toPx())
                    )
                )

                // Inner ear recessed 3D cavity
                val leftInnerEar = Path().apply {
                    moveTo(cx - 48.dp.toPx() + earOffsetX, petCenterY - 30.dp.toPx() + earOffsetY)
                    lineTo(cx - 62.dp.toPx() + earOffsetX, petCenterY - 68.dp.toPx() + earOffsetY)
                    lineTo(cx - 26.dp.toPx() + earOffsetX, petCenterY - 50.dp.toPx() + earOffsetY)
                    close()
                }
                val rightInnerEar = Path().apply {
                    moveTo(cx + 48.dp.toPx() + earOffsetX, petCenterY - 30.dp.toPx() + earOffsetY)
                    lineTo(cx + 62.dp.toPx() + earOffsetX, petCenterY - 68.dp.toPx() + earOffsetY)
                    lineTo(cx + 26.dp.toPx() + earOffsetX, petCenterY - 50.dp.toPx() + earOffsetY)
                    close()
                }
                drawPath(leftInnerEar, color = Color(0xFFFDE68A))
                drawPath(rightInnerEar, color = Color(0xFFFDE68A))

                // 6. Main 3D Spherical/Ellipsoidal Torso & Head with Volumetric Lighting
                val bodyW = 148.dp.toPx()
                val bodyH = 132.dp.toPx()
                val bodyLeft = cx - bodyW / 2f + parallaxX * 0.5f
                val bodyTop = petCenterY - bodyH / 2f + 10.dp.toPx() + parallaxY * 0.5f

                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFDE68A), // 3D Specular key-light highlight (top-left)
                            Color(0xFFFBBF24), // Mid-tone warm amber fur
                            Color(0xFFD97706), // Core shadow
                            Color(0xFF78350F)  // Deep ambient occlusion edge
                        ),
                        center = Offset(
                            bodyLeft + bodyW * 0.36f + parallaxX * 0.25f,
                            bodyTop + bodyH * 0.28f + parallaxY * 0.25f
                        ),
                        radius = bodyW * 0.72f
                    ),
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyW, bodyH)
                )

                // Subtle 3D Rim Light along the upper-right contour
                drawArc(
                    color = Color.White.copy(alpha = 0.32f),
                    startAngle = -75f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(bodyLeft + 4.dp.toPx(), bodyTop + 4.dp.toPx()),
                    size = Size(bodyW - 8.dp.toPx(), bodyH - 8.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 7. 3D Cream Belly & Cheek Volume (Foreground Layer 1)
                val bellyW = 114.dp.toPx()
                val bellyH = 82.dp.toPx()
                val bellyLeft = cx - bellyW / 2f + parallaxX * 0.78f
                val bellyTop = petCenterY - 8.dp.toPx() + parallaxY * 0.78f

                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFFFFBEB),
                            Color(0xFFFDE68A),
                            Color(0xFFD97706).copy(alpha = 0.55f)
                        ),
                        center = Offset(bellyLeft + bellyW * 0.45f, bellyTop + bellyH * 0.35f),
                        radius = bellyW * 0.65f
                    ),
                    topLeft = Offset(bellyLeft, bellyTop),
                    size = Size(bellyW, bellyH)
                )

                // 8. 3D Glossy Eyes & Rosy Cheeks (Foreground Layer 2 — Stronger Parallax)
                val faceX = cx + parallaxX * 0.95f
                val faceY = petCenterY + 2.dp.toPx() + parallaxY * 0.95f
                val eyeSpacing = 27.dp.toPx()

                // Rosy 3D cheeks
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NookiCoralAccent.copy(alpha = 0.75f), Color.Transparent),
                        center = Offset(faceX - eyeSpacing - 12.dp.toPx(), faceY + 13.dp.toPx()),
                        radius = 12.dp.toPx()
                    ),
                    radius = 12.dp.toPx(),
                    center = Offset(faceX - eyeSpacing - 12.dp.toPx(), faceY + 13.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NookiCoralAccent.copy(alpha = 0.75f), Color.Transparent),
                        center = Offset(faceX + eyeSpacing + 12.dp.toPx(), faceY + 13.dp.toPx()),
                        radius = 12.dp.toPx()
                    ),
                    radius = 12.dp.toPx(),
                    center = Offset(faceX + eyeSpacing + 12.dp.toPx(), faceY + 13.dp.toPx())
                )

                if (mood == NookiPetMood.SLEEPING) {
                    // Cozy closed sleeping eye arcs
                    drawArc(
                        color = Color(0xFF1E1B4B),
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(faceX - eyeSpacing - 9.dp.toPx(), faceY - 4.dp.toPx()),
                        size = Size(18.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF1E1B4B),
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(faceX + eyeSpacing - 9.dp.toPx(), faceY - 4.dp.toPx()),
                        size = Size(18.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    val eyeRadius = if (mood == NookiPetMood.EXCITED_RINGING) 9.5.dp.toPx() else 8.dp.toPx()
                    listOf(faceX - eyeSpacing, faceX + eyeSpacing).forEach { ex ->
                        // Eye socket ambient shadow
                        drawCircle(
                            color = Color(0xFF92400E).copy(alpha = 0.35f),
                            radius = eyeRadius + 2.dp.toPx(),
                            center = Offset(ex, faceY + 1.5.dp.toPx())
                        )
                        // 3D glossy iris sphere
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF312E81),
                                    Color(0xFF1E1B4B),
                                    Color(0xFF090818)
                                ),
                                center = Offset(ex - 2.dp.toPx(), faceY - 2.dp.toPx()),
                                radius = eyeRadius * 1.2f
                            ),
                            radius = eyeRadius,
                            center = Offset(ex, faceY)
                        )
                        // Dual 3D specular catchlights
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = Offset(ex - 2.8.dp.toPx(), faceY - 2.8.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f),
                            radius = 1.4.dp.toPx(),
                            center = Offset(ex + 2.5.dp.toPx(), faceY + 2.5.dp.toPx())
                        )
                    }
                }

                // 9. Protruding 3D Snout & Button Nose (Closest Foreground Layer — 1.25x Parallax)
                val snoutX = cx + parallaxX * 1.22f
                val snoutY = faceY + 12.dp.toPx() + parallaxY * 0.25f

                // Snout 3D cushion
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFFFFBEB),
                            Color(0xFFFDE68A)
                        ),
                        center = Offset(snoutX - 3.dp.toPx(), snoutY - 3.dp.toPx()),
                        radius = 18.dp.toPx()
                    ),
                    topLeft = Offset(snoutX - 16.dp.toPx(), snoutY - 9.dp.toPx()),
                    size = Size(32.dp.toPx(), 22.dp.toPx())
                )

                // 3D Glossy Nose
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFB923C),
                            Color(0xFFEA580C),
                            Color(0xFF9A3412)
                        ),
                        center = Offset(snoutX - 2.dp.toPx(), snoutY - 5.dp.toPx()),
                        radius = 8.dp.toPx()
                    ),
                    topLeft = Offset(snoutX - 6.5.dp.toPx(), snoutY - 7.dp.toPx()),
                    size = Size(13.dp.toPx(), 9.dp.toPx())
                )

                // Expressive Mouth
                if (mood == NookiPetMood.EXCITED_RINGING || mood == NookiPetMood.CELEBRATING_XP) {
                    drawOval(
                        color = Color(0xFFBE123C),
                        topLeft = Offset(snoutX - 7.dp.toPx(), snoutY + 3.dp.toPx()),
                        size = Size(14.dp.toPx(), 10.dp.toPx())
                    )
                } else {
                    drawArc(
                        color = Color(0xFF1E1B4B),
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(snoutX - 9.dp.toPx(), snoutY + 1.dp.toPx()),
                        size = Size(18.dp.toPx(), 9.dp.toPx()),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 10. 3D Sculpted Front Paws resting on the desk edge
                val pawY = petCenterY + 62.dp.toPx() + parallaxY * 0.6f
                listOf(cx - 34.dp.toPx() + parallaxX * 0.7f, cx + 34.dp.toPx() + parallaxX * 0.7f).forEach { px ->
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFDE68A),
                                Color(0xFFF59E0B),
                                Color(0xFFB45309)
                            ),
                            center = Offset(px - 3.dp.toPx(), pawY - 4.dp.toPx()),
                            radius = 18.dp.toPx()
                        ),
                        topLeft = Offset(px - 16.dp.toPx(), pawY - 11.dp.toPx()),
                        size = Size(32.dp.toPx(), 22.dp.toPx())
                    )
                }

                // 11. 3D Brass & Sky-Blue Alarm Bell Crown on Top of Nooki's Head
                val bellPivotX = cx + parallaxX * 0.65f
                val bellPivotY = petCenterY - 58.dp.toPx() + parallaxY * 0.65f
                rotate(
                    degrees = bellJingleDeg,
                    pivot = Offset(bellPivotX, bellPivotY)
                ) {
                    // Bell dome 3D sphere
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE0F2FE),
                                NookiSkyInfo,
                                Color(0xFF0369A1)
                            ),
                            center = Offset(bellPivotX - 4.dp.toPx(), bellPivotY - 6.dp.toPx()),
                            radius = 18.dp.toPx()
                        ),
                        radius = 14.dp.toPx(),
                        center = Offset(bellPivotX, bellPivotY)
                    )
                    // Golden ringer handle on top
                    drawRoundRect(
                        color = Color(0xFFFACC15),
                        topLeft = Offset(bellPivotX - 5.dp.toPx(), bellPivotY - 20.dp.toPx()),
                        size = Size(10.dp.toPx(), 6.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }

        // Interactive 3D Controls under the Centered 3D Model
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isAutoRotateEnabled,
                onClick = onToggleAutoRotate,
                label = { Text(if (isAutoRotateEnabled) "3D Orbit: ON" else "3D Orbit: Drag Only") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "Toggle 3D Auto Orbit",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("toggle_3d_orbit_chip")
            )

            FilterChip(
                selected = petCombo > 0,
                onClick = onPetTap,
                label = { Text("Pet Nooki (+2 XP)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Pet Nooki in 3D",
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("pet_nooki_3d_chip")
            )
        }
    }
}
