package com.example.presentation.ui.remember

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.attention.BriefComposer
import com.example.attention.TimeWords
import com.example.data.local.entity.LooseEndDirection
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.presentation.mvi.CaptureDraft
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.mvi.NookiUiState
import com.example.presentation.ui.today.QuietAction
import com.example.ui.theme.NookiPalette

/** Things Nooki is holding on to: where you put things, and what is out with other people. */
@Composable
fun RememberScreen(
    state: NookiUiState,
    onIntent: (NookiIntent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase()

    val memories = remember(state.memories, needle) {
        if (needle.isEmpty()) state.memories
        else state.memories.filter { "${it.text} ${it.subject} ${it.place}".lowercase().contains(needle) }
    }
    val looseEnds = remember(state.looseEnds, needle) {
        state.looseEnds.filter { end ->
            end.isOpen && (needle.isEmpty() || "${end.person} ${end.what}".lowercase().contains(needle))
        }
    }
    val owedToYou = looseEnds.filter { it.direction == LooseEndDirection.THEY_HAVE }.sumOf { it.amountRupees ?: 0L }
    val youOwe = looseEnds.filter { it.direction == LooseEndDirection.I_HAVE }.sumOf { it.amountRupees ?: 0L }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("remember_list"),
        contentPadding = contentPadding
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text("Remember", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Nooki holds these so you don't have to.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NookiPalette.colors.muted
                )
                Spacer(Modifier.height(14.dp))
                SearchField(query, onChange = { query = it })
            }
        }

        if (looseEnds.isNotEmpty()) {
            item(key = "money_heading") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 2.dp)) {
                    Text("Money and things", style = MaterialTheme.typography.titleLarge, color = NookiPalette.colors.forgot)
                    moneySummary(owedToYou, youOwe)?.let { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = NookiPalette.colors.muted)
                    }
                }
            }
            items(looseEnds, key = { "end_${it.id}" }) { end ->
                LooseEndRow(end, state.nowMillis, onIntent, Modifier.animateItem())
            }
        }

        if (memories.isNotEmpty()) {
            item(key = "saved_heading") {
                Text(
                    "Saved",
                    style = MaterialTheme.typography.titleLarge,
                    color = NookiPalette.colors.toDo,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 2.dp)
                )
            }
            items(memories, key = { "memory_${it.id}" }) { memory ->
                MemoryRow(memory, state.nowMillis, onIntent, Modifier.animateItem())
            }
        }

        if (memories.isEmpty() && looseEnds.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                    Text(
                        if (needle.isEmpty()) "Nothing here yet. Tell Nooki where you put something, or who has your things."
                        else "Nothing saved about \"$query\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NookiPalette.colors.muted
                    )
                    if (needle.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        listOf("Spare key is with Sharma aunty", "Car parked at B2, pillar 14", "Lent charger to Aman").forEach { line ->
                            QuietAction(line, MaterialTheme.colorScheme.secondary) {
                                onIntent(NookiIntent.OpenCapture(CaptureDraft(prefill = line)))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun moneySummary(owedToYou: Long, youOwe: Long): String? = when {
    owedToYou > 0 && youOwe > 0 -> "${TimeWords.rupees(owedToYou)} to come back, ${TimeWords.rupees(youOwe)} you owe."
    owedToYou > 0 -> "${TimeWords.rupees(owedToYou)} to come back."
    youOwe > 0 -> "${TimeWords.rupees(youOwe)} you owe."
    else -> null
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Surface(
        color = NookiPalette.colors.raised,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, NookiPalette.colors.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = NookiPalette.colors.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Where did I keep...", style = MaterialTheme.typography.bodyLarge, color = NookiPalette.colors.muted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("remember_search")
                )
            }
        }
    }
}

@Composable
private fun LooseEndRow(end: LooseEndEntity, nowMillis: Long, onIntent: (NookiIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(BriefComposer.owedSentence(end), style = MaterialTheme.typography.titleMedium)
            Text(
                TimeWords.openFor(end.createdAtMillis, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = NookiPalette.colors.muted
            )
            QuietAction("Settled", NookiPalette.colors.forgot) { onIntent(NookiIntent.SettleLooseEnd(end)) }
        }
        RowMenu(
            entries = listOf("Delete" to { onIntent(NookiIntent.DeleteLooseEnd(end)) })
        )
    }
}

@Composable
private fun MemoryRow(memory: MemoryEntity, nowMillis: Long, onIntent: (NookiIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (memory.pinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = NookiPalette.colors.muted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(memory.text, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Saved ${TimeWords.ago(memory.createdAtMillis, nowMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = NookiPalette.colors.muted
            )
        }
        RowMenu(
            entries = listOf(
                (if (memory.pinned) "Unpin" else "Pin to top") to {
                    onIntent(NookiIntent.SetMemoryPinned(memory, !memory.pinned))
                },
                "Delete" to { onIntent(NookiIntent.DeleteMemory(memory)) }
            )
        )
    }
}

@Composable
private fun RowMenu(entries: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = NookiPalette.colors.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        action()
                    }
                )
            }
        }
    }
}
