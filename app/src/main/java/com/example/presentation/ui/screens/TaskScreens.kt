package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.AccountEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.domain.health.HealthFormat
import com.example.domain.health.HealthGoals
import com.example.domain.model.ConsistencyStreakCalculator
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.ui.components.ConsistencyStreakCard
import com.example.presentation.ui.components.Nooki3DCenterpiece
import com.example.presentation.ui.components.OneBreathCaptureSection
import com.example.presentation.ui.components.SolidRingingBlock
import com.example.presentation.ui.components.ThreadEmptyNode
import com.example.presentation.ui.components.ThreadEveningWrapRow
import com.example.presentation.ui.components.ThreadKnotRow
import com.example.presentation.ui.components.ThreadSectionHeader
import com.example.presentation.ui.components.ThreadSuggestionNode
import com.example.presentation.viewmodel.HealthUiState
import com.example.scheduler.NotificationHelper
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.Marigold
import com.example.ui.theme.NookiSkyInfo
import com.example.ui.theme.Sindoor
import com.example.ui.theme.ThreadIndigoLight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

private fun DeskUiState.completedToday(): List<ReminderEntity> {
    val today = ConsistencyStreakCalculator.toLocalDayEpoch(nowMillis)
    return doneTasks.filter { completedDay(it) == today }
}

private fun completedDay(reminder: ReminderEntity): Long =
    if (reminder.completedDayEpoch > 0L) {
        reminder.completedDayEpoch
    } else {
        ConsistencyStreakCalculator.toLocalDayEpoch(reminder.completedAtMillis ?: reminder.createdAtMillis)
    }

private fun DeskUiState.pendingCount(): Int =
    youMissedKnots.size + youForgotAndHeldKnots.size + todayAndComingUpKnots.size + ringingReminders.size

private fun greeting(nowMillis: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}

private fun dueLabel(reminder: ReminderEntity, nowMillis: Long): String {
    if (reminder.status == ReminderStatus.MISSED || reminder.triggerAtMillis < nowMillis) {
        val mins = ((nowMillis - reminder.triggerAtMillis) / 60_000L).coerceAtLeast(1L)
        return when {
            mins < 60 -> "Missed ${mins}m ago"
            mins < 24 * 60 -> "Missed ${mins / 60}h ago"
            else -> "Missed ${mins / (24 * 60)}d ago"
        }
    }
    val diff = reminder.triggerAtMillis - nowMillis
    val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(reminder.triggerAtMillis))
    return when {
        diff < 60 * 60_000L -> "In ${(diff / 60_000L).coerceAtLeast(1)}m · $clock"
        isSameDay(reminder.triggerAtMillis, nowMillis) -> "Today · $clock"
        else -> SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(reminder.triggerAtMillis))
    }
}

private fun isSameDay(a: Long, b: Long): Boolean =
    ConsistencyStreakCalculator.toLocalDayEpoch(a) == ConsistencyStreakCalculator.toLocalDayEpoch(b)

/** Simple, readable row for a task: tick circle, title, when, and optional actions. */
@Composable
fun TaskRow(
    reminder: ReminderEntity,
    subtitle: String,
    accent: Color,
    isDone: Boolean,
    onPrimary: (() -> Unit)?,
    onDelete: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_row_${reminder.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onPrimary?.invoke() },
            enabled = onPrimary != null,
            modifier = Modifier.testTag(if (isDone) "reopen_${reminder.id}" else "complete_${reminder.id}")
        ) {
            if (isDone) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Done. Tap to move back to pending", tint = LeafGreen)
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(2.dp, accent)
                ) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {}
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = if (isDone) 0.7f else 1f),
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDone) scheme.onSurface.copy(alpha = 0.55f) else accent
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete",
                    tint = scheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// =================================================================================================
// HOME
// =================================================================================================

/**
 * Home dashboard: who you are, Nooki, what needs you right now, and four tiles that open each
 * section. Everything else lives in its own tab.
 */
