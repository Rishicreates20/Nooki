package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.database.NookiDatabase
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.repository.ReminderRepository
import com.example.data.repository.ReminderRepositoryImpl
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import com.example.scheduler.ScheduleResult
import com.example.scheduler.SchedulerDiagnosticEntry
import com.example.scheduler.receiver.ReminderAlarmReceiver
import com.example.pet.NookiPet3D
import com.example.pet.PetMood
import com.example.pet.PetMoodResolver
import com.example.ui.components.MoodBadge
import com.example.ui.components.NookiSpeechBubble
import com.example.ui.components.QuickAddSheet
import com.example.ui.components.describeTrigger
import com.example.ui.components.inferCategory
import com.example.ui.components.toReminderPriority
import com.example.feedback.PetFeedback
import com.example.parser.SmartReminderParser
import com.example.pet.LocalPetVoice
import com.example.pet.PetAction
import com.example.pet.PetTopic
import com.example.pet.PetVoice
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NookiAmberLight
import com.example.ui.theme.NookiCoralAccent
import com.example.ui.theme.NookiMintSuccess
import com.example.ui.theme.NookiSkyInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nooki brings up anything due within this window (PRD FR-2.2). */
private const val FOCUS_WINDOW_MILLIS = 30 * 60_000L

class MainActivity : ComponentActivity() {
    private var feedback: PetFeedback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannelCreated(this)

        val database = NookiDatabase.getInstance(applicationContext)
        val alarmScheduler = AndroidExactAlarmScheduler(applicationContext)
        val repository = ReminderRepositoryImpl(database.reminderDao(), alarmScheduler)
        val petFeedback = PetFeedback(this).also { feedback = it }

