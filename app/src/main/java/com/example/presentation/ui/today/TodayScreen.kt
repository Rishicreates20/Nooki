package com.example.presentation.ui.today

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.attention.AttentionItem
import com.example.attention.BriefComposer
import com.example.attention.TimeWords
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedType
import com.example.pet.PetMood
import com.example.presentation.mvi.CaptureDraft
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.mvi.NookiUiState
import com.example.presentation.ui.Words
import com.example.presentation.ui.components.NookiFace
import com.example.ui.theme.Cotton
import com.example.ui.theme.Neel
import com.example.ui.theme.NookiPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HeaderTop = 12.dp
private val PetSize = 118.dp
private val PetInsetEnd = 12.dp

/** How far down Nooki's drawing her collar bell sits: 83 on her 108 unit grid. */
private const val BellHeight = 83f / 108f

/** From the screen edge to the middle of Nooki, where the thread leaves her collar. */
private val ThreadStartInset = PetInsetEnd + PetSize / 2

private enum class Zone { MISSED, FORGOT, TO_DO, COMING_UP }

private data class Section(val zone: Zone, val title: String, val color: Color, val items: List<AttentionItem>)

/** The home screen: what did I miss, what did I forget, what do I have to do. */
@Composable
fun TodayScreen(
    state: NookiUiState,
    onIntent: (NookiIntent) -> Unit,
    onFixNotifications: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = NookiPalette.colors
    val digest = state.digest
    val sections = listOf(
        Section(Zone.MISSED, "You missed", colors.missed, digest.missed),
        Section(Zone.FORGOT, "You forgot", colors.forgot, digest.forgot),
        Section(Zone.TO_DO, "To do today", colors.toDo, digest.toDo),
        Section(Zone.COMING_UP, "Coming up", colors.comingUp, digest.comingUp)
    ).filter { it.items.isNotEmpty() }

    val firstColor = when {
        digest.ringing.isNotEmpty() -> MaterialTheme.colorScheme.primary
        sections.isNotEmpty() -> sections.first().color
        else -> colors.comingUp
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_list"),
        contentPadding = contentPadding
    ) {
        item(key = "header") {
            TodayHeader(
                nowMillis = state.nowMillis,
                headline = digest.headline,
                subline = digest.subline,
                mood = state.mood,
                reaction = state.petReaction,
                threadColor = firstColor,
                onPetTap = { onIntent(NookiIntent.PetNooki) }
            )
        }

        if (!state.readiness.canPostNotifications) {
            item(key = "notifications_off") {
                ThreadRow(color = firstColor, bendRight = true) {
                    Column(Modifier.weight(1f).padding(end = 20.dp, top = 8.dp, bottom = 8.dp)) {
                        Text("Notifications are off, so Nooki can't reach you.", style = MaterialTheme.typography.bodyMedium)
                        QuietAction("Turn on notifications", MaterialTheme.colorScheme.secondary, onFixNotifications)
                    }
                }
            }
        }

        digest.ringing.forEach { ringing ->
            item(key = ringing.key) {
                ThreadRow(
                    color = firstColor,
                    bendRight = false,
                    knot = { KnotMark(MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.animateItem()
                ) {
                    RingingBlock(ringing, onIntent, Modifier.weight(1f).padding(end = 16.dp, top = 6.dp, bottom = 10.dp))
                }
            }
        }

        var previous = firstColor
        sections.forEach { entry ->
            sectionItems(entry, previous, state.nowMillis, onIntent)
            previous = entry.color
        }

        if (digest.isEmpty) {
            item(key = "empty") { EmptyDesk(firstColor, onIntent) }
        } else {
            item(key = "thread_end") {
                ThreadRow(color = previous, endColor = previous.copy(alpha = 0f), bendRight = true, minHeight = 40.dp) {}
            }
        }
    }
}

private fun LazyListScope.sectionItems(
    section: Section,
    previous: Color,
    nowMillis: Long,
    onIntent: (NookiIntent) -> Unit
) {
    item(key = "heading_${section.title}") {
        ThreadRow(color = previous, endColor = section.color, bendRight = true, modifier = Modifier.animateItem()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                color = section.color,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
            )
        }
    }
    itemsIndexed(section.items, key = { _, item -> item.key }) { index, item ->
        AttentionRow(item, section, index, nowMillis, onIntent, Modifier.animateItem())
    }
}

@Composable
private fun TodayHeader(
    nowMillis: Long,
    headline: String,
    subline: String,
    mood: PetMood,
    reaction: Int,
    threadColor: Color,
    onPetTap: () -> Unit
) {
    Box(Modifier.fillMaxWidth().statusBarsPadding()) {
        ThreadSwoop(
            color = threadColor,
            fromRightInset = ThreadStartInset,
            startY = HeaderTop + PetSize * BellHeight,
            modifier = Modifier.matchParentSize()
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp)
                .padding(start = 20.dp, end = PetSize + PetInsetEnd + 4.dp, top = HeaderTop, bottom = 72.dp)
        ) {
            Text(
                text = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(Date(nowMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = NookiPalette.colors.muted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("today_headline")
            )
            if (subline.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(subline, style = MaterialTheme.typography.bodyLarge, color = NookiPalette.colors.muted)
            }
        }
        NookiFace(
            mood = mood,
            reaction = reaction,
            onTap = onPetTap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = PetInsetEnd, top = HeaderTop)
                .size(PetSize)
        )
    }
}

/** The one solid block in the app, because it is the one thing that cannot wait. */
@Composable
private fun RingingBlock(item: AttentionItem.Reminder, onIntent: (NookiIntent) -> Unit, modifier: Modifier = Modifier) {
    val reminder = item.reminder
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = Neel,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)) {
            Text("Right now", style = MaterialTheme.typography.labelMedium)
            Text(reminder.title, style = MaterialTheme.typography.headlineSmall)
            if (reminder.askCount > 0) {
                Text("Nooki has asked ${reminder.askCount + 1} times.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onIntent(NookiIntent.Complete(reminder)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Neel, contentColor = Cotton)
                ) { Text("Done") }
                Spacer(Modifier.width(4.dp))
                val minutes = if (reminder.keepAsking) 10 else 60
                TextButton(
                    onClick = { onIntent(NookiIntent.Snooze(reminder, minutes)) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Neel)
                ) { Text(if (reminder.keepAsking) "In 10 min" else "In 1 hour") }
            }
        }
    }
}

