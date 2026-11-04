package com.example.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.UserProfileEntity
import com.example.domain.model.ConsistencyStreakState
import com.example.domain.model.DayStreakNode
import com.example.domain.model.SensoryCue
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.mvi.TaskBoardFilter
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.Marigold
import com.example.ui.theme.NeelIndigo
import com.example.ui.theme.NightIndigo
import com.example.ui.theme.Sindoor
import com.example.ui.theme.ThreadIndigoLight
import com.example.ui.theme.WashedCotton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact header pill displayed at the top of the home screen showing the user's
 * Profile avatar/name and live Consistency Streak badge.
 */
@Composable
fun UserProfileAndStreakBannerChip(
    userProfile: UserProfileEntity,
    streakState: ConsistencyStreakState,
    isSoundEnabled: Boolean = true,
    isHapticsEnabled: Boolean = true,
    onToggleSound: () -> Unit = {},
    onToggleHaptics: () -> Unit = {},
    onOpenProfileTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable { onOpenProfileTab() }
            .testTag("home_profile_streak_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NightIndigo)
                        .border(1.5.dp, Marigold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userProfile.avatarBadge,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column {
                    Text(
                        text = "${userProfile.displayName} • ${userProfile.companionTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = WashedCotton,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Goal: ${streakState.completedTodayCount}/${streakState.dailyGoalCount} today • Tap for Profile & Tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = WashedCotton.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Quick Mute Toggles (Sound + Haptics) + Consistency Streak Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = NightIndigo,
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSoundEnabled) Marigold else WashedCotton.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { onToggleSound() }
                        .testTag("home_sound_toggle_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSoundEnabled) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = if (isSoundEnabled) "Mute cozy sound effects" else "Unmute cozy sound effects",
                            tint = if (isSoundEnabled) Marigold else WashedCotton.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = NightIndigo,
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            width = 1.dp,
                            color = if (isHapticsEnabled) LeafGreen else WashedCotton.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { onToggleHaptics() }
                        .testTag("home_haptics_toggle_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = if (isHapticsEnabled) "Mute haptic feedback" else "Unmute haptic feedback",
                            tint = if (isHapticsEnabled) LeafGreen else WashedCotton.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (streakState.isTodayStreakSecured) LeafGreen.copy(alpha = 0.2f) else Marigold.copy(alpha = 0.2f),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (streakState.isTodayStreakSecured) LeafGreen else Marigold,
                            shape = RoundedCornerShape(50)
                        )
                        .testTag("consistency_streak_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Consistency Streak",
                            tint = if (streakState.isTodayStreakSecured) LeafGreen else Marigold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${streakState.currentStreakDays}d",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (streakState.isTodayStreakSecured) LeafGreen else Marigold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual Consistency Streak Card displaying:
 * - Current consecutive days streak, Best streak, and Active days count
 * - 7-day Thread Knot timeline where each day is a knot on Nooki's horizontal thread
 * - Daily reminder goal progress bar
 */
@Composable
fun ConsistencyStreakCard(
    streakState: ConsistencyStreakState,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (streakState.isTodayStreakSecured) scheme.secondary.copy(alpha = 0.55f) else scheme.primary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("consistency_streak_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = streakState.streakTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (streakState.isTodayStreakSecured) LeafGreen else Marigold
                    )
                    Text(
                        text = streakState.streakSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton.copy(alpha = 0.84f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = NightIndigo
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${streakState.currentStreakDays}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Marigold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Days",
                                style = MaterialTheme.typography.labelSmall,
                                color = WashedCotton.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            // 7-Day Thread Knot Timeline
            if (streakState.last7DaysTimeline.isNotEmpty()) {
                SevenDayThreadTimeline(days = streakState.last7DaysTimeline)
            }

            // Daily Goal Progress & All-Time Metrics
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Today's Goal: ${streakState.completedTodayCount}/${streakState.dailyGoalCount} completed",
                        style = MaterialTheme.typography.labelLarge,
                        color = WashedCotton
                    )
                    Text(
                        text = "Best: ${streakState.longestStreakDays}d • Active Days: ${streakState.totalActiveDays}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Marigold
                    )
                }
                LinearProgressIndicator(
                    progress = { streakState.dailyGoalProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (streakState.isTodayStreakSecured) LeafGreen else Marigold,
                    trackColor = NightIndigo
                )
            }
        }
    }
}

@Composable
private fun SevenDayThreadTimeline(days: List<DayStreakNode>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Horizontal connecting thread behind the 7 day knots
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.Center)
        ) {
            val y = size.height / 2f
            drawLine(
                color = ThreadIndigoLight.copy(alpha = 0.4f),
                start = Offset(24.dp.toPx(), y),
                end = Offset(size.width - 24.dp.toPx(), y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            days.forEach { day ->
                val nodeColor = when {
                    day.isStreakDay -> LeafGreen
                    day.isToday -> Marigold
                    else -> ThreadIndigoLight.copy(alpha = 0.45f)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = day.dayShortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) Marigold else WashedCotton.copy(alpha = 0.75f),
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (day.isStreakDay) LeafGreen else NightIndigo)
                            .border(
                                width = if (day.isToday) 2.dp else 1.5.dp,
                                color = nodeColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day.isStreakDay) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed ${day.completedCount} reminders",
                                tint = NightIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = day.dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = WashedCotton.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Text(
                        text = if (day.completedCount > 0) "${day.completedCount} done" else if (day.isToday) "Today" else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isStreakDay) LeafGreen else WashedCotton.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

/**
 * Interactive section storing and managing **To-Do Tasks** and **Done Tasks**:
 * - Switch between `To-Do Tasks` and `Done Tasks` tabs
 * - Add new tasks directly with priority selection
 * - Check off a To-Do task to complete it (updating Nooki's MoodState & Consistency Streak)
 * - Reopen a Done task back to To-Do or delete/clear tasks
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoAndDoneTasksSection(
    todoTasks: List<ReminderEntity>,
    doneTasks: List<ReminderEntity>,
    selectedFilter: TaskBoardFilter,
    nowMillis: Long,
    onSelectFilter: (TaskBoardFilter) -> Unit,
    onQuickAddTodo: (String, ReminderPriority, ReminderCategory) -> Unit,
    onCompleteTask: (ReminderEntity) -> Unit,
    onReopenDoneTask: (ReminderEntity) -> Unit,
    onDeleteTask: (ReminderEntity) -> Unit,
    onClearAllDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newTaskTitle by rememberSaveable { mutableStateOf("") }
    var selectedPriority by rememberSaveable { mutableStateOf(ReminderPriority.MEDIUM) }

    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .testTag("todo_done_tasks_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "To-Do & Done Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        color = WashedCotton
                    )
                    Text(
                        text = "${todoTasks.size} To-Do • ${doneTasks.size} Done",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton.copy(alpha = 0.75f)
                    )
                }

                if (selectedFilter == TaskBoardFilter.DONE && doneTasks.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAllDone,
                        modifier = Modifier.testTag("clear_done_tasks_button")
                    ) {
                        Text("Clear Done", color = Sindoor)
                    }
                }
            }

            // Filter Toggle Row: To-Do Tasks vs Done Tasks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == TaskBoardFilter.TODO,
                    onClick = { onSelectFilter(TaskBoardFilter.TODO) },
                    label = {
                        Text(
                            text = "To-Do Tasks (${todoTasks.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Marigold,
                        selectedLabelColor = NightIndigo,
                        selectedLeadingIconColor = NightIndigo
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_todo_tasks")
                )

                FilterChip(
                    selected = selectedFilter == TaskBoardFilter.DONE,
                    onClick = { onSelectFilter(TaskBoardFilter.DONE) },
                    label = {
                        Text(
                            text = "Done Tasks (${doneTasks.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LeafGreen,
                        selectedLabelColor = NightIndigo,
                        selectedLeadingIconColor = NightIndigo
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_done_tasks")
                )
            }

            // Quick Add To-Do Task Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = {
                        Text(
                            text = "Add a To-Do task (e.g. \"Call CA at 4pm\", \"Buy milk\")...",
                            color = WashedCotton.copy(alpha = 0.55f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Marigold,
                        unfocusedBorderColor = ThreadIndigoLight.copy(alpha = 0.45f),
                        focusedContainerColor = NightIndigo,
                        unfocusedContainerColor = NightIndigo.copy(alpha = 0.7f),
                        focusedTextColor = WashedCotton,
                        unfocusedTextColor = WashedCotton
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    onQuickAddTodo(newTaskTitle, selectedPriority, ReminderCategory.DESK)
                                    newTaskTitle = ""
                                }
                            },
                            modifier = Modifier.testTag("quick_add_todo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add To-Do Task",
                                tint = Marigold
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_todo_input")
                )

                // Priority chips for new task
                AnimatedVisibility(visible = newTaskTitle.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReminderPriority.entries.forEach { priority ->
                            FilterChip(
                                selected = selectedPriority == priority,
                                onClick = { selectedPriority = priority },
                                label = { Text("${priority.label} (+${priority.xpReward} XP)") }
                            )
                        }
                    }
                }
            }

            // Task Items List
            val displayedTasks = if (selectedFilter == TaskBoardFilter.TODO) todoTasks else doneTasks

            if (displayedTasks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NightIndigo.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (selectedFilter == TaskBoardFilter.TODO) {
                            "All caught up! Add a To-Do task above or tell Nooki in one breath."
                        } else {
                            "No Done tasks yet. Check off any To-Do task to build your Consistency Streak!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton.copy(alpha = 0.7f),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayedTasks.forEach { task ->
                        val isCompleted = task.status == ReminderStatus.COMPLETED
                        TaskBoardItemRow(
                            task = task,
                            isCompleted = isCompleted,
                            nowMillis = nowMillis,
                            onToggleComplete = {
                                if (isCompleted) {
                                    onReopenDoneTask(task)
                                } else {
                                    onCompleteTask(task)
                                }
                            },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskBoardItemRow(
    task: ReminderEntity,
    isCompleted: Boolean,
    nowMillis: Long,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when {
        isCompleted -> LeafGreen
        task.status == ReminderStatus.MISSED || task.triggerAtMillis < nowMillis -> Sindoor
        task.priority == ReminderPriority.URGENT -> Sindoor
        task.priority == ReminderPriority.HIGH -> Marigold
        else -> ThreadIndigoLight
    }

    val subtitleText = remember(task.triggerAtMillis, task.completedAtMillis, isCompleted, nowMillis) {
        if (isCompleted) {
            val doneTime = task.completedAtMillis ?: task.createdAtMillis
            val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(doneTime))
            "Done • $fmt • +${task.petMoodImpact} XP"
        } else {
            val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(task.triggerAtMillis))
            "${task.priority.label} • Due $fmt • +${task.petMoodImpact} XP"
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NightIndigo,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag(if (isCompleted) "done_task_row_${task.id}" else "todo_task_row_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.testTag(if (isCompleted) "reopen_task_btn_${task.id}" else "complete_task_btn_${task.id}")
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isCompleted) "Move back to To-Do" else "Mark task as Done",
                    tint = accentColor
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCompleted) WashedCotton.copy(alpha = 0.65f) else WashedCotton,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor
                )
            }

            if (isCompleted) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.testTag("restore_todo_icon_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Restore to To-Do",
                        tint = Marigold
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_task_btn_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete task",
                    tint = WashedCotton.copy(alpha = 0.55f)
                )
            }
        }
    }
}

/**
 * Editable User Profile Card persisted in Room (`user_profile` table).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserProfileCard(
    userProfile: UserProfileEntity,
    level: Int,
    totalXp: Int,
    happinessScore: Int,
    todoCount: Int,
    doneCount: Int,
    streakState: ConsistencyStreakState,
    isSoundEnabled: Boolean = true,
    isHapticsEnabled: Boolean = true,
    onToggleSound: () -> Unit = {},
    onToggleHaptics: () -> Unit = {},
    onPreviewCue: (SensoryCue) -> Unit = {},
    onSaveProfile: (String, String, String, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var nameInput by rememberSaveable(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var titleInput by rememberSaveable(userProfile.companionTitle) { mutableStateOf(userProfile.companionTitle) }
    var badgeInput by rememberSaveable(userProfile.avatarBadge) { mutableStateOf(userProfile.avatarBadge) }
    var mottoInput by rememberSaveable(userProfile.bioMotto) { mutableStateOf(userProfile.bioMotto) }
    var dailyGoalInput by rememberSaveable(userProfile.dailyReminderGoal) {
        mutableIntStateOf(userProfile.dailyReminderGoal)
    }

    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .testTag("user_profile_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(NightIndigo)
                            .border(2.dp, Marigold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile.avatarBadge,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Column {
                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = WashedCotton,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${userProfile.companionTitle} • Lv.$level ($totalXp XP)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Marigold
                        )
                        Text(
                            text = userProfile.bioMotto,
                            style = MaterialTheme.typography.bodySmall,
                            color = WashedCotton.copy(alpha = 0.78f)
                        )
                    }
                }

                IconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.testTag("edit_user_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit User Profile",
                        tint = Marigold
                    )
                }
            }

            // Profile Stats Row (Streak, Best Streak, Done Tasks, To-Do)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStatBox(
                    value = "${streakState.currentStreakDays}d",
                    label = "Streak",
                    tint = Marigold,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatBox(
                    value = "${streakState.longestStreakDays}d",
                    label = "Best Streak",
                    tint = LeafGreen,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatBox(
                    value = "$doneCount",
                    label = "Done Tasks",
                    tint = LeafGreen,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatBox(
                    value = "$todoCount",
                    label = "To-Do",
                    tint = ThreadIndigoLight,
                    modifier = Modifier.weight(1f)
                )
            }

            // Cozy Soundscapes & Haptic Waveforms Settings Section in Profile
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NightIndigo,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ThreadIndigoLight.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sensory Feedback (Cozy SoundPool & VibrationEffect)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Marigold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = isSoundEnabled,
                            onClick = onToggleSound,
                            label = {
                                Text(if (isSoundEnabled) "Wooden Chime & Purr: ON" else "Sound: Muted")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSoundEnabled) {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Marigold,
                                selectedLabelColor = NightIndigo,
                                selectedLeadingIconColor = NightIndigo
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_sound_toggle")
                        )

                        FilterChip(
                            selected = isHapticsEnabled,
                            onClick = onToggleHaptics,
                            label = {
                                Text(if (isHapticsEnabled) "Haptics: ON" else "Haptics: Muted")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LeafGreen,
                                selectedLabelColor = NightIndigo,
                                selectedLeadingIconColor = NightIndigo
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_haptics_toggle")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { onPreviewCue(SensoryCue.PET_HEAD_PAT) },
                            label = { Text("Test Fox Purr", color = WashedCotton) },
                            modifier = Modifier.testTag("preview_fox_purr_button")
                        )
                        AssistChip(
                            onClick = { onPreviewCue(SensoryCue.PET_PLAY_BOOP) },
                            label = { Text("Test Wooden Hop", color = WashedCotton) },
                            modifier = Modifier.testTag("preview_wooden_hop_button")
                        )
                        AssistChip(
                            onClick = { onPreviewCue(SensoryCue.TASK_COMPLETED) },
                            label = { Text("Test Completion Chime", color = WashedCotton) },
                            modifier = Modifier.testTag("preview_completion_chime_button")
                        )
                    }
                }
            }

            // Inline Profile Editor
            AnimatedVisibility(visible = isEditing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose Avatar Badge",
                        style = MaterialTheme.typography.labelLarge,
                        color = Marigold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("🦊", "🐯", "🪷", "✨", "🌿", "🪔").forEach { emoji ->
                            FilterChip(
                                selected = badgeInput == emoji,
                                onClick = { badgeInput = emoji },
                                label = { Text(emoji) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Companion Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_title_input")
                    )

                    OutlinedTextField(
                        value = mottoInput,
                        onValueChange = { mottoInput = it },
                        label = { Text("Personal Motto") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_motto_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Reminder Goal: $dailyGoalInput task(s)/day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WashedCotton
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { dailyGoalInput = (dailyGoalInput - 1).coerceAtLeast(1) }
                            ) {
                                Text("-1")
                            }
                            OutlinedButton(
                                onClick = { dailyGoalInput = (dailyGoalInput + 1).coerceAtMost(10) }
                            ) {
                                Text("+1")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel", color = WashedCotton)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSaveProfile(
                                    nameInput,
                                    titleInput,
                                    badgeInput,
                                    mottoInput,
                                    dailyGoalInput
                                )
                                isEditing = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Marigold,
                                contentColor = NightIndigo
                            ),
                            modifier = Modifier.testTag("save_user_profile_button")
                        ) {
                            Text("Save Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatBox(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NightIndigo,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = WashedCotton.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dedicated screen for User Profile, Theme Selector (with synced 3D particle colors),
 * Pet Milestone Journal scrollable history feed, Consistency Streak 7-day tracker,
 * and To-Do / Done Tasks manager.
 */
@Composable
fun NookiTasksAndProfileScreen(
    uiState: DeskUiState,
    onSyncGpsWeather: () -> Unit = {},
    onIntent: (DeskIntent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. User Profile Card (with inline editor & sensory controls persisted to Room)
        item {
            UserProfileCard(
                userProfile = uiState.userProfile,
                level = uiState.level,
                totalXp = uiState.totalXp,
                happinessScore = uiState.moodState.happinessScore,
                todoCount = uiState.todoTasks.size,
                doneCount = uiState.doneTasks.size,
                streakState = uiState.consistencyStreak,
                isSoundEnabled = uiState.isSoundEnabled,
                isHapticsEnabled = uiState.isHapticsEnabled,
                onToggleSound = { onIntent(DeskIntent.ToggleSoundEnabled) },
                onToggleHaptics = { onIntent(DeskIntent.ToggleHapticsEnabled) },
                onPreviewCue = { cue -> onIntent(DeskIntent.PreviewSensoryCue(cue)) },
                onSaveProfile = { name, title, badge, motto, goal ->
                    onIntent(
                        DeskIntent.SaveUserProfile(
                            displayName = name,
                            companionTitle = title,
                            avatarBadge = badge,
                            bioMotto = motto,
                            dailyReminderGoal = goal
                        )
                    )
                }
            )
        }

        // 2. Local Weather & 3D Desk Environment Bar (Location API)
        item {
            LocalWeatherEnvironmentBar(
                weatherState = uiState.weatherState,
                onSyncGpsWeather = onSyncGpsWeather,
                onSelectWeatherCondition = { condition ->
                    onIntent(DeskIntent.SelectWeatherOverride(condition))
                }
            )
        }

        // 3. Sanctuary Theme Selector (changes app color palette & syncs 3D particle colors)
        item {
            ThemeSelectorCard(
                selectedTheme = uiState.selectedTheme,
                onSelectTheme = { theme -> onIntent(DeskIntent.SelectAppTheme(theme)) }
            )
        }

        // 3. Pet Milestone Journal (records high-streak achievements, personality shifts & memories in a scrollable feed)
        item {
            PetMilestoneJournalSection(
                entries = uiState.journalEntries,
                selectedFilter = uiState.selectedJournalFilter,
                onSelectFilter = { filter -> onIntent(DeskIntent.SelectJournalFilter(filter)) },
                onAddCustomMilestone = { title, narrative, mType ->
                    onIntent(
                        DeskIntent.AddCustomJournalNote(
                            title = title,
                            narrative = narrative,
                            milestoneType = mType
                        )
                    )
                },
                onDeleteEntry = { id -> onIntent(DeskIntent.DeleteJournalEntry(id)) }
            )
        }

        // 4. Consistency Streak Card (Consecutive days of completed scheduled reminders + 7-day thread timeline)
        item {
            ConsistencyStreakCard(streakState = uiState.consistencyStreak)
        }

        // 5. Dedicated To-Do Tasks & Done Tasks Section
        item {
            TodoAndDoneTasksSection(
                todoTasks = uiState.todoTasks,
                doneTasks = uiState.doneTasks,
                selectedFilter = uiState.selectedTaskFilter,
                nowMillis = uiState.nowMillis,
                onSelectFilter = { onIntent(DeskIntent.SelectTaskBoardFilter(it)) },
                onQuickAddTodo = { title, priority, category ->
                    onIntent(DeskIntent.QuickAddTodoTask(title, priority, category))
                },
                onCompleteTask = { onIntent(DeskIntent.CompleteReminder(it)) },
                onReopenDoneTask = { onIntent(DeskIntent.ReopenDoneTask(it)) },
                onDeleteTask = { onIntent(DeskIntent.DeleteReminder(it)) },
                onClearAllDone = { onIntent(DeskIntent.ClearCompleted) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
