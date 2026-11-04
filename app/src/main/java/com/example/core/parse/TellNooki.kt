package com.example.core.parse

import java.util.TimeZone

/** Which way money or a thing went. */
enum class MoneyDirection {
    /** They have it: "Rahul owes me 500", "lent charger to Aman". */
    THEY_OWE_ME,

    /** I have it: "I owe Priya 200", "borrowed 300 from Sam". */
    I_OWE_THEM,
}

/** What Nooki understood from one line typed or spoken into "Tell Nooki". */
sealed interface Understood {
    /** Something to do, with or without a time. Without one Nooki brings it up in the evening. */
    data class Reminder(val reminder: ParsedReminder) : Understood

    /** "passport is in the blue bag", "car parked at B2, pillar 14". */
    data class SavedThing(val thing: String, val place: String) : Understood

    /** "Rahul owes me 500", "lent charger to Aman". [amountPaise] or [thing] is set, or both. */
    data class Money(
        val person: String,
        val direction: MoneyDirection,
        val amountPaise: Long?,
        val thing: String?,
    ) : Understood

    /** "where is my passport", "who owes me". [query] is what to search for. */
    data class Question(val query: String, val aboutMoney: Boolean) : Understood

    /** Nothing to save. */
    data object Empty : Understood
}

/**
 * The one entry point for capture. Decides whether a line is a question, money, a saved thing or a
 * reminder, in English, Hinglish or Hindi. Never loses a thought: anything it cannot place becomes
 * a reminder without a time.
 *
 * Plain Kotlin, offline, never throws. (java.util.TimeZone/Calendar are plain JDK, not Android.)
 */
object TellNooki {

    private const val L = "\\p{L}\\p{M}"
    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    // ---- questions ------------------------------------------------------------------------------
    private val whereQuestion = rx("""^\s*(?:where(?:'s|\s+is|\s+are|\s+did\s+i\s+(?:put|keep|leave|park))|where)\s+(.+?)\??\s*$""")
    private val hindiWhere = rx("""^\s*(.+?)\s+(?:kahan|kaha|kidhar|कहाँ|कहां|किधर)(?:\s+(?:hai|hain|rakha|rakhi|है|हैं|रखा|रखी)[\s\p{L}]*)?\??\s*$""")
    private val moneyQuestion = rx("""^\s*(?:who\s+owes|how\s+much\s+(?:do\s+i\s+owe|does\s+\S+\s+owe|is\s+owed)|what\s+did\s+i\s+lend|who\s+has\s+my|kis\s+ne|kiske\s+paas|किसने|किसके\s+पास)""")
    private val questionFiller = rx("""(?<![$L])(?:my|the|mera|meri|mere|मेरा|मेरी|मेरे)(?![$L])""")

    // ---- money ----------------------------------------------------------------------------------
    private const val AMOUNT = """(?:₹|rs\.?|inr|rupees?)?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:₹|rs\.?|rupees?|rupaye|rupay|रुपये|रुपए)?"""
    private val owesMe = rx("""^\s*(.+?)\s+owes\s+(?:me\s+)?$AMOUNT(?:\s+for\s+.+)?\s*$""")
    private val owesMeThing = rx("""^\s*(.+?)\s+owes\s+me\s+(?:a\s+|an\s+|my\s+)?(.+?)\s*$""")
    private val iOwe = rx("""^\s*i\s+owe\s+(.+?)\s+$AMOUNT(?:\s+for\s+.+)?\s*$""")
    private val lentAmountTo = rx("""^\s*(?:i\s+)?(?:lent|gave|loaned|paid\s+for)\s+$AMOUNT\s+to\s+(.+?)\s*$""")
    private val lentPersonAmount = rx("""^\s*(?:i\s+)?(?:lent|loaned)\s+(.+?)\s+$AMOUNT\s*$""")
    private val lentThingTo = rx("""^\s*(?:i\s+)?(?:lent|gave|loaned)\s+(?:my\s+)?(.+?)\s+to\s+(.+?)\s*$""")
    private val borrowedFrom = rx("""^\s*(?:i\s+)?(?:borrowed|took)\s+$AMOUNT\s+from\s+(.+?)\s*$""")
    private val borrowedThingFrom = rx("""^\s*(?:i\s+)?borrowed\s+(?:a\s+|an\s+|the\s+)?(.+?)\s+from\s+(.+?)\s*$""")
    // Hinglish: "Rahul ko 500 diye" (I gave), "Rahul se 500 liye" (I took), "Rahul pe 500 baaki"
    private val hindiGave = rx("""^\s*(.+?)\s+(?:ko|को)\s+$AMOUNT\s+(?:diye|diya|udhaar\s+diye|दिए|दिये|दिया)\s*(?:hain|hai|है|हैं)?\s*$""")
    private val hindiGaveThing = rx("""^\s*(.+?)\s+(?:ko|को)\s+(?:apna\s+|apni\s+|mera\s+|meri\s+)?(.+?)\s+(?:diya|di|diye|दिया|दी|दिए)\s*(?:hai|है)?\s*$""")
    private val hindiTook = rx("""^\s*(.+?)\s+(?:se|से)\s+$AMOUNT\s+(?:liye|liya|udhaar\s+liye|लिए|लिये|लिया)\s*(?:hain|hai|है|हैं)?\s*$""")
    private val hindiPending = rx("""^\s*(.+?)\s+(?:pe|par|पे|पर)\s+$AMOUNT\s+(?:baaki|baki|बाकी|बाक़ी)\s*(?:hai|hain|है|हैं)?\s*$""")

