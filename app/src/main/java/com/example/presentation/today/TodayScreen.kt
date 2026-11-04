package com.example.presentation.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.today.Knot
import com.example.core.today.KnotRef
import com.example.core.today.KnotTone
import com.example.core.today.NookiMood
import com.example.core.today.TodayBoard
import com.example.pet.NookiPet3D
import com.example.pet.PetMood
import com.example.ui.theme.LocalThreadColors
import com.example.ui.theme.Marigold
import com.example.ui.theme.NightIndigo

/** The thread runs down the left at this x, starting from Nooki's paw. */
private val ThreadX: Dp = 36.dp
private val KnotSize: Dp = 22.dp

@Composable
fun TodayScreen(
    state: TodayState,
    onIntent: (TodayIntent) -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The one moment of motion: when the app opens, the thread drops from Nooki.
    val drop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drop.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val board = state.board
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "nooki") { NookiHeader(board?.mood ?: NookiMood.SLEEPING, board) }

            state.answer?.let { answer ->
                item(key = "answer") { AnswerBlock(answer, onClose = { onIntent(TodayIntent.CloseAnswer) }) }
            }

            if (board != null) {
                board.ringing?.let { ringing ->
                    item(key = "ringing") { RingingBlock(ringing, onIntent) }
                }
                if (board.isCalm && board.later.isEmpty() && board.done.isEmpty()) {
                    item(key = "calm") { CalmRow(drop.value) }
                }
                var index = 0
                fun LazyListScope.section(key: String, title: String, tone: KnotTone, knots: List<Knot>, laterLabel: String?) {
                    if (knots.isEmpty()) return
                    val start = index
                    item(key = "h-$key") { SectionHeader(title, tone, progress = drop.value, rowIndex = start) }
                    index++
                    itemsIndexed(knots, key = { _, k -> "$key-${k.ref}" }) { i, knot ->
                        KnotRow(
                            knot = knot,
                            progress = drop.value,
                            rowIndex = start + 1 + i,
                            onUntie = { onIntent(TodayIntent.Untie(knot)) },
                            laterLabel = laterLabel?.takeIf { knot.ref !is KnotRef.Money },
                            onLater = { onIntent(TodayIntent.Later(knot.ref)) },
                        )
                    }
                    index += knots.size
                }
                section("missed", "You missed", KnotTone.MISSED, board.missed, laterLabel = "Tomorrow")
                section("forgot", "You forgot", KnotTone.FORGOT, board.forgot, laterLabel = null)
                section("todo", "To do", KnotTone.TODO, board.todo, laterLabel = "Tomorrow")
                section("later", "Coming up", KnotTone.TODO, board.later, laterLabel = null)
                section("done", "Done today", KnotTone.DONE, board.done, laterLabel = null)

                if (state.showEveningWrap) {
                    item(key = "wrap") { EveningWrap(onMove = { onIntent(TodayIntent.MoveLeftoversToTomorrow) }) }
                }
            }
        }
        TellNookiBar(state, onIntent, onVoice)
    }
}

