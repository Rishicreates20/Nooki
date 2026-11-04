package com.example.core.today

import com.example.core.model.ReminderKind
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Plain snapshot of a reminder, so the Today rules have no Android or Room imports. */
data class TodayReminder(
    val id: Long,
    val title: String,
    val kind: ReminderKind,
    val triggerAtMillis: Long,
    val hasTime: Boolean,
    val isDone: Boolean,
    val completedAtMillis: Long?,
    val isMissed: Boolean,
    val snoozeCount: Int,
    val createdAtMillis: Long,
    val isRinging: Boolean = false,
)

data class TodayMoney(
    val id: Long,
    val person: String,
    val theyOweMe: Boolean,
    val amountPaise: Long?,
    val thing: String?,
    val sinceMillis: Long,
)

enum class TodayNoticeType { MISSED_CALL, UNREAD_MESSAGE, BILL }

data class TodayNotice(
    val id: Long,
    val type: TodayNoticeType,
    val who: String,
    val amountPaise: Long?,
    val dueAtMillis: Long?,
    val noticedAtMillis: Long,
)

/** The thread's colour for a knot: vermilion missed, marigold forgotten, indigo to do, leaf done. */
enum class KnotTone { MISSED, FORGOT, TODO, DONE }

/** What a knot points back to, so tapping it can untie the right thing. */
sealed interface KnotRef {
    data class Reminder(val id: Long) : KnotRef
    data class Money(val id: Long) : KnotRef
    data class Notice(val id: Long) : KnotRef
}

/** One knot on the thread. [line] is the main text, [detail] the quiet second line. */
data class Knot(
    val ref: KnotRef,
    val tone: KnotTone,
    val line: String,
    val detail: String,
    val sortKey: Long,
)

/** Nooki's mood comes from real data, never from random animation. */
enum class NookiMood { SLEEPING, CONTENT, LOOKING_UP, FLUSTERED }

data class TodayBoard(
    /** The one thing ringing right now. The only item that gets a solid block. */
    val ringing: Knot?,
    val missed: List<Knot>,
    val forgot: List<Knot>,
    val todo: List<Knot>,
    val later: List<Knot>,
    val done: List<Knot>,
    val mood: NookiMood,
    /** Lines without a time, waiting for the evening wrap. Also listed in [forgot]. */
    val noTimeCount: Int,
) {
    val isCalm: Boolean get() = ringing == null && missed.isEmpty() && forgot.isEmpty() && todo.isEmpty()
}

/**
 * Sorts everything Nooki knows into the three questions: what did I miss, what did I forget, what
 * do I have to do. Plain Kotlin, unit tested.
 */
object TodayBuilder {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** A reminder still not acted on this long after its time counts as missed. */
    const val MISSED_GRACE_MILLIS = 5 * MINUTE

    /** Messages still unopened after this long show up in "You missed". */
    const val UNREAD_AFTER_MILLIS = 2 * HOUR

    /** Pushed this many times, it is something you keep forgetting. */
    const val PUSHED_TIMES = 3

    /** Bills this close count as "getting close". */
    const val BILL_CLOSE_MILLIS = 3 * DAY

    /** How far ahead "Coming up" looks. */
    const val LATER_WINDOW_MILLIS = 7 * DAY