@Composable
private fun AttentionRow(
    item: AttentionItem,
    section: Section,
    index: Int,
    nowMillis: Long,
    onIntent: (NookiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val bend = index % 2 == 0
    when (item) {
        is AttentionItem.Reminder -> {
            val reminder = item.reminder
            val inMissed = section.zone == Zone.MISSED
            val inForgot = section.zone == Zone.FORGOT
            val inComingUp = section.zone == Zone.COMING_UP
            val trailing = when {
                inMissed || inForgot || reminder.undated -> null
                inComingUp -> TimeWords.day(reminder.triggerAtMillis, nowMillis)
                else -> TimeWords.clock(reminder.triggerAtMillis)
            }
            val meta = when {
                inMissed -> Words.missedMeta(reminder, nowMillis)
                inForgot -> Words.forgotMeta(reminder, item.forgotReason, nowMillis)
                inComingUp -> Words.comingUpMeta(reminder, nowMillis)
                else -> Words.toDoMeta(reminder)
            }
            ItemRow(
                title = reminder.title,
                meta = meta,
                trailing = trailing,
                color = section.color,
                bendRight = bend,
                knotLabel = "Mark ${reminder.title} as done",
                onUntie = { onIntent(NookiIntent.Complete(reminder)) },
                onClick = { onIntent(NookiIntent.OpenItem(reminder)) },
                modifier = modifier
            )
        }
        is AttentionItem.Noticed -> NoticedRow(item.noticed, section.color, bend, nowMillis, onIntent, modifier)
        is AttentionItem.Owed -> ItemRow(
            title = BriefComposer.owedSentence(item.end),
            meta = "${item.daysOpen} days",
            trailing = null,
            color = section.color,
            bendRight = bend,
            knotLabel = "Mark as settled",
            onUntie = { onIntent(NookiIntent.SettleLooseEnd(item.end)) },
            onClick = null,
            modifier = modifier
        )
        is AttentionItem.Event -> ThreadRow(
            color = section.color,
            bendRight = bend,
            knot = { KnotMark(section.color, size = 16.dp) },
            modifier = modifier
        ) {
            Row(Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp, end = 20.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Calendar", style = MaterialTheme.typography.bodySmall, color = NookiPalette.colors.muted)
                }
                Text(
                    text = if (item.event.allDay) "All day" else TimeWords.moment(item.event.beginMillis, nowMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = NookiPalette.colors.muted
                )
            }
        }
    }
}

@Composable
private fun ItemRow(
    title: String,
    meta: String?,
    trailing: String?,
    color: Color,
    bendRight: Boolean,
    knotLabel: String,
    onUntie: () -> Unit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null
) {
    ThreadRow(
        color = color,
        bendRight = bendRight,
        knot = { Knot(color = color, label = knotLabel, onUntie = onUntie) },
        modifier = modifier
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(top = 12.dp, bottom = if (actions == null) 12.dp else 2.dp, end = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(trailing, style = MaterialTheme.typography.labelMedium, color = NookiPalette.colors.muted)
                }
            }
            if (!meta.isNullOrBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = NookiPalette.colors.muted)
            }
            actions?.invoke()
        }
    }
}

