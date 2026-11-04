package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatListBulleted
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.repository.ReminderRepositoryImpl
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskSideEffect
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.ui.components.Nooki3DCenterpiece
import com.example.presentation.viewmodel.DeskPetViewModel
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import com.example.scheduler.SchedulerDiagnosticEntry
import com.example.scheduler.receiver.ReminderAlarmReceiver
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NookiAmberLight
import com.example.ui.theme.NookiCoralAccent
import com.example.ui.theme.NookiMintSuccess
import com.example.ui.theme.NookiSkyInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannelCreated(this)

        val database = AppDatabase.getInstance(applicationContext)
        val alarmScheduler = AndroidExactAlarmScheduler(applicationContext)
        val repository = ReminderRepositoryImpl(database.reminderDao(), alarmScheduler)

        setContent {
            MyApplicationTheme {
                val viewModel: DeskPetViewModel = viewModel(
                    factory = DeskPetViewModel.Factory(repository, alarmScheduler)
                )
                NookiMainApp(
                    viewModel = viewModel,
                    alarmScheduler = alarmScheduler
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NookiMainApp(
    viewModel: DeskPetViewModel,
    alarmScheduler: AndroidExactAlarmScheduler
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Lifecycle-aware collection of the reactive StateFlow<DeskUiState>
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DeskSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                DeskSideEffect.TriggerHapticPulse -> NotificationHelper.triggerDeskPetPulseVibration(context)
            }
        }
    }

    if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
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
                            contentDescription = "3D Nooki Center Stage"
                        )
                    },
                    label = { Text("3D Nooki Stage") },
                    modifier = Modifier.testTag("nav_tab_desk")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "State Machine & Scheduler Lab"
                        )
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("State & DB Lab")
                            if (uiState.telemetryLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge { Text("${uiState.telemetryLogs.size}") }
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
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2B1E4A),
                            MaterialTheme.colorScheme.background,
                            Color(0xFF100C1C)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            if (selectedTab == 0) {
                Centered3DDeskPetStage(
                    uiState = uiState,
                    hasNotificationPermission = hasNotificationPermission,
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
                    onIntent = viewModel::onIntent,
                    onFireAlarmBroadcastNow = { reminder ->
                        val broadcastIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                            action = AndroidExactAlarmScheduler.ACTION_TRIGGER_REMINDER_ALARM
                            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
                            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_TITLE, reminder.title)
                        }
                        context.sendBroadcast(broadcastIntent)
                    }
                )
            } else {
                MilestoneOneInspectorLab(
                    uiState = uiState,
                    onSimulateBootRecovery = { viewModel.onIntent(DeskIntent.SimulateBootRecovery) },
                    onSchedule5SecondTestAlarm = { viewModel.onIntent(DeskIntent.Schedule5SecondTestAlarm) },
                    onClearCompleted = { viewModel.onIntent(DeskIntent.ClearCompleted) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Centered3DDeskPetStage(
    uiState: DeskUiState,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onIntent: (DeskIntent) -> Unit,
    onFireAlarmBroadcastNow: (ReminderEntity) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 1. TOP HUD: Level, XP Progress Bar, and Reactive StateFlow Pill (Anchored TopCenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nooki • Lv.${uiState.level}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${uiState.totalXp} XP",
                                style = MaterialTheme.typography.labelLarge,
                                color = NookiMintSuccess
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HudMiniStat(
                                label = "Scheduled",
                                value = "${uiState.activeReminders.size}",
                                color = NookiSkyInfo
                            )
                            HudMiniStat(
                                label = "Done",
                                value = "${uiState.completedCount}",
                                color = NookiMintSuccess
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            (uiState.xpInCurrentLevel.toFloat() / uiState.xpForNextLevel.toFloat())
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Live StateFlow -> Room DB Synchronization Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("state_machine_status_strip")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = uiState.petMood.primaryAuraColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.stateTransitionSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!hasNotificationPermission || !uiState.canScheduleExactAlarms) {
                PermissionStatusBanner(
                    hasNotificationPermission = hasNotificationPermission,
                    canScheduleExact = uiState.canScheduleExactAlarms,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings
                )
            }

            // Ringing alert overlay if an alarm is currently ringing in Room
            uiState.ringingReminders.firstOrNull()?.let { ringing ->
                RingingAlertCard(
                    reminder = ringing,
                    onComplete = {
                        NotificationHelper.cancelNotification(context, ringing.id)
                        onIntent(DeskIntent.CompleteReminder(ringing))
                    },
                    onSnooze = {
                        NotificationHelper.cancelNotification(context, ringing.id)
                        onIntent(DeskIntent.SnoozeReminder(ringing, 5))
                    }
                )
            }
        }

        // 2. CENTERPIECE: 3D Nooki Model strictly at Alignment.Center
        Nooki3DCenterpiece(
            mood = uiState.petMood,
            speechText = uiState.speechBubbleText,
            isAutoRotateEnabled = uiState.isAutoRotate3DEnabled,
            petCombo = uiState.petInteractionCombo,
            onPetTap = { onIntent(DeskIntent.PetNookiHead) },
            onToggleAutoRotate = { onIntent(DeskIntent.ToggleAutoRotate3D) },
            modifier = Modifier.align(Alignment.Center)
        )

        // 3. BOTTOM DOCK: Next Up Preview + Quick Reminder & Queue Sheet Triggers (Anchored BottomCenter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val nextReminder = uiState.activeReminders.firstOrNull()
            if (nextReminder != null) {
                NextReminderDockCard(
                    reminder = nextReminder,
                    nowMillis = uiState.nowMillis,
                    onComplete = {
                        NotificationHelper.cancelNotification(context, nextReminder.id)
                        onIntent(DeskIntent.CompleteReminder(nextReminder))
                    },
                    onFireNow = { onFireAlarmBroadcastNow(nextReminder) },
                    onOpenQueue = { onIntent(DeskIntent.SetQueueSheetOpen(true)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onIntent(DeskIntent.SetComposerSheetOpen(true)) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(52.dp)
                        .testTag("open_composer_button")
                ) {
                    Icon(Icons.Default.AddAlert, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Reminder", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { onIntent(DeskIntent.SetQueueSheetOpen(true)) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_queue_button")
                ) {
                    Icon(Icons.Default.FormatListBulleted, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Queue (${uiState.activeReminders.size})")
                }
            }
        }
    }

    if (uiState.isComposerSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(DeskIntent.SetComposerSheetOpen(false)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ReminderComposerSheetContent(
                onSchedule = { title, notes, delayMins, priority, category ->
                    onIntent(
                        DeskIntent.ScheduleReminder(
                            title = title,
                            notes = notes,
                            delayMinutes = delayMins,
                            priority = priority,
                            category = category
                        )
                    )
                },
                onSchedule5sTest = {
                    onIntent(DeskIntent.SetComposerSheetOpen(false))
                    onIntent(DeskIntent.Schedule5SecondTestAlarm)
                }
            )
        }
    }

    if (uiState.isQueueSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(DeskIntent.SetQueueSheetOpen(false)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ReminderQueueSheetContent(
                activeReminders = uiState.activeReminders,
                allReminders = uiState.allReminders,
                nowMillis = uiState.nowMillis,
                onComplete = { reminder ->
                    NotificationHelper.cancelNotification(context, reminder.id)
                    onIntent(DeskIntent.CompleteReminder(reminder))
                },
                onSnooze = { reminder, mins ->
                    NotificationHelper.cancelNotification(context, reminder.id)
                    onIntent(DeskIntent.SnoozeReminder(reminder, mins))
                },
                onFireNow = onFireAlarmBroadcastNow,
                onDelete = { reminder ->
                    NotificationHelper.cancelNotification(context, reminder.id)
                    onIntent(DeskIntent.DeleteReminder(reminder))
                }
            )
        }
    }
}

@Composable
private fun HudMiniStat(
    label: String,
    value: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NextReminderDockCard(
    reminder: ReminderEntity,
    nowMillis: Long,
    onComplete: () -> Unit,
    onFireNow: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val diffMillis = reminder.triggerAtMillis - nowMillis
    val countdown = remember(diffMillis, reminder.status) {
        when {
            reminder.status == ReminderStatus.RINGING -> "RINGING NOW"
            diffMillis <= 0L -> "Due now"
            diffMillis < 60_000L -> "in ${diffMillis / 1000L}s"
            diffMillis < 3_600_000L -> "in ${diffMillis / 60_000L}m ${(diffMillis % 60_000L) / 1000L}s"
            else -> "in ${diffMillis / 3_600_000L}h ${(diffMillis % 3_600_000L) / 60_000L}m"
        }
    }

    Surface(
        onClick = onOpenQueue,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NEXT ALARM • $countdown",
                        style = MaterialTheme.typography.labelLarge,
                        color = NookiSkyInfo
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+${reminder.petMoodImpact} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = NookiMintSuccess
                    )
                }
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(
                    onClick = onFireNow,
                    label = { Text("Ring") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Trigger alarm now",
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
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderComposerSheetContent(
    onSchedule: (String, String, Int, ReminderPriority, ReminderCategory) -> Unit,
    onSchedule5sTest: () -> Unit
) {
    var titleInput by rememberSaveable { mutableStateOf("") }
    var notesInput by rememberSaveable { mutableStateOf("") }
    var selectedDelayMins by rememberSaveable { mutableIntStateOf(10) }
    var selectedPriority by rememberSaveable { mutableStateOf(ReminderPriority.MEDIUM) }
    var selectedCategory by rememberSaveable { mutableStateOf(ReminderCategory.DESK) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Schedule Exact Desk Reminder",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedButton(onClick = onSchedule5sTest) {
                Text("+5s Test Alarm")
            }
        }

        OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            label = { Text("What should Nooki remind you about?") },
            placeholder = { Text("e.g., Drink water & stretch wrists") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reminder_title_input")
        )

        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it },
            label = { Text("Optional desk note") },
            placeholder = { Text("Extra context...") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reminder_notes_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReminderCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category.label) }
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1 to "In 1m", 5 to "In 5m", 15 to "In 15m", 30 to "In 30m", 60 to "In 1h").forEach { (mins, label) ->
                FilterChip(
                    selected = selectedDelayMins == mins,
                    onClick = { selectedDelayMins = mins },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReminderPriority.entries.forEach { priority ->
                FilterChip(
                    selected = selectedPriority == priority,
                    onClick = { selectedPriority = priority },
                    label = { Text(priority.label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(
            onClick = {
                val cleanTitle = titleInput.trim().ifBlank {
                    "Desk check-in with Nooki (${selectedCategory.label})"
                }
                onSchedule(
                    cleanTitle,
                    notesInput,
                    selectedDelayMins,
                    selectedPriority,
                    selectedCategory
                )
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("schedule_reminder_button")
        ) {
            Icon(Icons.Default.AlarmOn, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Schedule Exact Alarm (in ${selectedDelayMins}m)",
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ReminderQueueSheetContent(
    activeReminders: List<ReminderEntity>,
    allReminders: List<ReminderEntity>,
    nowMillis: Long,
    onComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity, Int) -> Unit,
    onFireNow: (ReminderEntity) -> Unit,
    onDelete: (ReminderEntity) -> Unit
) {
    val completedList = remember(allReminders) {
        allReminders.filter { it.status == ReminderStatus.COMPLETED }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Active Desk Queue (${activeReminders.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (activeReminders.isEmpty()) {
            item {
                Text(
                    text = "No active alarms queued. Nooki is resting peacefully at the center of your desk!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(activeReminders, key = { it.id }) { reminder ->
                ReminderQueueItemCard(
                    reminder = reminder,
                    nowMillis = nowMillis,
                    onComplete = { onComplete(reminder) },
                    onSnooze = { onSnooze(reminder, 5) },
                    onFireNow = { onFireNow(reminder) },
                    onDelete = { onDelete(reminder) }
                )
            }
        }

        if (completedList.isNotEmpty()) {
            item {
                Text(
                    text = "Completed (${completedList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(completedList.take(5), key = { "done_${it.id}" }) { done ->
                CompletedReminderRow(
                    reminder = done,
                    onDelete = { onDelete(done) }
                )
            }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enable alarm permissions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (!hasNotificationPermission) {
                FilledTonalButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.testTag("grant_notifications_button")
                ) {
                    Text("Allow Alerts")
                }
            } else if (!canScheduleExact) {
                OutlinedButton(
                    onClick = onOpenExactAlarmSettings,
                    modifier = Modifier.testTag("exact_alarm_settings_button")
                ) {
                    Text("Exact Alarms")
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4C1D24)),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, NookiCoralAccent, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Ringing Alarm",
                    tint = NookiCoralAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NOOKI 3D BELL RINGING: ${reminder.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = NookiMintSuccess),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Complete (+${reminder.petMoodImpact} XP)", color = Color.Black)
                }
                FilledTonalButton(onClick = onSnooze) {
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = "${reminder.category.badgeText} • ${reminder.priority.label} (+${reminder.petMoodImpact} XP)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete reminder"
                    )
                }
            }

            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedClock ($countdownLabel)",
                    style = MaterialTheme.typography.labelLarge,
                    color = NookiSkyInfo
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = onFireNow,
                        label = { Text("Trigger") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Trigger now",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = onSnooze,
                        label = { Text("+5m") }
                    )
                    AssistChip(
                        onClick = onComplete,
                        label = { Text("Done") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete",
                                tint = NookiMintSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = NookiMintSuccess
                        )
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MilestoneOneInspectorLab(
    uiState: DeskUiState,
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
                            text = "Reactive StateFlow & Room DB Lab",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = "Current StateFlow Node: ${uiState.stateTransitionSummary}",
                        style = MaterialTheme.typography.labelLarge,
                        color = uiState.petMood.primaryAuraColor
                    )
                    Text(
                        text = "Every Room table emission (insert, RINGING broadcast, snooze, completion, or boot recovery) feeds into NookiStateMachine.reduce() and emits a new DeskUiState.",
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
                            text = "Exact Alarms: ${uiState.canScheduleExactAlarms} • Room Rows: ${uiState.allReminders.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.canScheduleExactAlarms) NookiMintSuccess else NookiAmberLight
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
                text = "StateFlow Transitions & Scheduler Telemetry (${uiState.telemetryLogs.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (uiState.telemetryLogs.isEmpty()) {
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
                            text = "Tap '+5s Exact Alarm' or 'Boot Recovery' above to watch live StateFlow transitions and AlarmManager logs.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(uiState.telemetryLogs, key = { it.id }) { entry ->
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
