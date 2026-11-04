package com.example.attention

import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderStatus
import java.util.TimeZone

/** A calendar event, read when the person allows it. Nooki never writes to the calendar. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val allDay: Boolean
)

/** Why something ended up in "You forgot". */
enum class ForgotReason { KEEPS_SLIPPING, NO_TIME_SET }

/** One line on the home screen. */
sealed interface AttentionItem {
    val key: String

    data class Reminder(val reminder: ReminderEntity, val forgotReason: ForgotReason? = null) : AttentionItem {
        override val key = "reminder_${reminder.id}"
    }

    data class Noticed(val noticed: NoticedEntity) : AttentionItem {
        override val key = "noticed_${noticed.id}"
    }

    data class Owed(val end: LooseEndEntity, val daysOpen: Int) : AttentionItem {
        override val key = "owed_${end.id}"
    }

    data class Event(val event: CalendarEvent) : AttentionItem {
        override val key = "event_${event.id}_${event.beginMillis}"
    }
}

/** The three questions, answered: what did I miss, what did I forget, what do I have to do. */
data class TodayDigest(
    val ringing: List<AttentionItem.Reminder> = emptyList(),
    val missed: List<AttentionItem> = emptyList(),
    val forgot: List<AttentionItem> = emptyList(),
    val toDo: List<AttentionItem> = emptyList(),
    val comingUp: List<AttentionItem> = emptyList(),
    val headline: String = "All clear.",
    val subline: String = ""
) {
    val needsAttentionCount: Int get() = ringing.size + missed.size + forgot.size
    val isEmpty: Boolean
        get() = ringing.isEmpty() && missed.isEmpty() && forgot.isEmpty() && toDo.isEmpty() && comingUp.isEmpty()
}

/**
 * Sorts everything Nooki knows into the home screen. This is the product's brain, so it stays free
 * of Android and is tested on its own.
 */
object AttentionEngine {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** A ringing reminder nobody answered stops shouting and moves to "You missed". */
    const val RINGING_WINDOW_MILLIS = 30 * MINUTE

    /** A call or an unread message stops mattering after a few days. */
    const val NOTICED_EXPIRY_MILLIS = 3 * DAY

    /** How long something can sit with no time before Nooki brings it back up. */
    const val UNDATED_GRACE_MILLIS = 3 * DAY

    /** Money or things out with someone for this long count as forgotten. */
    const val LOOSE_END_GRACE_DAYS = 7

    /** Snoozed this many times means it keeps slipping. */
    const val SNOOZES_BEFORE_FORGOTTEN = 3

    const val COMING_UP_DAYS = 14
    const val COMING_UP_LIMIT = 8