    fun build(
        reminders: List<TodayReminder>,
        money: List<TodayMoney>,
        notices: List<TodayNotice>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): TodayBoard {
        val endOfToday = endOfDay(nowMillis, timeZone)
        val startOfToday = endOfToday - DAY
        val missed = mutableListOf<Knot>()
        val forgot = mutableListOf<Knot>()
        val todo = mutableListOf<Knot>()
        val later = mutableListOf<Knot>()
        val done = mutableListOf<Knot>()
        var missedWeight = 0
        var noTime = 0
        var ringing: Knot? = null

        for (r in reminders) {
            val ref = KnotRef.Reminder(r.id)
            when {
                r.isRinging && !r.isDone && ringing == null -> {
                    ringing = Knot(ref, KnotTone.TODO, r.title, "Now", r.triggerAtMillis)
                }
                r.isDone -> {
                    val at = r.completedAtMillis ?: continue
                    if (at >= startOfToday) done += Knot(ref, KnotTone.DONE, r.title, "Done ${clock(at, timeZone)}", at)
                }
                !r.hasTime -> {
                    noTime++
                    forgot += Knot(ref, KnotTone.FORGOT, r.title, "No time yet. Pick one tonight.", r.createdAtMillis)
                }
                r.isMissed || r.triggerAtMillis < nowMillis - MISSED_GRACE_MILLIS -> {
                    missedWeight += r.kind.overdueWeight
                    missed += Knot(ref, KnotTone.MISSED, r.title, "Was due ${ago(r.triggerAtMillis, nowMillis, timeZone)}", r.triggerAtMillis)
                }
                r.snoozeCount >= PUSHED_TIMES -> {
                    forgot += Knot(ref, KnotTone.FORGOT, r.title, "Pushed ${r.snoozeCount} times", r.triggerAtMillis)
                }
                r.kind == ReminderKind.BILL && r.triggerAtMillis - nowMillis in 0..BILL_CLOSE_MILLIS && r.triggerAtMillis > endOfToday -> {
                    forgot += Knot(ref, KnotTone.FORGOT, r.title, "Due ${dayLabel(r.triggerAtMillis, nowMillis, timeZone)}", r.triggerAtMillis)
                }
                r.triggerAtMillis <= endOfToday -> {
                    todo += Knot(ref, KnotTone.TODO, r.title, clock(r.triggerAtMillis, timeZone), r.triggerAtMillis)
                }
                r.triggerAtMillis - nowMillis <= LATER_WINDOW_MILLIS -> {
                    later += Knot(ref, KnotTone.TODO, r.title, dayLabel(r.triggerAtMillis, nowMillis, timeZone), r.triggerAtMillis)
                }
            }
        }

        for (m in money) {
            val what = m.amountPaise?.let(::rupees) ?: m.thing ?: "something"
            val line = when {
                !m.theyOweMe -> "You owe ${m.person} $what"
                m.amountPaise != null -> "${m.person} owes you $what"
                else -> "${m.person} has your $what"
            }
            forgot += Knot(KnotRef.Money(m.id), KnotTone.FORGOT, line, "Since ${since(m.sinceMillis, nowMillis)}", m.sinceMillis)
        }

        for (n in notices) {
            val ref = KnotRef.Notice(n.id)
            when (n.type) {
                TodayNoticeType.MISSED_CALL ->
                    missed += Knot(ref, KnotTone.MISSED, "${n.who} called", ago(n.noticedAtMillis, nowMillis, timeZone), n.noticedAtMillis)
                TodayNoticeType.UNREAD_MESSAGE ->
                    if (nowMillis - n.noticedAtMillis >= UNREAD_AFTER_MILLIS) {
                        missed += Knot(ref, KnotTone.MISSED, "Unread message from ${n.who}", ago(n.noticedAtMillis, nowMillis, timeZone), n.noticedAtMillis)
                    }
                TodayNoticeType.BILL -> {
                    val amount = n.amountPaise?.let { " ${rupees(it)}" }.orEmpty()
                    val detail = n.dueAtMillis?.let { "Due ${dayLabel(it, nowMillis, timeZone)}" } ?: "From a message"
                    forgot += Knot(ref, KnotTone.FORGOT, "${n.who} bill$amount", detail, n.dueAtMillis ?: n.noticedAtMillis)
                }
            }
        }

        val soonest = (todo.map { it.sortKey }).filter { it >= nowMillis }.minOrNull()
        val billTomorrow = forgot.any { it.ref is KnotRef.Reminder && it.sortKey - nowMillis in 0..DAY }
        val mood = when {
            missedWeight >= 3 || missed.size >= 4 || (missed.size + forgot.size) >= 8 -> NookiMood.FLUSTERED
            ringing != null || missed.isNotEmpty() || billTomorrow || (soonest != null && soonest - nowMillis <= HOUR) -> NookiMood.LOOKING_UP
            todo.isNotEmpty() || forgot.isNotEmpty() || done.isNotEmpty() -> NookiMood.CONTENT
            else -> NookiMood.SLEEPING
        }

        return TodayBoard(
            ringing = ringing,
            missed = missed.sortedByDescending { it.sortKey },
            forgot = forgot.sortedBy { it.sortKey },
            todo = todo.sortedBy { it.sortKey },
            later = later.sortedBy { it.sortKey },
            done = done.sortedByDescending { it.sortKey },
            mood = mood,
            noTimeCount = noTime,
        )
    }

    fun rupees(paise: Long): String {
        val r = paise / 100
        val p = paise % 100
        val grouped = indianGrouping(r)
        return if (p == 0L) "₹$grouped" else "₹$grouped.${p.toString().padStart(2, '0')}"
    }

    /** 1234567 -> "12,34,567", the way amounts are written in India. */
    private fun indianGrouping(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }

    fun endOfDay(nowMillis: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    fun clock(millis: Long, timeZone: TimeZone): String {
        val c = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = millis }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ampm = if (h < 12) "am" else "pm"
        return if (m == 0) "$h12 $ampm" else "$h12:${m.toString().padStart(2, '0')} $ampm"
    }

    private fun dayLabel(millis: Long, nowMillis: Long, timeZone: TimeZone): String {
        val days = ((endOfDay(millis, timeZone) - endOfDay(nowMillis, timeZone)) / DAY).toInt()
        val c = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = millis }
        val day = when (days) {
            0 -> "today"
            1 -> "tomorrow"
            in 2..6 -> c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US) ?: "in $days days"
            else -> "${c.get(Calendar.DAY_OF_MONTH)} ${c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)}"
        }
        return "$day, ${clock(millis, timeZone)}"
    }

    private fun ago(millis: Long, nowMillis: Long, timeZone: TimeZone): String {
        val diff = nowMillis - millis
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> "${diff / MINUTE} min ago"
            diff < 2 * HOUR -> "1 hour ago"
            diff < DAY -> "${diff / HOUR} hours ago"
            else -> {
                val days = ((endOfDay(nowMillis, timeZone) - endOfDay(millis, timeZone)) / DAY).toInt()
                if (days <= 1) "yesterday" else "$days days ago"
            }
        }
    }

    private fun since(millis: Long, nowMillis: Long): String {
        val days = ((nowMillis - millis) / DAY).toInt()
        return when {
            days <= 0 -> "today"
            days == 1 -> "yesterday"
            days < 30 -> "$days days"
            else -> "${days / 30} months"
        }
    }
}
