package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.data.repository.ReminderRepositoryImpl
import com.example.domain.notice.NoticeInspectionResult
import com.example.domain.notice.NoticeSourceKind
import com.example.domain.parser.NookiCaptureKind
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskSideEffect
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.ui.components.ConsistencyStreakCard
import com.example.presentation.ui.components.Nooki3DCenterpiece
import com.example.presentation.ui.components.NookiTasksAndProfileScreen
import com.example.presentation.ui.components.TodoAndDoneTasksSection
import com.example.presentation.ui.components.UserProfileAndStreakBannerChip
import com.example.presentation.viewmodel.DeskPetViewModel
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NookiSensoryFeedbackManager
import com.example.scheduler.NotificationHelper
import com.example.scheduler.receiver.ReminderAlarmReceiver
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.Marigold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeelIndigo
import com.example.ui.theme.NightIndigo
import com.example.ui.theme.Sindoor
import com.example.ui.theme.ThreadIndigoLight
import com.example.ui.theme.WashedCotton
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
            val viewModel: DeskPetViewModel = viewModel(
                factory = DeskPetViewModel.Factory(repository, alarmScheduler)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(appTheme = uiState.selectedTheme) {
                NookiMainApp(
                    uiState = uiState,
                    viewModel = viewModel,
                    alarmScheduler = alarmScheduler
                )
            }
        }
    }
}

@Composable
fun NookiMainApp(
    uiState: DeskUiState,
    viewModel: DeskPetViewModel,
    alarmScheduler: AndroidExactAlarmScheduler
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme

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
    }

    // Voice capture launcher supporting English & Hindi ("कल शाम 6 बजे दवाई", "kal subah 9 baje doctor")
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            viewModel.onIntent(DeskIntent.UpdateBreathInput(spokenText))
        }
    }

    val sensoryManager = remember(context) {
        NookiSensoryFeedbackManager(context.applicationContext)
    }
    DisposableEffect(sensoryManager) {
        onDispose { sensoryManager.release() }
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DeskSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                DeskSideEffect.TriggerHapticPulse -> NotificationHelper.triggerDeskPetPulseVibration(context)
                is DeskSideEffect.PlaySensoryCue -> {
                    sensoryManager.performSensoryCue(
                        cue = effect.cue,
                        soundEnabled = effect.soundEnabled,
                        hapticsEnabled = effect.hapticsEnabled
                    )
                }
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
                containerColor = scheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = "Nooki & The Thread"
                        )
                    },
                    label = { Text("Nooki's Thread") },
                    modifier = Modifier.testTag("nav_tab_desk")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Tasks & User Profile"
                        )
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tasks & Profile")
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(containerColor = scheme.primary, contentColor = scheme.onPrimary) {
                                Text("${uiState.consistencyStreak.currentStreakDays}d")
                            }
                        }
                    },
                    modifier = Modifier.testTag("nav_tab_profile_tasks")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Nooki Notices & Reliability"
                        )
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Notices")
                            if (uiState.noticeSuggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = scheme.primary, contentColor = scheme.onPrimary) {
                                    Text("${uiState.noticeSuggestions.size}")
                                }
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
                            scheme.background,
                            scheme.surface,
                            scheme.background
                        )
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedTab) {
                0 -> {
                    NookiThreadHomeScreen(
                        uiState = uiState,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onStartVoiceCapture = {
                            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Nooki in English, Hinglish, or हिंदी...")
                            }
                            runCatching { speechRecognizerLauncher.launch(voiceIntent) }
                        },
                        onOpenProfileAndTasksTab = { selectedTab = 1 },
                        onIntent = viewModel::onIntent,
                        onFireAlarmNow = { reminder ->
                            val broadcastIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                                action = AndroidExactAlarmScheduler.ACTION_TRIGGER_REMINDER_ALARM
                                putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
                                putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_TITLE, reminder.title)
                            }
                            context.sendBroadcast(broadcastIntent)
                        }
                    )
                }
                1 -> {
                    NookiTasksAndProfileScreen(
                        uiState = uiState,
                        onIntent = viewModel::onIntent
                    )
                }
                else -> {
                    NookiNoticesAndReliabilityScreen(
                        uiState = uiState,
                        onOpenExactAlarmSettings = {
                            alarmScheduler.buildExactAlarmPermissionIntent()?.let { intent ->
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        onIntent = viewModel::onIntent
                    )
                }
            }
        }
    }
}