    fun build(
        reminders: List<ReminderEntity>,
        noticed: List<NoticedEntity>,
        looseEnds: List<LooseEndEntity>,
        events: List<CalendarEvent>,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): TodayDigest {
        val endOfToday = TimeWords.startOfDay(nowMillis, zone, offsetDays = 1)
        val comingUpEnd = TimeWords.startOfDay(nowMillis, zone, offsetDays = COMING_UP_DAYS + 1)

        val ringing = mutableListOf<AttentionItem.Reminder>()
        val missed = mutableListOf<AttentionItem>()
        val forgot = mutableListOf<AttentionItem>()
        val toDo = mutableListOf<AttentionItem>()
        val comingUp = mutableListOf<AttentionItem>()

        for (reminder in reminders) {
            val open = reminder.status == ReminderStatus.SCHEDULED ||
                reminder.status == ReminderStatus.SNOOZED ||
                reminder.status == ReminderStatus.RINGING ||
                reminder.status == ReminderStatus.MISSED
            if (!open) continue

            val slipping = reminder.snoozeCount >= SNOOZES_BEFORE_FORGOTTEN
            when {
                reminder.undated -> {
                    if (nowMillis - reminder.createdAtMillis >= UNDATED_GRACE_MILLIS) {
                        forgot += AttentionItem.Reminder(reminder, ForgotReason.NO_TIME_SET)
                    } else {
                        toDo += AttentionItem.Reminder(reminder)
                    }
                }
                reminder.status == ReminderStatus.RINGING &&
                    nowMillis - reminder.triggerAtMillis < RINGING_WINDOW_MILLIS -> {
                    ringing += AttentionItem.Reminder(reminder)
                }
                reminder.status == ReminderStatus.RINGING ||
                    reminder.status == ReminderStatus.MISSED ||
                    reminder.triggerAtMillis < nowMillis - MINUTE -> {
                    missed += AttentionItem.Reminder(reminder)
                }
                slipping -> forgot += AttentionItem.Reminder(reminder, ForgotReason.KEEPS_SLIPPING)
                reminder.triggerAtMillis < endOfToday -> toDo += AttentionItem.Reminder(reminder)
                reminder.triggerAtMillis < comingUpEnd -> comingUp += AttentionItem.Reminder(reminder)
            }
        }

        for (item in noticed) {
            if (item.status != NoticedStatus.NEW || item.surfaceAtMillis > nowMillis) continue
            val fades = item.type == NoticedType.MISSED_CALL || item.type == NoticedType.UNREAD_MESSAGE
            if (fades && nowMillis - item.noticedAtMillis > NOTICED_EXPIRY_MILLIS) continue
            // A parcel or an event that has passed has nothing left to say.
            val due = item.dueAtMillis
            if (!fades && due != null && due < TimeWords.startOfDay(nowMillis, zone)) continue
            missed += AttentionItem.Noticed(item)
        }

        for (end in looseEnds) {
            if (!end.isOpen) continue
            val daysOpen = TimeWords.daysBetween(end.createdAtMillis, nowMillis, zone)
            if (daysOpen >= LOOSE_END_GRACE_DAYS) forgot += AttentionItem.Owed(end, daysOpen)
        }

        for (event in events) {
            val inToday = event.beginMillis in nowMillis until endOfToday ||
                (event.allDay && event.beginMillis in TimeWords.startOfDay(nowMillis, zone) until endOfToday)
            when {
                inToday -> toDo += AttentionItem.Event(event)
                event.beginMillis in endOfToday until comingUpEnd -> comingUp += AttentionItem.Event(event)
            }
        }

        val sortedToDo = toDo.sortedWith(compareBy<AttentionItem>({ isUndated(it) }, { sortTime(it) }))
        // A repeating habit is not news. Coming up is for the dates people actually lose track of.
        val sortedComingUp = comingUp
            .filterNot { it is AttentionItem.Reminder && it.reminder.kind == ReminderKind.HABIT && it.reminder.isHourly }
            .sortedBy(::sortTime)
            .take(COMING_UP_LIMIT)
        val sortedMissed = missed.sortedByDescending(::sortTime)
        val sortedForgot = forgot.sortedBy(::sortTime)

        val (headline, subline) = headlines(
            ringing = ringing.size,
            missed = sortedMissed.size,
            forgot = sortedForgot.size,
            toDo = sortedToDo.size,
            comingUp = sortedComingUp.size
        )

        return TodayDigest(
            ringing = ringing.sortedBy { it.reminder.triggerAtMillis },
            missed = sortedMissed,
            forgot = sortedForgot,
            toDo = sortedToDo,
            comingUp = sortedComingUp,
            headline = headline,
            subline = subline
        )
    }

    /** The sentence at the top of the app: one idea, then whatever else is true. */
    fun headlines(ringing: Int, missed: Int, forgot: Int, toDo: Int, comingUp: Int): Pair<String, String> {
        val headline = when {
            ringing > 0 -> "It's time."
            missed == 1 -> "You missed one thing."
            missed > 1 -> "You missed $missed things."
            forgot == 1 -> "One thing slipped away."
            forgot > 1 -> "$forgot things slipped away."
            toDo == 1 -> "One thing left today."
            toDo > 1 -> "$toDo things left today."
            comingUp > 0 -> "Today is clear."
            else -> "All clear."
        }
        val rest = buildList {
            if (ringing > 0 && missed > 0) add("$missed missed")
            if ((ringing > 0 || missed > 0) && forgot > 0) add("$forgot forgotten")
            if ((ringing > 0 || missed > 0 || forgot > 0) && toDo > 0) add("$toDo left today")
            if (ringing == 0 && missed == 0 && forgot == 0 && toDo == 0 && comingUp > 0) {
                add(if (comingUp == 1) "One thing coming up." else "$comingUp things coming up.")
            }
        }
        val subline = rest.joinToString(", ").let { line ->
            if (line.isEmpty() || line.endsWith(".")) line else line.replaceFirstChar { it.uppercase() } + "."
        }
        return headline to subline
    }

    private val ReminderEntity.isHourly: Boolean
        get() = (recurrenceMinutes ?: Int.MAX_VALUE) < 24 * 60

    private fun isUndated(item: AttentionItem) = item is AttentionItem.Reminder && item.reminder.undated

    private fun sortTime(item: AttentionItem): Long = when (item) {
        is AttentionItem.Reminder -> if (item.reminder.undated) item.reminder.createdAtMillis else item.reminder.triggerAtMillis
        is AttentionItem.Noticed -> item.noticed.noticedAtMillis
        is AttentionItem.Owed -> item.end.createdAtMillis
        is AttentionItem.Event -> item.event.beginMillis
    }
}
