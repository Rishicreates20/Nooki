package com.example.core.notice

/**
 * A notification as Nooki's listener sees it, reduced to plain values so the rules below have no
 * Android imports. [category] is the notification category string (for example "call", "msg",
 * "email") when the posting app set one.
 */
data class RawNotification(
    val appPackage: String,
    val category: String?,
    val title: String?,
    val text: String?,
    val postedAtMillis: Long,
)

/** The kinds of noticing a person can switch off one by one. Off means off. */
data class NoticeSettings(
    val missedCalls: Boolean = true,
    val unreadMessages: Boolean = true,
    val bills: Boolean = true,
)

/** A calendar day without a time zone (java.time needs desugaring below API 26). */
data class DueDay(val day: Int, val month: Int, val year: Int?)

/**
 * What Nooki keeps from a notification. Never message content: only who and when, or for a bill
 * only the name, amount and date. Each one is a suggestion until the person taps it.
 */
sealed interface Noticed {
    val atMillis: Long

    data class MissedCall(val who: String, override val atMillis: Long) : Noticed

    data class UnreadMessage(val who: String, override val atMillis: Long) : Noticed

    data class Bill(
        val name: String,
        val amountPaise: Long?,
        val due: DueDay?,
        override val atMillis: Long,
    ) : Noticed
}

/**
 * Turns a [RawNotification] into a [Noticed] suggestion, or null when there is nothing worth
 * keeping. Sensitive notifications (OTPs, passwords, PINs, balances) are dropped first, before any
 * other rule looks at them.
 */
object NoticeExtractor {

    private const val MAX_NAME_LENGTH = 40

    private val missedCallTitle = Regex("""^\s*(\d+\s+)?missed\s+calls?\s*(from)?\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)
    private val missedCallAnywhere = Regex("""\bmissed\s+calls?\b|मिस्ड\s*कॉल""", RegexOption.IGNORE_CASE)
    private val billWords = Regex(
        """\b(bill|due|payable|pay\s+by|last\s+date|emi|premium|recharge\s+expires?|renewal|invoice)\b|बिल|देय""",
        RegexOption.IGNORE_CASE,
    )
    private val amount = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val numericDate = Regex("""\b([0-3]?\d)[/\-.]([01]?\d)(?:[/\-.](\d{2,4}))?\b""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val dayMonth = Regex("""\b([0-3]?\d)(?:st|nd|rd|th)?[\s\-]*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?(?:[\s,\-]*(\d{4}))?""", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+([0-3]?\d)(?:st|nd|rd|th)?(?:,?\s*(\d{4}))?""", RegexOption.IGNORE_CASE)

    fun extract(raw: RawNotification, settings: NoticeSettings = NoticeSettings()): Noticed? {
        // Rule 1: never touch OTPs, passwords, PINs or balances. Drop before anything else.
        if (SensitiveContent.isSensitive(raw.title, raw.text)) return null

        val title = raw.title?.trim().orEmpty()
        val text = raw.text?.trim().orEmpty()

        val looksLikeMissedCall = raw.category == "missed_call" ||
            missedCallAnywhere.containsMatchIn(title) ||
            (raw.category == "call" && missedCallAnywhere.containsMatchIn(text))
        if (looksLikeMissedCall) {
            if (!settings.missedCalls) return null
            val who = cleanName(callerFrom(title, text)) ?: return null
            return Noticed.MissedCall(who = who, atMillis = raw.postedAtMillis)
        }

        if (billWords.containsMatchIn(text) || billWords.containsMatchIn(title)) {
            val amountPaise = parseAmountPaise(text) ?: parseAmountPaise(title)
            if (amountPaise != null) {
                if (!settings.bills) return null
                val name = cleanName(title) ?: return null
                return Noticed.Bill(
                    name = name,
                    amountPaise = amountPaise,
                    due = parseDueDay(text),
                    atMillis = raw.postedAtMillis,
                )
            }
        }

        if (raw.category == "msg" || raw.category == "email") {
            if (!settings.unreadMessages) return null
            val who = cleanName(title) ?: return null
            return Noticed.UnreadMessage(who = who, atMillis = raw.postedAtMillis)
        }

        return null
    }

    private fun callerFrom(title: String, text: String): String {
        val stripped = missedCallTitle.replace(title, "").trim()
        if (stripped.isNotEmpty()) return stripped
        // Title was only "Missed call"; dialers then put the caller in the text.
        return text.replace(missedCallAnywhere, "").trim().removePrefix("from").trim()
    }

    private fun cleanName(value: String): String? {
        val name = value.trim().trim(':', '-', ',').trim()
        if (name.isEmpty()) return null
        return if (name.length > MAX_NAME_LENGTH) name.take(MAX_NAME_LENGTH).trimEnd() else name
    }

    fun parseAmountPaise(text: String): Long? {
        val match = amount.find(text) ?: return null
        val digits = match.groupValues[1].replace(",", "")
        val parts = digits.split('.')
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        return rupees * 100 + paise
    }

    fun parseDueDay(text: String): DueDay? {
        dayMonth.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = months.indexOf(m.groupValues[2].lowercase().take(3)) + 1
            return validDay(day, month, m.groupValues[3].toIntOrNull())
        }
        monthDay.find(text)?.let { m ->
            val month = months.indexOf(m.groupValues[1].lowercase().take(3)) + 1
            val day = m.groupValues[2].toInt()
            return validDay(day, month, m.groupValues[3].toIntOrNull())
        }
        numericDate.find(text)?.let { m ->
            val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            return validDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year)
        }
        return null
    }

    private fun validDay(day: Int, month: Int, year: Int?): DueDay? =
        if (day in 1..31 && month in 1..12) DueDay(day, month, year) else null
}
