package com.example.data.today

import com.example.core.today.TodayBoard
import com.example.core.today.TodayBuilder
import com.example.core.today.TodayMoney
import com.example.core.today.TodayNotice
import com.example.core.today.TodayNoticeType
import com.example.core.today.TodayReminder
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.MoneyDirectionColumn
import com.example.data.local.entity.MoneyEntryEntity
import com.example.data.local.entity.NoticedItemEntity
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

/** One read of everything Today needs, for places without a ViewModel (widget, daily notes). */
suspend fun loadTodayBoard(db: AppDatabase, nowMillis: Long = System.currentTimeMillis()): TodayBoard =
    TodayBuilder.build(
        reminders = db.reminderDao().observeAllReminders().first().filter { it.status != ReminderStatus.CANCELLED }.map { it.toToday() },
        money = db.moneyDao().getOpen().map { it.toToday() },
        notices = db.noticedDao().observeSuggested().first().map { it.toToday() },
        nowMillis = nowMillis,
    )

fun ReminderEntity.toToday() = TodayReminder(
    id = id,
    title = title,
    kind = kind,
    triggerAtMillis = triggerAtMillis,
    hasTime = hasTime,
    isDone = status == ReminderStatus.COMPLETED,
    completedAtMillis = completedAtMillis,
    isMissed = status == ReminderStatus.MISSED,
    snoozeCount = snoozeCount,
    createdAtMillis = createdAtMillis,
    isRinging = status == ReminderStatus.RINGING,
)

fun MoneyEntryEntity.toToday() = TodayMoney(
    id = id,
    person = person,
    theyOweMe = direction == MoneyDirectionColumn.THEY_OWE_ME,
    amountPaise = amountPaise,
    thing = thing,
    sinceMillis = createdAtMillis,
)

fun NoticedItemEntity.toToday(): TodayNotice {
    val dueAt = if (dueDay != null && dueMonth != null) {
        Calendar.getInstance(TimeZone.getDefault()).apply {
            val now = timeInMillis
            // Day 1 first, so a stray "31" can never roll the month forward.
            set(Calendar.DAY_OF_MONTH, 1)
            if (dueYear != null) set(Calendar.YEAR, dueYear)
            set(Calendar.MONTH, dueMonth - 1)
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
            if (dueYear == null && timeInMillis < now - 30L * 86_400_000L) {
                add(Calendar.YEAR, 1)
                set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
        }.timeInMillis
    } else {
        null
    }
    return TodayNotice(
        id = id,
        type = when (type) {
            NoticedType.MISSED_CALL -> TodayNoticeType.MISSED_CALL
            NoticedType.UNREAD_MESSAGE -> TodayNoticeType.UNREAD_MESSAGE
            NoticedType.BILL -> TodayNoticeType.BILL
        },
        who = who,
        amountPaise = amountPaise,
        dueAtMillis = dueAt,
        noticedAtMillis = noticedAtMillis,
    )
}
