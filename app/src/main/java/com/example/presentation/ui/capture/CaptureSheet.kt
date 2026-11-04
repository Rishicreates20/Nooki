package com.example.presentation.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.attention.BriefComposer
import com.example.attention.TimeWords
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.parser.Capture
import com.example.parser.CaptureRouter
import com.example.presentation.mvi.CaptureDraft
import com.example.presentation.mvi.CaptureRequest
import com.example.presentation.ui.Words
import com.example.presentation.ui.components.PickDateTimeDialog
import com.example.settings.Persona
import com.example.ui.theme.NookiPalette
import com.example.workflow.WorkflowSchedule

private data class TimeChoice(val label: String, val at: (Long) -> Long)

private val TIME_CHOICES = listOf(
    TimeChoice("In 1 hour") { it + 60 * 60_000L },
    TimeChoice("This evening") { WorkflowSchedule.nextDaily(18 * 60, it) },
    TimeChoice("Tomorrow morning") { TimeWords.startOfDay(it, offsetDays = 1) + 9 * 60 * 60_000L }
)

/** Finds saved things for a question like "where is my passport". */
fun findMemories(query: String, memories: List<MemoryEntity>): List<MemoryEntity> {
    val words = query.lowercase().split(' ').filter { it.length > 2 }
    if (words.isEmpty()) return emptyList()
    return memories.filter { memory ->
        val haystack = "${memory.text} ${memory.subject} ${memory.place}".lowercase()
        words.any { it in haystack }
    }
}

/**
 * Tell Nooki. One line in, and what Nooki understood appears as it is typed, so nobody has to fill
 * in a form or wonder how it will be read.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(
    draft: CaptureDraft,
    nowMillis: Long,
    persona: Persona,
    memories: List<MemoryEntity>,
    onDismiss: () -> Unit,
    onVoice: () -> Unit,
    onSubmit: (CaptureRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NookiPalette.colors.raised
    ) {
        CaptureContent(draft, nowMillis, persona, memories, onVoice, onSubmit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureContent(
    draft: CaptureDraft,
    nowMillis: Long,
    persona: Persona,
    memories: List<MemoryEntity>,
    onVoice: () -> Unit,
    onSubmit: (CaptureRequest) -> Unit,
    requestFocus: Boolean = true
) {
    var field by remember(draft) {
        mutableStateOf(TextFieldValue(draft.prefill, TextRange(draft.prefill.length)))
    }
    var choiceIndex by rememberSaveable(draft) { mutableStateOf(-1) }
    var pickedAt by rememberSaveable(draft) { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val text = field.text
    val capture = remember(text, nowMillis / 60_000L) {
        text.takeIf { it.isNotBlank() }?.let { CaptureRouter.route(it, nowMillis) }
    }
    val remind = capture as? Capture.Remind
    val noTime = remind != null && remind.parsed.triggerAtMillis == null
    val inPast = remind?.parsed?.triggerAtMillis?.let { it <= nowMillis } == true
    val canSave = capture != null && capture !is Capture.Ask && !inPast

    fun submit() {
        if (!canSave) return
        val override = when {
            !noTime -> null
            pickedAt != null -> pickedAt
            choiceIndex >= 0 -> TIME_CHOICES[choiceIndex].at(System.currentTimeMillis())
            else -> null
        }
        onSubmit(CaptureRequest(text.trim(), override, draft.source))
    }

    LaunchedEffect(requestFocus) { if (requestFocus) runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 12.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).padding(top = 6.dp)) {
                if (text.isEmpty()) {
                    Text(
                        "Tell Nooki",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NookiPalette.colors.muted
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        choiceIndex = -1
                        pickedAt = null
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag("capture_input")
                )
            }
            IconButton(onClick = onVoice) {
                Icon(Icons.Rounded.Mic, contentDescription = "Say it instead", tint = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(Modifier.padding(end = 8.dp)) {
            when (capture) {
                null -> {
                    Text(
                        "Say it the way you'd tell a friend, in English or Hindi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NookiPalette.colors.muted
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        persona.starters.take(3).forEach { example ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    field = TextFieldValue(example, TextRange(example.length))
                                },
                                label = { Text(example) }
                            )
                        }
                    }
                }
                is Capture.Remind -> {
                    Understood(capture.parsed.title, Words.understood(capture.parsed, nowMillis))
                    if (inPast) {
                        Text(
                            "That time has already passed. Try another time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (noTime) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                pickedAt != null -> TimeWords.moment(pickedAt!!, nowMillis)
                                choiceIndex >= 0 -> ""
                                else -> "No time, so it stays on today's list without ringing."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NookiPalette.colors.muted
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TIME_CHOICES.forEachIndexed { index, choice ->
                                FilterChip(
                                    selected = choiceIndex == index,
                                    onClick = {
                                        pickedAt = null
                                        choiceIndex = if (choiceIndex == index) -1 else index
                                    },
                                    label = { Text(choice.label) }
                                )
                            }
                            FilterChip(
                                selected = pickedAt != null,
                                onClick = { picking = true },
                                label = { Text("Pick a time") }
                            )
                        }
                    }
                }
                is Capture.Hold -> Understood(capture.draft.text, listOf("Nooki will hold on to this. Find it any time in Remember."))
                is Capture.Lend -> {
                    val preview = LooseEndEntity(
                        direction = capture.draft.direction,
                        person = capture.draft.person,
                        what = capture.draft.what,
                        amountRupees = capture.draft.amountRupees
                    )
                    Understood(BriefComposer.owedSentence(preview), listOf("Nooki will bring this up if it stays open for a week."))
                }
                is Capture.Ask -> {
                    val found = findMemories(capture.query, memories)
                    if (found.isEmpty()) {
                        Understood("Nothing saved about ${capture.query}", listOf("When you find it, tell Nooki where it is."))
                    } else {
                        found.take(3).forEach { memory ->
                            Understood(memory.text, listOf("Saved ${TimeWords.ago(memory.createdAtMillis, nowMillis)}"))
                        }
                    }
                }
            }

            if (capture !is Capture.Ask) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { submit() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("capture_save")
                ) {
                    Text(
                        when (capture) {
                            is Capture.Hold -> "Save to Remember"
                            is Capture.Lend -> "Add to money and things"
                            else -> "Save reminder"
                        }
                    )
                }
            }
        }
    }

    if (picking) {
        PickDateTimeDialog(
            initialMillis = nowMillis + 60 * 60_000L,
            onPicked = { at ->
                picking = false
                choiceIndex = -1
                pickedAt = at
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun Understood(title: String, lines: List<String>) {
    Column(Modifier.padding(vertical = 4.dp).testTag("capture_preview")) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = NookiPalette.colors.muted)
        }
    }
}

/** The bar that sits above the tabs, always one tap from saying something to Nooki. */
@Composable
fun CaptureBar(onOpen: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        color = NookiPalette.colors.raised,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, NookiPalette.colors.line),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("capture_bar")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
            com.example.presentation.ui.today.KnotMark(MaterialTheme.colorScheme.primary, size = 22.dp)
            Text(
                "Tell Nooki",
                style = MaterialTheme.typography.bodyLarge,
                color = NookiPalette.colors.muted,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onVoice) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Say it instead",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