@Composable
private fun NookiHeader(mood: NookiMood, board: TodayBoard?) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NookiPet3D(
            mood = mood.toPetMood(),
            stageColor = colors.background,
            surroundColor = colors.background,
            modifier = Modifier.size(112.dp),
            cornerRadius = 56.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Nooki", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
            Text(
                text = moodLine(mood, board),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun moodLine(mood: NookiMood, board: TodayBoard?): String = when (mood) {
    NookiMood.SLEEPING -> "Napping. Nothing needs you right now."
    NookiMood.CONTENT -> "Keeping an eye on today for you."
    NookiMood.LOOKING_UP -> if ((board?.missed?.size ?: 0) > 0) "Looked up. Something slipped by." else "Looked up. Something is close."
    NookiMood.FLUSTERED -> "A bit flustered. A few things piled up."
}

private fun NookiMood.toPetMood(): PetMood = when (this) {
    NookiMood.SLEEPING -> PetMood.SLEEPING
    NookiMood.CONTENT -> PetMood.HAPPY
    NookiMood.LOOKING_UP -> PetMood.ALERT
    NookiMood.FLUSTERED -> PetMood.OVERWHELMED
}

@Composable
private fun toneColor(tone: KnotTone): Color {
    val t = LocalThreadColors.current
    return when (tone) {
        KnotTone.MISSED -> t.missed
        KnotTone.FORGOT -> t.forgot
        KnotTone.TODO -> t.todo
        KnotTone.DONE -> t.done
    }
}

/** Draws this row's piece of the thread. Rows below the drop line stay bare while it falls. */
@Composable
private fun ThreadSegment(color: Color, progress: Float, rowIndex: Int, modifier: Modifier = Modifier) {
    // Each row takes a slice of the drop; the first rows appear first.
    val local = ((progress * 8f) - rowIndex).coerceIn(0f, 1f)
    Canvas(modifier) {
        val x = ThreadX.toPx()
        drawLine(color, Offset(x, 0f), Offset(x, size.height * local), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun SectionHeader(title: String, tone: KnotTone, progress: Float, rowIndex: Int) {
    val color = toneColor(tone)
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        ThreadSegment(color, progress, rowIndex, Modifier.matchParentSize())
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = ThreadX + 24.dp, bottom = 6.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun KnotRow(
    knot: Knot,
    progress: Float,
    rowIndex: Int,
    onUntie: () -> Unit,
    laterLabel: String?,
    onLater: () -> Unit,
) {
    val color = toneColor(knot.tone)
    val done = knot.tone == KnotTone.DONE
    val action = if (done) "Done" else "Untie to mark done"
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        ThreadSegment(color, progress, rowIndex, Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !done, role = Role.Button, onClickLabel = action, onClick = onUntie)
                .semantics(mergeDescendants = true) { contentDescription = "${knot.line}. ${knot.detail}. $action" }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(ThreadX * 2).height(KnotSize), contentAlignment = Alignment.Center) {
                KnotBead(color = color, tied = !done)
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = knot.line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(knot.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (laterLabel != null) {
                TextButton(onClick = onLater, modifier = Modifier.padding(end = 8.dp)) { Text(laterLabel) }
            }
        }
    }
}

/** A knot: a filled bead with a small loop. Untied (done) it becomes an open ring. */
@Composable
private fun KnotBead(color: Color, tied: Boolean) {
    val bg = MaterialTheme.colorScheme.background
    Canvas(Modifier.size(KnotSize)) {
        val r = size.minDimension / 2f
        drawCircle(bg, r)
        if (tied) {
            drawCircle(color, r * 0.78f)
            drawCircle(bg, r * 0.26f, center = Offset(center.x + r * 0.22f, center.y - r * 0.22f))
        } else {
            drawCircle(color, r * 0.7f, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

@Composable
private fun RingingBlock(knot: Knot, onIntent: (TodayIntent) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Now", style = MaterialTheme.typography.labelLarge)
            Text(knot.line, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onIntent(TodayIntent.Untie(knot)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = NightIndigo),
                ) { Text("Done") }
                TextButton(onClick = { onIntent(TodayIntent.Later(knot.ref)) }) {
                    Text("Tomorrow", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun CalmRow(progress: Float) {
    val color = LocalThreadColors.current.quiet
    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
        ThreadSegment(color, progress, 0, Modifier.matchParentSize())
        Column(Modifier.padding(start = ThreadX + 24.dp, end = 20.dp, top = 16.dp)) {
            Text("All calm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Tell Nooki anything you don't want to forget. A time, a bill, who owes you, where you kept something.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnswerBlock(answer: Answer, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(answer.question, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            answer.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

@Composable
private fun EveningWrap(onMove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = ThreadX + 24.dp, end = 20.dp, top = 20.dp)) {
        Text("Evening wrap", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Whatever is left can wait for tomorrow. Plug in your phone tonight so Nooki can ring on time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onMove) { Text("Move what's left to tomorrow") }
    }
}

@Composable
private fun TellNookiBar(state: TodayState, onIntent: (TodayIntent) -> Unit, onVoice: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (state.preview.isNotEmpty()) {
                Text(
                    text = state.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = { onIntent(TodayIntent.EditDraft(it)) },
                    placeholder = { Text("Tell Nooki… kal subah 9 baje doctor") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onIntent(TodayIntent.SubmitDraft) }),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                IconButton(onClick = onVoice) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Speak to Nooki in Hindi or English")
                }
                if (state.draft.isNotBlank()) {
                    Button(onClick = { onIntent(TodayIntent.SubmitDraft) }, shape = CircleShape) { Text("Save") }
                }
            }
        }
    }
}
