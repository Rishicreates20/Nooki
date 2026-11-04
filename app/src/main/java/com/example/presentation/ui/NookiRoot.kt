package com.example.presentation.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.local.entity.ReminderSource
import com.example.feedback.PetFeedback
import com.example.pet.NookiPet3D
import com.example.presentation.mvi.CaptureDraft
import com.example.presentation.mvi.NookiEffect
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.mvi.NookiTab
import com.example.presentation.mvi.NookiUiState
import com.example.presentation.ui.capture.CaptureBar
import com.example.presentation.ui.capture.CaptureSheet
import com.example.presentation.ui.components.NookiFace
import com.example.presentation.ui.lab.SchedulerLabScreen
import com.example.presentation.ui.nooki.NookiScreen
import com.example.presentation.ui.nooki.ReadinessActions
import com.example.presentation.ui.onboarding.OnboardingScreen
import com.example.presentation.ui.remember.RememberScreen
import com.example.presentation.ui.today.ItemSheet
import com.example.presentation.ui.today.TodayScreen
import com.example.presentation.viewmodel.NookiViewModel
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NotificationHelper
import com.example.ui.theme.NookiPalette
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Wires the screens to the ViewModel and to the Android things they must not touch themselves. */
@Composable
fun NookiRoot(
    viewModel: NookiViewModel,
    scheduler: AndroidExactAlarmScheduler,
    incoming: StateFlow<CaptureDraft?>,
    onIncomingHandled: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember { PetFeedback(context) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is NookiEffect.Message -> scope.launch {
                    snackbars.currentSnackbarData?.dismiss()
                    val result = snackbars.showSnackbar(
                        message = effect.text,
                        actionLabel = if (effect.undo != null) "Undo" else null,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) effect.undo?.let(onIntent)
                }
                is NookiEffect.CancelNotification -> NotificationHelper.cancelNotification(context, effect.reminderId)
                NookiEffect.Tap -> feedback.tap()
                NookiEffect.Confirm -> feedback.confirm()
                NookiEffect.Celebrate -> feedback.celebrate()
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        onIntent(NookiIntent.Refresh)
        onPauseOrDispose { }
    }

    val pendingCapture by incoming.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCapture) {
        pendingCapture?.let { draft ->
            onIntent(NookiIntent.OpenCapture(draft))
            onIncomingHandled()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onIntent(NookiIntent.Refresh)
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onIntent(NookiIntent.Refresh)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            onIntent(NookiIntent.OpenCapture(CaptureDraft(prefill = spoken, source = ReminderSource.VOICE)))
        }
    }

    fun openVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Nooki")
        }
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbars.showSnackbar("This phone has no voice input. Type it instead.") }
        }
    }

    fun startSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    val actions = remember(context) {
        ReadinessActions(
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startSettings(appInfoIntent(context.packageName))
                }
            },
            openExactAlarms = { scheduler.buildExactAlarmPermissionIntent()?.let(::startSettings) },
            openNoticeAccess = { startSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            openAppInfo = { startSettings(appInfoIntent(context.packageName)) },
            openBattery = { startSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            requestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
        )
    }

    if (!state.isLoaded) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }

    if (!state.prefs.onboarded) {
        OnboardingScreen(
            readiness = state.readiness,
            actions = actions,
            onFinish = { name, persona, starters ->
                onIntent(NookiIntent.FinishOnboarding(name, persona, starters))
            }
        )
        return
    }

    var showDeveloper by rememberSaveable { mutableStateOf(false) }
    if (showDeveloper && BuildConfig.DEBUG) {
        BackHandler { showDeveloper = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SchedulerLabScreen(state, onIntent, onClose = { showDeveloper = false })
        }
        return
    }

    if (state.tab != NookiTab.TODAY) {
        BackHandler { onIntent(NookiIntent.SelectTab(NookiTab.TODAY)) }
    }

    NookiShell(
        state = state,
        onIntent = onIntent,
        snackbars = snackbars,
        actions = actions,
        onVoice = ::openVoice,
        showDeveloper = BuildConfig.DEBUG,
        onOpenDeveloper = { showDeveloper = true },
        petStage = { modifier -> PetStage(state, onIntent, modifier) }
    )

    state.capture?.let { draft ->
        CaptureSheet(
            draft = draft,
            nowMillis = state.nowMillis,
            persona = state.prefs.persona,
            memories = state.memories,
            onDismiss = { onIntent(NookiIntent.CloseCapture) },
            onVoice = ::openVoice,
            onSubmit = { onIntent(NookiIntent.SubmitCapture(it)) }
        )
    }

    state.openItem?.let { reminder -> ItemSheet(reminder, state.nowMillis, onIntent) }
}

