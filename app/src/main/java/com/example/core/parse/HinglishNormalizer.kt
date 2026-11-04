package com.example.core.parse

/**
 * Rewrites the Hinglish and Hindi ways people say *when* into the English phrases
 * [ReminderTextParser] understands, and leaves everything else (the title) exactly as typed.
 *
 *   "kal subah 9 baje doctor"   -> "tomorrow in the morning at 9 doctor"
 *   "har somvar gym"            -> "every monday gym"
 *   "कल शाम 6 बजे दवाई"          -> "tomorrow in the evening at 6 दवाई"
 *
 * Plain Kotlin with no Android imports. Word boundaries are letter-aware so Devanagari works
 * (Java's \b only knows ASCII letters).
 */
object HinglishNormalizer {

    private const val L = "\\p{L}\\p{M}"
    private fun word(pattern: String) = Regex("(?i)(?<![$L])(?:$pattern)(?![$L])")

    private val devanagariDigits = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9',
    )

    private val weekdays = listOf(
        "monday" to "som(?:vaar|var|waar|war)|सोमवार",
        "tuesday" to "mangal(?:vaar|var|waar|war)|मंगलवार",
        "wednesday" to "budh(?:vaar|var|waar|war)|बुधवार",
        "thursday" to "guru(?:vaar|var|waar|war)|brihaspati(?:vaar|var)|गुरुवार|बृहस्पतिवार",
        "friday" to "shukra(?:vaar|var|waar|war)|शुक्रवार",
        "saturday" to "shani(?:vaar|var|waar|war)|शनिवार",
        "sunday" to "ravi(?:vaar|var|waar|war)|itvaar|itwar|रविवार|इतवार",
    )

    /** Ordered rewrites. Earlier rules see the raw text; later ones see the earlier results. */
    private val rules: List<Pair<Regex, String>> = buildList {
        // Polite filler that is not part of the title
        add(word("(?:mujhe\\s+)?yaad\\s+(?:dila(?:na|o|\\s+dena|\\s+do)?|rakhna)|remind\\s+kar(?:na|o|\\s+dena|\\s+do)|मुझे\\s+याद\\s+दिला(?:ना|ओ|\\s+देना)?|याद\\s+दिला(?:ना|ओ|\\s+देना)?") to " ")

        // Relative times: "2 ghante baad", "10 minute mein", "3 din baad"
        add(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:ghante|ghanta|ghanton|घंटे|घंटा)\\s*(?:baad|mein|me|बाद|में)(?![$L])") to " in $1 hours ")
        add(Regex("(?i)(\\d+)\\s*(?:minute|minutes|min|mint|मिनट)\\s*(?:baad|mein|me|बाद|में)(?![$L])") to " in $1 minutes ")
        add(Regex("(?i)(\\d+)\\s*(?:din|दिन)\\s*(?:baad|mein|me|बाद|में)(?![$L])") to " in $1 days ")
        add(word("aadhe\\s+ghante\\s+(?:baad|mein|me)|आधे\\s+घंटे\\s+(?:बाद|में)") to " in half an hour ")

        // Clock: "saade 9 baje" = 9:30, "sava 9" = 9:15, "pone 9" = 8:45, "9 baje", "9:30 baje"
        add(Regex("(?i)(?<![$L])(?:saade|sadhe|साढ़े)\\s+(\\d{1,2})\\s*(?:baje|bje|बजे)?(?![$L])") to " at $1:30 ")
        add(Regex("(?i)(?<![$L])(?:sava|sawa|सवा)\\s+(\\d{1,2})\\s*(?:baje|bje|बजे)?(?![$L])") to " at $1:15 ")
        add(Regex("(?i)(\\d{1,2}(?::\\d{2})?)\\s*(?:baje|bje|बजे)(?![$L])") to " at $1 ")

        // Days
        add(word("aaj\\s+raat|आज\\s+रात") to " tonight ")
        add(word("parso|parson|परसों|परसो") to " day after tomorrow ")
        add(word("kal|कल") to " tomorrow ")
        add(word("aaj|आज") to " today ")

        // Repeats: "har somvar", "har din", "roz", "har mahine", "har saal"
        weekdays.forEach { (english, hindi) -> add(word("(?:har|हर)\\s+(?:$hindi)") to " every $english ") }
        add(word("(?:har|हर)\\s+(?:din|दिन)|roz(?:ana)?|rozaana|रोज़?|रोज़ाना|रोजाना|प्रतिदिन") to " every day ")
        add(word("(?:har|हर)\\s+(?:subah|सुबह)") to " every morning ")
        add(word("(?:har|हर)\\s+(?:shaam|sham|शाम)") to " every evening ")
        add(word("(?:har|हर)\\s+(?:raat|रात)") to " every night ")
        add(word("(?:har|हर)\\s+(?:hafte|hafta|हफ्ते|हफ़्ते)") to " every week ")
        add(word("(?:har|हर)\\s+(?:mahine|mahina|month|महीने)") to " every month ")
        add(word("(?:har|हर)\\s+(?:saal|varsh|साल|वर्ष)") to " every year ")
        add(Regex("(?i)(?<![$L])(?:har|हर)\\s+(\\d+)\\s*(?:ghante|घंटे)(?![$L])") to " every $1 hours ")

        // Day of month: "1 tarikh ko" -> "on the 1st"
        add(Regex("(?i)(?<![$L\\d])(\\d{1,2})\\s*(?:tarikh|tareekh|तारीख)(?:\\s+(?:ko|को))?(?![$L])") to " on the $1th ")

        // Parts of the day
        add(word("subah|subha|सुबह") to " in the morning ")
        add(word("dopahar|dopeher|दोपहर") to " in the afternoon ")
        add(word("shaam|sham|शाम") to " in the evening ")
        add(word("raat|रात") to " in the night ")

        // Plain weekdays: "somvar ko" -> "on monday"
        weekdays.forEach { (english, hindi) ->
            add(word("(?:$hindi)(?:\\s+(?:ko|को))?") to " on $english ")
        }
    }

    fun normalize(input: String): String {
        var text = buildString(input.length) { input.forEach { append(devanagariDigits[it] ?: it) } }
        rules.forEach { (regex, replacement) -> text = regex.replace(text, replacement) }
        // Ordinals made from "1 tarikh" read "1th"; fix them so the parser's ordinal rule matches.
        text = text.replace(Regex("(?<!1)1th\\b"), "1st").replace(Regex("(?<!1)2th\\b"), "2nd").replace(Regex("(?<!1)3th\\b"), "3rd")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** True when the text has any Hindi or Hinglish time word worth normalizing. */
    fun hasHinglish(input: String): Boolean = normalize(input) != input.replace(Regex("\\s+"), " ").trim()
}