        setContent {
            MyApplicationTheme {
                NookiMilestoneOneWorkbench(
                    repository = repository,
                    alarmScheduler = alarmScheduler,
                    feedback = petFeedback
                )
            }
        }
    }

    override fun onDestroy() {
        feedback?.release()
        feedback = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NookiMilestoneOneWorkbench(
    repository: ReminderRepository,
    alarmScheduler: AndroidExactAlarmScheduler,
    feedback: PetFeedback? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allReminders by repository.allReminders.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeReminders by repository.activeReminders.collectAsStateWithLifecycle(initialValue = emptyList())
    val completedCount by repository.completedCount.collectAsStateWithLifecycle(initialValue = 0)
    val totalXp by repository.totalEarnedXp.collectAsStateWithLifecycle(initialValue = 0)
    val telemetryLogs by NookiSchedulerTelemetry.logs.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    val smartParser = remember { SmartReminderParser() }
    val petVoice = remember { LocalPetVoice() }

    // Something that just happened (added / completed) that Nooki reacts to for a few seconds,
    // before going back to talking about whatever is most pressing.
    var eventTopic by remember { mutableStateOf<Pair<String, PetTopic>?>(null) }
    LaunchedEffect(eventTopic) {
        if (eventTopic != null) {
            delay(7_000L)
            eventTopic = null
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        NookiSchedulerTelemetry.record(
            source = "PermissionFlow",
            message = "POST_NOTIFICATIONS permission result: granted=$granted",
            isWarning = !granted
        )
    }

    // Seed starter reminders on first launch & keep countdown clock ticking every second
    LaunchedEffect(Unit) {
        repository.seedStarterRemindersIfEmpty()
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val ringingReminders = remember(allReminders) {
        allReminders.filter { it.status == ReminderStatus.RINGING }
    }
    val overdueReminders = remember(allReminders, nowMillis) {
        allReminders.filter { it.isOverdue(nowMillis) && it.status != ReminderStatus.COMPLETED }
    }
    // Derived state only: Room stays the single source of truth for Nooki's mood.
    val petMood = remember(allReminders, nowMillis) { PetMoodResolver.resolve(allReminders, nowMillis) }

    // What Nooki talks about: ringing first, then overdue, then anything due in the next 30 min
    // (the PRD's alert window).
    val focusReminder = remember(allReminders, nowMillis) {
        ringingReminders.firstOrNull()
            ?: overdueReminders.minByOrNull { it.triggerAtMillis }
            ?: activeReminders
                .filter { it.triggerAtMillis - nowMillis in 0..FOCUS_WINDOW_MILLIS }
                .minByOrNull { it.triggerAtMillis }
    }
    val (petTopicKey, petTopic) = eventTopic ?: run {
        val focus = focusReminder
        if (focus != null) {
            val minutes = (focus.triggerAtMillis - nowMillis).floorDiv(60_000L)
            val phase = when {
                focus.status == ReminderStatus.RINGING || minutes in -1L..1L -> "now"
                minutes < -1L -> "late"
                else -> "soon"
            }
            "focus_${focus.id}_$phase" to PetTopic.Focus(petMood, focus.title, minutes)
        } else {
            "idle_${petMood.name}_${activeReminders.size}" to PetTopic.Idle(petMood, activeReminders.size)
        }
    }

    fun completeWithFeedback(reminder: ReminderEntity) {
        feedback?.celebrate()
        scope.launch {
            repository.completeReminder(reminder.id)
            NotificationHelper.cancelNotification(context, reminder.id)
            eventTopic = "done_${reminder.id}" to PetTopic.Completed(PetMood.HAPPY, reminder.title)
            snackbarHostState.showSnackbar("Nooki cheered! +${reminder.petMoodImpact} XP earned!")
        }
    }

    fun snoozeWithFeedback(reminder: ReminderEntity, minutes: Int) {
        feedback?.tap()
        scope.launch {
            repository.snoozeReminder(reminder.id, minutes)
            NotificationHelper.cancelNotification(context, reminder.id)
            snackbarHostState.showSnackbar("Snoozed '${reminder.title}' for $minutes min.")
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            nowMillis = nowMillis,
            onDismiss = { showQuickAdd = false },
            onChipTap = { feedback?.tap() },
            onSubmit = { request ->
                showQuickAdd = false
                feedback?.confirm()
                scope.launch {
                    val parsed = smartParser.parse(request.rawInput)
                    val triggerAt = parsed.triggerAtMillis ?: request.fallbackTriggerAtMillis
                        ?: (System.currentTimeMillis() + 10 * 60_000L)
                    val (entity, result) = repository.createAndScheduleReminder(
                        title = parsed.title,
                        rawInput = request.rawInput,
                        triggerAtMillis = triggerAt,
                        priority = parsed.priorityHint.toReminderPriority(),
                        category = inferCategory(parsed.title)
                    )
                    eventTopic = "added_${entity.id}" to PetTopic.Added(
                        PetMood.HAPPY,
                        entity.title,
                        describeTrigger(entity.triggerAtMillis, System.currentTimeMillis())
                    )
                    if (result is ScheduleResult.Failed) {
                        snackbarHostState.showSnackbar("Saved, but the alarm couldn't be set: ${result.errorMessage}")
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = "Nooki Desk Pet & Reminders"
                        )
                    },
                    label = { Text("Nooki Desk") },
                    modifier = Modifier.testTag("nav_tab_desk")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Milestone 1 Room & AlarmManager Lab"
                        )
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("M1 Scheduler Lab")
                            if (telemetryLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge { Text("${telemetryLogs.size}") }
                            }
                        }
                    },
                    modifier = Modifier.testTag("nav_tab_lab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            Color(0xFF1E1633),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (selectedTab == 0) {
                DeskPetReminderScreen(
                    petMood = petMood,
                    petTopic = petTopic,
                    petTopicKey = petTopicKey,
                    petVoice = petVoice,
                    onPetAction = { action ->
                        val focus = focusReminder
                        if (focus != null && eventTopic == null) {
                            when (action.id) {
                                PetAction.MARK_DONE -> completeWithFeedback(focus)
                                PetAction.SNOOZE_10M -> snoozeWithFeedback(focus, 10)
                            }
                        }
                    },
                    allReminders = allReminders,
                    activeReminders = activeReminders,
                    ringingReminders = ringingReminders,
                    overdueReminders = overdueReminders,
                    completedCount = completedCount,
                    totalXp = totalXp,
                    nowMillis = nowMillis,
                    hasNotificationPermission = hasNotificationPermission,
                    canScheduleExact = alarmScheduler.canScheduleExactAlarms(),
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExactAlarmSettings = {
                        alarmScheduler.buildExactAlarmPermissionIntent()?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    onOpenQuickAdd = {
                        feedback?.tap()
                        showQuickAdd = true
                    },
                    onCompleteReminder = { reminder -> completeWithFeedback(reminder) },
                    onSnoozeReminder = { reminder, mins -> snoozeWithFeedback(reminder, mins) },
                    onFireAlarmNow = { reminder ->
                        val broadcastIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                            action = AndroidExactAlarmScheduler.ACTION_TRIGGER_REMINDER_ALARM
                            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
                            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_TITLE, reminder.title)
                        }
                        context.sendBroadcast(broadcastIntent)
                        scope.launch {
                            snackbarHostState.showSnackbar("Dispatched exact alarm broadcast for '${reminder.title}'!")
                        }
                    },
                    onDeleteReminder = { reminder ->
                        scope.launch {
                            repository.deleteReminder(reminder.id)
                            NotificationHelper.cancelNotification(context, reminder.id)
                        }
                    }
                )
            } else {
                MilestoneOneInspectorLab(
                    allReminders = allReminders,
                    telemetryLogs = telemetryLogs,
                    canScheduleExact = alarmScheduler.canScheduleExactAlarms(),
                    onSimulateBootRecovery = {
                        scope.launch {
                            val report = repository.reconcileAndRescheduleAlarms()
                            NookiSchedulerTelemetry.record(
                                source = "BootRecoverySim",
                                message = "Manual Boot Recovery executed: rescheduled=${report.rescheduledCount}, missed=${report.markedMissedCount}"
                            )
                            snackbarHostState.showSnackbar(
                                "Boot recovery: ${report.rescheduledCount} rescheduled, ${report.markedMissedCount} marked missed."
                            )
                        }
                    },
                    onSchedule5SecondTestAlarm = {
                        scope.launch {
                            val triggerAt = System.currentTimeMillis() + 5_000L
                            val (entity, _) = repository.createAndScheduleReminder(
                                title = "5-Second Exact Alarm Test",
                                notes = "Verifies AlarmManager -> ReminderAlarmReceiver -> goAsync() -> Room RINGING",
                                rawInput = "Test exact alarm in 5s",
                                triggerAtMillis = triggerAt,
                                priority = ReminderPriority.URGENT,
                                category = ReminderCategory.DESK
                            )
                            snackbarHostState.showSnackbar(
                                "Scheduled #${entity.id} for +5 seconds! Watch Nooki & Telemetry log."
                            )
                        }
                    },
                    onClearCompleted = {
                        scope.launch {
                            repository.clearCompletedReminders()
                            snackbarHostState.showSnackbar("Cleared completed/cancelled rows from Room.")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeskPetReminderScreen(
    petMood: PetMood,
    petTopic: PetTopic,
    petTopicKey: String,
    petVoice: PetVoice,
    onPetAction: (PetAction) -> Unit,
    allReminders: List<ReminderEntity>,
    activeReminders: List<ReminderEntity>,
    ringingReminders: List<ReminderEntity>,
    overdueReminders: List<ReminderEntity>,
    completedCount: Int,
    totalXp: Int,
    nowMillis: Long,
    hasNotificationPermission: Boolean,
    canScheduleExact: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenQuickAdd: () -> Unit,
    onCompleteReminder: (ReminderEntity) -> Unit,
    onSnoozeReminder: (ReminderEntity, Int) -> Unit,
    onFireAlarmNow: (ReminderEntity) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Nooki Desk Stage
        item {
            NookiDeskHeroCard(
                mood = petMood,
                ringingCount = ringingReminders.size,
                overdueCount = overdueReminders.size,
                activeCount = activeReminders.size,
                completedCount = completedCount,
                totalXp = totalXp
            )
        }

        // Nooki talks: streamed line + Done / Snooze chips (PRD FR-2.3, FR-2.4)
        item {
            NookiSpeechBubble(
                topic = petTopic,
                topicKey = petTopicKey,
                voice = petVoice,
                onAction = onPetAction
            )
        }

        // Permission & Exact Alarm Status Banner if needed
        if (!hasNotificationPermission || !canScheduleExact) {
            item {
                PermissionStatusBanner(
                    hasNotificationPermission = hasNotificationPermission,
                    canScheduleExact = canScheduleExact,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings
                )
            }
        }

        // Active Ringing Banner (High-urgency desk alert)
        items(ringingReminders, key = { "ringing_${it.id}" }) { ringing ->
            RingingAlertCard(
                reminder = ringing,
                onComplete = { onCompleteReminder(ringing) },
                onSnooze = { onSnoozeReminder(ringing, 5) }
            )
        }

        // Quick add: one line of plain text instead of pickers (PRD FR-1)
        item {
            Surface(
                onClick = onOpenQuickAdd,
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_quick_add")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tell Nooki what to remember", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "\u201CCall mom tomorrow at 6pm\u201D",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section Title for Active Queue
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Desk Queue (${activeReminders.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Room Flow + AlarmManager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (activeReminders.isEmpty()) {
            item {
                EmptyRemindersCard()
            }
        } else {
            items(activeReminders, key = { it.id }) { reminder ->
                ReminderQueueItemCard(
                    reminder = reminder,
                    nowMillis = nowMillis,
                    onComplete = { onCompleteReminder(reminder) },
                    onSnooze = { onSnoozeReminder(reminder, 5) },
                    onFireNow = { onFireAlarmNow(reminder) },
                    onDelete = { onDeleteReminder(reminder) }
                )
            }
        }

        // Completed history preview
        val completedList = allReminders.filter { it.status == ReminderStatus.COMPLETED }
        if (completedList.isNotEmpty()) {
            item {
                Text(
                    text = "Recently Completed (${completedList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(completedList.take(5), key = { "done_${it.id}" }) { done ->
                CompletedReminderRow(
                    reminder = done,
                    onDelete = { onDeleteReminder(done) }
                )
            }
        }
    }
}

@Composable
private fun NookiDeskHeroCard(
    mood: PetMood,
    ringingCount: Int,
    overdueCount: Int,
    activeCount: Int,
    completedCount: Int,
    totalXp: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nooki_pet_breath")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (ringingCount > 0) 380 else 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nooki_bounce"
    )

    val moodState = when (mood) {
        PetMood.ALERT ->
            if (ringingCount > 0) "Alert: Nooki is ringing your desk bell!" else mood.caption
        PetMood.OVERWHELMED -> "Overwhelmed: $overdueCount overdue task(s) waiting"
        PetMood.HAPPY -> if (activeCount > 0) "Happy: watching $activeCount alarm(s)" else mood.caption
        PetMood.SLEEPING -> mood.caption
    }

    // Contextual color: urgency shifts the aura, the 3D stage and the badge together.
    val auraColor by animateColorAsState(
        targetValue = when (mood) {
            PetMood.SLEEPING -> NookiSkyInfo
            PetMood.HAPPY -> NookiMintSuccess
            PetMood.ALERT -> NookiAmberLight
            PetMood.OVERWHELMED -> NookiCoralAccent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nooki_aura_color"
    )
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val stageColor = lerp(MaterialTheme.colorScheme.surface, auraColor, 0.16f)
    // Asymmetric, hand-tuned corners instead of a uniform template radius.
    val heroShape = RoundedCornerShape(topStart = 36.dp, topEnd = 14.dp, bottomEnd = 36.dp, bottomStart = 22.dp)

    val level = (totalXp / 50) + 1
    val xpInLevel = totalXp % 50

    Card(
        shape = heroShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = auraColor.copy(alpha = 0.55f),
                shape = heroShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MoodBadge(
                    mood = mood,
                    color = auraColor,
                    contentColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nooki • Lv.$level Desk Spirit",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = moodState,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$totalXp XP ($xpInLevel/50)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time 3D desk pet (SceneView/Filament). The original 2D Canvas pet stays as a
            // fallback for Compose previews and devices where the model can't load.
            NookiPet3D(
                mood = mood,
                stageColor = stageColor,
                surroundColor = cardColor,
                cornerRadius = 26.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .testTag("nooki_pet_3d"),
                fallback = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f + bounceOffset

                            // Desk wooden mat surface
                            drawRoundRect(
                                color = Color(0xFF3E2723),
                                topLeft = Offset(centerX - 135.dp.toPx(), size.height - 24.dp.toPx()),
                                size = Size(270.dp.toPx(), 16.dp.toPx()),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                            )

                            // Soft radial glow behind Nooki
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(auraColor.copy(alpha = 0.32f), Color.Transparent),
                                    center = Offset(centerX, centerY),
                                    radius = 75.dp.toPx()
                                ),
                                radius = 75.dp.toPx(),
                                center = Offset(centerX, centerY)
                            )

                            // Ears
                            val leftEar = Path().apply {
                                moveTo(centerX - 38.dp.toPx(), centerY - 18.dp.toPx())
                                lineTo(centerX - 50.dp.toPx(), centerY - 52.dp.toPx())
                                lineTo(centerX - 16.dp.toPx(), centerY - 36.dp.toPx())
                                close()
                            }
                            val rightEar = Path().apply {
                                moveTo(centerX + 38.dp.toPx(), centerY - 18.dp.toPx())
                                lineTo(centerX + 50.dp.toPx(), centerY - 52.dp.toPx())
                                lineTo(centerX + 16.dp.toPx(), centerY - 36.dp.toPx())
                                close()
                            }
                            drawPath(leftEar, color = Color(0xFFF59E0B))
                            drawPath(rightEar, color = Color(0xFFF59E0B))

                            // Nooki chubby body
                            drawOval(
                                color = Color(0xFFFBBF24),
                                topLeft = Offset(centerX - 50.dp.toPx(), centerY - 38.dp.toPx()),
                                size = Size(100.dp.toPx(), 84.dp.toPx())
                            )

                            // Cream belly/cheek patch
                            drawOval(
                                color = Color(0xFFFFFBEB),
                                topLeft = Offset(centerX - 38.dp.toPx(), centerY - 10.dp.toPx()),
                                size = Size(76.dp.toPx(), 52.dp.toPx())
                            )

                            // Eyes (wide alert if ringing, cozy arcs or dots otherwise)
                            val eyeY = centerY - 6.dp.toPx()
                            val leftEyeX = centerX - 18.dp.toPx()
                            val rightEyeX = centerX + 18.dp.toPx()
                            val eyeRadius = if (ringingCount > 0) 6.5.dp.toPx() else 5.dp.toPx()

                            drawCircle(
                                color = Color(0xFF1E1B4B),
                                radius = eyeRadius,
                                center = Offset(leftEyeX, eyeY)
                            )
                            drawCircle(
                                color = Color(0xFF1E1B4B),
                                radius = eyeRadius,
                                center = Offset(rightEyeX, eyeY)
                            )
                            // Eye shine
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(leftEyeX - 1.5.dp.toPx(), eyeY - 1.5.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(rightEyeX - 1.5.dp.toPx(), eyeY - 1.5.dp.toPx())
                            )

                            // Rosy cheeks
                            drawCircle(
                                color = NookiCoralAccent.copy(alpha = 0.65f),
                                radius = 6.dp.toPx(),
                                center = Offset(leftEyeX - 10.dp.toPx(), eyeY + 9.dp.toPx())
                            )
                            drawCircle(
                                color = NookiCoralAccent.copy(alpha = 0.65f),
                                radius = 6.dp.toPx(),
                                center = Offset(rightEyeX + 10.dp.toPx(), eyeY + 9.dp.toPx())
                            )

                            // Nose & Mouth
                            drawCircle(
                                color = Color(0xFFEA580C),
                                radius = 3.5.dp.toPx(),
                                center = Offset(centerX, eyeY + 8.dp.toPx())
                            )
                            drawArc(
                                color = Color(0xFF1E1B4B),
                                startAngle = 15f,
                                sweepAngle = 150f,
                                useCenter = false,
                                topLeft = Offset(centerX - 8.dp.toPx(), eyeY + 10.dp.toPx()),
                                size = Size(16.dp.toPx(), 10.dp.toPx()),
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Stat Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    label = "Scheduled",
                    value = "$activeCount",
                    tint = NookiSkyInfo,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "Ringing",
                    value = "$ringingCount",
                    tint = if (ringingCount > 0) NookiCoralAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "Completed",
                    value = "$completedCount",
                    tint = NookiMintSuccess,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = tint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionStatusBanner(
    hasNotificationPermission: Boolean,
    canScheduleExact: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Alarm & Notification Readiness",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasNotificationPermission) {
                    FilledTonalButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.testTag("grant_notifications_button")
                    ) {
                        Text("Allow Notifications")
                    }
                }
                if (!canScheduleExact) {
                    OutlinedButton(
                        onClick = onOpenExactAlarmSettings,
                        modifier = Modifier.testTag("exact_alarm_settings_button")
                    ) {
                        Text("Enable Exact Alarms")
                    }
                }
            }
        }
    }
}

@Composable
private fun RingingAlertCard(
    reminder: ReminderEntity,
    onComplete: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4C1D24)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, NookiCoralAccent, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Ringing Alarm",
                    tint = NookiCoralAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NOOKI DESK BELL RINGING!",
                    style = MaterialTheme.typography.labelLarge,
                    color = NookiCoralAccent
                )
            }
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            if (reminder.notes.isNotBlank()) {
                Text(
                    text = reminder.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = NookiMintSuccess),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("complete_ringing_${reminder.id}")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Complete (+${reminder.petMoodImpact} XP)", color = Color.Black)
                }
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = Modifier.testTag("snooze_ringing_${reminder.id}")
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Snooze 5m")
                }
            }
        }
    }
}

@Composable
private fun ReminderQueueItemCard(
    reminder: ReminderEntity,
    nowMillis: Long,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onFireNow: () -> Unit,
    onDelete: () -> Unit
) {
    val diffMillis = reminder.triggerAtMillis - nowMillis
    val countdownLabel = remember(diffMillis, reminder.status) {
        when {
            reminder.status == ReminderStatus.RINGING -> "RINGING NOW"
            reminder.status == ReminderStatus.MISSED -> "MISSED (Boot Recovery)"
            diffMillis <= 0L -> "Due now"
            diffMillis < 60_000L -> "in ${diffMillis / 1000L}s"
            diffMillis < 3_600_000L -> "in ${diffMillis / 60_000L}m ${(diffMillis % 60_000L) / 1000L}s"
            else -> "in ${diffMillis / 3_600_000L}h ${(diffMillis % 3_600_000L) / 60_000L}m"
        }
    }

    val formattedClock = remember(reminder.triggerAtMillis) {
        SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(reminder.triggerAtMillis))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = reminder.category.badgeText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${reminder.priority.label} • +${reminder.petMoodImpact} XP",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (reminder.snoozeCount > 0) {
                        Text(
                            text = "Snoozed ×${reminder.snoozeCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NookiAmberLight
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete reminder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (reminder.notes.isNotBlank()) {
                Text(
                    text = reminder.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = NookiSkyInfo,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$formattedClock ($countdownLabel)",
                        style = MaterialTheme.typography.labelLarge,
                        color = NookiSkyInfo
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = onFireNow,
                        label = { Text("Trigger") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Trigger alarm broadcast now",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("trigger_now_${reminder.id}")
                    )
                    AssistChip(
                        onClick = onSnooze,
                        label = { Text("+5m") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = "Snooze 5 minutes",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = onComplete,
                        label = { Text("Done") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete reminder",
                                tint = NookiMintSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = NookiMintSuccess
                        ),
                        modifier = Modifier.testTag("complete_btn_${reminder.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedReminderRow(
    reminder: ReminderEntity,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = NookiMintSuccess,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete completed reminder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyRemindersCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Nooki's Desk Queue is Clear!",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Schedule a reminder above to register an exact PendingIntent with AlarmManager.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MilestoneOneInspectorLab(
    allReminders: List<ReminderEntity>,
    telemetryLogs: List<SchedulerDiagnosticEntry>,
    canScheduleExact: Boolean,
    onSimulateBootRecovery: () -> Unit,
    onSchedule5SecondTestAlarm: () -> Unit,
    onClearCompleted: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Milestone 1: Data & Scheduler Diagnostics",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = "Verify Room DB persistence, AlarmManager exact scheduling, ReminderAlarmReceiver (goAsync), and BootCompletedReceiver reconciliation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSchedule5SecondTestAlarm,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_5s_test_alarm")
                        ) {
                            Icon(Icons.Default.AlarmOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+5s Exact Alarm")
                        }
                        FilledTonalButton(
                            onClick = onSimulateBootRecovery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_simulate_boot")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Boot Recovery")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "canScheduleExactAlarms(): $canScheduleExact • Total DB Rows: ${allReminders.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canScheduleExact) NookiMintSuccess else NookiAmberLight
                        )
                        OutlinedButton(onClick = onClearCompleted) {
                            Text("Prune Completed")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Live Scheduler & BroadcastReceiver Telemetry (${telemetryLogs.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (telemetryLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = NookiSkyInfo)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tap '+5s Exact Alarm' or 'Boot Recovery' above to stream live AlarmManager & BroadcastReceiver logs.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(telemetryLogs, key = { it.id }) { entry ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.source,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (entry.isWarning) NookiCoralAccent else NookiSkyInfo
                            )
                            Text(
                                text = entry.formattedTime(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
