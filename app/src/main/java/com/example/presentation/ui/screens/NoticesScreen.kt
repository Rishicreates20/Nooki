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
 * Second tab:
 * - Explains clearly what Nooki reads and why before asking for access
 * - Lets the user turn off each kind of noticing ("off means off")
 * - Proves the OTP / Password / Bank Balance Dropper live
 * - Preserves the Milestone 1 & 2 Room + Exact Alarm + Boot Recovery diagnostics
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NookiNoticesAndReliabilityScreen(
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
