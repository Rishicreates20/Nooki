package com.example.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.LocalWeatherState
import com.example.domain.model.NookiAppTheme
import com.example.domain.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen animated weather sky backdrop rendered behind Nooki's Desk-Pet stage and Thread.
 * Blends the active [LocalWeatherState] sky palette with the user's selected [NookiAppTheme]
 * and renders live atmospheric weather particles (rain streaks, sunbeams, twilight fireflies,
 * twinkling constellations, or drifting 3D snowflakes).
 */
@Composable
fun DynamicWeatherSkyBackdrop(
    weatherState: LocalWeatherState,
    appTheme: NookiAppTheme,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val condition = weatherState.condition
    val themeBg = Color(appTheme.backgroundDeepHex)
    val themeSurface = Color(appTheme.surfaceContainerHex)

    val blendedSkyTop by animateColorAsState(
        targetValue = lerp(themeBg, Color(condition.skyTopHex), 0.58f),
        animationSpec = tween(durationMillis = 700),
        label = "weather_sky_top"
    )
    val blendedSkyMid by animateColorAsState(
        targetValue = lerp(themeSurface, Color(condition.skyHorizonHex), 0.55f),
        animationSpec = tween(durationMillis = 700),
        label = "weather_sky_mid"
    )
    val blendedSkyBottom by animateColorAsState(
        targetValue = lerp(themeBg, Color(condition.skyTopHex), 0.35f),
        animationSpec = tween(durationMillis = 700),
        label = "weather_sky_bottom"
    )
    val weatherAccent by animateColorAsState(
        targetValue = Color(condition.weatherParticleHex),
        animationSpec = tween(durationMillis = 700),
        label = "weather_particle_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "dynamic_weather_sky")
    val weatherPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_phase"
    )
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_shimmer"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("dynamic_weather_backdrop_${condition.id}")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Sky Horizon Vertical Gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        blendedSkyTop,
                        blendedSkyMid,
                        blendedSkyBottom
                    )
                )
            )

            // 2. Weather-Specific Atmospheric Phenomena
            when (condition) {
                WeatherCondition.SUNLIT_BREEZE -> {
                    drawSunlitBreezeAtmosphere(
                        phase = weatherPhase,
                        shimmer = shimmerPhase,
                        accentColor = weatherAccent
                    )
                }

                WeatherCondition.MONSOON_RAIN -> {
                    drawMonsoonRainAtmosphere(
                        phase = weatherPhase,
                        accentColor = weatherAccent
                    )
                }

                WeatherCondition.GOLDEN_TWILIGHT -> {
                    drawGoldenTwilightAtmosphere(
                        phase = weatherPhase,
                        shimmer = shimmerPhase,
                        accentColor = weatherAccent
                    )
                }

                WeatherCondition.STARRY_NIGHT_DEW -> {
                    drawStarryNightAtmosphere(
                        phase = weatherPhase,
                        shimmer = shimmerPhase,
                        accentColor = weatherAccent
                    )
                }

                WeatherCondition.MISTY_MOUNTAIN_SNOW -> {
                    drawAlpineSnowAtmosphere(
                        phase = weatherPhase,
                        shimmer = shimmerPhase,
                        accentColor = weatherAccent
                    )
                }
            }
        }

        content()
    }
}