/**
 * The signature Nooki Home Screen:
 * - User Profile & Consistency Streak Banner at the top
 * - Centered 3D Nooki Fox at the center
 * - "Tell Nooki: capture in one breath" bar with real-time "What Nooki understood" preview
 * - One continuous Thread dropping from Nooki's paw down the left of the screen
 * - Dedicated To-Do Tasks & Done Tasks organizer section
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NookiThreadHomeScreen(
    uiState: DeskUiState,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onOpenProfileAndTasksTab: () -> Unit,
    onIntent: (DeskIntent) -> Unit,
    onFireAlarmNow: (ReminderEntity) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    // Signature opening motion: "One moment when the app opens: the thread drops from Nooki."
    val threadDropProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        threadDropProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Header: Level, XP & Reactive MoodState Happiness Meter (driven by completion frequency)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Nooki • Lv.${uiState.level} • ${uiState.selectedTheme.badgeEmoji} ${uiState.selectedTheme.displayName}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = scheme.primary
                        )
                        Text(
                            text = "${uiState.moodState.happinessTier.badgeEmoji} ${uiState.moodState.happinessTier.label} • ${uiState.moodState.completedLast24Hours} untied in 24h",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onBackground.copy(alpha = 0.85f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = scheme.surface,
                            modifier = Modifier.border(1.dp, scheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(50))
                        ) {
                            Text(
                                text = "Happiness ${uiState.moodState.happinessScore}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.secondary,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("happiness_score_badge")
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = scheme.surface,
                            modifier = Modifier.border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(50))
                        ) {
                            Text(
                                text = "${uiState.totalXp} XP",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { uiState.moodState.happinessFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = scheme.secondary,
                    trackColor = scheme.surface
                )

                Spacer(modifier = Modifier.height(4.dp))

                UserProfileAndStreakBannerChip(
                    userProfile = uiState.userProfile,
                    streakState = uiState.consistencyStreak,
                    isSoundEnabled = uiState.isSoundEnabled,
                    isHapticsEnabled = uiState.isHapticsEnabled,
                    onToggleSound = { onIntent(DeskIntent.ToggleSoundEnabled) },
                    onToggleHaptics = { onIntent(DeskIntent.ToggleHapticsEnabled) },
                    onOpenProfileTab = onOpenProfileAndTasksTab
                )
            }
        }

        // 2. Centered 3D Nooki Fox Companion (with Theme-Synced Happy State 3D Particles & Touch Gestures)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Nooki3DCenterpiece(
                    mood = uiState.petMood,
                    moodState = uiState.moodState,
                    appTheme = uiState.selectedTheme,
                    speechText = uiState.speechBubbleText,
                    isAutoRotateEnabled = uiState.isAutoRotate3DEnabled,
                    petCombo = uiState.petInteractionCombo,
                    onPetTap = { onIntent(DeskIntent.PetNookiHead) },
                    onPetGesture = { gesture -> onIntent(DeskIntent.TriggerPetGesture(gesture)) },
                    onToggleAutoRotate = { onIntent(DeskIntent.ToggleAutoRotate3D) }
                )
            }
        }

        // 3. ONLY the thing ringing right now gets a solid block!
        items(uiState.ringingReminders, key = { "ringing_solid_${it.id}" }) { ringing ->
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 4. "Tell Nooki: capture in one breath" (English, Hinglish, Hindi, Held things, Money, Questions)
        item {
            OneBreathCaptureSection(
                inputText = uiState.breathInputText,
                liveParsed = uiState.liveParsedCapture,
                onInputChanged = { onIntent(DeskIntent.UpdateBreathInput(it)) },
                onSave = { onIntent(DeskIntent.SaveBreathInput) },
                onVoiceClick = onStartVoiceCapture
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Calm permission ask only when needed
        if (!hasNotificationPermission) {
            item {
                TextButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.testTag("grant_notifications_button")
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Marigold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Let Nooki ring your phone when a reminder is due",
                        color = Marigold
                    )
                }
            }
        }

        // =========================================================================
        // QUESTION 1: "You missed" (Sindoor / Vermilion #E23B3B Thread)
        // =========================================================================
        item {
            ThreadSectionHeader(
                threadColor = Sindoor,
                title = "You missed (${uiState.youMissedKnots.size})",
                subtitle = "Calls you didn't pick up, reminders you swiped away or haven't confirmed"
            )
        }

        if (uiState.youMissedKnots.isEmpty()) {
            item {
                ThreadEmptyNode(
                    threadColor = Sindoor,
                    text = "Nothing missed right now."
                )
            }
        } else {
            items(uiState.youMissedKnots, key = { "missed_${it.id}" }) { item ->
                ThreadKnotRow(
                    reminder = item,
                    knotColor = Sindoor,
                    nowMillis = uiState.nowMillis,
                    isDone = false,
                    onTapKnotToUntie = {
                        NotificationHelper.cancelNotification(context, item.id)
                        onIntent(DeskIntent.CompleteReminder(item))
                    },
                    onSnooze = { onIntent(DeskIntent.SnoozeReminder(item, 15)) },
                    onFireNow = { onFireAlarmNow(item) },
                    onDelete = { onIntent(DeskIntent.DeleteReminder(item)) }
                )
            }
        }

        // =========================================================================
        // QUESTION 2: "You forgot & Nooki holds for you" (Marigold #FFAE1A Thread)
        // =========================================================================
        item {
            val moneySummary = buildString {
                if (uiState.netMoneyOwedToYouInr > 0) {
                    append("People owe you ₹${uiState.netMoneyOwedToYouInr}")
                }
                if (uiState.netMoneyYouOweInr > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("You owe ₹${uiState.netMoneyYouOweInr}")
                }
                if (isEmpty()) {
                    append("Money with people, things you lent, saved places & approaching dates")
                }
            }
            ThreadSectionHeader(
                threadColor = Marigold,
                title = "You forgot & held for you (${uiState.youForgotAndHeldKnots.size})",
                subtitle = moneySummary
            )
        }

        // Privacy-safe Nooki Notices suggestions (nothing becomes a reminder until tapped)
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
                    text = "Try typing \"Rahul owes me 500\" or \"Passport is in the blue bag\"."
                )
            }
        } else {
            items(uiState.youForgotAndHeldKnots, key = { "forgot_${it.id}" }) { item ->
                ThreadKnotRow(
                    reminder = item,
                    knotColor = Marigold,
                    nowMillis = uiState.nowMillis,
                    isDone = false,
                    onTapKnotToUntie = {
                        NotificationHelper.cancelNotification(context, item.id)
                        onIntent(DeskIntent.CompleteReminder(item))
                    },
                    onSnooze = { onIntent(DeskIntent.SnoozeReminder(item, 60)) },
                    onFireNow = { onFireAlarmNow(item) },
                    onDelete = { onIntent(DeskIntent.DeleteReminder(item)) }
                )
            }
        }

        // =========================================================================
        // QUESTION 3: "Today & coming up" (Neel Indigo Thread)
        // =========================================================================
        item {
            ThreadSectionHeader(
                threadColor = ThreadIndigoLight,
                title = "Today & coming up (${uiState.todayAndComingUpKnots.size})",
                subtitle = "Tap any knot on the thread to untie it when done"
            )
        }

        if (uiState.todayAndComingUpKnots.isEmpty()) {
            item {
                ThreadEmptyNode(
                    threadColor = ThreadIndigoLight,
                    text = "Your list for today is clear."
                )
            }
        } else {
            items(uiState.todayAndComingUpKnots, key = { "today_${it.id}" }) { item ->
                ThreadKnotRow(
                    reminder = item,
                    knotColor = ThreadIndigoLight,
                    nowMillis = uiState.nowMillis,
                    isDone = false,
                    onTapKnotToUntie = {
                        NotificationHelper.cancelNotification(context, item.id)
                        onIntent(DeskIntent.CompleteReminder(item))
                    },
                    onSnooze = { onIntent(DeskIntent.SnoozeReminder(item, 10)) },
                    onFireNow = { onFireAlarmNow(item) },
                    onDelete = { onIntent(DeskIntent.DeleteReminder(item)) }
                )
            }

            // Evening Wrap one-tap action
            item {
                ThreadEveningWrapRow(
                    unfinishedCount = uiState.todayAndComingUpKnots.size,
                    onMoveAllToTomorrow = { onIntent(DeskIntent.EveningWrapMoveAllToTomorrow) }
                )
            }
        }

        // =========================================================================
        // Untied / Done Knots (Leaf Green #1F9D6B Thread)
        // =========================================================================
        if (uiState.doneKnots.isNotEmpty()) {
            item {
                ThreadSectionHeader(
                    threadColor = LeafGreen,
                    title = "Done (${uiState.doneKnots.size})",
                    subtitle = "Untied knots"
                )
            }
            items(uiState.doneKnots.take(6), key = { "done_${it.id}" }) { done ->
                ThreadKnotRow(
                    reminder = done,
                    knotColor = LeafGreen,
                    nowMillis = uiState.nowMillis,
                    isDone = true,
                    onTapKnotToUntie = {},
                    onSnooze = {},
                    onFireNow = {},
                    onDelete = { onIntent(DeskIntent.DeleteReminder(done)) }
                )
            }
        }

        // =========================================================================
        // Consistency Streak & Dedicated To-Do / Done Tasks Section on Home Screen
        // =========================================================================
        item {
            Spacer(modifier = Modifier.height(16.dp))
            ConsistencyStreakCard(streakState = uiState.consistencyStreak)
            Spacer(modifier = Modifier.height(14.dp))
            TodoAndDoneTasksSection(
                todoTasks = uiState.todoTasks,
                doneTasks = uiState.doneTasks,
                selectedFilter = uiState.selectedTaskFilter,
                nowMillis = uiState.nowMillis,
                onSelectFilter = { onIntent(DeskIntent.SelectTaskBoardFilter(it)) },
                onQuickAddTodo = { title, priority, category ->
                    onIntent(DeskIntent.QuickAddTodoTask(title, priority, category))
                },
                onCompleteTask = {
                    NotificationHelper.cancelNotification(context, it.id)
                    onIntent(DeskIntent.CompleteReminder(it))
                },
                onReopenDoneTask = { onIntent(DeskIntent.ReopenDoneTask(it)) },
                onDeleteTask = { onIntent(DeskIntent.DeleteReminder(it)) },
                onClearAllDone = { onIntent(DeskIntent.ClearCompleted) }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OneBreathCaptureSection(
    inputText: String,
    liveParsed: com.example.domain.parser.ParsedBreathCapture,
    onInputChanged: (String) -> Unit,
    onSave: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            placeholder = {
                Text(
                    text = "Tell Nooki: \"kal subah 9 baje doctor\", \"Rahul owes me 500\"...",
                    color = WashedCotton.copy(alpha = 0.55f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Marigold,
                unfocusedBorderColor = ThreadIndigoLight.copy(alpha = 0.55f),
                focusedContainerColor = NeelIndigo,
                unfocusedContainerColor = NeelIndigo.copy(alpha = 0.75f),
                focusedTextColor = WashedCotton,
                unfocusedTextColor = WashedCotton
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.testTag("voice_capture_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak in English or Hindi",
                            tint = Marigold
                        )
                    }
                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = onSave,
                            modifier = Modifier.testTag("save_breath_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Save to Nooki's thread",
                                tint = LeafGreen
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("breath_capture_input")
        )

        // Live "Show what Nooki understood as you type, before saving"
        AnimatedVisibility(visible = inputText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NeelIndigo,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (liveParsed.kind == NookiCaptureKind.QUESTION) Marigold else LeafGreen,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("live_understood_preview")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (liveParsed.kind == NookiCaptureKind.QUESTION) {
                                "Nooki's answer"
                            } else {
                                "What Nooki understood"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Marigold
                        )
                        Text(
                            text = liveParsed.toPlainSummary(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = WashedCotton
                        )
                    }
                    if (liveParsed.kind != NookiCaptureKind.QUESTION) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Marigold,
                                contentColor = NightIndigo
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Tie knot", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick 1-tap natural language examples (English, Hinglish, Hindi, Held things, Money, Questions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val examples = listOf(
                "kal subah 9 baje doctor",
                "कल शाम 6 बजे दवाई",
                "where is my passport",
                "Aman owes me 300",
                "har somvar gym",
                "Spare key is with Sharma aunty"
            )
            examples.forEach { sample ->
                AssistChip(
                    onClick = { onInputChanged(sample) },
                    label = {
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WashedCotton
                        )
                    }
                )
            }
        }
    }
}

/**
 * "Only the thing ringing right now gets a solid block."
 */
