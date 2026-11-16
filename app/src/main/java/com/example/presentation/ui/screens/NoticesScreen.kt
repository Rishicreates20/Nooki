package com.example.presentation.ui.screens

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
import com.example.presentation.ui.components.DynamicWeatherSkyBackdrop
import com.example.presentation.ui.components.LocalWeatherEnvironmentBar
import com.example.presentation.ui.components.Nooki3DCenterpiece
import com.example.presentation.ui.components.NookiTasksAndProfileScreen
import com.example.presentation.ui.components.TodoAndDoneTasksSection
import com.example.presentation.ui.components.UserProfileAndStreakBannerChip
import com.example.presentation.viewmodel.DeskPetViewModel
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiLocationWeatherProvider
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile > Privacy & reminders. Plain-language privacy, what Nooki may notice, whether alarms can
 * ring on time, and (folded away) testing tools.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NookiNoticesAndReliabilityScreen(
    uiState: DeskUiState,
    onOpenExactAlarmSettings: () -> Unit,
    onBack: () -> Unit,
    onIntent: (DeskIntent) -> Unit
) {
    var showTestingTools by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp)
            .testTag("screen_notices"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.testTag("notices_back")) {
                Text("‹ Back to Profile")
            }
            ScreenHeader(
                title = "Privacy & reminders",
                subtitle = "What Nooki can see, and whether reminders can ring on time"
            )
        }

        item {
            SectionCard(title = "Your privacy") {
                Text(
                    text = "• Nothing leaves your phone. Nooki has no internet access.\n" +
                        "• Message text is never saved, only who and when.\n" +
                        "• OTPs, passwords, PINs and bank balances are thrown away straight away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                Text(
                    text = "Nooki may suggest reminders from:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NoticeSourceKind.entries.forEach { kind ->
                        val enabled = uiState.noticeSettings.isAllowed(kind)
                        FilterChip(
                            selected = enabled,
                            onClick = { onIntent(DeskIntent.ToggleNoticeKind(kind)) },
                            label = { Text(kind.displayLabel) },
                            leadingIcon = if (enabled) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Text(
                    text = "Tap one to turn it off. Off means off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        item {
            SectionCard(title = "Reminders") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AlarmOn,
                        contentDescription = null,
                        tint = if (uiState.canScheduleExactAlarms) LeafGreen else Marigold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (uiState.canScheduleExactAlarms) {
                            "Medicine and important reminders will ring on time."
                        } else {
                            "Allow \"Alarms & reminders\" so medicine reminders ring on time."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!uiState.canScheduleExactAlarms) {
                    Button(onClick = onOpenExactAlarmSettings) { Text("Allow") }
                }
                OutlinedButton(
                    onClick = { onIntent(DeskIntent.Schedule5SecondTestAlarm) },
                    modifier = Modifier.testTag("btn_5s_test_alarm")
                ) { Text("Send me a test reminder") }
            }
        }

        item {
            TextButton(
                onClick = { showTestingTools = !showTestingTools },
                modifier = Modifier.testTag("toggle_testing_tools")
            ) {
                Text(if (showTestingTools) "Hide testing tools" else "Show testing tools")
            }
        }

        if (showTestingTools) {
            item {
                SectionCard(title = "Testing tools") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            modifier = Modifier.testTag("test_otp_drop_button")
                        ) { Text("Fake OTP message") }
                        OutlinedButton(
                            onClick = {
                                onIntent(
                                    DeskIntent.SimulateIncomingNotification(
                                        packageName = "com.android.dialer",
                                        senderOrTitle = "Mom",
                                        rawBody = "Missed call from Mom"
                                    )
                                )
                            },
                            modifier = Modifier.testTag("test_missed_call_button")
                        ) { Text("Fake missed call") }
                        OutlinedButton(
                            onClick = { onIntent(DeskIntent.SimulateBootRecovery) },
                            modifier = Modifier.testTag("btn_simulate_boot")
                        ) { Text("Restart check") }
                    }
                    uiState.lastDroppedNoticeAudit?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = LeafGreen)
                    }
                    Text(
                        text = "Log (${uiState.telemetryLogs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    uiState.telemetryLogs.take(30).forEach { entry ->
                        Text(
                            text = "${entry.formattedTime()}  ${entry.source}: ${entry.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.isWarning) Sindoor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
