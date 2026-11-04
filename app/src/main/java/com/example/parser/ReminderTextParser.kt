package com.example.parser

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** How strongly the user flagged a reminder ("urgent", "asap", "!!", "important"). */
enum class PriorityHint { HIGH, URGENT }

/**
 * Result of turning one free-text line ("call mom tomorrow at 6pm") into a reminder.
 *
 * @property title cleaned title with the time words and filler ("remind me to", "at", "on") removed.
 * @property triggerAtMillis when to ring, or null when the text had no time in it. The UI then asks
 *   for one instead of guessing.
 * @property priorityHint set only when the text explicitly asked for urgency.
 */
data class ParsedReminder(
    val title: String,
    val triggerAtMillis: Long?,
    val priorityHint: PriorityHint? = null
)

/**
 * Rule-based (regex) natural-language reminder parser (PRD FR-1.1 to FR-1.3).
 *
 * It is the always-available path: it runs offline in well under a millisecond, never throws, and
 * is what [SmartReminderParser] falls back to when on-device AI is missing or fails (FR-1.4).
 *
 * Understands, case-insensitively:
 * - relative times: "in 45 mins", "in 2 hours", "in an hour", "in half an hour", "in 1h 30m",
 *   "in 3 days", "20 minutes from now"
 * - days: "today", "tonight", "tomorrow"/"tmrw", "day after tomorrow", weekdays ("friday",
 *   "on fri", "next monday"), and dates ("nov 5", "5th december", "on 12/25")
 * - clock times: "at 3:15 PM", "3pm", "15:30", "at 9", "noon", "midnight"
 * - parts of day: "morning" (9:00), "afternoon" (15:00), "evening" (18:00), "tonight" (20:00)
 *
 * A time with no day means the next time the clock reaches it. A day with no time means 9:00 AM.
 */
object ReminderTextParser {

    private const val DEFAULT_HOUR = 9
    private const val OPT = "(?i)"

    private val LEADING_FILLER = Regex(
        "$OPT^\\s*(?:hey\\s+nooki[,!]?\\s*)?(?:please\\s+)?" +
            "(?:remind\\s+me\\s+(?:to|about|that|of)\\b|remind\\s+me\\b|don'?t\\s+(?:let\\s+me\\s+)?forget(?:\\s+(?:to|about))?\\b|" +
            "remember\\s+to\\b|i\\s+(?:need|have)\\s+to\\b|todo:?|task:?)\\s*"
    )

    private val URGENT = Regex("$OPT(?:\\b(?:urgent(?:ly)?|asap)\\b|!{2,})")
    private val IMPORTANT = Regex("$OPT(?:\\bimportant\\b|(?<=\\S)\\s*!\\s*$)")

    private const val UNIT =
        "(minutes?|mins?|m|hours?|hrs?|hr|h|days?|d|weeks?|wks?|w)"
    private const val AMOUNT = "(an?|one|two|three|four|five|six|ten|fifteen|twenty|thirty|\\d+(?:\\.\\d+)?)"

    // "in 1h 30m", "in 2 hours and 15 minutes", "in an hour", "after 10 min"
    private val RELATIVE = Regex(
        "$OPT\\b(?:in|after)\\s+$AMOUNT\\s*$UNIT\\b(?:\\s*(?:and\\s+)?(\\d+)\\s*(minutes?|mins?|m)\\b)?"
    )
    private val RELATIVE_FROM_NOW = Regex("$OPT\\b$AMOUNT\\s*$UNIT\\s+from\\s+now\\b")
    private val HALF_HOUR = Regex("$OPT\\b(?:in|after)\\s+(?:a\\s+)?half\\s+(?:an?\\s+)?hour\\b")
    private val HOUR_AND_HALF = Regex("$OPT\\b(?:in|after)\\s+(?:an?|one)\\s+hour\\s+and\\s+a\\s+half\\b")

    private val DAY_AFTER_TOMORROW = Regex("$OPT\\b(?:the\\s+)?day\\s+after\\s+(?:tomorrow|tmrw|tmr)\\b")
    private val RELATIVE_DAY = Regex("$OPT\\b(?:(?:by|on|for)\\s+)?(today|tonight|tomorrow|tmrw|tmr|tomorow)\\b")