@Composable
fun HomeScreen(
    account: AccountEntity,
    uiState: DeskUiState,
    healthState: HealthUiState,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onNavigate: (NookiTab) -> Unit,
    onIntent: (DeskIntent) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val completedToday = remember(uiState.doneTasks, uiState.nowMillis / 60_000L) { uiState.completedToday() }
    val pendingCount = uiState.pendingCount()
    val nextUp = remember(uiState.todayAndComingUpKnots) {
        uiState.todayAndComingUpKnots.sortedBy { it.triggerAtMillis }.take(3)
    }
    val name = uiState.userProfile.displayName.ifBlank { account.displayName }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp)
            .testTag("screen_home"),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${greeting(uiState.nowMillis)}, $name",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            pendingCount == 0 -> "You're all caught up. Nice."
                            uiState.youMissedKnots.isNotEmpty() -> "${uiState.youMissedKnots.size} missed · $pendingCount pending in total"
                            else -> "$pendingCount thing${if (pendingCount == 1) "" else "s"} pending"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onBackground.copy(alpha = 0.75f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = scheme.primary.copy(alpha = 0.2f),
                    onClick = { onNavigate(NookiTab.PROFILE) },
                    modifier = Modifier.testTag("home_profile_avatar")
                ) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Text(uiState.userProfile.avatarBadge.ifBlank { "🦊" }, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        // Ringing right now always comes first.
        items(uiState.ringingReminders, key = { "home_ringing_${it.id}" }) { ringing ->
            SolidRingingBlock(
                reminder = ringing,
                onUntieDone = {
                    NotificationHelper.cancelNotification(context, ringing.id)
                    onIntent(DeskIntent.CompleteReminder(ringing))
                },
                onAskLater = {
                    NotificationHelper.cancelNotification(context, ringing.id)
                    onIntent(DeskIntent.SnoozeReminder(ringing, 5))
                }
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Nooki3DCenterpiece(
                    mood = uiState.petMood,
                    moodState = uiState.moodState,
                    appTheme = uiState.selectedTheme,
                    weatherCondition = uiState.weatherState.condition,
                    speechText = uiState.speechBubbleText,
                    isAutoRotateEnabled = uiState.isAutoRotate3DEnabled,
                    petCombo = uiState.petInteractionCombo,
                    onPetTap = { onIntent(DeskIntent.PetNookiHead) },
                    onPetGesture = { gesture -> onIntent(DeskIntent.TriggerPetGesture(gesture)) },
                    onToggleAutoRotate = { onIntent(DeskIntent.ToggleAutoRotate3D) }
                )
            }
        }

        item {
            SectionCard(title = "Add something") {
                OneBreathCaptureSection(
                    inputText = uiState.breathInputText,
                    liveParsed = uiState.liveParsedCapture,
                    onInputChanged = { onIntent(DeskIntent.UpdateBreathInput(it)) },
                    onSave = { onIntent(DeskIntent.SaveBreathInput) },
                    onVoiceClick = onStartVoiceCapture
                )
            }
        }

        if (!hasNotificationPermission) {
            item {
                TextButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.testTag("grant_notifications_button")
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Marigold)
                    Spacer(Modifier.width(8.dp))
                    Text("Allow notifications so Nooki can ring when something is due", color = Marigold)
                }
            }
        }

        item {
            Text(
                text = "Today at a glance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Pending",
                    value = pendingCount.toString(),
                    caption = if (uiState.youMissedKnots.isEmpty()) "Nothing missed" else "${uiState.youMissedKnots.size} missed",
                    icon = Icons.Filled.Schedule,
                    accent = if (uiState.youMissedKnots.isEmpty()) ThreadIndigoLight else Sindoor,
                    modifier = Modifier.weight(1f).testTag("tile_pending"),
                    onClick = { onNavigate(NookiTab.PENDING) }
                )
                val goal = uiState.userProfile.dailyReminderGoal.coerceAtLeast(1)
                StatTile(
                    label = "Steps completed",
                    value = "${completedToday.size}/$goal",
                    caption = "Done today · ${uiState.consistencyStreak.currentStreakDays}d streak",
                    icon = Icons.Filled.Check,
                    accent = LeafGreen,
                    progress = HealthFormat.progress(completedToday.size, goal),
                    modifier = Modifier.weight(1f).testTag("tile_done"),
                    onClick = { onNavigate(NookiTab.DONE) }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Walking",
                    value = HealthFormat.steps(healthState.today.steps),
                    caption = "steps · ${healthState.activeMinutesToday} active min",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    accent = Marigold,
                    progress = HealthFormat.progress(healthState.today.steps, HealthGoals.STEPS),
                    modifier = Modifier.weight(1f).testTag("tile_steps"),
                    onClick = { onNavigate(NookiTab.HEALTH) }
                )
                StatTile(
                    label = "Water",
                    value = "${healthState.today.waterGlasses}/${HealthGoals.WATER_GLASSES}",
                    caption = "glasses today",
                    icon = Icons.Filled.LocalDrink,
                    accent = NookiSkyInfo,
                    progress = HealthFormat.progress(healthState.today.waterGlasses, HealthGoals.WATER_GLASSES),
                    modifier = Modifier.weight(1f).testTag("tile_water"),
                    onClick = { onNavigate(NookiTab.HEALTH) }
                )
            }
        }

        if (uiState.youMissedKnots.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Missed (${uiState.youMissedKnots.size})",
                    actionLabel = "See all",
                    onAction = { onNavigate(NookiTab.PENDING) }
                ) {
                    uiState.youMissedKnots.take(3).forEach { item ->
                        TaskRow(
                            reminder = item,
                            subtitle = dueLabel(item, uiState.nowMillis),
                            accent = Sindoor,
                            isDone = false,
                            onPrimary = {
                                NotificationHelper.cancelNotification(context, item.id)
                                onIntent(DeskIntent.CompleteReminder(item))
                            }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Next up",
                actionLabel = "All pending",
                onAction = { onNavigate(NookiTab.PENDING) }
            ) {
                if (nextUp.isEmpty()) {
                    EmptyHint("Nothing coming up. Add something above, like \"call mom at 6pm\".")
                } else {
                    nextUp.forEach { item ->
                        TaskRow(
                            reminder = item,
                            subtitle = dueLabel(item, uiState.nowMillis),
                            accent = ThreadIndigoLight,
                            isDone = false,
                            onPrimary = {
                                NotificationHelper.cancelNotification(context, item.id)
                                onIntent(DeskIntent.CompleteReminder(item))
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =================================================================================================
// PENDING
// =================================================================================================

/**
 * Everything still to do, grouped into Missed, Held for you, and Today & coming up.
 */
@Composable
fun PendingScreen(
    uiState: DeskUiState,
    onStartVoiceCapture: () -> Unit,
    onFireAlarmNow: (ReminderEntity) -> Unit,
    onIntent: (DeskIntent) -> Unit
) {
    val context = LocalContext.current
    val complete: (ReminderEntity) -> Unit = {
        NotificationHelper.cancelNotification(context, it.id)
        onIntent(DeskIntent.CompleteReminder(it))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp)
            .testTag("screen_pending"),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            ScreenHeader(
                title = "Pending",
                subtitle = "Everything still to do. Tap the knot on the left when it's done."
            )
            Spacer(Modifier.height(10.dp))
            SectionCard {
                OneBreathCaptureSection(
                    inputText = uiState.breathInputText,
                    liveParsed = uiState.liveParsedCapture,
                    onInputChanged = { onIntent(DeskIntent.UpdateBreathInput(it)) },
                    onSave = { onIntent(DeskIntent.SaveBreathInput) },
                    onVoiceClick = onStartVoiceCapture
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        items(uiState.ringingReminders, key = { "pending_ringing_${it.id}" }) { ringing ->
            SolidRingingBlock(
                reminder = ringing,
                onUntieDone = { complete(ringing) },
                onAskLater = {
                    NotificationHelper.cancelNotification(context, ringing.id)
                    onIntent(DeskIntent.SnoozeReminder(ringing, 5))
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        // Missed
        item {
            ThreadSectionHeader(
                threadColor = Sindoor,
                title = "Missed (${uiState.youMissedKnots.size})",
                subtitle = "Reminders you didn't get to yet"
            )
        }
        if (uiState.youMissedKnots.isEmpty()) {
            item { ThreadEmptyNode(threadColor = Sindoor, text = "Nothing missed right now.") }
        } else {
            items(uiState.youMissedKnots, key = { "missed_${it.id}" }) { item ->
                ThreadKnotRow(
                    reminder = item,
                    knotColor = Sindoor,
                    nowMillis = uiState.nowMillis,
                    isDone = false,
                    onTapKnotToUntie = { complete(item) },
                    onSnooze = { onIntent(DeskIntent.SnoozeReminder(item, 15)) },
                    onFireNow = { onFireAlarmNow(item) },
                    onDelete = { onIntent(DeskIntent.DeleteReminder(item)) }
                )
            }
        }

        // Held for you (money, lent items, saved places, things snoozed again and again)
        item {
            val moneySummary = buildString {
                if (uiState.netMoneyOwedToYouInr > 0) append("People owe you ₹${uiState.netMoneyOwedToYouInr}")
                if (uiState.netMoneyYouOweInr > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("You owe ₹${uiState.netMoneyYouOweInr}")
                }
                if (isEmpty()) append("Money, lent things, saved places and dates coming close")
            }
            ThreadSectionHeader(
                threadColor = Marigold,
                title = "Held for you (${uiState.youForgotAndHeldKnots.size})",
                subtitle = moneySummary
            )
        }
        items(uiState.noticeSuggestions, key = { "suggestion_${it.sanitizedTitle}_${it.timestampMillis}" }) { suggestion ->
            ThreadSuggestionNode(
                suggestion = suggestion,
                onAccept = { onIntent(DeskIntent.AcceptNoticeSuggestion(suggestion)) },
                onDismiss = { onIntent(DeskIntent.DismissNoticeSuggestion(suggestion)) }
            )
        }
        if (uiState.youForgotAndHeldKnots.isEmpty() && uiState.noticeSuggestions.isEmpty()) {
            item {
                ThreadEmptyNode(
                    threadColor = Marigold,
                    text = "Try \"Rahul owes me 500\" or \"Passport is in the blue bag\"."
                )
            }
        } else {
            items(uiState.youForgotAndHeldKnots, key = { "held_${it.id}" }) { item ->
                ThreadKnotRow(
                    reminder = item,
                    knotColor = Marigold,
                    nowMillis = uiState.nowMillis,
                    isDone = false,
                    onTapKnotToUntie = { complete(item) },
                    onSnooze = { onIntent(DeskIntent.SnoozeReminder(item, 60)) },
                    onFireNow = { onFireAlarmNow(item) },
                    onDelete = { onIntent(DeskIntent.DeleteReminder(item)) }
                )
            }
        }

        // Today & coming up
        item {
            ThreadSectionHeader(
                threadColor = ThreadIndigoLight,
                title = "Today & coming up (${uiState.todayAndComingUpKnots.size})",
                subtitle = "Your list, in time order"
            )
        }
        if (uiState.todayAndComingUpKnots.isEmpty()) {
            item { ThreadEmptyNode(threadColor = ThreadIndigoLight, text = "Your list is clear.") }
        } else {
            items(
                uiState.todayAndComingUpKnots.sortedBy { it.triggerAtMillis },
                key = { "today_${it.id}" }
            ) { item ->
                ThreadKnotRow(
                    reminder = item,
                    knotColor = ThreadIndigoLight,
                    nowMillis = uiState.nowMillis,
                    isDone = false,
                    onTapKnotToUntie = { complete(item) },
                    onSnooze = { onIntent(DeskIntent.SnoozeReminder(item, 10)) },
                    onFireNow = { onFireAlarmNow(item) },
                    onDelete = { onIntent(DeskIntent.DeleteReminder(item)) }
                )
            }
            item {
                ThreadEveningWrapRow(
                    unfinishedCount = uiState.todayAndComingUpKnots.size,
                    onMoveAllToTomorrow = { onIntent(DeskIntent.EveningWrapMoveAllToTomorrow) }
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// =================================================================================================
// DONE
// =================================================================================================

/**
 * Everything finished: today's count against the daily goal, the streak, and a history grouped by
 * day. Anything can be moved back to Pending.
 */
@Composable
fun CompletedScreen(
    uiState: DeskUiState,
    onIntent: (DeskIntent) -> Unit
) {
    val todayEpoch = ConsistencyStreakCalculator.toLocalDayEpoch(uiState.nowMillis)
    val groups = remember(uiState.doneTasks, todayEpoch) {
        uiState.doneTasks.groupBy { completedDay(it) }.toSortedMap(compareByDescending<Long> { it })
    }
    val doneToday = groups[todayEpoch]?.size ?: 0
    val doneThisWeek = groups.filterKeys { it > todayEpoch - 7 }.values.sumOf { it.size }
    val goal = uiState.userProfile.dailyReminderGoal.coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp)
            .testTag("screen_done"),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Done",
                subtitle = "Steps you've completed. Tap the green tick to move one back to Pending."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Today",
                    value = "$doneToday/$goal",
                    caption = if (doneToday >= goal) "Daily goal reached" else "${goal - doneToday} to reach your goal",
                    icon = Icons.Filled.Check,
                    accent = LeafGreen,
                    progress = HealthFormat.progress(doneToday, goal),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "This week",
                    value = doneThisWeek.toString(),
                    caption = "${uiState.doneTasks.size} all time",
                    icon = Icons.Filled.CheckCircle,
                    accent = Marigold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { ConsistencyStreakCard(streakState = uiState.consistencyStreak) }

        if (groups.isEmpty()) {
            item {
                SectionCard(title = "Nothing done yet") {
                    EmptyHint("When you finish something in Pending, it shows up here.")
                }
            }
        } else {
            groups.forEach { (day, tasks) ->
                item(key = "done_day_$day") {
                    val label = when (day) {
                        todayEpoch -> "Today"
                        todayEpoch - 1 -> "Yesterday"
                        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                            .format(Date(tasks.first().completedAtMillis ?: tasks.first().createdAtMillis))
                    }
                    SectionCard(title = "$label (${tasks.size})") {
                        tasks.forEach { task ->
                            val time = task.completedAtMillis?.let {
                                "Done at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
                            } ?: "Done"
                            TaskRow(
                                reminder = task,
                                subtitle = time,
                                accent = LeafGreen,
                                isDone = true,
                                onPrimary = { onIntent(DeskIntent.ReopenDoneTask(task)) },
                                onDelete = { onIntent(DeskIntent.DeleteReminder(task)) }
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onIntent(DeskIntent.ClearCompleted) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().testTag("clear_done_button")
                ) {
                    Text("Clear the done list")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
