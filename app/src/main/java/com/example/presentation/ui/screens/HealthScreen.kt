package com.example.presentation.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.domain.health.HealthFormat
import com.example.domain.health.HealthGoals
import com.example.domain.model.ConsistencyStreakCalculator
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.viewmodel.HealthUiState
import com.example.scheduler.NotificationHelper
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.Marigold
import com.example.ui.theme.NookiSkyInfo
import com.example.ui.theme.Sindoor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SleepPurple = Color(0xFFA78BFA)

private fun isMedicine(r: ReminderEntity): Boolean =
    r.notes.contains("KIND:MEDICINE") || r.title.contains("दवाई") ||
        r.title.contains("medicine", ignoreCase = true) || r.title.contains("tablet", ignoreCase = true)

/**
 * Health: steps, water, sleep, mood and today's medicines, all saved in the signed-in user's own
 * database.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen(
    healthState: HealthUiState,
    deskState: DeskUiState,
    stepStatus: StepSensorStatus,
    onRequestStepPermission: () -> Unit,
    onAddSteps: (Int) -> Unit,
    onChangeWater: (Int) -> Unit,
    onSetSleepMinutes: (Int) -> Unit,
    onSetMood: (Int) -> Unit,
    onIntent: (DeskIntent) -> Unit
) {
    val context = LocalContext.current
    val today = healthState.today
    val todayEpoch = ConsistencyStreakCalculator.toLocalDayEpoch(deskState.nowMillis)
    val medicines = deskState.allReminders.filter { r ->
        isMedicine(r) && r.status != ReminderStatus.CANCELLED &&
            (r.status != ReminderStatus.COMPLETED || r.completedDayEpoch == todayEpoch)
    }.sortedBy { it.triggerAtMillis }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp)
            .testTag("screen_health"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Health",
                subtitle = "Steps, water, sleep, mood and medicines for today"
            )
        }

        // Steps
        item {
            SectionCard(title = "Steps") {
                MetricHeadline(
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    accent = Marigold,
                    value = "%,d".format(today.steps),
                    caption = "of %,d steps goal".format(HealthGoals.STEPS),
                    progress = HealthFormat.progress(today.steps, HealthGoals.STEPS)
                )
                Text(
                    text = when (stepStatus) {
                        StepSensorStatus.COUNTING -> "Counting with your phone's step sensor while Nooki is open."
                        StepSensorStatus.NEEDS_PERMISSION -> "Allow activity access so Nooki can count your steps."
                        StepSensorStatus.NO_SENSOR -> "This phone has no step sensor. Add your walks by hand."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stepStatus == StepSensorStatus.NEEDS_PERMISSION) {
                        FilledTonalButton(
                            onClick = onRequestStepPermission,
                            modifier = Modifier.testTag("allow_step_counting")
                        ) { Text("Allow step counting") }
                    }
                    OutlinedButton(onClick = { onAddSteps(500) }) { Text("+500") }
                    OutlinedButton(onClick = { onAddSteps(1000) }) { Text("+1,000") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Last 7 days · ${healthState.daysStepGoalMet(HealthGoals.STEPS)} of 7 days on goal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                WeeklyBars(
                    values = healthState.lastSevenDays.map { it.steps },
                    labels = healthState.lastSevenDays.map { dayLabel(it.dayEpoch, todayEpoch) },
                    goal = HealthGoals.STEPS,
                    color = Marigold
                )
            }
        }

        // Water
        item {
            SectionCard(title = "Water") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricHeadline(
                        icon = Icons.Filled.LocalDrink,
                        accent = NookiSkyInfo,
                        value = "${today.waterGlasses} / ${HealthGoals.WATER_GLASSES}",
                        caption = "glasses today",
                        progress = HealthFormat.progress(today.waterGlasses, HealthGoals.WATER_GLASSES),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { onChangeWater(-1) },
                        enabled = today.waterGlasses > 0,
                        modifier = Modifier.testTag("water_minus")
                    ) { Icon(Icons.Filled.Remove, contentDescription = "One glass less") }
                    FilledTonalIconButton(
                        onClick = { onChangeWater(1) },
                        modifier = Modifier.testTag("water_plus")
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add a glass") }
                }
            }
        }

        // Sleep
        item {
            SectionCard(title = "Sleep last night") {
                MetricHeadline(
                    icon = Icons.Filled.Bedtime,
                    accent = SleepPurple,
                    value = HealthFormat.sleep(today.sleepMinutes),
                    caption = "goal ${HealthFormat.sleep(HealthGoals.SLEEP_MINUTES)}",
                    progress = HealthFormat.progress(today.sleepMinutes, HealthGoals.SLEEP_MINUTES)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(5, 6, 7, 8, 9).forEach { hours ->
                        FilterChip(
                            selected = today.sleepMinutes == hours * 60,
                            onClick = { onSetSleepMinutes(hours * 60) },
                            label = { Text("${hours}h") }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { onSetSleepMinutes(today.sleepMinutes - 30) },
                        label = { Text("−30m") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onSetSleepMinutes(today.sleepMinutes + 30) },
                        label = { Text("+30m") }
                    )
                }
            }
        }

        // Mood
        item {
            SectionCard(title = "How are you feeling?") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("😞" to "Low", "😕" to "Meh", "🙂" to "Okay", "😊" to "Good", "🤩" to "Great")
                        .forEachIndexed { index, (emoji, label) ->
                            val score = index + 1
                            val selected = today.moodScore == score
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) LeafGreen.copy(alpha = 0.25f) else Color.Transparent,
                                onClick = { onSetMood(if (selected) 0 else score) },
                                modifier = Modifier.testTag("mood_$score")
                            ) {
                                Column(
                                    modifier = Modifier.width(56.dp).height(64.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                }
                if (today.moodScore == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SentimentSatisfied,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        EmptyHint("Tap one. Only you can see this.")
                    }
                }
            }
        }

        // Medicines
        item {
            SectionCard(title = "Medicines today") {
                if (medicines.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.MedicalServices,
                            contentDescription = null,
                            tint = Sindoor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        EmptyHint("No medicines. Add one from Home, like \"medicine at 9pm daily\".")
                    }
                } else {
                    medicines.forEach { med ->
                        val done = med.status == ReminderStatus.COMPLETED
                        TaskRow(
                            reminder = med,
                            subtitle = if (done) "Taken" else SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(med.triggerAtMillis)),
                            accent = Sindoor,
                            isDone = done,
                            onPrimary = if (done) {
                                { onIntent(DeskIntent.ReopenDoneTask(med)) }
                            } else {
                                {
                                    NotificationHelper.cancelNotification(context, med.id)
                                    onIntent(DeskIntent.CompleteReminder(med))
                                }
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MetricHeadline(
    icon: ImageVector,
    accent: Color,
    value: String,
    caption: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.18f)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = accent,
            trackColor = accent.copy(alpha = 0.18f)
        )
    }
}

private fun dayLabel(dayEpoch: Long, todayEpoch: Long): String {
    if (dayEpoch == todayEpoch) return "Today"
    // dayEpoch counts local days since 1970-01-01; noon UTC of that day formats to the right weekday.
    val noonUtc = dayEpoch * 86_400_000L + 12 * 3_600_000L
    return SimpleDateFormat("EEE", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date(noonUtc))
}

/** Seven simple bars with a line marking the daily goal. */
@Composable
private fun WeeklyBars(values: List<Int>, labels: List<String>, goal: Int, color: Color) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val goalColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val maxValue = maxOf(goal, values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.5f
            values.forEachIndexed { i, v ->
                val left = i * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                val h = size.height * (v.toFloat() / maxValue)
                if (h > 0f) {
                    drawRoundRect(
                        color = if (v >= goal) LeafGreen else color,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
            val goalY = size.height * (1f - goal.toFloat() / maxValue)
            drawLine(goalColor, Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 2f)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
