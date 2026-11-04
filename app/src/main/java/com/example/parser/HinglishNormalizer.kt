package com.example.parser

/**
 * Rewrites the time words of Hindi and Hinglish into the English [ReminderTextParser] reads, leaving
 * everything else alone: "kal subah 9 baje doctor" becomes "tomorrow at 9am doctor". Voice input in
 * Hindi arrives in Devanagari, so those words are mapped first. This keeps one set of date rules for
 * all three ways of speaking.
 */
object HinglishNormalizer {

    private const val AFTER = "\\s*(?:baad|bad|mein|me|में)(?=[\\s,.!?।]|$)"
    private const val PART = "subah|savere|sawere|dopahar|dupahar|shaam|sham|raat"
    private const val ENGLISH_DAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val MONTHS =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    private val DEVANAGARI_DIGITS = "०१२३४५६७८९"

    /** Whole words only, so that a title like "कल्पना" is never touched. */
    private val DEVANAGARI_WORDS = mapOf(
        "कल" to "kal", "आज" to "aaj", "परसों" to "parso", "सुबह" to "subah", "सवेरे" to "savere",
        "दोपहर" to "dopahar", "शाम" to "shaam", "रात" to "raat", "बजे" to "baje", "हर" to "har",
        "रोज" to "roz", "रोज़" to "roz", "रोजाना" to "roz", "रोज़ाना" to "roz", "सोमवार" to "somvar",
        "मंगलवार" to "mangalvar", "बुधवार" to "budhvar", "गुरुवार" to "guruvar",
        "बृहस्पतिवार" to "guruvar", "शुक्रवार" to "shukravar", "शनिवार" to "shanivar",
        "रविवार" to "ravivar", "इतवार" to "ravivar", "मिनट" to "minute", "घंटे" to "ghante",
        "घंटा" to "ghante", "दिन" to "din", "हफ्ते" to "hafte", "हफ़्ते" to "hafte",
        "महीने" to "mahine", "साल" to "saal", "बाद" to "baad", "अगले" to "agle", "अगली" to "agle",
        "आधे" to "aadhe", "सवा" to "sawa", "साढ़े" to "sadhe", "पौने" to "paune", "डेढ़" to "dedh",
        "ढाई" to "dhai", "तारीख" to "tarikh", "तारीख़" to "tarikh", "जनवरी" to "january",
        "फरवरी" to "february", "मार्च" to "march", "अप्रैल" to "april", "मई" to "may",
        "जून" to "june", "जुलाई" to "july", "अगस्त" to "august", "सितंबर" to "september",
        "अक्टूबर" to "october", "नवंबर" to "november", "दिसंबर" to "december"
    )

    private val WEEKDAYS = listOf(
        "s[o]m[vw]a+r|sombar" to "monday",
        "mangal[vw]a+r" to "tuesday",
        "budh[vw]a+r" to "wednesday",
        "guru[vw]a+r|veer[vw]a+r|brihaspati[vw]a+r" to "thursday",
        "shukra[vw]a+r" to "friday",
        "shani[vw]a+r" to "saturday",
        "ravi[vw]a+r|it[vw]a+r" to "sunday"
    )

