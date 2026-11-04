package com.example.parser

import com.example.data.local.entity.LooseEndDirection
import java.util.Locale
import java.util.TimeZone

/** A sentence for Remember: "passport is in the blue bag". */
data class MemoryDraft(val text: String, val subject: String, val place: String)

/** Money or a thing out with someone: "Rahul owes me 500". */
data class LooseEndDraft(
    val direction: LooseEndDirection,
    val person: String,
    val what: String,
    val amountRupees: Long?
)

/** What one line typed or spoken to Nooki turned out to be. */
sealed interface Capture {
    /** Something to do, with or without a time. */
    data class Remind(val parsed: ParsedReminder) : Capture

    /** Something to hold on to. */
    data class Hold(val draft: MemoryDraft) : Capture

    /** Money or a thing with another person. */
    data class Lend(val draft: LooseEndDraft) : Capture

    /** A question about something saved: "where is my passport". */
    data class Ask(val query: String) : Capture
}

/**
 * Decides what a line is. The order matters: a question and a loan have a clear shape, anything with
 * a time is a reminder ("meeting is at 5"), and only then can "X is in Y" safely mean a saved thing.
 * Pure Kotlin, so every rule here is unit tested.
 */
object CaptureRouter {

    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private const val PLACE_WORD = "in|at|on|under|inside|with|behind|near|beside|next\\s+to"
    private const val NAME = "([\\p{L}]+(?:'s)?)"

    private val ASK_WHERE = ci(
        "^(?:hey\\s+nooki[,!]?\\s*)?where(?:'s|\\s+is|\\s+are|\\s+did\\s+i\\s+(?:keep|put|leave|park|hide))\\s+" +
            "(?:my\\s+|the\\s+)?(.+?)[?\\s]*$"
    )
    private val ASK_KAHAN = ci("^(?:mera\\s+|meri\\s+|mere\\s+)?(.+?)\\s+(?:kahan|kidhar|कहाँ|कहां)(?:\\s+.*)?\\??$")

    private val OWES_ME = ci("^$NAME\\s+owes\\s+me\\s+(.+)$")
    private val I_OWE = ci("^i\\s+owe\\s+$NAME\\s+(.+)$")
    private val LENT = ci("^(?:i\\s+)?lent\\s+(.+?)\\s+to\\s+$NAME$")
    private val BORROWED_FROM = ci("^(?:i\\s+)?borrowed\\s+(.+?)\\s+from\\s+$NAME$")
    private val THEY_BORROWED = ci("^$NAME\\s+borrowed\\s+(?:my\\s+)?(.+)$")
    private val GAVE_HINDI = ci("^$NAME\\s+ko\\s+(.+?)\\s+(?:diye|diya|di|udhaar\\s+diye|udhaar\\s+diya)$")
    private val TOOK_HINDI = ci("^$NAME\\s+se\\s+(.+?)\\s+(?:liye|liya|li|udhaar\\s+liye|udhaar\\s+liya)$")

    private val AMOUNT = ci("^(?:₹|rs\\.?|inr|rupees?)?\\s*(\\d[\\d,]*)\\s*(k|rs\\.?|rupees?|rupaye|rupay|/-)?$")

    private val EXPLICIT_NOTE = ci("^(?:note|keep\\s+this|remember\\s+that|remember:|yaad\\s+rakhna)[:\\s]+(.+)$")
    private val KEPT = ci("^(?:i\\s+)?(?:kept|put|left|hid|placed|stored)\\s+(?:my\\s+|the\\s+)?(.+?)\\s+(?:$PLACE_WORD)\\s+(.+)$")
    private val PARKED = ci("^(?:my\\s+|the\\s+)?(.+?)\\s+(?:is\\s+)?parked\\s+(?:$PLACE_WORD)\\s+(.+)$")
    private val IS_PLACED = ci("^(?:my\\s+|the\\s+)?(.+?)\\s+(?:is|are)\\s+(?:kept\\s+)?(?:$PLACE_WORD)\\s+(.+)$")
    private val RAKHA = ci("^(?:mera\\s+|meri\\s+|mere\\s+)?(\\S+)\\s+(.+?)\\s+(?:rakha|rakhi|rakhe)(?:\\s+(?:hai|hain|hua))?$")

