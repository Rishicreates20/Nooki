package com.example.attention

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dates and durations the way a person would say them. Pure, so it is tested directly. */
object TimeWords {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun clock(millis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        format("h:mm a", millis, zone).lowercase(Locale.ENGLISH)

    /** Start of the day [offsetDays] from the day containing [millis]. */
    fun startOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault(), offsetDays: Int = 0): Long =
        Calendar.getInstance(zone, Locale.ENGLISH).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }.timeInMillis

    fun daysBetween(fromMillis: Long, toMillis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val from = startOfDay(fromMillis, zone)
        val to = startOfDay(toMillis, zone)
        // Rounding absorbs the 23 and 25 hour days of a daylight saving change.
        return Math.round((to - from).toDouble() / DAY).toInt()
    }

    /** "5:00 pm", "Tomorrow, 9:00 am", "Friday, 3:15 pm", "12 Dec", "12 Jan 2028". */
    fun moment(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val days = daysBetween(nowMillis, millis, zone)
        val time = clock(millis, zone)
        return when {
            days == 0 -> time
            days == 1 -> "Tomorrow, $time"
            days == -1 -> "Yesterday, $time"
            days in 2..6 -> "${format("EEEE", millis, zone)}, $time"
            else -> date(millis, nowMillis, zone)
        }
    }

    /** "Today", "Tomorrow", "Friday", "12 Dec", "12 Jan 2028": the same, without the clock. */
    fun day(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val days = daysBetween(nowMillis, millis, zone)
        return when {
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days == -1 -> "Yesterday"
            days in 2..6 -> format("EEEE", millis, zone)
            else -> date(millis, nowMillis, zone)
        }
    }

    fun date(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val year = Calendar.getInstance(zone, Locale.ENGLISH).apply { timeInMillis = millis }.get(Calendar.YEAR)
        val thisYear = Calendar.getInstance(zone, Locale.ENGLISH).apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
        return format(if (year == thisYear) "d MMM" else "d MMM yyyy", millis, zone)
    }

    /** "just now", "20 min ago", "2 hours ago", "yesterday", "3 days ago". */
    fun ago(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val diff = (nowMillis - millis).coerceAtLeast(0L)
        val days = daysBetween(millis, nowMillis, zone)
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> "${diff / MINUTE} min ago"
            days == 0 -> plural(diff / HOUR, "hour") + " ago"
            days == 1 -> "yesterday"
            else -> plural(days.toLong(), "day") + " ago"
        }
    }

    /** "in 2 days", "tomorrow", "today", "2 days ago", for how far off something is. */
    fun dayGap(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val days = daysBetween(nowMillis, millis, zone)
        return when {
            days == 0 -> "today"
            days == 1 -> "tomorrow"
            days == -1 -> "yesterday"
            days > 1 -> "in $days days"
            else -> "${-days} days ago"
        }
    }

    /** "₹1,240" with Indian digit grouping: 12,34,567. */
    fun rupees(amount: Long): String {
        val digits = amount.toString()
        if (digits.length <= 3) return "₹$digits"
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val groups = rest.reversed().chunked(2).joinToString(",").reversed()
        return "₹$groups,$last3"
    }

    fun plural(count: Long, unit: String): String = if (count == 1L) "1 $unit" else "$count ${unit}s"

    private fun format(pattern: String, millis: Long, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = zone }.format(Date(millis))
}
