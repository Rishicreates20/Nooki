package com.example.demo

import com.example.AppContainer
import com.example.data.local.entity.LooseEndDirection
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderStatus
import com.example.notice.NoticedCandidate
import com.example.parser.LooseEndDraft
import com.example.parser.MemoryDraft

/** A believable day, used by the developer screen and the screenshot tests. All of it is invented. */
object SampleDay {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun reminders(now: Long): List<ReminderEntity> = listOf(
        ReminderEntity(id = 1, title = "Take BP tablet", triggerAtMillis = now - 3 * MINUTE, status = ReminderStatus.RINGING, kind = ReminderKind.MEDICINE, keepAsking = true, recurrenceRule = RecurrenceRule.DAILY),
        ReminderEntity(id = 2, title = "Pay electricity bill", triggerAtMillis = now - 26 * HOUR, status = ReminderStatus.MISSED, kind = ReminderKind.BILL),
        ReminderEntity(id = 3, title = "Book gas cylinder", triggerAtMillis = now + 5 * HOUR, snoozeCount = 4, createdAtMillis = now - 6 * DAY),
        ReminderEntity(id = 4, title = "Call dentist", triggerAtMillis = now + 2 * HOUR),
        ReminderEntity(id = 5, title = "Submit assignment", triggerAtMillis = now + 3 * HOUR, kind = ReminderKind.DEADLINE),
        ReminderEntity(id = 6, title = "Buy milk", triggerAtMillis = now, undated = true, createdAtMillis = now - HOUR),
        ReminderEntity(id = 7, title = "Pay rent", triggerAtMillis = now + 2 * DAY, kind = ReminderKind.BILL, recurrenceRule = RecurrenceRule.MONTHLY),
        ReminderEntity(id = 8, title = "Maa's birthday", triggerAtMillis = now + 6 * DAY, kind = ReminderKind.BIRTHDAY, recurrenceRule = RecurrenceRule.YEARLY),
        ReminderEntity(id = 9, title = "Car insurance expires", triggerAtMillis = now + 12 * DAY, kind = ReminderKind.EXPIRY)
    )

    fun noticed(now: Long): List<NoticedEntity> = listOf(
        NoticedEntity(id = 1, type = NoticedType.MISSED_CALL, title = "Mom called", person = "Mom", times = 2, appLabel = "Phone", dedupeKey = "call", noticedAtMillis = now - 2 * HOUR),
        NoticedEntity(id = 2, type = NoticedType.BILL, title = "Airtel bill", detail = "₹799, due 12 Nov", amountRupees = 799, dueAtMillis = now + 8 * DAY, appLabel = "Messages", dedupeKey = "bill", noticedAtMillis = now - 5 * HOUR)
    )

    fun looseEnds(now: Long): List<LooseEndEntity> = listOf(
        LooseEndEntity(id = 1, direction = LooseEndDirection.THEY_HAVE, person = "Rahul", what = "500", amountRupees = 500, createdAtMillis = now - 12 * DAY),
        LooseEndEntity(id = 2, direction = LooseEndDirection.THEY_HAVE, person = "Aman", what = "phone charger", createdAtMillis = now - 2 * DAY),
        LooseEndEntity(id = 3, direction = LooseEndDirection.I_HAVE, person = "Priya", what = "200", amountRupees = 200, createdAtMillis = now - 3 * DAY)
    )

    fun memories(now: Long): List<MemoryEntity> = listOf(
        MemoryEntity(id = 1, text = "Passport is in the blue bag, top shelf", subject = "Passport", place = "the blue bag, top shelf", pinned = true, createdAtMillis = now - 40 * DAY),
        MemoryEntity(id = 2, text = "Car parked at B2, pillar 14", subject = "Car", place = "B2, pillar 14", createdAtMillis = now - 3 * HOUR),
        MemoryEntity(id = 3, text = "Spare key is with Sharma aunty", subject = "Spare key", place = "Sharma aunty", createdAtMillis = now - 9 * DAY)
    )

    /** Fills a real install with the sample day, through the same code paths the app uses. */
    suspend fun seed(graph: AppContainer, now: Long) {
        reminders(now).forEach { r ->
            val saved = graph.reminders.createAndScheduleReminder(
                title = r.title,
                rawInput = r.title,
                triggerAtMillis = r.triggerAtMillis.coerceAtLeast(now + MINUTE),
                kind = r.kind,
                recurrenceRule = r.recurrenceRule,
                undated = r.undated,
                keepAsking = r.keepAsking
            ).first
            if (r.status == ReminderStatus.MISSED) graph.reminders.dismissReminder(saved.id)
            if (r.status == ReminderStatus.RINGING) graph.reminders.onAlarmFired(saved.id, now)
        }
        noticed(now).forEach { n ->
            graph.attention.ingest(
                NoticedCandidate(
                    type = n.type, title = n.title, detail = n.detail, person = n.person,
                    amountRupees = n.amountRupees, dueAtMillis = n.dueAtMillis, appLabel = n.appLabel,
                    packageName = n.appLabel, dedupeKey = "sample_${n.dedupeKey}_$now",
                    noticedAtMillis = n.noticedAtMillis, surfaceAtMillis = n.surfaceAtMillis
                )
            )
        }
        looseEnds(now).forEach { e ->
            val saved = graph.attention.saveLooseEnd(LooseEndDraft(e.direction, e.person, e.what, e.amountRupees), "${e.person} ${e.what}")
            graph.attention.restoreLooseEnd(saved.copy(createdAtMillis = e.createdAtMillis))
        }
        memories(now).forEach { m -> graph.attention.saveMemory(MemoryDraft(m.text, m.subject, m.place)) }
    }
}
