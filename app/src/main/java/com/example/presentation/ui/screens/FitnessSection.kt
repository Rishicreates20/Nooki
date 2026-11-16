package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.WorkoutEntity
import com.example.domain.health.FitnessGoals
import com.example.domain.health.HealthFormat
import com.example.domain.health.WorkoutMath
import com.example.domain.health.WorkoutType
import com.example.presentation.viewmodel.HealthUiState
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.Sindoor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FitnessOrange = Color(0xFFFF7A45)

/**
 * Fitness tracker: start a timed workout or log one that already happened, then see active
 * minutes, calories and the weekly goal.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FitnessSection(
    state: HealthUiState,
    dayLabels: List<String>,
    onStartWorkout: (WorkoutType) -> Unit,
    onFinishWorkout: () -> Unit,
    onCancelWorkout: () -> Unit,
    onLogWorkout: (WorkoutType, Int) -> Unit,
    onDeleteWorkout: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var selectedType by rememberSaveable { mutableStateOf(WorkoutType.WALK.name) }
    var timedMode by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    val type = WorkoutType.fromName(selectedType)

    SectionCard(title = "Fitness", modifier = Modifier.testTag("fitness_section")) {
        // Today + week summary
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Active today",
                value = "${state.activeMinutesToday} min",
                caption = "goal ${FitnessGoals.ACTIVE_MINUTES_PER_DAY} min",
                icon = Icons.Filled.Timer,
                accent = FitnessOrange,
                progress = HealthFormat.progress(state.activeMinutesToday, FitnessGoals.ACTIVE_MINUTES_PER_DAY),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Calories",
                value = "${state.caloriesToday}",
                caption = "kcal burned today (est.)",
                icon = Icons.Filled.LocalFireDepartment,
                accent = Sindoor,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "This week: ${state.activeMinutesThisWeek} of ${FitnessGoals.ACTIVE_MINUTES_PER_WEEK} active minutes",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface.copy(alpha = 0.8f)
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { HealthFormat.progress(state.activeMinutesThisWeek, FitnessGoals.ACTIVE_MINUTES_PER_WEEK) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = if (state.activeMinutesThisWeek >= FitnessGoals.ACTIVE_MINUTES_PER_WEEK) LeafGreen else FitnessOrange,
            trackColor = FitnessOrange.copy(alpha = 0.18f)
        )
        WeeklyBars(
            values = state.activeMinutesByDay,
            labels = dayLabels,
            goal = FitnessGoals.ACTIVE_MINUTES_PER_DAY,
            color = FitnessOrange
        )

        // Live timer
        val active = state.activeWorkout
        if (active != null) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(active.startedAtMillis) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1_000L)
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FitnessOrange.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().testTag("active_workout")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${active.type.emoji} ${active.type.label} in progress",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    val elapsed = now - active.startedAtMillis
                    Text(
                        text = WorkoutMath.formatTimer(elapsed),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = FitnessOrange
                    )
                    Text(
                        text = "≈ ${WorkoutMath.estimateCalories(active.type, (elapsed / 60_000L).toInt())} kcal so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onFinishWorkout,
                            colors = ButtonDefaults.buttonColors(containerColor = LeafGreen),
                            modifier = Modifier.testTag("finish_workout")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Finish & save")
                        }
                        TextButton(onClick = onCancelWorkout) { Text("Discard") }
                    }
                }
            }
        } else {
            // Pick a workout
            Text(
                text = "Pick a workout",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface.copy(alpha = 0.8f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WorkoutType.entries.forEach { t ->
                    FilterChip(
                        selected = t == type,
                        onClick = { selectedType = t.name },
                        label = { Text("${t.emoji} ${t.label}") },
                        modifier = Modifier.testTag("workout_type_${t.name}")
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = timedMode,
                    onClick = { timedMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Start timer") }
                SegmentedButton(
                    selected = !timedMode,
                    onClick = { timedMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Already done") }
            }
            if (timedMode) {
                Button(
                    onClick = { onStartWorkout(type) },
                    colors = ButtonDefaults.buttonColors(containerColor = FitnessOrange),
                    modifier = Modifier.fillMaxWidth().testTag("start_workout")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start ${type.label.lowercase()}")
                }
            } else {
                Text(
                    text = "How long was it?",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface.copy(alpha = 0.7f)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(10, 15, 20, 30, 45, 60, 90).forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = { onLogWorkout(type, minutes) },
                            label = { Text("$minutes min") },
                            modifier = Modifier.testTag("log_workout_$minutes")
                        )
                    }
                }
            }
        }

        // Today's workouts
        val todays = state.todayWorkouts
        if (todays.isEmpty()) {
            EmptyHint("No workouts yet today. Even a 10 minute walk counts.")
        } else {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface.copy(alpha = 0.8f)
            )
            todays.forEach { w ->
                val wt = WorkoutType.fromName(w.type)
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("workout_row_${w.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(wt.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wt.label, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${w.durationMinutes} min · ${w.calories} kcal · " +
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(w.startedAtMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = { pendingDelete = w }) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete workout",
                            tint = scheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = scheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.width(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Calories are estimates for a ${FitnessGoals.DEFAULT_BODY_WEIGHT_KG.toInt()} kg adult.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }

    pendingDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this workout?") },
            text = { Text("${WorkoutType.fromName(w.type).label}, ${w.durationMinutes} min") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteWorkout(w.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } }
        )
    }
}
