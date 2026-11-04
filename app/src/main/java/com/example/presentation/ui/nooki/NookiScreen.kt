package com.example.presentation.ui.nooki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.attention.TimeWords
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.mvi.NookiUiState
import com.example.settings.NookiPrefs
import com.example.ui.theme.NookiPalette
import java.util.Calendar

/** The ways Nooki can ask Android for something. The screen itself never touches the system. */
data class ReadinessActions(
    val requestNotifications: () -> Unit = {},
    val openExactAlarms: () -> Unit = {},
    val openNoticeAccess: () -> Unit = {},
    val openAppInfo: () -> Unit = {},
    val openBattery: () -> Unit = {},
    val requestCalendar: () -> Unit = {}
)

/** Nooki herself, how she is doing, and everything she is allowed to do. */
@Composable
fun NookiScreen(
    state: NookiUiState,
    onIntent: (NookiIntent) -> Unit,
    actions: ReadinessActions,
    contentPadding: PaddingValues,
    showDeveloper: Boolean,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
    petStage: @Composable (Modifier) -> Unit
) {
    var editing by remember { mutableStateOf<BriefTime?>(null) }
    val prefs = state.prefs
    val readiness = state.readiness

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .statusBarsPadding()
    ) {
        petStage(Modifier.fillMaxWidth().height(250.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            Text("Level ${state.level}", style = MaterialTheme.typography.displaySmall)
            Text(moodSentence(state), style = MaterialTheme.typography.bodyLarge, color = NookiPalette.colors.muted)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.xpInLevel.toFloat() / state.xpForNextLevel },
                color = MaterialTheme.colorScheme.primary,
                trackColor = NookiPalette.colors.line,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.xpForNextLevel - state.xpInLevel} XP to the next level. ${state.completedCount} things done so far.",
                style = MaterialTheme.typography.bodySmall,
                color = NookiPalette.colors.muted
            )

            Heading("What Nooki watches")
            if (readiness.noticingEnabled) {
                Toggle("Missed calls", "Shows who called.", prefs.watchCalls) { on ->
                    onIntent(NookiIntent.UpdatePrefs { it.copy(watchCalls = on) })
                }
                Toggle("Unopened messages", "Only who and when, never the words.", prefs.watchMessages) { on ->
                    onIntent(NookiIntent.UpdatePrefs { it.copy(watchMessages = on) })
                }
                Toggle("Bills, parcels and appointments", "Keeps the name, amount and date.", prefs.watchBills) { on ->
                    onIntent(NookiIntent.UpdatePrefs { it.copy(watchBills = on) })
                }
                Note("OTPs, passwords and bank balances are dropped before anything is saved.")
            } else {
                Note(
                    "Noticing is off. Turn it on and Nooki can catch missed calls, unopened messages and bills in " +
                        "your notifications. It reads them on this phone only, and saves only who, when and how much."
                )
                OutlinedButton(onClick = actions.openNoticeAccess) { Text("Turn on noticing") }
                TextButton(onClick = actions.openAppInfo) { Text("Android says it's restricted?") }
            }

            Heading("Daily rhythm")
            Toggle("Morning brief and evening wrap", "One calm note, only if there's something to say.", prefs.briefsOn) { on ->
                onIntent(NookiIntent.UpdatePrefs { it.copy(briefsOn = on) })
            }
            if (prefs.briefsOn) {
                ValueRow("Morning brief", clockLabel(prefs.morningMinutes)) { editing = BriefTime.MORNING }
                ValueRow("Evening wrap", clockLabel(prefs.eveningMinutes)) { editing = BriefTime.EVENING }
            }

            Heading("Reliability")
            if (!readiness.canPostNotifications) {
                ActionRow("Notifications are off", "Nooki can't reach you.", "Turn on", actions.requestNotifications)
            }
            if (!readiness.canScheduleExact) {
                ActionRow(
                    "Exact timing is off",
                    "Everyday reminders may run a few minutes late. Worth turning on for medicine.",
                    "Turn on",
                    actions.openExactAlarms
                )
            }
            ActionRow("Battery saver", "Some phones stop reminders to save power.", "Check", actions.openBattery)
            if (!readiness.calendarAllowed) {
                ActionRow("Calendar", "Show today's events alongside your list.", "Allow", actions.requestCalendar)
            }

            Heading("Your data")
            Note("Everything stays on this phone. Nooki doesn't have internet permission, so it can't send anything anywhere.")
            TextButton(onClick = { onIntent(NookiIntent.ClearNoticedHistory) }) { Text("Delete everything Nooki noticed") }

            if (showDeveloper) {
                Heading("Developer")
                ValueRow("Scheduler diagnostics", "${state.telemetry.size} events", onOpenDeveloper)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    editing?.let { target ->
        BriefTimeDialog(
            initialMinutes = if (target == BriefTime.MORNING) prefs.morningMinutes else prefs.eveningMinutes,
            onDismiss = { editing = null },
            onSave = { minutes ->
                editing = null
                val update: (NookiPrefs) -> NookiPrefs = if (target == BriefTime.MORNING) {
                    { it.copy(morningMinutes = minutes) }
                } else {
                    { it.copy(eveningMinutes = minutes) }
                }
                onIntent(NookiIntent.UpdatePrefs(update))
            }
        )
    }
}

private enum class BriefTime { MORNING, EVENING }

private fun moodSentence(state: NookiUiState): String = when {
    state.caughtThisWeek == 1 -> "Nooki caught 1 thing for you this week."
    state.caughtThisWeek > 1 -> "Nooki caught ${state.caughtThisWeek} things for you this week."
    else -> state.mood.caption
}

private fun clockLabel(minutes: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
    }
    return TimeWords.clock(cal.timeInMillis)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefTimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val time = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = time) },
        confirmButton = { TextButton(onClick = { onSave(time.hour * 60 + time.minute) }) { Text("Save time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = NookiPalette.colors.toDo,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp)
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = NookiPalette.colors.muted,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = NookiPalette.colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.labelMedium, color = NookiPalette.colors.muted)
    }
}

@Composable
private fun ActionRow(title: String, detail: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = NookiPalette.colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onClick, border = BorderStroke(1.dp, NookiPalette.colors.line)) { Text(action) }
    }
}

