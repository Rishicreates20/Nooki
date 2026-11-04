package com.example.core.parse

import com.example.core.model.RecurrenceRule
import com.example.core.model.ReminderKind
import com.example.core.today.TodayBuilder
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The line under "Tell Nooki" that shows what Nooki understood, before saving. Plain words only.
 */
object UnderstoodText {

    fun describe(u: Understood, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String = when (u) {
        Understood.Empty -> ""
        is Understood.Question ->
            if (u.aboutMoney) "Nooki will show who owes what" else "Nooki will look for “${u.query}”"
        is Understood.SavedThing -> "Remember: ${u.thing} → ${u.place}"
        is Understood.Money -> {
            val what = u.amountPaise?.let(TodayBuilder::rupees) ?: u.thing ?: "something"
            when {
                u.direction == MoneyDirection.I_OWE_THEM -> "You owe ${u.person} $what"
                u.amountPaise != null -> "${u.person} owes you $what"
                else -> "${u.person} has your $what"
            }
        }
        is Understood.Reminder -> reminder(u.reminder, nowMillis, timeZone)
    }

    private fun reminder(r: ParsedReminder, nowMillis: Long, timeZone: TimeZone): String {
        val parts = mutableListOf(r.title)
        val at = r.triggerAtMillis
        if (at == null) {
            parts += "no time yet, Nooki will ask this evening"
        } else {
            parts += whenText(at, nowMillis, timeZone)
        }
        repeatText(r)?.let { parts += it }
        if (r.kind == ReminderKind.MEDICINE) parts += "asks again until you confirm"
        if (r.leadMinutes.isNotEmpty() && at != null) {
            parts += "warns " + r.leadMinutes.joinToString(", ") { lead(it) } + " before"
        }
        return parts.joinToString(" · ")
    }

    private fun repeatText(r: ParsedReminder): String? {
        r.recurrenceMinutes?.let { return "every ${lead(it)}" }
        return when (r.recurrenceRule) {
            RecurrenceRule.DAILY -> "every day"
            RecurrenceRule.WEEKDAYS -> "every weekday"
            RecurrenceRule.WEEKLY -> "every week"
            RecurrenceRule.MONTHLY -> "every month"
            RecurrenceRule.YEARLY -> "every year"
            null -> null
        }
    }

    private fun whenText(at: Long, nowMillis: Long, timeZone: TimeZone): String {
        val days = ((TodayBuilder.endOfDay(at, timeZone) - TodayBuilder.endOfDay(nowMillis, timeZone)) / (24 * 3_600_000L)).toInt()
        val c = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = at }
        val day = when (days) {
            0 -> "today"
            1 -> "tomorrow"
            in 2..6 -> c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US) ?: "in $days days"
            else -> "${c.get(Calendar.DAY_OF_MONTH)} ${c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)}"
        }
        return "$day, ${TodayBuilder.clock(at, timeZone)}"
    }

    private fun lead(minutes: Int): String = when {
        minutes % (24 * 60) == 0 -> (minutes / (24 * 60)).let { if (it == 1) "1 day" else "$it days" }
        minutes % 60 == 0 -> (minutes / 60).let { if (it == 1) "1 hour" else "$it hours" }
        else -> "$minutes min"
    }
}
