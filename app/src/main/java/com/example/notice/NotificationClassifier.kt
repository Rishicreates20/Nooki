package com.example.notice

import com.example.attention.TimeWords
import com.example.data.local.entity.NoticedType
import com.example.parser.CaptureRouter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The fields of a notification Nooki looks at. The text exists only in memory, only for this check. */
data class IncomingNotice(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val category: String? = null,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val isGroupConversation: Boolean = false
)

/** What is allowed to be saved. There is deliberately no field for message text. */
data class NoticedCandidate(
    val type: NoticedType,
    val title: String,
    val detail: String = "",
    val person: String? = null,
    val amountRupees: Long? = null,
    val dueAtMillis: Long? = null,
    val appLabel: String,
    val packageName: String,
    val dedupeKey: String,
    val noticedAtMillis: Long,
    val surfaceAtMillis: Long
)

/**
 * Decides whether a notification is worth remembering, on the phone, with plain rules. The first
 * check drops anything secret, so an OTP can never reach the database. Anything it is unsure about
 * is ignored: a missed suggestion costs less than a wrong one.
 */
object NotificationClassifier {

    /** A message gets this long to be opened before Nooki brings it up. */
    const val MESSAGE_GRACE_MILLIS = 2 * 60 * 60_000L

    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val SECRET = ci(
        "\\b(?:otp|one[\\s-]?time[\\s-]?(?:password|code|pin)|verification\\s+code|security\\s+code|auth(?:entication)?\\s+code|" +
            "login\\s+code|passcode|password|cvv|upi\\s+pin|m-?pin|t-?pin|\\bpin\\b|do\\s+not\\s+share|don'?t\\s+share|never\\s+share|" +
            "account\\s+balance|avl\\.?\\s*bal|available\\s+balance|a/c\\s+(?:no|x+)|credited|debited)\\b|ओटीपी|पासवर्ड"
    )
    private val MARKETING = ci(
        "\\b(?:offer|sale|discount|cashback|coupon|% ?off|flat\\s+\\d+|lowest\\s+price|limited\\s+time|hurry|deal|win|won|" +
            "free|pre-?approved|upgrade\\s+now|shop\\s+now|click\\s+here)\\b"
    )
    private val ALREADY_DONE = ci(
        "\\b(?:paid|payment\\s+(?:of|received|successful)|received|successful(?:ly)?|thank\\s+you\\s+for|has\\s+been\\s+delivered|was\\s+delivered|delivered\\s+to)\\b"
    )

    private val MISSED_CALL_TEXT = ci("\\bmissed\\s+(?:voice\\s+|video\\s+|whatsapp\\s+)?calls?\\b|मिस्ड\\s+कॉल")
    private val COUNT_CALLS = ci("^\\d+\\s+missed\\s+calls?$")

    private val BILL_NOUN = ci(
        "\\b((?:[a-z]+\\s+)?(?:bill|emi|premium|recharge|rent|fees?|challan|subscription|renewal|instal+ment))\\b"
    )
    private val DUE_WORD = ci("\\b(?:due|payable|pay\\s+by|last\\s+date|expires?|expiring|overdue|pending|before)\\b")

    private val DELIVERY = ci(
        "\\b(?:out\\s+for\\s+delivery|arriving\\s+(?:today|tomorrow|by)|arrives?\\s+(?:today|tomorrow)|" +
            "will\\s+be\\s+delivered|delivery\\s+(?:today|tomorrow)|reach\\s+you\\s+today)\\b"
    )
    private val EVENT_NOUN = ci(
        "\\b(appointment|interview|exam(?:ination)?|hall\\s+ticket|admit\\s+card|flight|pnr|train|check-?in|consultation|counselling)\\b"
    )
    private val EVENT_SHAPE = ci("\\b(?:on|at|for|scheduled|booked|confirmed|date|dep|departs?)\\b")

    private val RUPEES = ci("(?:₹|\\brs\\.?|\\binr)\\s*([\\d,]+)(?:\\.\\d{1,2})?")

    private const val MONTH_NAME =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val DATE_DAY_NAME = ci("\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s-]+$MONTH_NAME\\b(?:[\\s,-]+(20\\d{2}))?")
    private val DATE_NAME_DAY = ci("\\b$MONTH_NAME\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(20\\d{2}))?")
    private val DATE_NUMERIC = ci("\\b(\\d{1,2})[/-](\\d{1,2})[/-](20\\d{2}|\\d{2})\\b")
    private val DATE_TOMORROW = ci("\\btomorrow\\b")
    private val DATE_TODAY = ci("\\btoday\\b")