@Composable
private fun SolidRingingBlock(
    reminder: ReminderEntity,
    onUntieDone: () -> Unit,
    onAskLater: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Sindoor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("solid_ringing_block_${reminder.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Ringing right now",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ringing right now",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onUntieDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = NightIndigo
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Untie knot (Done)", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onAskLater,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask in 5m")
                }
            }
        }
    }
}

/**
 * Section header anchored to Nooki's vertical thread.
 */
@Composable
private fun ThreadSectionHeader(
    threadColor: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Thread Spine
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                drawLine(
                    color = threadColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 14.dp, bottom = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = threadColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = WashedCotton.copy(alpha = 0.72f)
            )
        }
    }
}

/**
 * Empty state line hanging directly off the thread without card clutter.
 */
@Composable
private fun ThreadEmptyNode(
    threadColor: Color,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                drawLine(
                    color = threadColor.copy(alpha = 0.45f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = WashedCotton.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

/**
 * A single item hanging off Nooki's thread as a tactile Knot.
 * "Every item is a knot on that thread. Tap a knot to untie it, and the thing is done."
 */
@Composable
private fun ThreadKnotRow(
    reminder: ReminderEntity,
    knotColor: Color,
    nowMillis: Long,
    isDone: Boolean,
    onTapKnotToUntie: () -> Unit,
    onSnooze: () -> Unit,
    onFireNow: () -> Unit,
    onDelete: () -> Unit
) {
    val isHeldMemory = reminder.notes.contains("KIND:HELD_MEMORY")
    val isMoneyOrLent = reminder.notes.contains("KIND:MONEY_") || reminder.notes.contains("KIND:ITEM_LENT")
    val isMedicine = reminder.notes.contains("KIND:MEDICINE") || reminder.title.contains("दवाई")

    val timeLabel = remember(reminder.triggerAtMillis, nowMillis, isDone, isHeldMemory, isMoneyOrLent) {
        when {
            isDone -> "Untied"
            isHeldMemory -> "Saved in Nooki's memory"
            isMoneyOrLent -> "Held until settled"
            reminder.status == ReminderStatus.MISSED -> {
                val minsAgo = ((nowMillis - reminder.triggerAtMillis) / 60_000L).coerceAtLeast(1L)
                if (minsAgo < 60L) "Missed ${minsAgo}m ago" else "Missed ${minsAgo / 60L}h ago"
            }
            else -> {
                val diff = reminder.triggerAtMillis - nowMillis
                val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(reminder.triggerAtMillis))
                when {
                    diff <= 0L -> "Due now ($clock)"
                    diff < 3_600_000L -> "in ${diff / 60_000L}m ($clock)"
                    diff < 24 * 3_600_000L -> "Today at $clock"
                    else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(reminder.triggerAtMillis))
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("thread_knot_row_${reminder.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Thread & Interactive Knot Target (48dp minimum touch target for accessibility)
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .clickable(enabled = !isDone, onClick = onTapKnotToUntie)
                .testTag("untie_knot_btn_${reminder.id}"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f

                // Continuous vertical thread line
                drawLine(
                    color = knotColor.copy(alpha = if (isDone) 0.5f else 0.9f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 3.5.dp.toPx()
                )

                if (isDone) {
                    // Untied open loop in Leaf Green
                    drawCircle(
                        color = NightIndigo,
                        radius = 7.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = LeafGreen,
                        radius = 7.dp.toPx(),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                } else {
                    // Tied handkerchief/sari knot with double loop ring
                    drawCircle(
                        color = knotColor.copy(alpha = 0.28f),
                        radius = 14.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = knotColor,
                        radius = 9.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = WashedCotton,
                        radius = 3.5.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                }
            }
        }

        // Right Content hanging directly off the thread (Left-aligned, one column, generous space)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDone) WashedCotton.copy(alpha = 0.55f) else WashedCotton,
                textDecoration = if (isDone) TextDecoration.LineThrough else null
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = knotColor
                )
                if (isMedicine && !isDone) {
                    Text(
                        text = "• Asks until confirmed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sindoor
                    )
                } else if (reminder.recurrenceMinutes != null && !isDone) {
                    Text(
                        text = "• Repeats",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Marigold
                    )
                }
            }

            if (!isDone) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap knot to untie",
                        style = MaterialTheme.typography.labelLarge,
                        color = LeafGreen,
                        modifier = Modifier.clickable { onTapKnotToUntie() }
                    )
                    if (!isHeldMemory && !isMoneyOrLent) {
                        Text(
                            text = "Later",
                            style = MaterialTheme.typography.labelLarge,
                            color = WashedCotton.copy(alpha = 0.75f),
                            modifier = Modifier.clickable { onSnooze() }
                        )
                        Text(
                            text = "Ring now",
                            style = MaterialTheme.typography.labelLarge,
                            color = Marigold,
                            modifier = Modifier.clickable { onFireNow() }
                        )
                    }
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelLarge,
                        color = WashedCotton.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onDelete() }
                    )
                }
            }
        }
    }
}

