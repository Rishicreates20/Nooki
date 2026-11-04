package com.example.core.parse

import com.example.core.model.RecurrenceRule
import com.example.core.model.ReminderKind
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
 * @property kind exam, deadline, AI limit reset, bill or habit, spotted from keywords.
 * @property leadMinutes heads-ups before the reminder: the kind's defaults, or what the text asked
 *   for ("remind me 2 days before").
 * @property recurrenceRule a calendar repeat ("every day", "every monday", "monthly").
 * @property recurrenceMinutes a fixed-interval repeat ("every 2 hours").
 */
data class ParsedReminder(
    val title: String,
    val triggerAtMillis: Long?,
    val priorityHint: PriorityHint? = null,
    val kind: ReminderKind = ReminderKind.TASK,
    val leadMinutes: List<Int> = kind.defaultLeadMinutes,
    val recurrenceRule: RecurrenceRule? = null,
    val recurrenceMinutes: Int? = null
)

/**
 * Rule-based (regex) English reminder parser. Plain Kotlin, offline, never throws.
 * Hinglish and Hindi reach it through [HinglishNormalizer]; [TellNooki] decides whether a line is a
 * reminder at all.
 *
 * Understands, case-insensitively:
 * - relative times: "in 45 mins", "in 2 hours", "in an hour", "in half an hour", "in 1h 30m",
 *   "in 3 days", "20 minutes from now"
 * - days: "today", "tonight", "tomorrow"/"tmrw", "day after tomorrow", weekdays ("friday",
 *   "on fri", "next monday"), and dates ("nov 5", "5th december", "on 12/25")
 * - clock times: "at 3:15 PM", "3pm", "15:30", "at 9", "noon", "midnight"
 * - parts of day: "morning" (9:00), "afternoon" (15:00), "evening" (18:00), "tonight" (20:00)
 *
 * - workflow kinds: exams ("physics exam on friday"), deadlines ("assignment due monday"), AI limit
 *   resets ("claude limit resets in 5h"), bills ("pay rent on the 1st"); everything else is a task
 * - repeats: "every day", "daily", "every morning", "every weekday", "every monday", "weekly",
 *   "every month", "yearly", "every 2 hours"
 * - heads-up overrides: "remind me 2 days before", "a day before", "1 hour ahead"
 * - day of month: "on the 1st", "on the 15th"
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

    // ---- workflow ---------------------------------------------------------------------------------
    // "remind me 2 days before", ", a day before", "ping me an hour ahead", "the day before"
    private val LEAD_BEFORE = Regex(
        "$OPT(?:[,;]?\\s*(?:and\\s+)?(?:(?:remind|ping|alert|warn|tell)\\s+me\\s+)?)" +
            "(?:$AMOUNT\\s*$UNIT|the\\s+(day|night|week))\\s+(?:before(?:hand)?|ahead|prior|early|earlier)\\b"
    )
    private val EVERY_INTERVAL = Regex(
        "$OPT\\b(?:every|each)\\s+(?:(\\d+|two|three|four|five|six|ten|fifteen|twenty|thirty)\\s*)?" +
            "(minutes?|mins?|hours?|hrs?|hr|h)\\b|\\bhourly\\b"
    )
    private val EVERY_DAY = Regex(
        "$OPT\\b(?:(?:every|each)\\s*(day|night|morning|afternoon|evening)|daily|everyday|nightly)\\b"
    )
    private val EVERY_WEEKDAY = Regex("$OPT\\b(?:(?:every|each|on)\\s+weekdays?|weekdays)\\b")
    private val EVERY_NAMED_DAY = Regex(
        "$OPT\\b(?:every|each)\\s+(?=(?:sun|mon|tues?|wed(?:nes)?|thu(?:rs)?|fri|sat(?:ur)?)(?:day)?s?\\.?\\b)"
    )
    private val EVERY_WEEK = Regex("$OPT\\b(?:(?:every|each|once\\s+a)\\s+week|weekly)\\b")
    private val EVERY_MONTH = Regex("$OPT\\b(?:(?:every|each|once\\s+a)\\s+month|monthly)\\b")
    private val EVERY_YEAR = Regex("$OPT\\b(?:(?:every|each|once\\s+a)\\s+year|yearly|annually)\\b")

    // "on the 1st", "the 15th", "on 3rd" (needs the ordinal so "on 5" stays a time)
    private val DAY_OF_MONTH = Regex("$OPT\\b(?:on\\s+(?:the\\s+)?|the\\s+)(\\d{1,2})(?:st|nd|rd|th)\\b(?!\\s+of\\b)")

    private val AI_TOOL = Regex(
        "$OPT\\b(?:claude|chat\\s?gpt|gpt[-\\s]?\\d*[a-z]*|openai|gemini|copilot|cursor|perplexity|midjourney|" +
            "grok|codex|windsurf|deepseek|mistral|llm|ai|opus|sonnet|haiku|anthropic|bard|poe)\\b"
    )
    private val AI_LIMIT_WORD = Regex(
        "$OPT\\b(?:limits?|quotas?|credits?|usage|caps?|tokens?|rate[-\\s]?limit(?:ed)?|messages?|resets?|refills?|cooldown)\\b"
    )
    private val EXAM_WORD = Regex(
        "$OPT\\b(?:exams?|midterms?|mid[-\\s]?sems?|end[-\\s]?sems?|finals|quiz(?:zes)?|viva|tests?|boards|olympiad|entrance)\\b"
    )
    private val BILL_WORD = Regex(
        "$OPT\\b(?:bills?|rent|emi|fees?|subscriptions?|renews?|renewal|recharge|premium|insurance|pay|payment|invoice|" +
            "electricity|wifi|broadband|netflix|spotify|prime|tax(?:es)?)\\b"
    )
    private val MEDICINE_WORD = Regex(
        "$OPT\\b(?:medicines?|meds|medication|tablets?|pills?|capsules?|insulin|syrup|inhaler|dose|dawai|dawa|dava|goli)\\b|दवाई|दवा|गोली"
    )
    private val BIRTHDAY_WORD = Regex("$OPT\\b(?:birthday|b'?day|bday|janmdin|anniversary)\\b|जन्मदिन|सालगिरह")
    private val EXPIRY_WORD = Regex(
        "$OPT\\b(?:expir(?:y|es|ing|ed)|valid\\s+(?:till|until|upto)|warranty|passport|licen[cs]e|visa|insurance\\s+(?:ends|expires))\\b"
    )
    private val DEADLINE_WORD = Regex(
        "$OPT\\b(?:deadline|due|submit|submission|assignment|homework|hand\\s+in|turn\\s+in|last\\s+date|apply|" +
            "application|register|registration|project|report|thesis|essay|lab\\s+record)\\b"
    )

    /** Picks the workflow kind from the words in [text]. Recurring chores become habits. */
    fun inferKind(text: String, recurring: Boolean = false): ReminderKind = when {
        AI_TOOL.containsMatchIn(text) && AI_LIMIT_WORD.containsMatchIn(text) -> ReminderKind.AI_LIMIT
        MEDICINE_WORD.containsMatchIn(text) -> ReminderKind.MEDICINE
        BIRTHDAY_WORD.containsMatchIn(text) -> ReminderKind.BIRTHDAY
        EXPIRY_WORD.containsMatchIn(text) -> ReminderKind.EXPIRY
        EXAM_WORD.containsMatchIn(text) -> ReminderKind.EXAM
        BILL_WORD.containsMatchIn(text) -> ReminderKind.BILL
        DEADLINE_WORD.containsMatchIn(text) && !recurring -> ReminderKind.DEADLINE
        recurring -> ReminderKind.HABIT
        else -> ReminderKind.TASK
    }

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

        // ---- workflow: heads-up overrides, repeats, kind ----------------------------------------
        // Read the kind before time words are stripped, so "exam on friday" still says exam.
        val kindSource = text
        var customLeads: MutableList<Int>? = null
        while (true) {
            val m = consume(LEAD_BEFORE) ?: break
            val minutes = if (m.groupValues[3].isNotEmpty()) {
                if (m.groupValues[3].equals("week", ignoreCase = true)) 7 * 24 * 60 else 24 * 60
            } else {
                (amount(m.groupValues[1]) * unitMillis(m.groupValues[2]) / MINUTE).toInt()
            }
            if (minutes > 0) (customLeads ?: mutableListOf<Int>().also { customLeads = it }).add(minutes)
        }

        var recurrenceRule: RecurrenceRule? = null
        var recurrenceMinutes: Int? = null
        var recurringImpliedHour: Int? = null
        consume(EVERY_INTERVAL)?.let { m ->
            val n = if (m.groupValues[1].isEmpty()) 1.0 else amount(m.groupValues[1])
            val unit = m.groupValues[2].ifEmpty { "h" }
            recurrenceMinutes = (n * unitMillis(unit) / MINUTE).toInt().coerceAtLeast(1)
        }
        if (recurrenceMinutes == null) {
            when {
                consume(EVERY_WEEKDAY) != null -> recurrenceRule = RecurrenceRule.WEEKDAYS
                consume(EVERY_DAY)?.also { m ->
                    recurringImpliedHour = when (m.groupValues[1].lowercase()) {
                        "morning" -> 9
                        "afternoon" -> 15
                        "evening" -> 18
                        "night" -> 20
                        else -> null
                    }
                } != null -> recurrenceRule = RecurrenceRule.DAILY
                EVERY_NAMED_DAY.find(text) != null -> {
                    // "every monday": keep the weekday for the day parser below, as "on monday".
                    val m = EVERY_NAMED_DAY.find(text)!!
                    text = text.replaceRange(m.range, " on ")
                    recurrenceRule = RecurrenceRule.WEEKLY
                }
                consume(EVERY_WEEK) != null -> recurrenceRule = RecurrenceRule.WEEKLY
                consume(EVERY_MONTH) != null -> recurrenceRule = RecurrenceRule.MONTHLY
                consume(EVERY_YEAR) != null -> recurrenceRule = RecurrenceRule.YEARLY
            }
        }
        val kind = inferKind(kindSource, recurrenceRule != null || recurrenceMinutes != null)
        if (kind.repeatsYearly && recurrenceRule == null && recurrenceMinutes == null) {
            recurrenceRule = RecurrenceRule.YEARLY
        }
        val leads = customLeads?.let { list -> list.filter { it > 0 }.distinct().sortedDescending() } ?: kind.defaultLeadMinutes

        fun result(title: String, trigger: Long?) = ParsedReminder(
            title = title,
            triggerAtMillis = trigger,
            priorityHint = priority,
            kind = kind,
            leadMinutes = leads,
            recurrenceRule = recurrenceRule,
            recurrenceMinutes = recurrenceMinutes
        )

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
            return result(cleanTitle(text, original), nowMillis + offsetMillis!!)
        }

        // ---- day -------------------------------------------------------------------------------
        var dayOffset: Int? = null
        var absoluteDate: Triple<Int, Int, Boolean>? = null // month(0-11), day, yearExplicitlyRollable
        var weekday: Pair<Int, Boolean>? = null // calendar weekday, "next"
        var impliedHour: Int? = recurringImpliedHour
        var dayOfMonth: Int? = null

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
        if (dayOffset == null && weekday == null && absoluteDate == null) {
            consume(DAY_OF_MONTH)?.let { m ->
                m.groupValues[1].toInt().takeIf { it in 1..31 }?.let { dayOfMonth = it }
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

        val hasDay = dayOffset != null || weekday != null || absoluteDate != null || dayOfMonth != null
        // "today" alone has no sensible default hour (9 AM may already be gone), so ask instead.
        if ((!hasDay || dayOffset == 0) && hour == null) {
            // "drink water every 2 hours" with no time starts one interval from now.
            val intervalStart = recurrenceMinutes?.let { nowMillis + it * MINUTE }
            return result(cleanTitle(text, original), if (dayOffset == null) intervalStart else null)
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
                applyClock()
                fun placeDay() = cal.set(
                    Calendar.DAY_OF_MONTH,
                    day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                )
                placeDay()
                if (cal.timeInMillis <= nowMillis) {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.YEAR, 1)
                    placeDay()
                }
                // "feb 29" waits for the next leap year instead of landing on the 28th.
                if (month == Calendar.FEBRUARY && day == 29) {
                    while (cal.getActualMaximum(Calendar.DAY_OF_MONTH) < 29) {
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        cal.add(Calendar.YEAR, 1)
                    }
                    cal.set(Calendar.DAY_OF_MONTH, 29)
                }
            }
            dayOfMonth != null -> {
                // The next time the month reaches that day; short months use their last day.
                cal.set(Calendar.DAY_OF_MONTH, 1)
                applyClock()
                fun placeDay() = cal.set(
                    Calendar.DAY_OF_MONTH,
                    dayOfMonth!!.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                )
                placeDay()
                if (cal.timeInMillis <= nowMillis) {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, 1)
                    placeDay()
                }
            }
            else -> {
                applyClock()
                // Time only: the next time the clock reads that.
                if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return result(cleanTitle(text, original), cal.timeInMillis)
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