private fun appInfoIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

@Composable
private fun PetStage(state: NookiUiState, onIntent: (NookiIntent) -> Unit, modifier: Modifier) {
    NookiPet3D(
        mood = state.mood,
        stageColor = NookiPalette.colors.raised,
        surroundColor = MaterialTheme.colorScheme.background,
        onPetTapped = { onIntent(NookiIntent.PetNooki) },
        modifier = modifier,
        fallback = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NookiFace(state.mood, Modifier.size(210.dp), onTap = { onIntent(NookiIntent.PetNooki) }, reaction = state.petReaction)
            }
        }
    )
}

/** Everything inside the tabs. Takes plain state, so screenshot tests can render it. */
@Composable
fun NookiShell(
    state: NookiUiState,
    onIntent: (NookiIntent) -> Unit,
    snackbars: SnackbarHostState,
    actions: ReadinessActions,
    onVoice: () -> Unit,
    showDeveloper: Boolean,
    onOpenDeveloper: () -> Unit,
    petStage: @Composable (Modifier) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbars) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    actionColor = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        bottomBar = {
            val paper = MaterialTheme.colorScheme.background
            Column(Modifier.fillMaxWidth()) {
                if (state.tab != NookiTab.NOOKI) {
                    // The list fades out behind the bar, so nothing is cut off mid-line.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(paper.copy(alpha = 0f), paper)))
                            .padding(top = 18.dp, bottom = 6.dp)
                    ) {
                        CaptureBar(onOpen = { onIntent(NookiIntent.OpenCapture()) }, onVoice = onVoice)
                    }
                }
                Column(Modifier.fillMaxWidth().background(paper).navigationBarsPadding()) {
                    BottomTabs(state, onIntent)
                }
            }
        }
    ) { inner ->
        val padding = PaddingValues(bottom = inner.calculateBottomPadding() + 8.dp)
        when (state.tab) {
            NookiTab.TODAY -> TodayScreen(
                state = state,
                onIntent = onIntent,
                onFixNotifications = actions.requestNotifications,
                contentPadding = padding
            )
            NookiTab.REMEMBER -> RememberScreen(state, onIntent, padding)
            NookiTab.NOOKI -> NookiScreen(
                state = state,
                onIntent = onIntent,
                actions = actions,
                contentPadding = padding,
                showDeveloper = showDeveloper,
                onOpenDeveloper = onOpenDeveloper,
                petStage = petStage
            )
        }
    }
}

@Composable
private fun BottomTabs(state: NookiUiState, onIntent: (NookiIntent) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Tab(NookiTab.TODAY, Icons.Rounded.Today, state, state.digest.needsAttentionCount, onIntent)
        Tab(NookiTab.REMEMBER, Icons.Rounded.Inventory2, state, 0, onIntent)
        Tab(NookiTab.NOOKI, Icons.Rounded.Pets, state, 0, onIntent)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(
    tab: NookiTab,
    icon: ImageVector,
    state: NookiUiState,
    badge: Int,
    onIntent: (NookiIntent) -> Unit
) {
    val selected = state.tab == tab
    val tint = if (selected) MaterialTheme.colorScheme.onBackground else NookiPalette.colors.muted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onIntent(NookiIntent.SelectTab(tab)) }
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = if (badge > 0) "${tab.label}, $badge need attention" else tab.label
            }
            .testTag("tab_${tab.name.lowercase()}")
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
            if (badge > 0) {
                Badge(
                    containerColor = NookiPalette.colors.missed,
                    contentColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopEnd).padding(start = 18.dp)
                ) { Text("$badge") }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}
