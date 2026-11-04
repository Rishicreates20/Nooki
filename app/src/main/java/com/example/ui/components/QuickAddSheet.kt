package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderPriority
import com.example.parser.ParsedReminder
import com.example.parser.PriorityHint
import com.example.parser.ReminderTextParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What the quick-add sheet hands back once the user sends a line. */
data class QuickAddRequest(
    val rawInput: String,
    /** Set when the user picked a fallback time chip; otherwise the caller parses [rawInput]. */
    val fallbackTriggerAtMillis: Long?
)

private data class FallbackTime(val label: String, val resolve: (Long) -> Long)

private val FALLBACK_TIMES = listOf(
    FallbackTime("In 10 min") { it + 10 * 60_000L },
    FallbackTime("In 30 min") { it + 30 * 60_000L },
    FallbackTime("In 1 hour") { it + 60 * 60_000L },
    FallbackTime("Tomorrow 9 AM") { now ->
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
)

private val EXAMPLES = listOf(
    "Call mom tomorrow at 6pm",
    "Stretch in 45 mins",
    "Submit report Friday at 3:15 PM",
    "Drink water in 20 min"
)

/**
 * One-line reminder entry (PRD FR-1, data-flow step 1). The preview under the field is the same
 * regex parse the app falls back to, so what you see is what gets scheduled. When no time is found,
 * Nooki asks for one with a few chips instead of guessing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    nowMillis: Long,
    onDismiss: () -> Unit,
    onSubmit: (QuickAddRequest) -> Unit,
    onChipTap: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    var fallbackIndex by rememberSaveable { mutableStateOf(-1) }
    val focusRequester = remember { FocusRequester() }

    val preview: ParsedReminder? = remember(text, nowMillis / 60_000L) {
        text.takeIf { it.isNotBlank() }?.let { ReminderTextParser.parse(it, nowMillis) }
    }
    val needsTime = preview != null && preview.triggerAtMillis == null
    val inPast = preview?.triggerAtMillis?.let { it <= nowMillis } == true
    val canSend = preview != null && !inPast && (!needsTime || fallbackIndex >= 0)

    fun send() {
        if (!canSend) return
        val fallback = if (needsTime) FALLBACK_TIMES[fallbackIndex].resolve(System.currentTimeMillis()) else null
        onSubmit(QuickAddRequest(rawInput = text.trim(), fallbackTriggerAtMillis = fallback))
        text = ""
        fallbackIndex = -1
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tell Nooki", style = MaterialTheme.typography.titleLarge)
            Text(
                "Type it the way you'd say it. Nooki finds the time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; fallbackIndex = -1 },
                placeholder = { Text("e.g. Call mom tomorrow at 6pm") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("quick_add_input")
            )

            if (preview == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMPLES.forEach { example ->
                        SuggestionChip(
                            onClick = { onChipTap(); text = example },
                            label = { Text(example) }
                        )
                    }
                }
            } else {
                ParsePreview(preview, nowMillis, inPast)
                if (needsTime) {
                    Text(
                        "No time in that one. When should Nooki remind you?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FALLBACK_TIMES.forEachIndexed { index, option ->
                            FilterChip(
                                selected = fallbackIndex == index,
                                onClick = { onChipTap(); fallbackIndex = index },
                                label = { Text(option.label) },
                                leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { send() },
                enabled = canSend,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("quick_add_send")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Remind me", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ParsePreview(parsed: ParsedReminder, nowMillis: Long, inPast: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                parsed.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val trigger = parsed.triggerAtMillis
                Icon(
                    if (inPast) Icons.Outlined.ErrorOutline else Icons.Default.Event,
                    contentDescription = null,
                    tint = if (inPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        trigger == null -> "No time yet"
                        inPast -> "${describeTrigger(trigger, nowMillis)} has already passed"
                        else -> describeTrigger(trigger, nowMillis).replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                )
                parsed.priorityHint?.let { hint ->
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Default.PriorityHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (hint == PriorityHint.URGENT) "Urgent" else "Important",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** "in 45 min", "today at 6:00 PM", "tomorrow at 9:00 AM", "Fri, Nov 6 at 3:15 PM". */
fun describeTrigger(triggerAtMillis: Long, nowMillis: Long): String {
    val diff = triggerAtMillis - nowMillis
    if (diff in 0 until 60 * 60_000L) return "in ${(diff + 59_999) / 60_000} min"
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(triggerAtMillis))
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val target = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(today, target)) return "today at $time"
    today.add(Calendar.DAY_OF_YEAR, 1)
    if (sameDay(today, target)) return "tomorrow at $time"
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(triggerAtMillis))
    return "$date at $time"
}

fun PriorityHint?.toReminderPriority(): ReminderPriority = when (this) {
    PriorityHint.URGENT -> ReminderPriority.URGENT
    PriorityHint.HIGH -> ReminderPriority.HIGH
    null -> ReminderPriority.MEDIUM
}

/** Picks the desk category from keywords so the user never has to. */
fun inferCategory(title: String): ReminderCategory {
    val t = title.lowercase(Locale.ROOT)
    fun has(vararg words: String) = words.any { Regex("\\b$it").containsMatchIn(t) }
    return when {
        has("water", "drink", "hydrat", "tea", "coffee", "sip") -> ReminderCategory.HYDRATION
        has("stretch", "walk", "posture", "exercise", "gym", "yoga", "break", "eyes") -> ReminderCategory.STRETCH
        has("focus", "study", "code", "write", "deep work", "read", "revise", "exam") -> ReminderCategory.FOCUS
        has("buy", "pick up", "groceries", "shop", "pay", "bank", "post", "collect", "call") -> ReminderCategory.ERRAND
        else -> ReminderCategory.DESK
    }
}