private fun DrawScope.drawSunlitBreezeAtmosphere(
    phase: Float,
    shimmer: Float,
    accentColor: Color
) {
    val sunCenter = Offset(size.width * 0.85f, size.height * 0.11f)
    val pulse = 1f + sin(shimmer) * 0.08f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.34f),
                accentColor.copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = sunCenter,
            radius = 220.dp.toPx() * pulse
        ),
        radius = 220.dp.toPx() * pulse,
        center = sunCenter
    )

    // Diagonal warm sunbeams
    for (i in 0 until 4) {
        val beamStartX = size.width * (0.55f + i * 0.12f)
        val beamPath = Path().apply {
            moveTo(beamStartX, 0f)
            lineTo(beamStartX + 46.dp.toPx(), 0f)
            lineTo(beamStartX - 140.dp.toPx(), size.height * 0.58f)
            lineTo(beamStartX - 195.dp.toPx(), size.height * 0.58f)
            close()
        }
        drawPath(
            path = beamPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.09f),
                    Color.Transparent
                )
            )
        )
    }

    // Floating sunlit pollen motes
    val moteCount = 16
    for (i in 0 until moteCount) {
        val progress = (phase + i * (1f / moteCount)) % 1f
        val x = (size.width * ((i * 0.19f + sin(progress * 2f * PI.toFloat() + i) * 0.05f) % 1f))
            .coerceIn(0f, size.width)
        val y = size.height * (0.85f - progress * 0.75f)
        val alpha = sin(progress * PI.toFloat()) * 0.45f
        drawCircle(
            color = accentColor.copy(alpha = alpha.coerceIn(0.05f, 0.45f)),
            radius = (2.5f + (i % 3) * 1.3f).dp.toPx(),
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawMonsoonRainAtmosphere(
    phase: Float,
    accentColor: Color
) {
    val dropCount = 34
    val windSlantPx = -24.dp.toPx()
    val streakHeightPx = 32.dp.toPx()

    for (i in 0 until dropCount) {
        val speedFactor = if (i % 2 == 0) 1f else 1.5f
        val progress = ((phase * speedFactor) + (i * 0.073f)) % 1f
        val startX = ((i * 97f) % 100f) / 100f * size.width
        val startY = progress * (size.height + streakHeightPx) - streakHeightPx
        val alpha = (0.18f + (i % 4) * 0.08f).coerceAtMost(0.48f)

        drawLine(
            color = accentColor.copy(alpha = alpha),
            start = Offset(startX, startY),
            end = Offset(startX + windSlantPx, startY + streakHeightPx),
            strokeWidth = (1.4f + (i % 2) * 0.6f).dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawGoldenTwilightAtmosphere(
    phase: Float,
    shimmer: Float,
    accentColor: Color
) {
    val horizonCenter = Offset(size.width * 0.5f, size.height * 0.36f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFB923C).copy(alpha = 0.28f),
                Color(0xFFF43F5E).copy(alpha = 0.12f),
                Color.Transparent
            ),
            center = horizonCenter,
            radius = 270.dp.toPx()
        ),
        radius = 270.dp.toPx(),
        center = horizonCenter
    )

    // Rising dusk fireflies
    val fireflyCount = 18
    for (i in 0 until fireflyCount) {
        val progress = (phase + i * (1f / fireflyCount)) % 1f
        val xBase = ((i * 61f) % 100f) / 100f * size.width
        val sway = sin(shimmer + i) * 18.dp.toPx()
        val y = size.height * (0.90f - progress * 0.78f)
        val glowAlpha = (0.25f + 0.35f * abs(sin(shimmer * 1.2f + i))).coerceIn(0.1f, 0.65f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = glowAlpha),
                    Color.Transparent
                ),
                center = Offset(xBase + sway, y),
                radius = 9.dp.toPx()
            ),
            radius = 9.dp.toPx(),
            center = Offset(xBase + sway, y)
        )
    }
}

private fun DrawScope.drawStarryNightAtmosphere(
    phase: Float,
    shimmer: Float,
    accentColor: Color
) {
    // Glowing Crescent Moon in upper right sky
    val moonCenter = Offset(size.width * 0.82f, size.height * 0.13f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.28f),
                Color.Transparent
            ),
            center = moonCenter,
            radius = 90.dp.toPx()
        ),
        radius = 90.dp.toPx(),
        center = moonCenter
    )
    drawCircle(
        color = Color(0xFFFEF9C3).copy(alpha = 0.85f),
        radius = 20.dp.toPx(),
        center = moonCenter
    )
    drawCircle(
        color = Color(0xFF0F172A),
        radius = 17.dp.toPx(),
        center = Offset(moonCenter.x - 7.dp.toPx(), moonCenter.y - 4.dp.toPx())
    )

    // Twinkling Constellation Stars
    val starCount = 26
    for (i in 0 until starCount) {
        val sx = (((i * 73 + 19) % 96) / 96f) * size.width
        val sy = (((i * 41 + 11) % 70) / 100f) * size.height
        val twinkle = (0.22f + 0.55f * abs(sin(shimmer + i * 0.9f + phase * PI.toFloat())))
        val radius = (1.5f + (i % 3) * 1.0f).dp.toPx()
        drawCircle(
            color = accentColor.copy(alpha = twinkle.coerceIn(0.15f, 0.82f)),
            radius = radius,
            center = Offset(sx, sy)
        )
    }
}

