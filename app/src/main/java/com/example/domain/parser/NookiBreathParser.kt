package com.example.domain.parser

import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import java.util.Calendar

/**
 * Semantic classification of a single-breath user input in Nooki.
 * Pure Kotlin (zero Android imports) so it can be unit-tested directly on the JVM.
 */
enum class NookiCaptureKind(val plainLabel: String) {
    REMINDER("Reminder"),
    MEDICINE("Medicine (keeps asking until confirmed)"),
    RECURRING_HABIT("Repeating"),
    IMPORTANT_DATE("Date that matters (advance warnings)"),
    HELD_MEMORY("Saved thing Nooki holds for you"),
    MONEY_OWED_TO_ME("Money owed to you"),
    MONEY_I_OWE("Money you owe"),
    ITEM_LENT("Thing lent out"),
    QUESTION("Question for Nooki")
}

/**
 * Real-time structured interpretation shown to the user *as they type* before saving.
 */
data class ParsedBreathCapture(
    val rawInput: String,
    val cleanTitle: String,
    val kind: NookiCaptureKind,
    val triggerAtMillis: Long,
    val timeExplanation: String,
    val recurrenceMinutes: Int? = null,
    val recurrenceExplanation: String? = null,
    val advanceWarningDays: List<Int> = emptyList(),
    val personName: String? = null,
    val amountInr: Int? = null,
    val isCriticalExactAlarm: Boolean = false,
    val instantAnswerFromMemory: String? = null,
    val mappedCategory: ReminderCategory = ReminderCategory.DESK,
    val mappedPriority: ReminderPriority = ReminderPriority.MEDIUM
) {
    /**
     * Human-friendly sentence explaining what Nooki understood, using plain language.
     */
    fun toPlainSummary(): String {
        if (kind == NookiCaptureKind.QUESTION) {
            return instantAnswerFromMemory
                ?: "Asking Nooki's memory... Type a keyword like 'passport', 'key', 'car', or a person's name."
        }
        return buildString {
            append(kind.plainLabel)
            append(": \"")
            append(cleanTitle)
            append("\"")
            if (amountInr != null && personName != null) {
                append(" • ₹$amountInr ($personName)")
            } else if (personName != null) {
                append(" • with $personName")
            }
            if (kind != NookiCaptureKind.HELD_MEMORY &&
                kind != NookiCaptureKind.MONEY_OWED_TO_ME &&
                kind != NookiCaptureKind.MONEY_I_OWE &&
                kind != NookiCaptureKind.ITEM_LENT
            ) {
                append(" • ")
                append(timeExplanation)
            }
            if (recurrenceExplanation != null) {
                append(" • ")
                append(recurrenceExplanation)
            }
            if (advanceWarningDays.isNotEmpty()) {
                append(" • Warns ")
                append(advanceWarningDays.joinToString(", ") { "${it}d" })
                append(" before")
            }
        }
    }
}

/**
 * Pure Kotlin Natural Language Parser for "Tell Nooki: capture in one breath".
 * Understands English, Hinglish ("kal subah 9 baje doctor", "har somvar gym"),
 * Hindi ("कल शाम 6 बजे दवाई"), saved things ("passport is in the blue bag"),
 * money & lending ("Rahul owes me 500", "lent charger to Aman"), and questions ("where is my passport").
 */
object NookiBreathParser {

    private val hindiDigitMap = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    fun normalizeDigits(input: String): String =
        buildString(input.length) {
            for (ch in input) {
                append(hindiDigitMap[ch] ?: ch)
            }
        }

    fun parse(
        rawInput: String,
        nowMillis: Long = System.currentTimeMillis(),
        existingItems: List<ReminderEntity> = emptyList()
    ): ParsedBreathCapture {
        val trimmed = normalizeDigits(rawInput).trim()
        if (trimmed.isEmpty()) {
            val eveningMillis = computeEveningWrapMillis(nowMillis)
            return ParsedBreathCapture(
                rawInput = "",
                cleanTitle = "",
                kind = NookiCaptureKind.REMINDER,
                triggerAtMillis = eveningMillis,
                timeExplanation = "Tonight at 8:00 PM (Evening wrap)"
            )
        }

        val lower = trimmed.lowercase()

        // 1. Check if the person is asking a question ("where is my passport", "kahan hai", "who owes")
        if (isQuestionInput(lower)) {
            val answer = answerQuestionFromMemory(lower, existingItems)
            return ParsedBreathCapture(
                rawInput = trimmed,
                cleanTitle = trimmed,
                kind = NookiCaptureKind.QUESTION,
                triggerAtMillis = nowMillis,
                timeExplanation = "Instant lookup",
                instantAnswerFromMemory = answer
            )
        }

        // 2. Check Money & Lending ("Rahul owes me 500", "roommate owes ₹300", "I owe Priya 400", "lent charger to Aman")
        parseMoneyOrLending(trimmed, lower, nowMillis)?.let { return it }

        // 3. Check "Things to hold" ("passport is in the blue bag", "car parked at B2, pillar 14", "spare key is with Sharma aunty")
        parseHeldMemory(trimmed, lower, nowMillis)?.let { return it }

        // 4. Parse Time, Recurrence, Medicine, and Dates that matter across English, Hinglish, and Hindi
        return parseTemporalReminder(trimmed, lower, nowMillis)
    }

