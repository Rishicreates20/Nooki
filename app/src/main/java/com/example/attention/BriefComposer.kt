package com.example.attention

import com.example.data.local.entity.LooseEndDirection
import com.example.data.local.entity.LooseEndEntity
import java.util.TimeZone

data class Brief(val title: String, val body: String, val offerMoveToTomorrow: Boolean = false)

/**
 * The morning brief and evening wrap. When there is nothing worth saying these return null, because
 * a calm app stays quiet.
 */
object BriefComposer {

    fun morning(
        digest: TodayDigest,
        name: String,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): Brief? {
        val lines = buildList {
            val timed = digest.toDo.filter { it.isTimedToday() }
            when {
                timed.size == 1 -> add("Today: ${describe(timed.first(), nowMillis, zone)}.")
                timed.size > 1 -> add("${timed.size} things today, first ${describe(timed.first(), nowMillis, zone)}.")
            }
            digest.comingUp.firstOrNull()?.let { next ->
                if (TimeWords.daysBetween(nowMillis, whenOf(next) ?: nowMillis, zone) <= 3) {
                    add("Coming up: ${titleOf(next)}, ${TimeWords.dayGap(whenOf(next) ?: nowMillis, nowMillis, zone)}.")
                }
            }
            if (digest.missed.isNotEmpty()) {
                add(if (digest.missed.size == 1) "1 thing needs a look." else "${digest.missed.size} things need a look.")
            }
            digest.forgot.filterIsInstance<AttentionItem.Owed>().firstOrNull()?.let { add(owedLine(it)) }
        }
        if (lines.isEmpty()) return null
        return Brief(title = if (name.isBlank()) "Good morning" else "Good morning, $name", body = lines.joinToString(" "))
    }

    fun evening(
        digest: TodayDigest,
        batteryPercent: Int?,
        isCharging: Boolean,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): Brief? {
        val open = digest.toDo.filterIsInstance<AttentionItem.Reminder>() +
            digest.missed.filterIsInstance<AttentionItem.Reminder>()
        val movable = open.filter { !it.reminder.undated && it.reminder.recurrenceRule == null && it.reminder.recurrenceMinutes == null }
        val lines = buildList {
            when {
                open.size == 1 -> add("Still open: ${open.first().reminder.title}.")
                open.size in 2..3 -> add("Still open: ${open.joinToString(", ") { it.reminder.title }}.")
                open.size > 3 -> add("${open.size} things are still open.")
            }
            val calls = digest.missed.count { it is AttentionItem.Noticed }
            if (calls > 0) add(if (calls == 1) "1 thing Nooki noticed is waiting." else "$calls things Nooki noticed are waiting.")
            val tomorrow = digest.comingUp.firstOrNull { (whenOf(it)?.let { w -> TimeWords.daysBetween(nowMillis, w, zone) }) == 1 }
            if (tomorrow != null) add("Tomorrow: ${titleOf(tomorrow)}.")
            if (batteryPercent != null && batteryPercent < LOW_BATTERY_PERCENT && !isCharging) {
                add("Phone is at $batteryPercent%. Plug it in tonight.")
            }
        }
        if (lines.isEmpty()) return null
        return Brief(title = "Before you sleep", body = lines.joinToString(" "), offerMoveToTomorrow = movable.isNotEmpty())
    }

    private fun AttentionItem.isTimedToday() = when (this) {
        is AttentionItem.Reminder -> !reminder.undated
        is AttentionItem.Event -> true
        else -> false
    }

    private fun describe(item: AttentionItem, nowMillis: Long, zone: TimeZone): String {
        val at = whenOf(item)
        val allDay = item is AttentionItem.Event && item.event.allDay
        return if (at == null || allDay) titleOf(item) else "${titleOf(item)} at ${TimeWords.clock(at, zone)}"
    }

    private fun whenOf(item: AttentionItem): Long? = when (item) {
        is AttentionItem.Reminder -> item.reminder.triggerAtMillis
        is AttentionItem.Event -> item.event.beginMillis
        is AttentionItem.Noticed -> item.noticed.dueAtMillis
        is AttentionItem.Owed -> null
    }

    private fun titleOf(item: AttentionItem): String = when (item) {
        is AttentionItem.Reminder -> item.reminder.title
        is AttentionItem.Event -> item.event.title
        is AttentionItem.Noticed -> item.noticed.title
        is AttentionItem.Owed -> item.end.person
    }

    private fun owedLine(item: AttentionItem.Owed): String = owedSentence(item.end)

    /** "Rahul still owes you ₹500.", "You still have Aman's charger." */
    fun owedSentence(end: LooseEndEntity): String {
        val amount = end.amountRupees?.let(TimeWords::rupees)
        return when {
            end.direction == LooseEndDirection.THEY_HAVE && amount != null -> "${end.person} still owes you $amount."
            end.direction == LooseEndDirection.THEY_HAVE -> "${end.person} still has your ${end.what}."
            amount != null -> "You still owe ${end.person} $amount."
            else -> "You still have ${end.person}'s ${end.what}."
        }
    }

    const val LOW_BATTERY_PERCENT = 30
}
