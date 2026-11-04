package com.example

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notice.NookiNotificationListener
import com.example.rhythm.DailyRhythm
import com.example.presentation.today.NoticingSheet
import com.example.presentation.today.TodayEffect
import com.example.presentation.today.TodayIntent
import com.example.presentation.today.TodayScreen
import com.example.presentation.today.TodayViewModel
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NotificationHelper
import com.example.ui.theme.NookiTheme

/**
 * Nooki's only screen: Today, answering what did I miss, what did I forget, what do I have to do.
 * Also receives text from the share sheet and from text selection in any app.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TodayViewModel by viewModels()

    /** Bumped when opened from the tile or the app-icon shortcut, to focus "Tell Nooki". */
    private val focusCapture = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannelCreated(this)
        DailyRhythm.schedule(this)
        handleIncomingText(intent)

        setContent {
            NookiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                var showNoticing by rememberSaveable { mutableStateOf(false) }
                var listenerOn by remember { mutableStateOf(isListenerOn()) }
                LifecycleResumeEffect(Unit) {
                    listenerOn = isListenerOn()
                    onPauseOrDispose { }
                }

                // Ask for notifications only at the moment it clearly helps: right after the first
                // reminder with a time is saved.
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val spoken = result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                    if (!spoken.isNullOrBlank()) viewModel.onIntent(TodayIntent.EditDraft(spoken))
                }

                LaunchedEffect(Unit) {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is TodayEffect.Say -> snackbar.showSnackbar(effect.message)
                            TodayEffect.AskNotificationPermission -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            TodayEffect.OfferExactAlarms -> {
                                val result = snackbar.showSnackbar(
                                    message = "Allow exact alarms so medicine and critical reminders ring on the minute.",
                                    actionLabel = "Allow",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    AndroidExactAlarmScheduler(this@MainActivity).buildExactAlarmPermissionIntent()?.let {
                                        runCatching { startActivity(it) }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    TodayScreen(
                        state = state,
                        onIntent = viewModel::onIntent,
                        noticingOn = listenerOn,
                        focusCaptureTick = focusCapture.intValue,
                        onOpenNoticing = { showNoticing = true },
                        onVoice = {
                            val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                // Hindi first; the recogniser also understands English and Hinglish.
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Nooki")
                            }
                            try {
                                voice.launch(speech)
                            } catch (_: ActivityNotFoundException) {
                                viewModel.onIntent(TodayIntent.EditDraft(state.draft))
                            }
                        },
                    )
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
                }

                if (showNoticing) {
                    NoticingSheet(
                        accessOn = listenerOn,
                        settings = state.noticeSettings,
                        onSettingsChange = { viewModel.onIntent(TodayIntent.SetNoticeSettings(it)) },
                        onOpenAccess = { openNotificationAccess() },
                        onOpenAppInfo = {
                            runCatching {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                            }
                        },
                        onClose = { showNoticing = false },
                    )
                }
            }
        }
    }

    private fun isListenerOn(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openNotificationAccess() {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, NookiNotificationListener::class.java).flattenToString(),
            )
        } else {
            null
        }
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (detail == null || runCatching { startActivity(detail) }.isFailure) runCatching { startActivity(list) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingText(intent)
    }

    /** Text shared from another app, or selected text sent with "Tell Nooki". */
    private fun handleIncomingText(intent: Intent?) {
        if (intent?.action == ACTION_TELL) focusCapture.intValue++
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) {
            viewModel.prefill(text)
            focusCapture.intValue++
        }
    }

    companion object {
        /** Opens straight into "Tell Nooki" (quick settings tile, app-icon shortcut). */
        const val ACTION_TELL = "com.example.nooki.TELL"
    }
}
