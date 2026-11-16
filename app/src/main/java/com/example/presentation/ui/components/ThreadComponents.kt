package com.example.presentation.ui.components

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

// Thread building blocks (knots, section headers, capture bar) shared by the Home and Pending screens.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OneBreathCaptureSection(
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
                            Text("Add", fontWeight = FontWeight.Bold)
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
internal fun SolidRingingBlock(
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
                    Text("Mark done", fontWeight = FontWeight.Bold)
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
internal fun ThreadSectionHeader(
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
internal fun ThreadEmptyNode(
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
internal fun ThreadKnotRow(
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
            isDone -> "Done"
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
                        text = "Tap the circle when done",
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
internal fun ThreadSuggestionNode(
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
                        Text("Add", color = LeafGreen, fontWeight = FontWeight.Bold)
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
internal fun ThreadEveningWrapRow(
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
