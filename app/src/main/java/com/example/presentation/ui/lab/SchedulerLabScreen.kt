package com.example.presentation.ui.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.mvi.NookiUiState
import com.example.ui.theme.NookiPalette
import com.example.ui.theme.quietButtonColors

/** Alarm and receiver diagnostics. Only reachable in debug builds. */
@Composable
fun SchedulerLabScreen(
    state: NookiUiState,
    onIntent: (NookiIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(colors = quietButtonColors(), onClick = onClose, contentPadding = PaddingValues(0.dp)) { Text("Back") }
                Text("Scheduler diagnostics", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Exact alarms allowed: ${state.readiness.canScheduleExact}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NookiPalette.colors.muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onIntent(NookiIntent.TestAlarm) }, modifier = Modifier.weight(1f)) { Text("Ring in 5s") }
                    OutlinedButton(onClick = { onIntent(NookiIntent.SimulateBootRecovery) }, modifier = Modifier.weight(1f)) { Text("Boot recovery") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onIntent(NookiIntent.LoadSampleDay) }, modifier = Modifier.weight(1f)) { Text("Load sample day") }
                    OutlinedButton(onClick = { onIntent(NookiIntent.ClearCompleted) }, modifier = Modifier.weight(1f)) { Text("Clear finished") }
                }
            }
        }
        items(state.telemetry, key = { it.id }) { entry ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        entry.source,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.isWarning) NookiPalette.colors.missed else NookiPalette.colors.toDo
                    )
                    Text(entry.formattedTime(), style = MaterialTheme.typography.labelSmall, color = NookiPalette.colors.muted)
                }
                Text(entry.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