@Composable
private fun NoticedRow(
    item: NoticedEntity,
    color: Color,
    bendRight: Boolean,
    nowMillis: Long,
    onIntent: (NookiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    ItemRow(
        title = Words.noticedTitle(item),
        meta = Words.noticedMeta(item, nowMillis),
        trailing = null,
        color = color,
        bendRight = bendRight,
        knotLabel = "Mark ${item.title} as handled",
        onUntie = { onIntent(NookiIntent.NoticedHandled(item)) },
        onClick = null,
        modifier = modifier,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                when (item.type) {
                    NoticedType.MISSED_CALL, NoticedType.UNREAD_MESSAGE -> {
                        QuietAction("Remind me in an hour", color) { onIntent(NookiIntent.NoticedTrack(item, 60)) }
                        QuietAction("Ignore", NookiPalette.colors.muted) { onIntent(NookiIntent.NoticedIgnored(item)) }
                    }
                    NoticedType.BILL -> {
                        QuietAction("Remind me to pay", color) { onIntent(NookiIntent.NoticedTrack(item)) }
                        QuietAction("Ignore", NookiPalette.colors.muted) { onIntent(NookiIntent.NoticedIgnored(item)) }
                    }
                    NoticedType.EVENT -> {
                        QuietAction("Add a reminder", color) { onIntent(NookiIntent.NoticedTrack(item)) }
                        QuietAction("Ignore", NookiPalette.colors.muted) { onIntent(NookiIntent.NoticedIgnored(item)) }
                    }
                    NoticedType.DELIVERY -> Unit
                }
            }
        }
    )
}

@Composable
internal fun QuietAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .padding(end = 18.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun EmptyDesk(color: Color, onIntent: (NookiIntent) -> Unit) {
    ThreadRow(color = color, endColor = color.copy(alpha = 0f), bendRight = true) {
        Column(Modifier.weight(1f).padding(end = 20.dp, top = 6.dp, bottom = 40.dp)) {
            Text(
                "Tell Nooki the next thing on your mind. Try one of these:",
                style = MaterialTheme.typography.bodyLarge,
                color = NookiPalette.colors.muted
            )
            Spacer(Modifier.height(8.dp))
            listOf("Call mom tomorrow at 6pm", "kal subah 9 baje doctor", "Passport is in the blue bag", "Rahul owes me 500").forEach { line ->
                QuietAction(line, MaterialTheme.colorScheme.secondary) {
                    onIntent(NookiIntent.OpenCapture(CaptureDraft(prefill = line)))
                }
            }
        }
    }
}

