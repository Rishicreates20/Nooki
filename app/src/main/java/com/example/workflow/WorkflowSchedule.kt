package com.example.workflow

import com.example.data.local.entity.RecurrenceRule
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One heads-up before a reminder: when it rings, and how long before the moment that is. */
data class LeadAlert(val atMillis: Long, val leadMinutes: Int)

/**
 * Pure time math for workflow reminders: heads-ups before exams and deadlines, calendar repeats,
 * and the countdown text on cards. Nothing here touches Android, so it is unit tested directly.
 */
object WorkflowSchedule {

    private const val MINUTE = 60_000L

    /** Lead times are stored in Room as "10080,1440,120" (minutes before the reminder). */
    fun encodeLeadTimes(leadMinutes: List<Int>): String =
        leadMinutes.filter { it > 0 }.distinct().sortedDescending().joinToString(",")

    fun decodeLeadTimes(raw: String?): List<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            .distinct().sortedDescending()

    /**
     * The earliest heads-up still ahead of [nowMillis], or null when none is left. A heads-up that
     * would land within a minute of the reminder itself is skipped, since the reminder rings anyway.
     */
    fun nextLeadAlert(triggerAtMillis: Long, leadMinutes: List<Int>, nowMillis: Long): LeadAlert? =
        leadMinutes
            .filter { it >= 1 }
            .map { LeadAlert(triggerAtMillis - it * MINUTE, it) }
            .filter { it.atMillis > nowMillis && triggerAtMillis - it.atMillis >= MINUTE }
            .minByOrNull { it.atMillis }

    /** Heads-ups that will still happen, for the quick add preview ("1 week, 1 day, 2 hours before"). */
    fun upcomingLeads(triggerAtMillis: Long, leadMinutes: List<Int>, nowMillis: Long): List<Int> =
        leadMinutes.filter { triggerAtMillis - it * MINUTE > nowMillis }.sortedDescending()

    /** "1 week", "2 days", "1 day", "3 hours", "1 hour 30 min", "45 min". */
    fun describeLead(minutes: Int): String {
        fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"
        return when {
            minutes % (7 * 24 * 60) == 0 -> plural(minutes / (7 * 24 * 60), "week")
            minutes % (24 * 60) == 0 -> plural(minutes / (24 * 60), "day")
            minutes % 60 == 0 -> plural(minutes / 60, "hour")
            minutes > 60 -> "${plural(minutes / 60, "hour")} ${minutes % 60} min"
            else -> "$minutes min"
        }
    }

    /** The countdown on cards and heads-up notifications: "6 days left", "5 h left", "12 min left". */
    fun countdown(triggerAtMillis: Long, nowMillis: Long): String {
        val diff = triggerAtMillis - nowMillis
        if (diff <= 0) return "now"
        val minutes = (diff + MINUTE - 1) / MINUTE
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days >= 2 -> "$days days left"
            hours >= 24 -> if (hours == 24L) "1 day left" else "1 day ${hours - 24} h left"
            hours >= 1 -> if (minutes % 60 == 0L || hours >= 5) "$hours h left" else "$hours h ${minutes % 60} min left"
            else -> "$minutes min left"
        }
    }

    /**
     * The first occurrence of [rule] after [nowMillis], keeping [triggerAtMillis]'s clock time. A
     * monthly bill on the 31st lands on the last day of shorter months, then goes back to the 31st.
     */
    fun nextOccurrence(
        triggerAtMillis: Long,
        rule: RecurrenceRule,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val anchor = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = triggerAtMillis }
        val anchorDay = anchor.get(Calendar.DAY_OF_MONTH)
        val anchorMonth = anchor.get(Calendar.MONTH)
        var step = 0
        while (true) {
            step++
            val cal = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = triggerAtMillis }
            when (rule) {
                RecurrenceRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, step)
                RecurrenceRule.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7 * step)
                RecurrenceRule.WEEKDAYS -> {
                    cal.add(Calendar.DAY_OF_YEAR, step)
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) continue
                }
                RecurrenceRule.MONTHLY -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, step)
                    cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                RecurrenceRule.YEARLY -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.YEAR, step)
                    cal.set(Calendar.MONTH, anchorMonth)
                    cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
            }
            if (cal.timeInMillis > nowMillis) return cal.timeInMillis
            if (step > 5_000) return nowMillis + MINUTE // a trigger decades in the past; never loop forever
        }
    }
}
