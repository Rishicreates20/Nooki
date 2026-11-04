package com.example.attention

import com.example.data.local.entity.LooseEndDirection
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AttentionEngineTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    // Wednesday 4 November 2026, 11:00 local time.
    private val now: Long = Calendar.getInstance(zone, Locale.US).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 4, 11, 0, 0)
    }.timeInMillis

    private fun build(
        reminders: List<ReminderEntity> = emptyList(),
        noticed: List<NoticedEntity> = emptyList(),
        looseEnds: List<LooseEndEntity> = emptyList(),
        events: List<CalendarEvent> = emptyList()
    ) = AttentionEngine.build(reminders, noticed, looseEnds, events, now, zone)

    private fun reminder(
        offset: Long,
        status: ReminderStatus = ReminderStatus.SCHEDULED,
        title: String = "t",
        kind: ReminderKind = ReminderKind.TASK,
        snoozes: Int = 0,
        undated: Boolean = false,
        createdAt: Long = now,
        interval: Int? = null
    ) = ReminderEntity(
        title = title,
        triggerAtMillis = now + offset,
        status = status,
        kind = kind,
        snoozeCount = snoozes,
        undated = undated,
        createdAtMillis = createdAt,
        recurrenceMinutes = interval
    )

    private fun noticed(type: NoticedType, surfaceAt: Long = now - hour, noticedAt: Long = now - hour, due: Long? = null) =
        NoticedEntity(
            type = type,
            title = "n",
            dedupeKey = "k$type$surfaceAt",
            noticedAtMillis = noticedAt,
            surfaceAtMillis = surfaceAt,
            dueAtMillis = due
        )

    @Test
    fun `an empty phone is all clear`() {
        val d = build()
        assertTrue(d.isEmpty)
        assertEquals("All clear.", d.headline)
    }

    @Test
    fun `a fresh ring needs you now and an old one becomes missed`() {
        assertEquals(1, build(listOf(reminder(-5 * minute, ReminderStatus.RINGING))).ringing.size)
        val stale = build(listOf(reminder(-40 * minute, ReminderStatus.RINGING)))
        assertTrue(stale.ringing.isEmpty())
        assertEquals(1, stale.missed.size)
    }

    @Test
    fun `a swiped away reminder is still in you missed`() {
        assertEquals(1, build(listOf(reminder(-2 * hour, ReminderStatus.MISSED))).missed.size)
    }

    @Test
    fun `an alarm the phone never delivered counts as missed`() {
        assertEquals(1, build(listOf(reminder(-10 * minute))).missed.size)
    }

    @Test
    fun `finished things are gone`() {
        assertTrue(build(listOf(reminder(-hour, ReminderStatus.COMPLETED), reminder(-hour, ReminderStatus.CANCELLED))).isEmpty)
    }

    @Test
    fun `today and coming up split at midnight`() {
        val d = build(
            listOf(
                reminder(2 * hour, title = "later today"),
                reminder(day, title = "tomorrow"),
                reminder(13 * day, title = "soon"),
                reminder(40 * day, title = "far away")
            )
        )
        assertEquals(1, d.toDo.size)
        assertEquals(2, d.comingUp.size)
    }

    @Test
    fun `undated items wait on the list and then come back as forgotten`() {
        val fresh = build(listOf(reminder(0, undated = true, createdAt = now - hour)))
        assertEquals(1, fresh.toDo.size)
        val old = build(listOf(reminder(0, undated = true, createdAt = now - 4 * day)))
        assertEquals(ForgotReason.NO_TIME_SET, (old.forgot.single() as AttentionItem.Reminder).forgotReason)
    }

    @Test
    fun `undated items sort after timed ones`() {
        val d = build(listOf(reminder(0, undated = true, title = "any time"), reminder(hour, title = "timed")))
        assertEquals("timed", (d.toDo.first() as AttentionItem.Reminder).reminder.title)
    }

    @Test
    fun `putting something off again and again counts as forgetting it`() {
        val d = build(listOf(reminder(hour, ReminderStatus.SNOOZED, snoozes = 3)))
        assertEquals(ForgotReason.KEEPS_SLIPPING, (d.forgot.single() as AttentionItem.Reminder).forgotReason)
    }

    @Test
    fun `money out for a week is forgotten but a fresh loan is not`() {
        val fresh = LooseEndEntity(direction = LooseEndDirection.THEY_HAVE, person = "A", what = "500", createdAtMillis = now - 2 * day)
        val old = fresh.copy(id = 2, createdAtMillis = now - 8 * day)
        val settled = fresh.copy(id = 3, createdAtMillis = now - 30 * day, settledAtMillis = now)
        val d = build(looseEnds = listOf(fresh, old, settled))
        assertEquals(8, (d.forgot.single() as AttentionItem.Owed).daysOpen)
    }

    @Test
    fun `a message waits out its grace period`() {
        assertTrue(build(noticed = listOf(noticed(NoticedType.UNREAD_MESSAGE, surfaceAt = now + hour))).missed.isEmpty())
        assertEquals(1, build(noticed = listOf(noticed(NoticedType.UNREAD_MESSAGE))).missed.size)
    }

    @Test
    fun `old calls fade and handled ones never show`() {
        val old = noticed(NoticedType.MISSED_CALL, noticedAt = now - 4 * day)
        val handled = noticed(NoticedType.MISSED_CALL).copy(status = NoticedStatus.HANDLED)
        assertTrue(build(noticed = listOf(old, handled)).missed.isEmpty())
    }

    @Test
    fun `a bill stays until its date has passed`() {
        assertEquals(1, build(noticed = listOf(noticed(NoticedType.BILL, noticedAt = now - 5 * day, due = now + day))).missed.size)
        assertTrue(build(noticed = listOf(noticed(NoticedType.BILL, due = now - 3 * day))).missed.isEmpty())
    }

    @Test
    fun `calendar events join the day`() {
        val d = build(events = listOf(
            CalendarEvent(1, "Standup", now + hour, false),
            CalendarEvent(2, "Trip", now + 3 * day, false),
            CalendarEvent(3, "Finished", now - hour, false)
        ))
        assertEquals(1, d.toDo.size)
        assertEquals(1, d.comingUp.size)
    }

    @Test
    fun `hourly habits do not crowd coming up`() {
        val d = build(listOf(reminder(day, kind = ReminderKind.HABIT, interval = 120), reminder(2 * day, kind = ReminderKind.BILL)))
        assertEquals(1, d.comingUp.size)
    }

    @Test
    fun `the home sentence leads with what matters most`() {
        assertEquals("It's time." to "", AttentionEngine.headlines(1, 0, 0, 0, 0))
        assertEquals("You missed 2 things." to "1 forgotten, 3 left today.", AttentionEngine.headlines(0, 2, 1, 3, 0))
        assertEquals("You missed one thing." to "", AttentionEngine.headlines(0, 1, 0, 0, 0))
        assertEquals("One thing slipped away." to "2 left today.", AttentionEngine.headlines(0, 0, 1, 2, 0))
        assertEquals("3 things left today." to "", AttentionEngine.headlines(0, 0, 0, 3, 2))
        assertEquals("Today is clear." to "One thing coming up.", AttentionEngine.headlines(0, 0, 0, 0, 1))
        assertEquals("All clear." to "", AttentionEngine.headlines(0, 0, 0, 0, 0))
    }

    @Test
    fun `morning brief says something useful or stays quiet`() {
        assertEquals(null, BriefComposer.morning(build(), "Rishi", now, zone))
        val brief = BriefComposer.morning(
            build(
                reminders = listOf(reminder(2 * hour, title = "Call dentist"), reminder(day, title = "Pay rent", kind = ReminderKind.BILL)),
                looseEnds = listOf(LooseEndEntity(direction = LooseEndDirection.THEY_HAVE, person = "Rahul", what = "500", amountRupees = 500, createdAtMillis = now - 10 * day))
            ),
            "Rishi", now, zone
        )!!
        assertEquals("Good morning, Rishi", brief.title)
        assertTrue(brief.body, brief.body.contains("Call dentist at 1:00 pm"))
        assertTrue(brief.body, brief.body.contains("Pay rent, tomorrow"))
        assertTrue(brief.body, brief.body.contains("Rahul still owes you ₹500."))
    }

    @Test
    fun `evening wrap offers to move things and watches the battery`() {
        val open = build(listOf(reminder(hour, title = "Call dentist")))
        val brief = BriefComposer.evening(open, batteryPercent = 18, isCharging = false, nowMillis = now, zone = zone)!!
        assertTrue(brief.body, brief.body.contains("Still open: Call dentist."))
        assertTrue(brief.body, brief.body.contains("Phone is at 18%"))
        assertTrue(brief.offerMoveToTomorrow)

        assertEquals(null, BriefComposer.evening(build(), batteryPercent = 18, isCharging = true, nowMillis = now, zone = zone))
        assertEquals(null, BriefComposer.evening(build(), batteryPercent = 90, isCharging = false, nowMillis = now, zone = zone))
    }

    @Test
    fun `repeating reminders are not offered the move`() {
        val habit = ReminderEntity(title = "Vitamins", triggerAtMillis = now + hour, recurrenceRule = RecurrenceRule.DAILY)
        val brief = BriefComposer.evening(build(listOf(habit)), null, false, now, zone)!!
        assertEquals(false, brief.offerMoveToTomorrow)
    }

    @Test
    fun `time words`() {
        assertEquals("1:00 pm", TimeWords.moment(now + 2 * hour, now, zone))
        assertEquals("Tomorrow, 11:00 am", TimeWords.moment(now + day, now, zone))
        assertEquals("Friday, 11:00 am", TimeWords.moment(now + 2 * day, now, zone))
        assertEquals("14 Nov", TimeWords.moment(now + 10 * day, now, zone))
        assertEquals("20 min ago", TimeWords.ago(now - 20 * minute, now, zone))
        assertEquals("2 hours ago", TimeWords.ago(now - 2 * hour, now, zone))
        assertEquals("yesterday", TimeWords.ago(now - day, now, zone))
        assertEquals("3 days ago", TimeWords.ago(now - 3 * day, now, zone))
        assertEquals("₹500", TimeWords.rupees(500))
        assertEquals("₹1,240", TimeWords.rupees(1240))
        assertEquals("₹12,34,567", TimeWords.rupees(1234567))
    }
}