    private val WEEKDAYS = listOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    // Full names stand alone. Short forms ("sat", "sun", "wed") are real words, so they need a lead-in.
    private val WEEKDAY_FULL = Regex(
        "$OPT\\b(?:(on|this|next|by|until|till)\\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)s?\\b"
    )
    private val WEEKDAY_SHORT = Regex(
        "$OPT\\b(on|this|next|by)\\s+(sun|mon|tues?|wed|thu(?:rs?)?|fri|sat)\\.?\\b"
    )

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val MONTH_DAY = Regex("$OPT\\b(?:on\\s+)?$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
    private val DAY_MONTH = Regex("$OPT\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTH\\b")
    private val NUMERIC_DATE = Regex("$OPT\\b(?:on\\s+)(\\d{1,2})/(\\d{1,2})\\b")

    // "at 3:15 pm", "3pm", "at 10.30am"
    // "2 a day" must not become 2 AM, so a spaced meridiem needs its "m".
    private val TIME_12H = Regex(
        "$OPT\\b(?:(?:at|by|@)\\s*)?(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*([ap])\\.?m\\.?|([ap]))(?![a-z])"
    )
    // "at 15:30", "15:30"
    private val TIME_24H = Regex("$OPT\\b(?:(?:at|by|@)\\s*)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")
    // "at 9" (needs the "at", otherwise "buy 2 apples" would ring at 2)
    private val TIME_BARE = Regex("$OPT\\b(?:at|@)\\s*(\\d{1,2})\\b(?!\\s*(?:%|st|nd|rd|th|/))")
    private val NOON = Regex("$OPT\\b(?:at\\s+|by\\s+)?(noon|midday|midnight)\\b")
    private val PART_OF_DAY = Regex(
        "$OPT\\b(?:(?:in\\s+the|this|tomorrow|tonight's?)\\s+)?(morning|afternoon|evening|night)\\b"
    )
    // Once a clock time is known, only "in the evening"-style phrases adjust it, so a title like
    // "evening walk at 6pm" keeps its word.
    private val PART_OF_DAY_QUALIFIER = Regex("$OPT\\b(?:in\\s+the|this)\\s+(morning|afternoon|evening|night)\\b")

    private val TRAILING_FILLER = Regex(
        "$OPT(?:[\\s,;:.\\-]+|\\b(?:at|on|by|for|this|next|the|around|until|till)\\b|@)+$"
    )
    private val LEADING_CONNECTOR = Regex("$OPT^(?:[\\s,;:.\\-]+|\\b(?:to|about|and|then)\\b)+")

    fun parse(
        input: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): ParsedReminder {
        val original = input.trim()
        if (original.isEmpty()) return ParsedReminder(title = "", triggerAtMillis = null)

        var text = " " + original.replace(Regex("\\s+"), " ") + " "

        fun consume(regex: Regex): MatchResult? {
            val match = regex.find(text) ?: return null
            text = text.replaceRange(match.range, " ")
            return match
        }

        text = " " + LEADING_FILLER.replaceFirst(text.trim(), "") + " "

        // ---- priority -------------------------------------------------------------------------
        var priority: PriorityHint? = null
        if (consume(URGENT) != null) priority = PriorityHint.URGENT
        while (URGENT.containsMatchIn(text)) consume(URGENT)
        if (consume(IMPORTANT) != null && priority == null) priority = PriorityHint.HIGH

        // ---- relative offsets win over everything else -----------------------------------------
        var offsetMillis: Long? = null
        consume(HOUR_AND_HALF)?.let { offsetMillis = 90 * MINUTE }
        if (offsetMillis == null) consume(HALF_HOUR)?.let { offsetMillis = 30 * MINUTE }
        if (offsetMillis == null) {
            (consume(RELATIVE) ?: consume(RELATIVE_FROM_NOW))?.let { m ->
                val main = amount(m.groupValues[1]) * unitMillis(m.groupValues[2])
                val extra = m.groupValues.getOrNull(3)?.toDoubleOrNull()?.times(MINUTE) ?: 0.0
                offsetMillis = (main + extra).toLong()
            }
        }

        val cal = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = nowMillis }

        if (offsetMillis != null) {
            // Strip any stray day words so they don't end up in the title ("in 2 hours today").
            consume(RELATIVE_DAY)
            return ParsedReminder(cleanTitle(text, original), nowMillis + offsetMillis!!, priority)
        }

        // ---- day -------------------------------------------------------------------------------
        var dayOffset: Int? = null
        var absoluteDate: Triple<Int, Int, Boolean>? = null // month(0-11), day, yearExplicitlyRollable
        var weekday: Pair<Int, Boolean>? = null // calendar weekday, "next"
        var impliedHour: Int? = null

        if (consume(DAY_AFTER_TOMORROW) != null) {
            dayOffset = 2
        } else {
            consume(RELATIVE_DAY)?.let { m ->
                when (m.groupValues[1].lowercase()) {
                    "today" -> dayOffset = 0
                    "tonight" -> { dayOffset = 0; impliedHour = 20 }
                    else -> dayOffset = 1
                }
            }
        }

        if (dayOffset == null) {
            (consume(WEEKDAY_FULL) ?: consume(WEEKDAY_SHORT))?.let { m ->
                val name = m.groupValues[2].lowercase()
                val day = WEEKDAYS.first { it.first.startsWith(name.take(3)) }.second
                weekday = day to (m.groupValues[1].lowercase() == "next")
            }
        }

        if (dayOffset == null && weekday == null) {
            consume(MONTH_DAY)?.let { m ->
                absoluteDate = Triple(monthIndex(m.groupValues[1]), m.groupValues[2].toInt(), true)
            } ?: consume(DAY_MONTH)?.let { m ->
                absoluteDate = Triple(monthIndex(m.groupValues[2]), m.groupValues[1].toInt(), true)
            } ?: consume(NUMERIC_DATE)?.let { m ->
                // Month/day, the US order the app's English UI uses.
                val month = m.groupValues[1].toInt() - 1
                val day = m.groupValues[2].toInt()
                if (month in 0..11 && day in 1..31) absoluteDate = Triple(month, day, true)
            }
        }

        // ---- time of day -----------------------------------------------------------------------
        var hour: Int? = null
        var minute = 0

        consume(TIME_12H)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                val meridiem = m.groupValues[3].ifEmpty { m.groupValues[4] }
                val pm = meridiem.equals("p", ignoreCase = true)
                hour = (h % 12) + if (pm) 12 else 0
                minute = m.groupValues[2].toIntOrNull() ?: 0
            }
        }
        if (hour == null) {
            consume(TIME_24H)?.let { m ->
                hour = m.groupValues[1].toInt()
                minute = m.groupValues[2].toInt()
                if (hour!! in 1..6 && !m.groupValues[1].startsWith("0")) hour = hour!! + 12
            }
        }
        if (hour == null) {
            consume(NOON)?.let { m ->
                if (m.groupValues[1].equals("midnight", ignoreCase = true)) {
                    hour = 23; minute = 59
                } else {
                    hour = 12
                }
            }
        }
        if (hour == null) {
            consume(TIME_BARE)?.let { m ->
                val h = m.groupValues[1].toInt()
                if (h in 0..23) {
                    // Nobody sets a reminder "at 3" meaning 3 AM; 1-6 are afternoon/evening.
                    hour = if (h in 1..6) h + 12 else h
                }
            }
        }
        consume(if (hour == null) PART_OF_DAY else PART_OF_DAY_QUALIFIER)?.let { m ->
            val partHour = when (m.groupValues[1].lowercase()) {
                "morning" -> 9
                "afternoon" -> 15
                "evening" -> 18
                else -> 20
            }
            if (hour == null) {
                hour = partHour
            } else if (partHour >= 12 && hour!! < 12) {
                hour = hour!! + 12 // "at 7 in the evening"
            }
        }
        if (hour == null) {
            hour = impliedHour
        } else if (impliedHour != null && hour!! < 12) {
            hour = hour!! + 12 // "tonight at 9"
        }

        val hasDay = dayOffset != null || weekday != null || absoluteDate != null
        if (!hasDay && hour == null) {
            return ParsedReminder(cleanTitle(text, original), null, priority)
        }

        // ---- resolve to an instant -------------------------------------------------------------
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val targetHour = hour ?: DEFAULT_HOUR
        val targetMinute = if (hour == null) 0 else minute

        fun applyClock() {
            cal.set(Calendar.HOUR_OF_DAY, targetHour)
            cal.set(Calendar.MINUTE, targetMinute)
        }

        when {
            dayOffset != null -> {
                cal.add(Calendar.DAY_OF_YEAR, dayOffset!!)
                applyClock()
            }
            weekday != null -> {
                val (target, next) = weekday!!
                var ahead = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                applyClock()
                if (ahead == 0 && (next || cal.timeInMillis <= nowMillis)) ahead = 7
                cal.add(Calendar.DAY_OF_YEAR, ahead)
            }
            absoluteDate != null -> {
                val (month, day, _) = absoluteDate!!
                cal.set(Calendar.DAY_OF_MONTH, 1) // so Jan 31 -> "feb 3" doesn't roll into March
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                applyClock()
                if (cal.timeInMillis <= nowMillis) cal.add(Calendar.YEAR, 1)
            }
            else -> {
                applyClock()
                // Time only: the next time the clock reads that.
                if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return ParsedReminder(cleanTitle(text, original), cal.timeInMillis, priority)
    }

    private fun cleanTitle(remaining: String, original: String): String {
        var t = remaining.replace(Regex("\\s+"), " ").trim()
        // Repeat: removing "at" can expose a trailing "," and vice versa.
        repeat(3) {
            t = TRAILING_FILLER.replace(t, "").trim()
            t = LEADING_CONNECTOR.replace(t, "").trim()
        }
        t = t.replace(Regex("\\s+([,.!?;:])"), "$1")
        if (t.isBlank()) {
            t = LEADING_FILLER.replaceFirst(original, "").trim().ifBlank { original }
        }
        return t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun amount(raw: String): Double = when (raw.lowercase()) {
        "a", "an", "one" -> 1.0
        "two" -> 2.0
        "three" -> 3.0
        "four" -> 4.0
        "five" -> 5.0
        "six" -> 6.0
        "ten" -> 10.0
        "fifteen" -> 15.0
        "twenty" -> 20.0
        "thirty" -> 30.0
        else -> raw.toDoubleOrNull() ?: 0.0
    }

    private fun unitMillis(raw: String): Long {
        val u = raw.lowercase()
        return when {
            u.startsWith("w") -> 7 * DAY
            u.startsWith("d") -> DAY
            u.startsWith("h") -> HOUR
            else -> MINUTE
        }
    }

    private fun monthIndex(raw: String): Int = MONTHS.indexOf(raw.lowercase().take(3))

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
}
