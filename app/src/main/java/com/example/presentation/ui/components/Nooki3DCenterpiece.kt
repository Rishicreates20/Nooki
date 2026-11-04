package com.example.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.MoodState
import com.example.domain.model.NookiPetMood
import com.example.domain.model.PetGestureType
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.Marigold
import com.example.ui.theme.NookiCoralAccent
import com.example.ui.theme.NookiSkyInfo
import com.example.ui.theme.Sindoor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shape type for 3D particles orbiting or bursting around Nooki.
 */
private enum class ParticleGlyph {
    HEART,
    STAR_4PT,
    GLOW_ORB
}

/**
 * Interactive burst particle spawned on tap, double-tap play, or belly scritch drag.
 */
private data class InteractiveParticle3D(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val zDepth: Float,
    val sizeDp: Float,
    val color: Color,
    val glyph: ParticleGlyph,
    var lifeRemaining: Float = 1f
)

/**
 * Interactive 3D-shaded Nooki Desk-Pet model rendered at the center of the viewport.
 * Features:
 * - Depth-sorted 3D orbital particle system (hearts, 4-point marigold stars, leaf-green fireflies)
 *   whenever Nooki is in a happy state (`moodState.isHappyState`)
 * - Physics-driven touch particle bursts on tap (`HEAD_PAT`), double-tap (`PLAY_BOOP`),
 *   and continuous drag rub (`BELLY_SCRITCH`)
 * - 360° 3D Play Spin & Hop animation, ear flutter, tail wag speed modulation, and happy crescent eyes
 */