    private fun isQuestionInput(lower: String): Boolean {
        return lower.startsWith("where is ") ||
            lower.startsWith("where's ") ||
            lower.startsWith("where did i ") ||
            lower.startsWith("who owes") ||
            lower.startsWith("how much ") ||
            lower.contains("kahan hai") ||
            lower.contains("kaha hai") ||
            lower.contains("कहाँ है") ||
            lower.contains("कहां है") ||
            lower.endsWith("?")
    }

    private fun answerQuestionFromMemory(
        queryLower: String,
        existingItems: List<ReminderEntity>
    ): String {
        val stopWords = setOf(
            "where", "is", "my", "the", "did", "i", "put", "keep", "kept",
            "kahan", "kaha", "hai", "mera", "meri", "कहाँ", "कहां", "है", "मेरा", "मेरी"
        )
        val keywords = queryLower
            .replace("?", "")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }

        if (queryLower.contains("who owes") || queryLower.contains("money")) {
            val moneyItems = existingItems.filter {
                it.category == ReminderCategory.ERRAND && it.notes.contains("MONEY_")
            }
            if (moneyItems.isNotEmpty()) {
                return "Nooki remembers: " + moneyItems.joinToString("; ") { it.title }
            }
        }

        if (keywords.isNotEmpty()) {
            val match = existingItems.firstOrNull { item ->
                val searchable = "${item.title} ${item.notes} ${item.rawInput}".lowercase()
                keywords.any { kw -> searchable.contains(kw) }
            }
            if (match != null) {
                return "Nooki found it: \"${match.title}\""
            }
        }
        return "Nooki doesn't have a match for that yet. Save it like: \"Passport is in the blue bag\"."
    }

    private fun parseMoneyOrLending(
        original: String,
        lower: String,
        nowMillis: Long
    ): ParsedBreathCapture? {
        // Pattern A: "<Person> owes me <amount>" or "<Person> owes ₹<amount>"
        val owesMeRegex = Regex("""^([a-zA-Z\u0900-\u097F\s]+?)\s+owes\s+(?:me\s+)?₹?\s*(\d+)(.*)$""", RegexOption.IGNORE_CASE)
        owesMeRegex.find(original)?.let { match ->
            val person = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val amount = match.groupValues[2].toIntOrNull() ?: 0
            val suffix = match.groupValues[3].trim()
            val cleanTitle = buildString {
                append("$person owes you ₹$amount")
                if (suffix.isNotEmpty()) append(" $suffix")
            }
            return ParsedBreathCapture(
                rawInput = original,
                cleanTitle = cleanTitle,
                kind = NookiCaptureKind.MONEY_OWED_TO_ME,
                triggerAtMillis = computeFollowUpDaysMillis(nowMillis, days = 3),
                timeExplanation = "Kept in 'What did I forget?' (nudges in 3 days)",
                personName = person,
                amountInr = amount,
                mappedCategory = ReminderCategory.ERRAND,
                mappedPriority = ReminderPriority.HIGH
            )
        }

        // Pattern B: "I owe <Person> <amount>"
        val iOweRegex = Regex("""^i\s+owe\s+([a-zA-Z\u0900-\u097F\s]+?)\s+₹?\s*(\d+)(.*)$""", RegexOption.IGNORE_CASE)
        iOweRegex.find(original)?.let { match ->
            val person = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val amount = match.groupValues[2].toIntOrNull() ?: 0
            val cleanTitle = "You owe $person ₹$amount"
            return ParsedBreathCapture(
                rawInput = original,
                cleanTitle = cleanTitle,
                kind = NookiCaptureKind.MONEY_I_OWE,
                triggerAtMillis = computeFollowUpDaysMillis(nowMillis, days = 2),
                timeExplanation = "Kept in 'What did I forget?' (nudges in 2 days)",
                personName = person,
                amountInr = amount,
                mappedCategory = ReminderCategory.ERRAND,
                mappedPriority = ReminderPriority.HIGH
            )
        }

        // Pattern C: "lent <item> to <Person>"
        val lentRegex = Regex("""^lent\s+(.+?)\s+to\s+([a-zA-Z\u0900-\u097F\s]+)$""", RegexOption.IGNORE_CASE)
        lentRegex.find(original)?.let { match ->
            val item = match.groupValues[1].trim()
            val person = match.groupValues[2].trim().replaceFirstChar { it.uppercase() }
            val amount = Regex("""₹?\s*(\d+)""").find(item)?.groupValues?.get(1)?.toIntOrNull()
            return ParsedBreathCapture(
                rawInput = original,
                cleanTitle = "Lent $item to $person",
                kind = if (amount != null) NookiCaptureKind.MONEY_OWED_TO_ME else NookiCaptureKind.ITEM_LENT,
                triggerAtMillis = computeFollowUpDaysMillis(nowMillis, days = 5),
                timeExplanation = "Kept in 'What did I forget?' until returned",
                personName = person,
                amountInr = amount,
                mappedCategory = ReminderCategory.ERRAND,
                mappedPriority = ReminderPriority.MEDIUM
            )
        }

        return null
    }

    private fun parseHeldMemory(
        original: String,
        lower: String,
        nowMillis: Long
    ): ParsedBreathCapture? {
        val isLocationOrHolding = lower.contains(" is in ") ||
            lower.contains(" is with ") ||
            lower.contains(" parked at ") ||
            lower.contains(" kept in ") ||
            lower.contains(" kept at ") ||
            lower.contains(" रखा है") ||
            lower.contains(" में है") ||
            lower.contains(" के पास है")

        if (!isLocationOrHolding) return null

        return ParsedBreathCapture(
            rawInput = original,
            cleanTitle = original.replaceFirstChar { it.uppercase() },
            kind = NookiCaptureKind.HELD_MEMORY,
            triggerAtMillis = nowMillis + 365L * 24 * 60 * 60_000L, // stays in Nooki's memory bank
            timeExplanation = "Saved safely in 'Nooki holds for you' — ask anytime",
            mappedCategory = ReminderCategory.CUSTOM,
            mappedPriority = ReminderPriority.LOW
        )
    }

    private fun parseTemporalReminder(
        original: String,
        lower: String,
        nowMillis: Long
    ): ParsedBreathCapture {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        // Detect Medicine (English, Hinglish, Hindi Devanagari)
        val isMedicine = lower.contains("medicine") ||
            lower.contains("tablet") ||
            lower.contains("pill") ||
            lower.contains("insulin") ||
            lower.contains("dawai") ||
            lower.contains("dawa") ||
            lower.contains("goli") ||
            lower.contains("दवाई") ||
            lower.contains("दवा") ||
            lower.contains("गोली")

        // Detect Dates that matter (Bills, Rent, EMI, Fee, Birthday, Passport/Insurance Expiry, Exam)
        val isBillOrFee = lower.contains("rent") ||
            lower.contains("bill") ||
            lower.contains("emi") ||
            lower.contains("fee") ||
            lower.contains("kiraya") ||
            lower.contains("किराया") ||
            lower.contains("बिल")
        val isBirthday = lower.contains("birthday") || lower.contains("janamdin") || lower.contains("जन्मदिन")
        val isExpiryOrExam = lower.contains("expiry") ||
            lower.contains("expires") ||
            lower.contains("insurance") ||
            lower.contains("warranty") ||
            lower.contains("exam") ||
            lower.contains("परीक्षा")

        val warningDays = when {
            isExpiryOrExam -> listOf(30, 7, 1)
            isBirthday -> listOf(7, 1)
            isBillOrFee -> listOf(3, 1)
            else -> emptyList()
        }

        // Detect Recurrence ("every month", "har mahine", "har somvar", "every monday", "daily", "roz")
        var recurrenceMins: Int? = null
        var recurrenceDesc: String? = null

        val weekdayMap = mapOf(
            "monday" to Calendar.MONDAY, "somvar" to Calendar.MONDAY, "सोमवार" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY, "mangalvar" to Calendar.TUESDAY, "मंगलवार" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY, "budhvar" to Calendar.WEDNESDAY, "बुधवार" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY, "guruvar" to Calendar.THURSDAY, "गुरुवार" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY, "shukravar" to Calendar.FRIDAY, "शुक्रवार" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY, "shanivar" to Calendar.SATURDAY, "शनिवार" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY, "ravivar" to Calendar.SUNDAY, "रविवार" to Calendar.SUNDAY
        )

        var targetDayOfWeek: Int? = null
        for ((word, dayConst) in weekdayMap) {
            if (lower.contains("har $word") || lower.contains("every $word") || lower.contains("हर $word")) {
                recurrenceMins = 7 * 24 * 60
                recurrenceDesc = "Repeats every ${word.replaceFirstChar { it.uppercase() }}"
                targetDayOfWeek = dayConst
                break
            } else if (lower.contains(word)) {
                targetDayOfWeek = dayConst
            }
        }

        if (recurrenceMins == null) {
            when {
                lower.contains("every month") || lower.contains("monthly") || lower.contains("har mahine") || lower.contains("हर महीने") -> {
                    recurrenceMins = 30 * 24 * 60
                    recurrenceDesc = "Repeats monthly (survives missed months)"
                }
                lower.contains("every year") || lower.contains("yearly") || isBirthday -> {
                    recurrenceMins = 365 * 24 * 60
                    recurrenceDesc = "Repeats yearly"
                }
                lower.contains("every day") || lower.contains("daily") || lower.contains("har roz") || lower.contains("roz ") || lower.contains("रोज़") || lower.contains("रोज") -> {
                    recurrenceMins = 24 * 60
                    recurrenceDesc = "Repeats every day"
                }
            }
        }

        // Day offset detection: "kal" / "कल" / "tomorrow", "parso" / "परसों", "aaj" / "आज" / "today"
        var dayOffset = 0
        var hasExplicitDay = false
        when {
            lower.contains("parso") || lower.contains("परसों") || lower.contains("day after tomorrow") -> {
                dayOffset = 2
                hasExplicitDay = true
            }
            lower.contains("kal") || lower.contains("कल") || lower.contains("tomorrow") -> {
                dayOffset = 1
                hasExplicitDay = true
            }
            lower.contains("aaj") || lower.contains("आज") || lower.contains("today") || lower.contains("tonight") -> {
                dayOffset = 0
                hasExplicitDay = true
            }
        }

        // Relative "in X min / hours / days / weeks"
        val relMinutesMatch = Regex("""in\s+(\d+)\s*(?:min|mins|minute|minutes|m)\b""").find(lower)
        val relHoursMatch = Regex("""in\s+(\d+)\s*(?:hr|hrs|hour|hours|h)\b""").find(lower)
        val relDaysMatch = Regex("""in\s+(\d+)\s*(?:day|days|d)\b""").find(lower)
        val relWeeksMatch = Regex("""in\s+(\d+)\s*(?:week|weeks|w)\b""").find(lower)

        if (relMinutesMatch != null) {
            val mins = relMinutesMatch.groupValues[1].toIntOrNull() ?: 10
            val trigger = nowMillis + mins * 60_000L
            return buildTemporalCapture(
                original = original,
                cleanTitle = cleanTaskTitle(original),
                triggerAtMillis = trigger,
                timeExplanation = "In $mins min",
                isMedicine = isMedicine,
                warningDays = warningDays,
                recurrenceMins = recurrenceMins,
                recurrenceDesc = recurrenceDesc
            )
        }
        if (relHoursMatch != null) {
            val hrs = relHoursMatch.groupValues[1].toIntOrNull() ?: 1
            val trigger = nowMillis + hrs * 3_600_000L
            return buildTemporalCapture(
                original = original,
                cleanTitle = cleanTaskTitle(original),
                triggerAtMillis = trigger,
                timeExplanation = "In $hrs hr(s)",
                isMedicine = isMedicine,
                warningDays = warningDays,
                recurrenceMins = recurrenceMins,
                recurrenceDesc = recurrenceDesc
            )
        }
        if (relDaysMatch != null || relWeeksMatch != null) {
            val days = (relDaysMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0) +
                ((relWeeksMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0) * 7)
            val trigger = computeFollowUpDaysMillis(nowMillis, days.coerceAtLeast(1))
            return buildTemporalCapture(
                original = original,
                cleanTitle = cleanTaskTitle(original),
                triggerAtMillis = trigger,
                timeExplanation = "In $days days (9:00 AM)",
                isMedicine = isMedicine,
                warningDays = warningDays,
                recurrenceMins = recurrenceMins,
                recurrenceDesc = recurrenceDesc
            )
        }

        // Day-of-month pattern: "on the 1st", "on the 5th"
        val dayOfMonthMatch = Regex("""on\s+the\s+(\d{1,2})(?:st|nd|rd|th)\b""").find(lower)
        if (dayOfMonthMatch != null) {
            val dom = (dayOfMonthMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 28)
            val targetCal = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, dom)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= nowMillis) {
                    add(Calendar.MONTH, 1)
                }
            }
            return buildTemporalCapture(
                original = original,
                cleanTitle = cleanTaskTitle(original),
                triggerAtMillis = targetCal.timeInMillis,
                timeExplanation = "Day $dom of the month at 9:00 AM",
                isMedicine = isMedicine,
                warningDays = warningDays,
                recurrenceMins = recurrenceMins ?: (30 * 24 * 60),
                recurrenceDesc = recurrenceDesc ?: "Repeats on the ${dom}th every month"
            )
        }

        // Clock time detection:
        // - Hindi/Hinglish: "9 baje", "9:30 baje", "6 बजे", "subah 9 baje", "shaam 6 baje"
        // - English: "at 9am", "at 6:30 pm", "9:00 am"
        val isMorning = lower.contains("subah") || lower.contains("सुबह") || lower.contains("सवेरे") || lower.contains("morning") || lower.contains("am")
        val isEveningOrNight = lower.contains("shaam") || lower.contains("sham") || lower.contains("शाम") ||
            lower.contains("raat") || lower.contains("रात") ||
            lower.contains("dopahar") || lower.contains("दोपहर") ||
            lower.contains("evening") || lower.contains("night") || lower.contains("pm")

        val bajeMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(?:baje|बजे)""").find(lower)
        val amPmMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""").find(lower)
        val atHourMatch = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\b""").find(lower)

        var parsedHour24: Int? = null
        var parsedMinute = 0

        when {
            bajeMatch != null -> {
                val rawH = bajeMatch.groupValues[1].toIntOrNull() ?: 9
                parsedMinute = bajeMatch.groupValues[2].toIntOrNull() ?: 0
                parsedHour24 = when {
                    isEveningOrNight && rawH < 12 -> rawH + 12
                    isMorning && rawH == 12 -> 0
                    !isMorning && !isEveningOrNight && rawH in 1..7 -> rawH + 12
                    else -> rawH
                }
            }
            amPmMatch != null -> {
                val rawH = amPmMatch.groupValues[1].toIntOrNull() ?: 9
                parsedMinute = amPmMatch.groupValues[2].toIntOrNull() ?: 0
                val meridiem = amPmMatch.groupValues[3]
                parsedHour24 = when {
                    meridiem == "pm" && rawH < 12 -> rawH + 12
                    meridiem == "am" && rawH == 12 -> 0
                    else -> rawH
                }
            }
            atHourMatch != null -> {
                val rawH = atHourMatch.groupValues[1].toIntOrNull() ?: 9
                parsedMinute = atHourMatch.groupValues[2].toIntOrNull() ?: 0
                parsedHour24 = if (isEveningOrNight && rawH < 12) rawH + 12 else rawH
            }
            isMorning -> {
                parsedHour24 = 9
                parsedMinute = 0
            }
            isEveningOrNight -> {
                parsedHour24 = 18
                parsedMinute = 0
            }
        }

        val targetCal = (cal.clone() as Calendar)
        if (targetDayOfWeek != null && !hasExplicitDay) {
            while (targetCal.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else if (dayOffset > 0) {
            targetCal.add(Calendar.DAY_OF_YEAR, dayOffset)
        }

        val timeExplanation: String
        if (parsedHour24 != null) {
            targetCal.set(Calendar.HOUR_OF_DAY, parsedHour24.coerceIn(0, 23))
            targetCal.set(Calendar.MINUTE, parsedMinute.coerceIn(0, 59))
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
            if (targetCal.timeInMillis <= nowMillis && dayOffset == 0 && targetDayOfWeek == null) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
                dayOffset = 1
            }
            val dayLabel = when {
                dayOffset == 1 -> "Tomorrow"
                dayOffset == 2 -> "Day after tomorrow"
                targetDayOfWeek != null -> "Next scheduled day"
                else -> "Today"
            }
            val displayH = if (parsedHour24 % 12 == 0) 12 else parsedHour24 % 12
            val amPm = if (parsedHour24 >= 12) "PM" else "AM"
            val minStr = parsedMinute.toString().padStart(2, '0')
            timeExplanation = "$dayLabel at $displayH:$minStr $amPm"
        } else {
            // Spec rule: "If there is no time, keep the item anyway and bring it up in the evening. Never lose a thought because a field was empty."
            targetCal.timeInMillis = computeEveningWrapMillis(nowMillis + dayOffset * 24 * 3_600_000L)
            timeExplanation = if (dayOffset == 1) {
                "Tomorrow evening wrap (8:00 PM)"
            } else {
                "No time given — Nooki will bring it up in tonight's 8:00 PM Evening wrap"
            }
        }

        return buildTemporalCapture(
            original = original,
            cleanTitle = cleanTaskTitle(original),
            triggerAtMillis = targetCal.timeInMillis,
            timeExplanation = timeExplanation,
            isMedicine = isMedicine,
            warningDays = warningDays,
            recurrenceMins = recurrenceMins,
            recurrenceDesc = recurrenceDesc
        )
    }

    private fun buildTemporalCapture(
        original: String,
        cleanTitle: String,
        triggerAtMillis: Long,
        timeExplanation: String,
        isMedicine: Boolean,
        warningDays: List<Int>,
        recurrenceMins: Int?,
        recurrenceDesc: String?
    ): ParsedBreathCapture {
        val kind = when {
            isMedicine -> NookiCaptureKind.MEDICINE
            warningDays.isNotEmpty() -> NookiCaptureKind.IMPORTANT_DATE
            recurrenceMins != null -> NookiCaptureKind.RECURRING_HABIT
            else -> NookiCaptureKind.REMINDER
        }
        val priority = when {
            isMedicine -> ReminderPriority.URGENT
            warningDays.isNotEmpty() -> ReminderPriority.HIGH
            else -> ReminderPriority.MEDIUM
        }
        val category = when {
            isMedicine -> ReminderCategory.HYDRATION
            warningDays.isNotEmpty() -> ReminderCategory.FOCUS
            recurrenceMins != null -> ReminderCategory.STRETCH
            else -> ReminderCategory.DESK
        }
        return ParsedBreathCapture(
            rawInput = original,
            cleanTitle = cleanTitle.ifBlank { original },
            kind = kind,
            triggerAtMillis = triggerAtMillis,
            timeExplanation = timeExplanation,
            recurrenceMinutes = recurrenceMins,
            recurrenceExplanation = recurrenceDesc,
            advanceWarningDays = warningDays,
            isCriticalExactAlarm = isMedicine || priority == ReminderPriority.URGENT,
            mappedCategory = category,
            mappedPriority = priority
        )
    }

    /**
     * Strips conversational temporal tokens (English, Hinglish, Hindi) so the knot title is clean.
     */
    fun cleanTaskTitle(input: String): String {
        var working = normalizeDigits(input).trim()
        val patternsToRemove = listOf(
            Regex("""^(?:remind\s+me\s+to|remind\s+me|tell\s+nooki\s+to)\s+""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:kal|aaj|parso|subah|shaam|sham|raat|dopahar)\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:कल|आज|परसों|सुबह|शाम|रात|दोपहर|हर\s+सोमवार|हर\s+महीने|हर\s+रोज़|हर\s+रोज)"""),
            Regex("""\bhar\s+(?:somvar|mangalvar|budhvar|guruvar|shukravar|shanivar|ravivar|mahine|roz)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bevery\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|month|day|year)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bon\s+the\s+\d{1,2}(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b\d{1,2}(?::\d{2})?\s*(?:baje|बजे|am|pm)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bin\s+\d+\s*(?:min|mins|minute|minutes|hr|hrs|hour|hours|day|days|week|weeks)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:tomorrow|today|tonight|at\s+\d{1,2}(?::\d{2})?)\b""", RegexOption.IGNORE_CASE)
        )
        for (regex in patternsToRemove) {
            working = regex.replace(working, " ")
        }
        val squashed = working.replace(Regex("""\s+"""), " ").trim()
        return if (squashed.isEmpty()) {
            input.trim().replaceFirstChar { it.uppercase() }
        } else {
            squashed.replaceFirstChar { it.uppercase() }
        }
    }

    fun computeEveningWrapMillis(referenceMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceMillis
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= referenceMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun computeFollowUpDaysMillis(referenceMillis: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceMillis
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