    private val NUMBER_WORDS = mapOf("ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5)

    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val FILLER = ci(
        "\\b(?:mujhe\\s+)?yaad\\s+(?:dila(?:na|do|\\s+dena|\\s+do|\\s+dijiye)?|kara(?:na|\\s+dena|\\s+do)?)\\b"
    )
    private val NUMBER_BEFORE_UNIT = ci(
        "\\b(ek|do|teen|chaar|char|paanch|panch)\\s+(ghante|ghanta|din|hafte|hafta|minute|min)\\b"
    )
    private val HALF_HOUR = ci("\\baadhe\\s+ghante$AFTER")
    private val HOUR_AND_HALF = ci("\\bdedh\\s+ghante$AFTER")
    private val IN_HOURS = ci("\\b(\\d+)\\s*(?:ghante|ghanta|ghanton)$AFTER")
    private val IN_MINUTES = ci("\\b(\\d+)\\s*(?:minutes?|mins?|mint)$AFTER")
    private val IN_DAYS = ci("\\b(\\d+)\\s*din$AFTER")
    private val IN_WEEKS = ci("\\b(\\d+)\\s*haft[ae]$AFTER")

    private val QUARTER_PAST = ci("\\bsawa\\s+(\\d{1,2})\\b(?:\\s*baje\\b)?")
    private val HALF_PAST = ci("\\bsa+dhe\\s+(\\d{1,2})\\b(?:\\s*baje\\b)?")
    private val QUARTER_TO = ci("\\bpaune\\s+(\\d{1,2})\\b(?:\\s*baje\\b)?")
    private val DEDH_BAJE = ci("\\bdedh\\s+baje\\b")
    private val DHAI_BAJE = ci("\\bdhai\\s+baje\\b")

    private val EVERY_PART_AT = ci("\\bhar\\s+($PART)\\s+(?:(?:ko|ke)\\s+)?(\\d{1,2})(?::(\\d{2}))?(?:\\s*baje\\b)?")
    private val CLOCK = ci("\\b(?:($PART)\\s+(?:(?:ko|ke)\\s+)?)?(\\d{1,2})(?::(\\d{2}))?\\s*baje\\b(?:\\s+($PART)\\b)?")
    private val PART_THEN_NUMBER = ci("\\b($PART)\\s+(?:(?:ko|ke)\\s+)?(\\d{1,2})(?::(\\d{2}))?\\b(?!\\s*(?:din|ghant|min|haft|tarikh))")

    private val TONIGHT = ci("\\baaj\\s+raat\\b")
    private val DAY_AFTER = ci("\\bparso[n]?\\b")
    private val TOMORROW = ci("\\bkal\\b")
    private val TODAY = ci("\\baaj\\b")

    private val NEXT_WORD = ci("\\bagl[ea]\\s+($ENGLISH_DAYS)\\b")
    private val EVERY_NAMED_DAY = ci("\\bhar\\s+($ENGLISH_DAYS)\\b")
    private val EVERY_DAY = ci("\\bhar\\s+din\\b|\\broza?ana\\b|\\brojana\\b|\\broz\\b")
    private val EVERY_MORNING = ci("\\bhar\\s+(?:subah|savere|sawere)\\b")
    private val EVERY_EVENING = ci("\\bhar\\s+(?:shaam|sham)\\b")
    private val EVERY_NIGHT = ci("\\bhar\\s+raat\\b")
    private val EVERY_WEEK = ci("\\bhar\\s+haft[ae]\\b")
    private val EVERY_MONTH = ci("\\bhar\\s+mahin[ae]\\b")
    private val EVERY_YEAR = ci("\\bhar\\s+saal\\b")
    private val EVERY_N_HOURS = ci("\\bhar\\s+(\\d+)\\s*(?:ghante|ghanta)\\b")
    private val EVERY_HOUR = ci("\\bhar\\s+ghante\\b")
    private val EVERY_N_DAYS = ci("\\bhar\\s+(\\d+)\\s*din\\b")
    private val EVERY_N_MINUTES = ci("\\bhar\\s+(\\d+)\\s*(?:minutes?|mins?|mint)\\b")

    private val ON_DATE = ci("\\b(\\d{1,2})\\s+tarikh\\b(?:\\s+(?:ko|को)(?=[\\s,.!?।]|$))?")
    private val PARTS = listOf(
        ci("\\b(?:subah|savere|sawere)\\b") to "morning",
        ci("\\b(?:dopahar|dupahar)\\b") to "afternoon",
        ci("\\b(?:shaam|sham)\\b") to "evening",
        ci("\\braat\\b") to "night"
    )
    private val DANGLING_KO = ci("\\b($ENGLISH_DAYS|tomorrow|today|$MONTHS)\\s+(?:ko|को|tak|तक)(?=[\\s,.!?।]|$)")

    fun normalize(input: String): String {
        if (input.isBlank()) return input
        var text = translateDevanagari(input)

        text = FILLER.replace(text, " ")
        text = NUMBER_BEFORE_UNIT.replace(text) { m ->
            "${NUMBER_WORDS[m.groupValues[1].lowercase()]} ${m.groupValues[2]}"
        }

        text = HALF_HOUR.replace(text, "in half an hour")
        text = HOUR_AND_HALF.replace(text, "in 90 minutes")
        text = EVERY_N_HOURS.replace(text, "every $1 hours")
        text = EVERY_N_DAYS.replace(text, "every $1 days")
        text = EVERY_N_MINUTES.replace(text, "every $1 minutes")
        text = EVERY_HOUR.replace(text, "every hour")
        text = IN_HOURS.replace(text, "in $1 hours")
        text = IN_MINUTES.replace(text, "in $1 minutes")
        text = IN_DAYS.replace(text, "in $1 days")
        text = IN_WEEKS.replace(text, "in $1 weeks")

        text = QUARTER_PAST.replace(text, "$1:15 baje")
        text = HALF_PAST.replace(text, "$1:30 baje")
        text = QUARTER_TO.replace(text) { m ->
            val hour = m.groupValues[1].toInt()
            "${if (hour <= 1) 12 else hour - 1}:45 baje"
        }
        text = DEDH_BAJE.replace(text, "1:30 baje")
        text = DHAI_BAJE.replace(text, "2:30 baje")

        text = EVERY_PART_AT.replace(text) { m ->
            "every day " + clock(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
        }
        text = CLOCK.replace(text) { m ->
            clock(m.groupValues[1].ifEmpty { m.groupValues[4] }, m.groupValues[2].toInt(), m.groupValues[3])
        }

        // Weekdays come first so that "har somvar" and "agle somvar" can read them below.
        WEEKDAYS.forEach { (pattern, english) -> text = ci("\\b(?:$pattern)\\b").replace(text, english) }

        text = EVERY_MORNING.replace(text, "every morning")
        text = EVERY_EVENING.replace(text, "every evening")
        text = EVERY_NIGHT.replace(text, "every night")
        text = EVERY_DAY.replace(text, "every day")
        text = EVERY_WEEK.replace(text, "every week")
        text = EVERY_MONTH.replace(text, "every month")
        text = EVERY_YEAR.replace(text, "every year")
        text = EVERY_NAMED_DAY.replace(text, "every $1")
        text = NEXT_WORD.replace(text, "next $1")

        text = PART_THEN_NUMBER.replace(text) { m ->
            clock(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
        }

        text = TONIGHT.replace(text, "tonight")
        text = DAY_AFTER.replace(text, "day after tomorrow")
        text = TOMORROW.replace(text, "tomorrow")
        text = TODAY.replace(text, "today")
        text = ON_DATE.replace(text, "on the $1th")
        PARTS.forEach { (regex, english) -> text = regex.replace(text, english) }
        text = DANGLING_KO.replace(text, "$1")

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** "subah" and 9 become "at 9am". With no part of the day, the parser picks the likely one. */
    private fun clock(part: String, hour: Int, minutes: String): String {
        val mm = if (minutes.isEmpty()) "" else ":$minutes"
        return when (part.lowercase()) {
            "subah", "savere", "sawere" -> "at $hour${mm}am"
            "dopahar", "dupahar" -> if (hour == 11) "at 11${mm}am" else "at $hour${mm}pm"
            "shaam", "sham" -> "at $hour${mm}pm"
            "raat" -> when (hour) {
                12 -> if (minutes.isEmpty()) "at midnight" else "at 12${mm}am"
                in 1..4 -> "at $hour${mm}am"
                else -> "at $hour${mm}pm"
            }
            else -> "at $hour$mm"
        }
    }

    private fun translateDevanagari(input: String): String {
        if (input.none { it in 'ऀ'..'ॿ' }) return input
        val digits = buildString(input.length) {
            input.forEach { c ->
                val digit = DEVANAGARI_DIGITS.indexOf(c)
                append(if (digit >= 0) ('0' + digit) else c)
            }
        }
        return digits.split(' ').joinToString(" ") { word ->
            val bare = word.trimEnd(',', '.', '!', '?', '।')
            val mapped = DEVANAGARI_WORDS[bare] ?: return@joinToString word
            mapped + word.removePrefix(bare)
        }
    }
}