@Composable
fun Nooki3DCenterpiece(
    mood: NookiPetMood,
    moodState: MoodState = MoodState(petMood = mood),
    speechText: String,
    isAutoRotateEnabled: Boolean,
    petCombo: Int,
    onPetTap: () -> Unit,
    onPetGesture: (PetGestureType) -> Unit = { onPetTap() },
    onToggleAutoRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // User drag angles in degrees (-45f..45f yaw, -25f..25f pitch)
    var userYaw by remember { mutableFloatStateOf(0f) }
    var userPitch by remember { mutableFloatStateOf(0f) }
    var accumulatedRubDistance by remember { mutableFloatStateOf(0f) }

    // 3D Play Spin & Ear-Wiggle animatables triggered by gestures
    val playSpinDegrees = remember { Animatable(0f) }
    val playHopProgress = remember { Animatable(0f) }
    val earFlutterProgress = remember { Animatable(0f) }

    // Physics-driven interactive particle burst pool
    val burstParticles = remember { mutableStateListOf<InteractiveParticle3D>() }

    fun spawnParticleBurst(originX: Float, originY: Float, count: Int, intensePlay: Boolean) {
        val palette = listOf(Marigold, Sindoor, LeafGreen, Color(0xFFFF8FA3), Color(0xFFFDE68A))
        repeat(count) { idx ->
            val angle = (idx.toFloat() / count.toFloat()) * (2f * Math.PI.toFloat()) +
                Random.nextFloat() * 0.45f
            val speed = if (intensePlay) {
                110f + Random.nextFloat() * 140f
            } else {
                65f + Random.nextFloat() * 95f
            }
            val glyph = when (idx % 3) {
                0 -> ParticleGlyph.HEART
                1 -> ParticleGlyph.STAR_4PT
                else -> ParticleGlyph.GLOW_ORB
            }
            burstParticles.add(
                InteractiveParticle3D(
                    id = System.nanoTime() + idx,
                    x = originX + (Random.nextFloat() - 0.5f) * 24f,
                    y = originY + (Random.nextFloat() - 0.5f) * 24f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 75f, // upward buoyancy
                    zDepth = 0.6f + Random.nextFloat() * 0.7f,
                    sizeDp = if (intensePlay) 11f + Random.nextFloat() * 6f else 8f + Random.nextFloat() * 5f,
                    color = palette[idx % palette.size],
                    glyph = glyph,
                    lifeRemaining = 1f
                )
            )
        }
    }

    fun triggerHeadPatAnimation(tapX: Float, tapY: Float) {
        spawnParticleBurst(tapX, tapY, count = 14, intensePlay = false)
        onPetGesture(PetGestureType.HEAD_PAT)
        scope.launch {
            earFlutterProgress.snapTo(0f)
            earFlutterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
            earFlutterProgress.snapTo(0f)
        }
    }

    fun triggerPlayBoopAnimation(tapX: Float, tapY: Float) {
        spawnParticleBurst(tapX, tapY, count = 22, intensePlay = true)
        onPetGesture(PetGestureType.PLAY_BOOP)
        scope.launch {
            launch {
                playSpinDegrees.snapTo(0f)
                playSpinDegrees.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
                )
                playSpinDegrees.snapTo(0f)
            }
            launch {
                playHopProgress.snapTo(0f)
                playHopProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 410, easing = FastOutSlowInEasing)
                )
                playHopProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    // React to external celebration triggers (e.g. completing a reminder knot)
    LaunchedEffect(mood, moodState.activePlayGesture, petCombo) {
        if (mood == NookiPetMood.CELEBRATING_XP && burstParticles.size < 8) {
            spawnParticleBurst(originX = 380f, originY = 320f, count = 16, intensePlay = true)
        }
    }

    // 60fps physics loop for interactive burst particles
    LaunchedEffect(burstParticles.size > 0) {
        var lastNanos = 0L
        while (burstParticles.isNotEmpty()) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNanos = nowNanos

                val iterator = burstParticles.listIterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    val newLife = p.lifeRemaining - dt * 0.85f
                    if (newLife <= 0f) {
                        iterator.remove()
                    } else {
                        iterator.set(
                            p.copy(
                                x = p.x + p.vx * dt,
                                y = p.y + p.vy * dt,
                                vy = p.vy - 35f * dt, // gentle upward float
                                vx = p.vx * (1f - 1.5f * dt),
                                lifeRemaining = newLife
                            )
                        )
                    }
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "nooki_3d_stage")

    // Smooth 3D orbital angle when auto-rotate is enabled
    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nooki_3d_orbit"
    )

    // Continuous 3D Happy Particle Ring phase (orbits around Nooki when in happy state)
    val happyParticlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "happy_3d_particles"
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

    // Tail wag oscillation modulated by MoodState.happinessTier.tailWagMultiplier
    val tailWagPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (520f / moodState.happinessTier.tailWagMultiplier).toInt().coerceIn(180, 1200),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nooki_3d_tail_wag"
    )

    // Bell shake when ringing or playing
    val bellJingleDeg by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == NookiPetMood.EXCITED_RINGING || playHopProgress.value > 0.1f) 140 else 1200,
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

    val isHappyParticleFieldActive = moodState.isHappyState

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
                .padding(horizontal = 20.dp)
                .widthIn(max = 380.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${moodState.happinessTier.badgeEmoji} ${mood.displayTitle.uppercase()} • ${moodState.happinessScore}% HAPPY",
                    style = MaterialTheme.typography.labelLarge,
                    color = auraColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = speechText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Centered 3D Model Viewport with Petting, Play Hop & Rubbing Gestures
        Box(
            modifier = Modifier
                .size(285.dp)
                .testTag("nooki_3d_center_model")
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            accumulatedRubDistance = 0f
                        },
                        onDragEnd = {
                            userYaw *= 0.35f
                            userPitch *= 0.35f
                            accumulatedRubDistance = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        userYaw = (userYaw + dragAmount.x * 0.25f).coerceIn(-42f, 42f)
                        userPitch = (userPitch - dragAmount.y * 0.20f).coerceIn(-24f, 24f)

                        val deltaDist = hypot(dragAmount.x, dragAmount.y)
                        accumulatedRubDistance += deltaDist

                        // Spawn subtle sparkle trail while rubbing Nooki in 3D
                        if (Random.nextFloat() < 0.38f) {
                            burstParticles.add(
                                InteractiveParticle3D(
                                    id = System.nanoTime(),
                                    x = change.position.x,
                                    y = change.position.y,
                                    vx = (Random.nextFloat() - 0.5f) * 50f,
                                    vy = -45f - Random.nextFloat() * 45f,
                                    zDepth = 1f,
                                    sizeDp = 7.5f,
                                    color = if (Random.nextBoolean()) Marigold else Sindoor,
                                    glyph = if (Random.nextBoolean()) ParticleGlyph.HEART else ParticleGlyph.STAR_4PT,
                                    lifeRemaining = 0.75f
                                )
                            )
                        }

                        // Trigger BELLY_SCRITCH MoodState boost when the user rubs across Nooki
                        if (accumulatedRubDistance > 135f) {
                            accumulatedRubDistance = 0f
                            onPetGesture(PetGestureType.BELLY_SCRITCH)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            triggerHeadPatAnimation(tapOffset.x, tapOffset.y)
                        },
                        onDoubleTap = { tapOffset ->
                            userYaw = 0f
                            userPitch = 0f
                            triggerPlayBoopAnimation(tapOffset.x, tapOffset.y)
                        }
                    )
                }
                .graphicsLayer {
                    rotationY = animatedYaw * 0.45f + playSpinDegrees.value
                    rotationX = animatedPitch * 0.45f
                    val hopScale = 1f + sin(playHopProgress.value * Math.PI).toFloat() * 0.08f
                    scaleX = hopScale
                    scaleY = hopScale
                    cameraDistance = 15f * density
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                val parallaxX = (animatedYaw / 42f) * 24.dp.toPx()
                val parallaxY = -(animatedPitch / 24f) * 14.dp.toPx()

                val baseBounce = if (mood == NookiPetMood.EXCITED_RINGING || mood == NookiPetMood.CELEBRATING_XP) {
                    breathProgress * 11.dp.toPx()
                } else {
                    breathProgress * 5.5.dp.toPx()
                }
                val playHopOffset = -sin(playHopProgress.value * Math.PI).toFloat() * 30.dp.toPx()
                val jumpOffset = baseBounce + playHopOffset
                val petCenterY = cy - 6.dp.toPx() + jumpOffset

                // 1. Volumetric 3D Aura Halo behind the 3D model
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor.copy(alpha = if (isHappyParticleFieldActive) 0.48f else 0.36f),
                            auraColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(cx - parallaxX * 0.3f, petCenterY),
                        radius = 138.dp.toPx()
                    ),
                    radius = 138.dp.toPx(),
                    center = Offset(cx - parallaxX * 0.3f, petCenterY)
                )

                // 1B. BACK-LAYER 3D Happy State Orbital Particles (z < 0 — behind Nooki's body)
                if (isHappyParticleFieldActive) {
                    drawHappyOrbitalParticles(
                        cx = cx,
                        cy = petCenterY,
                        phase = happyParticlePhase,
                        renderFrontHalf = false
                    )
                }

                // 2. Floating 3D Isometric Desk Island Pedestal
                val islandTopY = cy + 84.dp.toPx() + parallaxY * 0.25f
                val islandWidth = 216.dp.toPx()
                val islandHeight = 46.dp.toPx()

                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2A1810), Color(0xFF140B07))
                    ),
                    topLeft = Offset(cx - islandWidth / 2f, islandTopY - 6.dp.toPx()),
                    size = Size(islandWidth, islandHeight + 14.dp.toPx())
                )
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
                drawOval(
                    color = auraColor.copy(alpha = 0.55f),
                    topLeft = Offset(cx - islandWidth / 2f + 6.dp.toPx(), islandTopY - 9.dp.toPx()),
                    size = Size(islandWidth - 12.dp.toPx(), islandHeight - 6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. Dynamic Ambient Occlusion Ground Shadow
                val shadowScale = (1f - (abs(jumpOffset) / 55.dp.toPx())).coerceIn(0.65f, 1.15f)
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

                // 4. 3D Fluffy Tail with Dynamic Wagging (modulated by MoodState)
                val tailWagOffsetPx = tailWagPhase * 10.dp.toPx() * moodState.happinessTier.tailWagMultiplier
                val tailCenterX = cx - parallaxX * 0.85f + 52.dp.toPx() + tailWagOffsetPx
                val tailCenterY = petCenterY + 34.dp.toPx() - parallaxY * 0.4f - abs(tailWagOffsetPx) * 0.25f
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

                // 5. 3D Sculpted Ears (with happy ear-perk & head-pat flutter)
                val earOffsetX = parallaxX * 0.45f
                val earFlutterY = sin(earFlutterProgress.value * Math.PI * 4).toFloat() * 6.dp.toPx()
                val earPerkY = -moodState.happinessTier.earPerkDegrees.dp.toPx() * 0.4f
                val earOffsetY = parallaxY * 0.45f + earFlutterY + earPerkY

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
                            Color(0xFFFDE68A),
                            Color(0xFFFBBF24),
                            Color(0xFFD97706),
                            Color(0xFF78350F)
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

                drawArc(
                    color = Color.White.copy(alpha = 0.32f),
                    startAngle = -75f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(bodyLeft + 4.dp.toPx(), bodyTop + 4.dp.toPx()),
                    size = Size(bodyW - 8.dp.toPx(), bodyH - 8.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 7. 3D Cream Belly & Cheek Volume
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

                // 8. 3D Glossy Eyes & Rosy Cheeks
                val faceX = cx + parallaxX * 0.95f
                val faceY = petCenterY + 2.dp.toPx() + parallaxY * 0.95f
                val eyeSpacing = 27.dp.toPx()

                // Extra rosy cheek glow when in happy state or being petted
                val cheekAlpha = if (isHappyParticleFieldActive) 0.92f else 0.72f
                val cheekRadius = if (isHappyParticleFieldActive) 14.dp.toPx() else 12.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NookiCoralAccent.copy(alpha = cheekAlpha), Color.Transparent),
                        center = Offset(faceX - eyeSpacing - 12.dp.toPx(), faceY + 13.dp.toPx()),
                        radius = cheekRadius
                    ),
                    radius = cheekRadius,
                    center = Offset(faceX - eyeSpacing - 12.dp.toPx(), faceY + 13.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NookiCoralAccent.copy(alpha = cheekAlpha), Color.Transparent),
                        center = Offset(faceX + eyeSpacing + 12.dp.toPx(), faceY + 13.dp.toPx()),
                        radius = cheekRadius
                    ),
                    radius = cheekRadius,
                    center = Offset(faceX + eyeSpacing + 12.dp.toPx(), faceY + 13.dp.toPx())
                )

                val showHappyCrescentEyes = earFlutterProgress.value > 0.05f || playHopProgress.value > 0.05f
                if (mood == NookiPetMood.SLEEPING) {
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
                } else if (showHappyCrescentEyes) {
                    // Joyful upward '^ ^' crescent eyes during petting or play!
                    drawArc(
                        color = Color(0xFF1E1B4B),
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(faceX - eyeSpacing - 9.dp.toPx(), faceY - 6.dp.toPx()),
                        size = Size(18.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF1E1B4B),
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(faceX + eyeSpacing - 9.dp.toPx(), faceY - 6.dp.toPx()),
                        size = Size(18.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    val eyeRadius = if (mood == NookiPetMood.EXCITED_RINGING) 9.5.dp.toPx() else 8.dp.toPx()
                    listOf(faceX - eyeSpacing, faceX + eyeSpacing).forEach { ex ->
                        drawCircle(
                            color = Color(0xFF92400E).copy(alpha = 0.35f),
                            radius = eyeRadius + 2.dp.toPx(),
                            center = Offset(ex, faceY + 1.5.dp.toPx())
                        )
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

                // 9. Protruding 3D Snout & Button Nose
                val snoutX = cx + parallaxX * 1.22f
                val snoutY = faceY + 12.dp.toPx() + parallaxY * 0.25f

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

                if (mood == NookiPetMood.EXCITED_RINGING || mood == NookiPetMood.CELEBRATING_XP || showHappyCrescentEyes) {
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

                // 10. 3D Sculpted Front Paws
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
                    drawRoundRect(
                        color = Color(0xFFFACC15),
                        topLeft = Offset(bellPivotX - 5.dp.toPx(), bellPivotY - 20.dp.toPx()),
                        size = Size(10.dp.toPx(), 6.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }

                // 12. FRONT-LAYER 3D Happy State Orbital Particles (z >= 0 — in front of Nooki)
                if (isHappyParticleFieldActive) {
                    drawHappyOrbitalParticles(
                        cx = cx,
                        cy = petCenterY,
                        phase = happyParticlePhase,
                        renderFrontHalf = true
                    )
                }

                // 13. Interactive Touch-Spawned Burst Particles (Tap / Double-Tap Play / Belly Rub)
                for (particle in burstParticles) {
                    val alpha = particle.lifeRemaining.coerceIn(0f, 1f)
                    val radiusPx = particle.sizeDp.dp.toPx() * (0.7f + 0.5f * alpha)
                    drawParticleGlyph(
                        glyph = particle.glyph,
                        center = Offset(particle.x, particle.y),
                        radiusPx = radiusPx,
                        color = particle.color.copy(alpha = alpha)
                    )
                }
            }
        }

        // Interactive 3D Gesture & Orbit Controls under the Centered 3D Model
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isAutoRotateEnabled,
                onClick = onToggleAutoRotate,
                label = { Text(if (isAutoRotateEnabled) "3D Orbit" else "Drag 3D") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "Toggle 3D Auto Orbit",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("toggle_3d_orbit_chip")
            )

            FilterChip(
                selected = petCombo > 0,
                onClick = {
                    triggerHeadPatAnimation(340f, 260f)
                },
                label = { Text("Pat (+3%)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Pat Nooki Head",
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("pet_nooki_3d_chip")
            )

            FilterChip(
                selected = moodState.activePlayGesture == PetGestureType.PLAY_BOOP,
                onClick = {
                    triggerPlayBoopAnimation(340f, 260f)
                },
                label = { Text("Play Spin (+5%)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = "Trigger 3D Play Spin",
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("play_nooki_3d_chip")
            )
        }
    }
}

/**
 * Draws the depth-sorted 3D orbital particle field around Nooki when in a happy state.
 */
private fun DrawScope.drawHappyOrbitalParticles(
    cx: Float,
    cy: Float,
    phase: Float,
    renderFrontHalf: Boolean
) {
    val totalParticles = 12
    val orbitRadiusX = 114.dp.toPx()
    val orbitRadiusY = 38.dp.toPx()
    val palette = listOf(Marigold, Sindoor, LeafGreen, Color(0xFFFF8FA3))

    for (i in 0 until totalParticles) {
        val baseAngle = phase + (i.toFloat() / totalParticles.toFloat()) * (2f * Math.PI.toFloat())
        val zDepth = sin(baseAngle) // -1f (back) .. +1f (front)
        val isFront = zDepth >= 0f
        if (isFront != renderFrontHalf) continue

        val x = cx + cos(baseAngle) * orbitRadiusX
        val verticalWave = sin(baseAngle * 2f + i) * 32.dp.toPx()
        val y = cy - 8.dp.toPx() + zDepth * orbitRadiusY + verticalWave

        val perspectiveScale = 0.68f + (zDepth + 1f) * 0.28f
        val alpha = (0.45f + (zDepth + 1f) * 0.25f).coerceIn(0.3f, 0.95f)
        val glyph = when (i % 3) {
            0 -> ParticleGlyph.STAR_4PT
            1 -> ParticleGlyph.HEART
            else -> ParticleGlyph.GLOW_ORB
        }
        val color = palette[i % palette.size].copy(alpha = alpha)

        drawParticleGlyph(
            glyph = glyph,
            center = Offset(x, y),
            radiusPx = 8.5.dp.toPx() * perspectiveScale,
            color = color
        )
    }
}

/**
 * Renders a single 3D particle glyph (Heart, 4-Point Starburst, or Glowing Firefly Orb).
 */
private fun DrawScope.drawParticleGlyph(
    glyph: ParticleGlyph,
    center: Offset,
    radiusPx: Float,
    color: Color
) {
    when (glyph) {
        ParticleGlyph.GLOW_ORB -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = color.alpha), color, Color.Transparent),
                    center = center,
                    radius = radiusPx * 1.35f
                ),
                radius = radiusPx * 1.35f,
                center = center
            )
        }

        ParticleGlyph.STAR_4PT -> {
            val starPath = Path().apply {
                moveTo(center.x, center.y - radiusPx)
                quadraticTo(center.x, center.y, center.x + radiusPx, center.y)
                quadraticTo(center.x, center.y, center.x, center.y + radiusPx)
                quadraticTo(center.x, center.y, center.x - radiusPx, center.y)
                quadraticTo(center.x, center.y, center.x, center.y - radiusPx)
                close()
            }
            drawPath(path = starPath, color = color)
        }

        ParticleGlyph.HEART -> {
            val r = radiusPx * 0.85f
            val heartPath = Path().apply {
                moveTo(center.x, center.y + r * 0.85f)
                cubicTo(
                    center.x - r * 1.4f, center.y + r * 0.1f,
                    center.x - r * 0.9f, center.y - r * 1.1f,
                    center.x, center.y - r * 0.35f
                )
                cubicTo(
                    center.x + r * 0.9f, center.y - r * 1.1f,
                    center.x + r * 1.4f, center.y + r * 0.1f,
                    center.x, center.y + r * 0.85f
                )
                close()
            }
            drawPath(path = heartPath, color = color)
        }
    }
}