    private val MESSAGE_APPS = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.instagram.android", "com.facebook.orca"
    )
    private val NOT_A_PERSON = ci("^(?:[A-Z]{2}-[A-Z0-9-]+|\\d[\\d\\s+-]*|.*\\b(?:new\\s+messages?|messages?\\s+from|chats?)\\b.*)$")

    fun classify(
        notice: IncomingNotice,
        nowMillis: Long,
        ownPackage: String = "",
        zone: TimeZone = TimeZone.getDefault()
    ): NoticedCandidate? {
        if (notice.packageName == ownPackage) return null
        if (notice.isGroupSummary || notice.isOngoing) return null
        val combined = "${notice.title} ${notice.text}".trim()
        if (combined.isEmpty()) return null
        if (SECRET.containsMatchIn(combined)) return null

        missedCall(notice, combined, nowMillis)?.let { return it }
        if (ALREADY_DONE.containsMatchIn(combined) || MARKETING.containsMatchIn(combined)) {
            return unreadMessage(notice, nowMillis)
        }
        return bill(notice, combined, nowMillis, zone)
            ?: delivery(notice, combined, nowMillis, zone)
            ?: event(notice, combined, nowMillis, zone)
            ?: unreadMessage(notice, nowMillis)
    }

    /** The key a notification would be saved under, so it can be cleared once it is opened. */
    fun messageKey(notice: IncomingNotice): String? =
        messagePerson(notice)?.let { key(NoticedType.UNREAD_MESSAGE, notice.packageName, it) }

    private fun missedCall(notice: IncomingNotice, combined: String, nowMillis: Long): NoticedCandidate? {
        if (notice.category != "missed_call" && !MISSED_CALL_TEXT.containsMatchIn(combined)) return null
        val titleSaysMissed = MISSED_CALL_TEXT.containsMatchIn(notice.title)
        val raw = (if (titleSaysMissed) notice.text else notice.title).trim()
        // "3 missed calls" with no name tells Nooki nothing it can act on.
        if (raw.isEmpty() || COUNT_CALLS.matches(raw)) return null
        val person = raw.substringBefore('\n').trim().take(MAX_NAME)
        return NoticedCandidate(
            type = NoticedType.MISSED_CALL,
            title = "$person called",
            person = person,
            appLabel = notice.appLabel,
            packageName = notice.packageName,
            dedupeKey = key(NoticedType.MISSED_CALL, notice.packageName, person),
            noticedAtMillis = nowMillis,
            surfaceAtMillis = nowMillis
        )
    }

    private fun bill(notice: IncomingNotice, combined: String, nowMillis: Long, zone: TimeZone): NoticedCandidate? {
        val noun = BILL_NOUN.find(combined)?.groupValues?.get(1) ?: return null
        if (!DUE_WORD.containsMatchIn(combined)) return null
        val amount = RUPEES.find(combined)?.groupValues?.get(1)?.let(CaptureRouter::parseRupees)
        val due = findDate(combined, nowMillis, zone)
        if (amount == null && due == null) return null
        val name = noun.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val detail = listOfNotNull(
            amount?.let(TimeWords::rupees),
            due?.let { "due ${TimeWords.date(it, nowMillis, zone)}" }
        ).joinToString(", ")
        return NoticedCandidate(
            type = NoticedType.BILL,
            title = name,
            detail = detail,
            amountRupees = amount,
            dueAtMillis = due,
            appLabel = notice.appLabel,
            packageName = notice.packageName,
            dedupeKey = key(NoticedType.BILL, notice.packageName, "$name|${due?.let { TimeWords.startOfDay(it, zone) }}"),
            noticedAtMillis = nowMillis,
            surfaceAtMillis = nowMillis
        )
    }

    private fun delivery(notice: IncomingNotice, combined: String, nowMillis: Long, zone: TimeZone): NoticedCandidate? {
        if (!DELIVERY.containsMatchIn(combined)) return null
        val tomorrow = DATE_TOMORROW.containsMatchIn(combined)
        val due = TimeWords.startOfDay(nowMillis, zone, offsetDays = if (tomorrow) 1 else 0)
        val source = notice.appLabel.ifBlank { "A parcel" }
        return NoticedCandidate(
            type = NoticedType.DELIVERY,
            title = "$source delivery ${if (tomorrow) "tomorrow" else "today"}",
            dueAtMillis = due,
            appLabel = notice.appLabel,
            packageName = notice.packageName,
            dedupeKey = key(NoticedType.DELIVERY, notice.packageName, due.toString()),
            noticedAtMillis = nowMillis,
            surfaceAtMillis = nowMillis
        )
    }

    private fun event(notice: IncomingNotice, combined: String, nowMillis: Long, zone: TimeZone): NoticedCandidate? {
        val noun = EVENT_NOUN.find(combined)?.groupValues?.get(1) ?: return null
        if (!EVENT_SHAPE.containsMatchIn(combined)) return null
        val due = findDate(combined, nowMillis, zone) ?: return null
        if (due < TimeWords.startOfDay(nowMillis, zone)) return null
        val name = when (noun.lowercase(Locale.ROOT)) {
            "pnr" -> "Train"
            "examination" -> "Exam"
            "hall ticket", "admit card" -> "Exam"
            else -> noun.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        return NoticedCandidate(
            type = NoticedType.EVENT,
            title = name,
            detail = TimeWords.date(due, nowMillis, zone),
            dueAtMillis = due,
            appLabel = notice.appLabel,
            packageName = notice.packageName,
            dedupeKey = key(NoticedType.EVENT, notice.packageName, "$name|${TimeWords.startOfDay(due, zone)}"),
            noticedAtMillis = nowMillis,
            surfaceAtMillis = nowMillis
        )
    }

    private fun unreadMessage(notice: IncomingNotice, nowMillis: Long): NoticedCandidate? {
        val person = messagePerson(notice) ?: return null
        return NoticedCandidate(
            type = NoticedType.UNREAD_MESSAGE,
            title = "Unread message from $person",
            person = person,
            appLabel = notice.appLabel,
            packageName = notice.packageName,
            dedupeKey = key(NoticedType.UNREAD_MESSAGE, notice.packageName, person),
            noticedAtMillis = nowMillis,
            surfaceAtMillis = nowMillis + MESSAGE_GRACE_MILLIS
        )
    }

    private fun messagePerson(notice: IncomingNotice): String? {
        if (notice.category != "msg" && notice.packageName !in MESSAGE_APPS) return null
        if (notice.isGroupConversation || notice.isGroupSummary) return null
        val title = notice.title.trim()
        if (title.isEmpty() || title.length > MAX_NAME) return null
        if (title.equals(notice.appLabel, ignoreCase = true)) return null
        // Group chats write "Family: Aman" or "Family (3 messages)".
        if (':' in title || NOT_A_PERSON.matches(title)) return null
        return title
    }

    /** Finds the first date, read day first the way Indian bills write it. */
    private fun findDate(text: String, nowMillis: Long, zone: TimeZone): Long? {
        fun build(day: Int, month: Int, year: Int?): Long? {
            if (day !in 1..31 || month !in 0..11) return null
            val cal = Calendar.getInstance(zone, Locale.ENGLISH).apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, 1)
                if (year != null) set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // "due 3 Jan" read in December means next January.
            if (year == null && cal.timeInMillis < TimeWords.startOfDay(nowMillis, zone) - 30L * 24 * 60 * 60_000L) {
                cal.add(Calendar.YEAR, 1)
            }
            return cal.timeInMillis
        }
        fun monthIndex(name: String) = MONTHS.indexOf(name.lowercase(Locale.ROOT).take(3))

        DATE_DAY_NAME.find(text)?.let { m ->
            return build(m.groupValues[1].toInt(), monthIndex(m.groupValues[2]), m.groupValues[3].toIntOrNull())
        }
        DATE_NAME_DAY.find(text)?.let { m ->
            return build(m.groupValues[2].toInt(), monthIndex(m.groupValues[1]), m.groupValues[3].toIntOrNull())
        }
        DATE_NUMERIC.find(text)?.let { m ->
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            return build(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, year)
        }
        if (DATE_TOMORROW.containsMatchIn(text)) return TimeWords.startOfDay(nowMillis, zone, offsetDays = 1) + 9 * 60 * 60_000L
        if (DATE_TODAY.containsMatchIn(text)) return TimeWords.startOfDay(nowMillis, zone) + 9 * 60 * 60_000L
        return null
    }

    private fun key(type: NoticedType, packageName: String, value: String) =
        "${type.name}|$packageName|${value.lowercase(Locale.ROOT)}"

    private const val MAX_NAME = 40
}
