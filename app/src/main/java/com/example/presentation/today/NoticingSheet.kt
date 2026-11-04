package com.example.presentation.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.notice.NoticeSettings

/**
 * The in-app disclosure, shown right before notification access is requested (a Play requirement,
 * and the honest thing to do). Also where each kind of noticing is turned on or off.
 */
@Composable
fun NoticingSheet(
    accessOn: Boolean,
    settings: NoticeSettings,
    onSettingsChange: (NoticeSettings) -> Unit,
    onOpenAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Let Nooki notice", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "Nooki can catch missed calls, messages you haven't opened, and bills that arrive by SMS, " +
                    "so they show up in You missed and You forgot without you typing anything.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Block("What Nooki reads", "The title and short text of notifications on this phone, at the moment they arrive.")
            Block("What Nooki keeps", "For missed calls and messages: only who, and when. For bills: only the name, the amount and the due date.")
            Block("What Nooki never keeps", "Message text, OTPs, passwords, PINs or bank balances. These are dropped before anything is saved.")
            Block("Where it goes", "Nowhere. Nooki has no internet permission, so nothing can leave your phone.")
            Block("Your choice", "Nothing becomes a reminder until you tap it. You can turn off each kind below, and off means off.")

            Spacer(Modifier.height(16.dp))
            Toggle("Missed calls", settings.missedCalls) { onSettingsChange(settings.copy(missedCalls = it)) }
            Toggle("Messages unopened for 2 hours", settings.unreadMessages) { onSettingsChange(settings.copy(unreadMessages = it)) }
            Toggle("Bills and dues from SMS", settings.bills) { onSettingsChange(settings.copy(bills = it)) }

            Spacer(Modifier.height(20.dp))
            if (accessOn) {
                Text("Notification access is on.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onOpenAccess) { Text("Turn it off in settings") }
            } else {
                Button(onClick = onOpenAccess, modifier = Modifier.fillMaxWidth()) { Text("Turn on notification access") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "If Android says the setting is restricted (this happens for apps installed from a file): open App info, " +
                        "tap the three dots at the top right, choose “Allow restricted settings”, then come back and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenAppInfo) { Text("Open App info") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