/**
 * Privacy-safe Nooki Notice suggestion node on the Marigold thread.
 * "Noticing is a suggestion. Nothing becomes a reminder until the person taps."
 */
@Composable
private fun ThreadSuggestionNode(
    suggestion: NoticeInspectionResult.SafeSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                drawLine(
                    color = Marigold.copy(alpha = 0.65f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                drawCircle(
                    color = Marigold,
                    radius = 7.dp.toPx(),
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NeelIndigo.copy(alpha = 0.85f),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .border(1.dp, Marigold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nooki noticed: ${suggestion.sanitizedTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Marigold
                    )
                    Text(
                        text = suggestion.sanitizedSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton.copy(alpha = 0.8f)
                    )
                }
                Row {
                    TextButton(onClick = onAccept) {
                        Text("Tie knot", color = LeafGreen, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Ignore suggestion",
                            tint = WashedCotton.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Evening wrap row hanging off the Indigo thread:
 * "Evening wrap: what did not get done, with one tap to move it all to tomorrow, plus a nudge if the phone needs charging overnight."
 */
@Composable
private fun ThreadEveningWrapRow(
    unfinishedCount: Int,
    onMoveAllToTomorrow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                drawLine(
                    color = ThreadIndigoLight.copy(alpha = 0.55f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NeelIndigo,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
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
                        imageVector = Icons.Default.WbTwilight,
                        contentDescription = null,
                        tint = Marigold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Evening wrap",
                            style = MaterialTheme.typography.titleMedium,
                            color = WashedCotton
                        )
                        Text(
                            text = "Move $unfinishedCount open item(s) to tomorrow morning & plug in your charger.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WashedCotton.copy(alpha = 0.75f)
                        )
                    }
                }
                TextButton(
                    onClick = onMoveAllToTomorrow,
                    modifier = Modifier.testTag("evening_wrap_move_button")
                ) {
                    Text("Move to tomorrow", color = Marigold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Second tab:
 * - Explains clearly what Nooki reads and why before asking for access
 * - Lets the user turn off each kind of noticing ("off means off")
 * - Proves the OTP / Password / Bank Balance Dropper live
 * - Preserves the Milestone 1 & 2 Room + Exact Alarm + Boot Recovery diagnostics
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NookiNoticesAndReliabilityScreen(
    uiState: DeskUiState,
    onOpenExactAlarmSettings: () -> Unit,
    onIntent: (DeskIntent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Plain-language Privacy Disclosure & Individual Noticing Toggles
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NeelIndigo),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = LeafGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How Nooki notices (100% on your phone)",
                            style = MaterialTheme.typography.titleLarge,
                            color = WashedCotton
                        )
                    }
                    Text(
                        text = "• Nooki has NO internet permission. Nothing ever leaves your phone.\n" +
                            "• Nooki never stores message text — only who messaged or called and when.\n" +
                            "• OTPs, passwords, PINs, and bank balances are dropped immediately before anything is saved.\n" +
                            "• Turn off any kind of noticing below — 'off' means off.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton.copy(alpha = 0.85f)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NoticeSourceKind.entries.forEach { kind ->
                            val enabled = uiState.noticeSettings.isAllowed(kind)
                            FilterChip(
                                selected = enabled,
                                onClick = { onIntent(DeskIntent.ToggleNoticeKind(kind)) },
                                label = { Text(kind.displayLabel) },
                                leadingIcon = if (enabled) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    // Live OTP & Privacy Filter Verification Buttons
                    Text(
                        text = "Test Nooki's Privacy Filter:",
                        style = MaterialTheme.typography.labelLarge,
                        color = Marigold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onIntent(
                                    DeskIntent.SimulateIncomingNotification(
                                        packageName = "com.bank.sms",
                                        senderOrTitle = "HDFC Bank",
                                        rawBody = "849201 is your OTP for transaction. Avail Bal INR 42,100."
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_otp_drop_button")
                        ) {
                            Text("Simulate OTP SMS", color = Sindoor)
                        }
                        FilledTonalButton(
                            onClick = {
                                onIntent(
                                    DeskIntent.SimulateIncomingNotification(
                                        packageName = "com.android.dialer",
                                        senderOrTitle = "Mom",
                                        rawBody = "Missed call from Mom"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_missed_call_button")
                        ) {
                            Text("Simulate Missed Call")
                        }
                    }

                    if (uiState.lastDroppedNoticeAudit != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NightIndigo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.lastDroppedNoticeAudit,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LeafGreen,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Reliability & Reboot Survival Lab (Pre-built Milestone 1 & 2 Controls)
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NeelIndigo),
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
                            tint = Marigold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reliability, Exact Medicine Alarms & Reboot Check",
                            style = MaterialTheme.typography.titleLarge,
                            color = WashedCotton
                        )
                    }
                    Text(
                        text = "StateFlow: ${uiState.stateTransitionSummary}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Marigold
                    )
                    Text(
                        text = "Exact alarms are used only for medicine or critical items. Standard reminders use battery-friendly batch alarms.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onIntent(DeskIntent.Schedule5SecondTestAlarm) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Marigold,
                                contentColor = NightIndigo
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_5s_test_alarm")
                        ) {
                            Icon(Icons.Default.AlarmOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+5s Medicine Test", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onIntent(DeskIntent.SimulateBootRecovery) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_simulate_boot")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reboot Check")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Critical Exact Alarms: ${if (uiState.canScheduleExactAlarms) "Ready" else "Tap to allow"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.canScheduleExactAlarms) LeafGreen else Marigold,
                            modifier = Modifier.clickable {
                                if (!uiState.canScheduleExactAlarms) onOpenExactAlarmSettings()
                            }
                        )
                        TextButton(onClick = { onIntent(DeskIntent.ClearCompleted) }) {
                            Text("Clear Done (${uiState.doneKnots.size})", color = WashedCotton)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Real-time Measurements & Telemetry (${uiState.telemetryLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                color = WashedCotton
            )
        }

        items(uiState.telemetryLogs, key = { it.id }) { entry ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NeelIndigo,
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
                            color = if (entry.isWarning) Sindoor else Marigold
                        )
                        Text(
                            text = entry.formattedTime(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = WashedCotton.copy(alpha = 0.65f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WashedCotton
                    )
                }
            }
        }
    }
}