    fun route(
        input: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Capture {
        val line = input.trim().trimEnd('.')
        if (line.isEmpty()) return Capture.Remind(ReminderTextParser.parse(line, nowMillis, timeZone))

        ASK_WHERE.matchEntire(line)?.let { return Capture.Ask(it.groupValues[1].trim()) }
        ASK_KAHAN.matchEntire(line)?.let { return Capture.Ask(it.groupValues[1].trim()) }

        matchLend(line)?.let { return Capture.Lend(it) }

        EXPLICIT_NOTE.matchEntire(line)?.let { m ->
            val body = m.groupValues[1].trim()
            val placed = matchHold(body)
            return Capture.Hold(placed?.copy(text = body) ?: MemoryDraft(sentence(body), "", ""))
        }

        val parsed = ReminderTextParser.parse(line, nowMillis, timeZone)
        val timed = parsed.triggerAtMillis != null || parsed.recurrenceRule != null || parsed.recurrenceMinutes != null
        if (timed) return Capture.Remind(parsed)

        matchHold(line)?.let { return Capture.Hold(it) }
        return Capture.Remind(parsed)
    }

    private fun matchLend(line: String): LooseEndDraft? {
        OWES_ME.matchEntire(line)?.let { return draft(LooseEndDirection.THEY_HAVE, it.groupValues[1], it.groupValues[2]) }
        I_OWE.matchEntire(line)?.let { return draft(LooseEndDirection.I_HAVE, it.groupValues[1], it.groupValues[2]) }
        LENT.matchEntire(line)?.let { return draft(LooseEndDirection.THEY_HAVE, it.groupValues[2], it.groupValues[1]) }
        BORROWED_FROM.matchEntire(line)?.let { return draft(LooseEndDirection.I_HAVE, it.groupValues[2], it.groupValues[1]) }
        THEY_BORROWED.matchEntire(line)?.takeIf { !it.groupValues[1].equals("i", ignoreCase = true) }?.let {
            return draft(LooseEndDirection.THEY_HAVE, it.groupValues[1], it.groupValues[2])
        }
        GAVE_HINDI.matchEntire(line)?.let { return draft(LooseEndDirection.THEY_HAVE, it.groupValues[1], it.groupValues[2]) }
        TOOK_HINDI.matchEntire(line)?.let { return draft(LooseEndDirection.I_HAVE, it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun draft(direction: LooseEndDirection, person: String, what: String): LooseEndDraft {
        val cleanWhat = what.trim().removePrefix("my ").removePrefix("the ").trim()
        return LooseEndDraft(
            direction = direction,
            person = person.removeSuffix("'s").replaceFirstChar { it.titlecase(Locale.ROOT) },
            what = cleanWhat,
            amountRupees = parseRupees(cleanWhat)
        )
    }

    /** "500", "₹1,200", "rs 500", "2k". Null when the text is not purely an amount. */
    fun parseRupees(text: String): Long? {
        val m = AMOUNT.matchEntire(text.trim()) ?: return null
        val value = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        return if (m.groupValues[2].equals("k", ignoreCase = true)) value * 1000 else value
    }

    private fun matchHold(line: String): MemoryDraft? {
        val m = KEPT.matchEntire(line)
            ?: PARKED.matchEntire(line)
            ?: IS_PLACED.matchEntire(line)
            ?: RAKHA.matchEntire(line)
            ?: return null
        return MemoryDraft(sentence(line), m.groupValues[1].trim(), m.groupValues[2].trim())
    }

    private fun sentence(text: String) = text.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
