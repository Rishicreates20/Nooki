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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AccountEntity
import com.example.auth.AuthRepository
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.ReminderEntity
import com.example.data.repository.HealthRepository
import com.example.data.repository.ReminderRepositoryImpl
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskSideEffect
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.ui.components.DynamicWeatherSkyBackdrop
import com.example.presentation.ui.screens.AuthScreen
import com.example.presentation.ui.screens.CompletedScreen
import com.example.presentation.ui.screens.HealthScreen
import com.example.presentation.ui.screens.HomeScreen
import com.example.presentation.ui.screens.NookiNoticesAndReliabilityScreen
import com.example.presentation.ui.screens.NookiTab
import com.example.presentation.ui.screens.PendingScreen
import com.example.presentation.ui.screens.ProfileScreen
import com.example.presentation.ui.screens.StepCounterEffect
import com.example.presentation.ui.screens.StepSensor
import com.example.presentation.ui.screens.StepSensorStatus
import com.example.presentation.viewmodel.DeskPetViewModel
import com.example.presentation.viewmodel.HealthUiState
import com.example.presentation.viewmodel.HealthViewModel
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiLocationWeatherProvider
import com.example.scheduler.NookiSensoryFeedbackManager
import com.example.scheduler.NotificationHelper
import com.example.scheduler.receiver.ReminderAlarmReceiver
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannelCreated(this)

        val authRepository = AuthRepository(applicationContext)

        setContent {
            var sessionChecked by remember { mutableStateOf(false) }
            var account by remember { mutableStateOf<AccountEntity?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                account = authRepository.currentAccount()
                sessionChecked = true
            }

            val signedIn = account
            when {
                !sessionChecked -> MyApplicationTheme {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }

                signedIn == null -> MyApplicationTheme {
                    AuthScreen(
                        authRepository = authRepository,
                        onSignedIn = { account = it }
                    )
                }

                else -> SignedInApp(
                    account = signedIn,
                    onLogOut = {
                        scope.launch {
                            // Stop this user's screens first, then close their database, then start
                            // fresh on the login screen.
                            viewModelStore.clear()
                            authRepository.logOut()
                            recreate()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Opens the signed-in account's own database and view models, then shows the app.
 */
@Composable
private fun SignedInApp(account: AccountEntity, onLogOut: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val database = remember(account.id) { AppDatabase.getInstance(appContext) }
    val alarmScheduler = remember { AndroidExactAlarmScheduler(appContext) }
    val repository = remember(database) { ReminderRepositoryImpl(database.reminderDao(), alarmScheduler) }

    val deskViewModel: DeskPetViewModel = viewModel(
        key = "desk_${account.id}",
        factory = DeskPetViewModel.Factory(repository, alarmScheduler)
    )
    val healthViewModel: HealthViewModel = viewModel(
        key = "health_${account.id}",
        factory = HealthViewModel.Factory(HealthRepository(database.healthDao()))
    )
    val uiState by deskViewModel.uiState.collectAsStateWithLifecycle()
    val healthState by healthViewModel.uiState.collectAsStateWithLifecycle()

    MyApplicationTheme(appTheme = uiState.selectedTheme) {
        NookiMainApp(
            account = account,
            uiState = uiState,
            healthState = healthState,
            viewModel = deskViewModel,
            healthViewModel = healthViewModel,
            alarmScheduler = alarmScheduler,
            onLogOut = onLogOut
        )
    }
}

@Composable
fun NookiMainApp(
    account: AccountEntity,
    uiState: DeskUiState,
    healthState: HealthUiState,
    viewModel: DeskPetViewModel,
    healthViewModel: HealthViewModel,
    alarmScheduler: AndroidExactAlarmScheduler,
    onLogOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme

    var selectedTabName by rememberSaveable { mutableStateOf(NookiTab.HOME.name) }
    val selectedTab = NookiTab.valueOf(selectedTabName)
    var showNotices by rememberSaveable { mutableStateOf(false) }
    val navigateTo: (NookiTab) -> Unit = {
        showNotices = false
        selectedTabName = it.name
    }

    // ---- Weather (on-device, used for Nooki's backdrop) ----
    val locationWeatherProvider = remember(context) { NookiLocationWeatherProvider(context.applicationContext) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        coroutineScope.launch {
            viewModel.onIntent(DeskIntent.ApplyLocationWeatherUpdate(locationWeatherProvider.fetchLocalWeatherState()))
            if (!granted) snackbarHostState.showSnackbar("Using local time-of-day weather instead (location not allowed)")
        }
    }
    val requestOrSyncLocationWeather: () -> Unit = {
        if (locationWeatherProvider.hasLocationPermission()) {
            coroutineScope.launch {
                viewModel.onIntent(DeskIntent.ApplyLocationWeatherUpdate(locationWeatherProvider.fetchLocalWeatherState()))
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    LaunchedEffect(locationWeatherProvider) {
        viewModel.onIntent(DeskIntent.ApplyLocationWeatherUpdate(locationWeatherProvider.fetchLocalWeatherState()))
    }

    // ---- Notifications ----
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- Step counting (phone's built-in sensor, offline) ----
    var stepStatus by remember { mutableStateOf(StepSensor.status(context)) }
    val stepPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { stepStatus = StepSensor.status(context) }
    StepCounterEffect(
        context = context.applicationContext,
        enabled = stepStatus == StepSensorStatus.COUNTING,
        onReading = healthViewModel::onStepCounterReading
    )

    // ---- Voice capture (English, Hinglish, Hindi) ----
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spokenText.isNullOrBlank()) viewModel.onIntent(DeskIntent.UpdateBreathInput(spokenText))
    }
    val startVoiceCapture: () -> Unit = {
        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Nooki in English, Hinglish, or हिंदी...")
        }
        runCatching { speechRecognizerLauncher.launch(voiceIntent) }
    }

    val fireAlarmNow: (ReminderEntity) -> Unit = { reminder ->
        val broadcastIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = AndroidExactAlarmScheduler.ACTION_TRIGGER_REMINDER_ALARM
            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_TITLE, reminder.title)
        }
        context.sendBroadcast(broadcastIntent)
    }

    // ---- Sounds, haptics and messages from the view model ----
    val sensoryManager = remember(context) { NookiSensoryFeedbackManager(context.applicationContext) }
    DisposableEffect(sensoryManager) { onDispose { sensoryManager.release() } }
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DeskSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                DeskSideEffect.TriggerHapticPulse -> NotificationHelper.triggerDeskPetPulseVibration(context)
                is DeskSideEffect.PlaySensoryCue -> sensoryManager.performSensoryCue(
                    cue = effect.cue,
                    soundEnabled = effect.soundEnabled,
                    hapticsEnabled = effect.hapticsEnabled
                )
            }
        }
    }

    // Back goes: Notices -> Profile, any tab -> Home.
    BackHandler(enabled = showNotices || selectedTab != NookiTab.HOME) {
        if (showNotices) showNotices = false else selectedTabName = NookiTab.HOME.name
    }

    val pendingCount = uiState.youMissedKnots.size + uiState.youForgotAndHeldKnots.size +
        uiState.todayAndComingUpKnots.size + uiState.ringingReminders.size

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = scheme.surface) {
                NookiTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(tab) },
                        icon = {
                            val badgeCount = when (tab) {
                                NookiTab.PENDING -> pendingCount
                                else -> 0
                            }
                            val needsAttention = tab == NookiTab.PENDING &&
                                (uiState.youMissedKnots.isNotEmpty() || uiState.ringingReminders.isNotEmpty())
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge(
                                            containerColor = if (needsAttention) scheme.error else scheme.primary,
                                            contentColor = if (needsAttention) scheme.onError else scheme.onPrimary
                                        ) { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = { Text(tab.label, maxLines = 1) },
                        alwaysShowLabel = true,
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        DynamicWeatherSkyBackdrop(
            weatherState = uiState.weatherState,
            appTheme = uiState.selectedTheme,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (showNotices) {
                    NookiNoticesAndReliabilityScreen(
                        uiState = uiState,
                        onOpenExactAlarmSettings = {
                            alarmScheduler.buildExactAlarmPermissionIntent()?.let { intent ->
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        onIntent = viewModel::onIntent
                    )
                } else {
                    when (selectedTab) {
                        NookiTab.HOME -> HomeScreen(
                            account = account,
                            uiState = uiState,
                            healthState = healthState,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestNotificationPermission = requestNotificationPermission,
                            onStartVoiceCapture = startVoiceCapture,
                            onNavigate = navigateTo,
                            onIntent = viewModel::onIntent
                        )

                        NookiTab.PENDING -> PendingScreen(
                            uiState = uiState,
                            onStartVoiceCapture = startVoiceCapture,
                            onFireAlarmNow = fireAlarmNow,
                            onIntent = viewModel::onIntent
                        )

                        NookiTab.DONE -> CompletedScreen(
                            uiState = uiState,
                            onIntent = viewModel::onIntent
                        )

                        NookiTab.HEALTH -> HealthScreen(
                            healthState = healthState,
                            deskState = uiState,
                            stepStatus = stepStatus,
                            onRequestStepPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    stepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            },
                            onAddSteps = healthViewModel::addSteps,
                            onChangeWater = healthViewModel::changeWater,
                            onSetSleepMinutes = healthViewModel::setSleepMinutes,
                            onSetMood = healthViewModel::setMood,
                            onStartWorkout = healthViewModel::startWorkout,
                            onFinishWorkout = healthViewModel::finishWorkout,
                            onCancelWorkout = healthViewModel::cancelWorkout,
                            onLogWorkout = healthViewModel::logWorkout,
                            onDeleteWorkout = healthViewModel::deleteWorkout,
                            onIntent = viewModel::onIntent
                        )

                        NookiTab.PROFILE -> ProfileScreen(
                            account = account,
                            uiState = uiState,
                            onOpenNoticesAndReliability = { showNotices = true },
                            onSyncGpsWeather = requestOrSyncLocationWeather,
                            onLogOut = onLogOut,
                            onIntent = viewModel::onIntent
                        )
                    }
                }
            }
        }
    }
}
