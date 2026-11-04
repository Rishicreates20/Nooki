package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.ReminderSource
import com.example.demo.SampleDay
import com.example.notice.NookiNotificationListener
import com.example.presentation.mvi.CaptureDraft
import com.example.presentation.mvi.Readiness
import com.example.presentation.ui.NookiRoot
import com.example.presentation.viewmodel.NookiViewModel
import com.example.scheduler.NotificationHelper
import com.example.ui.theme.NookiTheme
import com.example.widget.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val incomingCapture = MutableStateFlow<CaptureDraft?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannelCreated(this)
        val graph = NookiGraph.get(applicationContext)
        handleIncoming(intent)

        setContent {
            NookiTheme {
                val viewModel: NookiViewModel = viewModel(
                    factory = NookiViewModel.Factory(
                        graph = graph,
                        readReadiness = ::readReadiness,
                        refreshWidget = { WidgetRefresher.refresh(applicationContext) }
                    )
                )
                NookiRoot(
                    viewModel = viewModel,
                    scheduler = graph.scheduler,
                    incoming = incomingCapture.asStateFlow(),
                    onIncomingHandled = { incomingCapture.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun readReadiness(): Readiness {
        val graph = NookiGraph.get(applicationContext)
        val mayPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return Readiness(
            canPostNotifications = mayPost && NotificationManagerCompat.from(this).areNotificationsEnabled(),
            canScheduleExact = graph.scheduler.canScheduleExactAlarms(),
            noticingEnabled = NookiNotificationListener.isEnabled(this),
            calendarAllowed = graph.calendar.hasPermission()
        )
    }

    /** Text can arrive from the share sheet, a text selection, the tile, a shortcut or the widget. */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val draft = when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                CaptureDraft(prefill = text.ifBlank { subject }.trim(), source = ReminderSource.SHARED)
            }
            Intent.ACTION_PROCESS_TEXT -> CaptureDraft(
                prefill = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty().trim(),
                source = ReminderSource.SHARED
            )
            ACTION_CAPTURE -> CaptureDraft()
            else -> null
        }
        if (draft != null) incomingCapture.value = draft

        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SAMPLE_DAY, false)) {
            intent.removeExtra(EXTRA_SAMPLE_DAY)
            val graph = NookiGraph.get(applicationContext)
            lifecycleScope.launch {
                SampleDay.seed(graph, System.currentTimeMillis())
                graph.settings.update { it.copy(onboarded = true, name = "Rishi") }
            }
        }
    }

    companion object {
        const val ACTION_CAPTURE = "com.example.nooki.action.CAPTURE"
        private const val EXTRA_SAMPLE_DAY = "nooki.sample_day"
    }
}
