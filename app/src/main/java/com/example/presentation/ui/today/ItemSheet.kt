package com.example.presentation.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.attention.TimeWords
import com.example.data.local.entity.ReminderEntity
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.ui.Words
import com.example.presentation.ui.components.PickDateTimeDialog
import com.example.ui.theme.NookiPalette
import com.example.workflow.WorkflowSchedule

private data class LaterOption(val label: String, val at: (Long) -> Long)

private val LATER_OPTIONS = listOf(
    LaterOption("In 1 hour") { it + 60 * 60_000L },
    LaterOption("This evening") { WorkflowSchedule.nextDaily(18 * 60, it) },
    LaterOption("Tomorrow morning") { TimeWords.startOfDay(it, offsetDays = 1) + 9 * 60 * 60_000L },
    LaterOption("Next week") { TimeWords.startOfDay(it, offsetDays = 7) + 9 * 60 * 60_000L }
)

/** Everything you can do to one reminder. Opening a row is the only way here, so it stays simple. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    reminder: ReminderEntity,
    nowMillis: Long,
    onIntent: (NookiIntent) -> Unit
) {
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onIntent(NookiIntent.CloseItem) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NookiPalette.colors.raised
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Text(reminder.title, style = MaterialTheme.typography.headlineSmall)
            val whenLine = if (reminder.undated) "No time set" else TimeWords.moment(reminder.triggerAtMillis, nowMillis)
            Text(
                listOfNotNull(whenLine, Words.repeatLabel(reminder)).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = NookiPalette.colors.muted
            )
            if (reminder.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(reminder.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onIntent(NookiIntent.Complete(reminder)) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Done") }

            Spacer(Modifier.height(18.dp))
            Text(
                if (reminder.undated) "Set a time" else "Move it",
                style = MaterialTheme.typography.titleSmall,
                color = NookiPalette.colors.muted
            )
            LATER_OPTIONS.forEach { option ->
                SheetRow(option.label) {
                    onIntent(NookiIntent.Reschedule(reminder, option.at(System.currentTimeMillis())))
                }
            }
            SheetRow("Pick a date and time") { picking = true }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep asking", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Nooki rings again until you tap Done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NookiPalette.colors.muted
                    )
                }
                Switch(
                    checked = reminder.keepAsking,
                    onCheckedChange = { onIntent(NookiIntent.SetKeepAsking(reminder, it)) }
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onIntent(NookiIntent.Delete(reminder)) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        }
    }

    if (picking) {
        PickDateTimeDialog(
            initialMillis = if (reminder.undated) nowMillis + 60 * 60_000L else reminder.triggerAtMillis.coerceAtLeast(nowMillis),
            onPicked = { at ->
                picking = false
                onIntent(NookiIntent.Reschedule(reminder, at))
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp)
    )
}