private fun DrawScope.drawAlpineSnowAtmosphere(
    phase: Float,
    shimmer: Float,
    accentColor: Color
) {
    val flakeCount = 28
    for (i in 0 until flakeCount) {
        val progress = (phase + i * (1f / flakeCount)) % 1f
        val xBase = (((i * 53 + 7) % 100) / 100f) * size.width
        val driftX = sin(shimmer + i * 0.7f) * 16.dp.toPx()
        val y = progress * size.height
        val flakeRadius = (2.2f + (i % 4) * 1.2f).dp.toPx()
        val alpha = (0.30f + (i % 3) * 0.18f).coerceAtMost(0.75f)

        drawCircle(
            color = accentColor.copy(alpha = alpha),
            radius = flakeRadius,
            center = Offset(xBase + driftX, y)
        )
    }
}

private fun abs(value: Float): Float = if (value < 0f) -value else value

/**
 * Interactive Weather & Location Environment Strip displayed on the Home screen and Profile screen.
 * Shows the live Location API weather status, how it affects Nooki's 3D desk island,
 * a button to sync GPS coordinates via the Android Location API, and quick environment chips.
 */
@Composable
fun LocalWeatherEnvironmentBar(
    weatherState: LocalWeatherState,
    onSyncGpsWeather: () -> Unit,
    onSelectWeatherCondition: (WeatherCondition?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val condition = weatherState.condition
    val weatherTint = Color(condition.islandRimGlowHex)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface.copy(alpha = 0.92f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, weatherTint.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .testTag("local_weather_environment_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = condition.badgeEmoji,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${condition.displayName} • ${weatherState.temperatureCelsius}°C",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("weather_condition_headline")
                            )
                            Text(
                                text = "(+${condition.moodHappinessBonus}% Mood)",
                                style = MaterialTheme.typography.labelSmall,
                                color = weatherTint,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${weatherState.sourceBadgeText} • ${weatherState.humidityPercent}% RH • ${weatherState.windSpeedKmh} km/h",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("weather_location_source_label")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                AssistChip(
                    onClick = onSyncGpsWeather,
                    label = {
                        Text(
                            text = if (weatherState.isFromLiveLocationApi && !weatherState.isManualOverride) {
                                "GPS Synced"
                            } else {
                                "Sync GPS"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (weatherState.isFromLiveLocationApi) {
                                Icons.Default.MyLocation
                            } else {
                                Icons.Default.LocationOn
                            },
                            contentDescription = "Sync weather from local Location API",
                            tint = weatherTint,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = scheme.background,
                        labelColor = scheme.onBackground
                    ),
                    modifier = Modifier.testTag("sync_location_weather_button")
                )
            }

            // Environment effect summary on Nooki's 3D Desk Island
            Text(
                text = "3D Island Environment: ${condition.environmentDescription}",
                style = MaterialTheme.typography.bodySmall,
                color = weatherTint.copy(alpha = 0.95f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Quick Weather Environment Switcher (Auto GPS + 5 Weather Atmospheres)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !weatherState.isManualOverride,
                    onClick = { onSelectWeatherCondition(null) },
                    label = { Text("📍 Auto GPS") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = weatherTint,
                        selectedLabelColor = scheme.background
                    ),
                    modifier = Modifier.testTag("weather_chip_AUTO")
                )
                WeatherCondition.entries.forEach { option ->
                    val isSelected = weatherState.isManualOverride && weatherState.condition == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectWeatherCondition(option) },
                        label = { Text("${option.badgeEmoji} ${option.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(option.islandRimGlowHex),
                            selectedLabelColor = Color(option.skyTopHex)
                        ),
                        modifier = Modifier.testTag("weather_chip_${option.id}")
                    )
                }
            }
        }
    }
}