    // ---- saved things ---------------------------------------------------------------------------
    private val parked = rx("""^\s*(?:my\s+)?(car|bike|scooter|scooty|cycle|vehicle|गाड़ी)?\s*(?:is\s+)?parked\s+(?:at|in|on|near)\s+(.+?)\s*$""")
    private val isAtPlace = rx("""^\s*(?:my\s+|the\s+)?(.+?)\s+(?:is|are)\s+((?:kept\s+)?(?:in|inside|at|on|under|behind|near|next\s+to|with|in\s+the|on\s+the)\s+.+?)\s*$""")
    private val keptIn = rx("""^\s*(?:i\s+)?(?:kept|put|left|placed|hid|stored)\s+(?:my\s+|the\s+)?(.+?)\s+((?:in|inside|at|on|under|behind|near|with)\s+.+?)\s*$""")
    private val hindiPlace = rx("""^\s*(?:mera\s+|meri\s+|mere\s+|मेरा\s+|मेरी\s+|मेरे\s+)?(.+?)\s+(.+?)\s+(mein|me|में|par|pe|पर|पे|ke\s+paas|के\s+पास|ke\s+neeche|के\s+नीचे)\s+(?:hai|hain|rakha\s+hai|rakhi\s+hai|rakha|rakhi|है|हैं|रखा\s+है|रखी\s+है)\s*$""")

    fun understand(
        input: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Understood {
        val line = input.replace(Regex("\\s+"), " ").trim().trimEnd('.', '!')
        if (line.isEmpty()) return Understood.Empty

        question(line)?.let { return it }
        money(line)?.let { return it }

        val normalized = HinglishNormalizer.normalize(line)
        val reminder = ReminderTextParser.parse(normalized, nowMillis, timeZone)
        val hasWhen = reminder.triggerAtMillis != null || reminder.recurrenceRule != null || reminder.recurrenceMinutes != null
        if (!hasWhen) savedThing(line)?.let { return it }

        return Understood.Reminder(reminder)
    }

    private fun question(line: String): Understood.Question? {
        if (moneyQuestion.containsMatchIn(line)) return Understood.Question(query = "", aboutMoney = true)
        val raw = whereQuestion.find(line)?.groupValues?.get(1)
            ?: hindiWhere.find(line)?.groupValues?.get(1)
            ?: return null
        val query = questionFiller.replace(raw, " ").replace(Regex("\\s+"), " ").trim().trimEnd('?')
        return if (query.isEmpty()) null else Understood.Question(query = query, aboutMoney = false)
    }

    private fun money(line: String): Understood.Money? {
        fun m(person: String, dir: MoneyDirection, amount: String?, thing: String?) =
            Understood.Money(person.trim(), dir, amount?.let(::paise), thing?.trim()?.takeIf { it.isNotEmpty() })

        owesMe.find(line)?.let { return m(it.groupValues[1], MoneyDirection.THEY_OWE_ME, it.groupValues[2], null) }
        iOwe.find(line)?.let { return m(it.groupValues[1], MoneyDirection.I_OWE_THEM, it.groupValues[2], null) }
        lentAmountTo.find(line)?.let { return m(it.groupValues[2], MoneyDirection.THEY_OWE_ME, it.groupValues[1], null) }
        borrowedFrom.find(line)?.let { return m(it.groupValues[2], MoneyDirection.I_OWE_THEM, it.groupValues[1], null) }
        lentPersonAmount.find(line)?.let { return m(it.groupValues[1], MoneyDirection.THEY_OWE_ME, it.groupValues[2], null) }
        hindiGave.find(line)?.let { return m(it.groupValues[1], MoneyDirection.THEY_OWE_ME, it.groupValues[2], null) }
        hindiTook.find(line)?.let { return m(it.groupValues[1], MoneyDirection.I_OWE_THEM, it.groupValues[2], null) }
        hindiPending.find(line)?.let { return m(it.groupValues[1], MoneyDirection.THEY_OWE_ME, it.groupValues[2], null) }
        lentThingTo.find(line)?.let { return m(it.groupValues[2], MoneyDirection.THEY_OWE_ME, null, it.groupValues[1]) }
        borrowedThingFrom.find(line)?.let { return m(it.groupValues[2], MoneyDirection.I_OWE_THEM, null, it.groupValues[1]) }
        owesMeThing.find(line)?.let { return m(it.groupValues[1], MoneyDirection.THEY_OWE_ME, null, it.groupValues[2]) }
        hindiGaveThing.find(line)?.let { return m(it.groupValues[1], MoneyDirection.THEY_OWE_ME, null, it.groupValues[2]) }
        return null
    }

    private fun savedThing(line: String): Understood.SavedThing? {
        parked.find(line)?.let {
            val thing = it.groupValues[1].ifBlank { "Car" }
            return saved(thing, it.groupValues[2])
        }
        hindiPlace.find(line)?.let {
            val postposition = it.groupValues[3].lowercase()
            val place = if (postposition.contains("paas") || postposition.contains("पास")) {
                "with ${it.groupValues[2]}"
            } else {
                "${it.groupValues[2]} ${it.groupValues[3]}"
            }
            return saved(it.groupValues[1], place)
        }
        keptIn.find(line)?.let { return saved(it.groupValues[1], it.groupValues[2]) }
        isAtPlace.find(line)?.let { return saved(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun saved(thing: String, place: String): Understood.SavedThing? {
        val t = thing.trim().trim(',', '-')
        val p = place.trim().trim(',', '-').removePrefix("kept ").trim()
        if (t.isEmpty() || p.isEmpty()) return null
        return Understood.SavedThing(
            thing = t.replaceFirstChar { it.uppercaseChar() },
            place = p,
        )
    }

    private fun paise(raw: String): Long? {
        val parts = raw.replace(",", "").split('.')
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        return rupees * 100 + paise
    }
}
