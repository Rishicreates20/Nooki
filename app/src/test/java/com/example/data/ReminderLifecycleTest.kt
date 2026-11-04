package com.example.data

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.NookiDatabase
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderStatus
import com.example.data.repository.AttentionRepository
import com.example.data.repository.ReminderRepositoryImpl
import com.example.notice.NoticedCandidate
import com.example.scheduler.ExactAlarmScheduler
import com.example.scheduler.ScheduleResult
import com.example.workflow.LeadAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The promise that matters most: nothing quietly disappears. Runs against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderLifecycleTest {

    private class FakeScheduler : ExactAlarmScheduler {
        val armed = mutableMapOf<Long, Long>()
        val cancelled = mutableListOf<Long>()
        override fun canScheduleExactAlarms() = true
        override fun scheduleExact(reminder: ReminderEntity): ScheduleResult {
            if (reminder.undated) return ScheduleResult.NoAlarmNeeded(reminder.id)
            armed[reminder.id] = reminder.triggerAtMillis
            return ScheduleResult.ExactScheduled(reminder.id, reminder.triggerAtMillis)
        }
        override fun cancel(reminderId: Long) {
            armed.remove(reminderId)
            cancelled += reminderId
        }
        override fun buildExactAlarmPermissionIntent(): Intent? = null
        override fun scheduleLeadAlert(reminder: ReminderEntity, alert: LeadAlert) {}
        override fun cancelLeadAlert(reminderId: Long) {}
    }

    private lateinit var db: NookiDatabase
    private lateinit var scheduler: FakeScheduler
    private lateinit var repository: ReminderRepositoryImpl
    private lateinit var attention: AttentionRepository
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NookiDatabase::class.java).allowMainThreadQueries().build()
        scheduler = FakeScheduler()
        repository = ReminderRepositoryImpl(db.reminderDao(), scheduler)
        attention = AttentionRepository(db.attentionDao())
    }

    @After
    fun tearDown() = db.close()

    private fun all() = runBlocking { repository.allReminders.first() }

    private fun add(
        trigger: Long = now + hour,
        rule: RecurrenceRule? = null,
        interval: Int? = null,
        keepAsking: Boolean = false,
        undated: Boolean = false
    ) = runBlocking {
        repository.createAndScheduleReminder(
            title = "Thing",
            triggerAtMillis = trigger,
            recurrenceRule = rule,
            recurrenceMinutes = interval,
            keepAsking = keepAsking,
            undated = undated
        ).first
    }

    @Test
    fun `an ignored monthly reminder still books next month`() = runBlocking {
        val rent = add(rule = RecurrenceRule.MONTHLY)
        repository.onAlarmFired(rent.id, now + hour)
        repository.dismissReminder(rent.id)

        val rows = all()
        assertEquals(ReminderStatus.MISSED, rows.first { it.id == rent.id }.status)
        val next = rows.single { it.id != rent.id }
        assertEquals(ReminderStatus.SCHEDULED, next.status)
        assertTrue(next.triggerAtMillis > now + 27 * 24 * hour)
        assertTrue(next.id in scheduler.armed)
    }

    @Test
    fun `done after ringing does not book the next one twice`() = runBlocking {
        val daily = add(rule = RecurrenceRule.DAILY)
        repository.onAlarmFired(daily.id, now + hour)
        repository.completeReminder(daily.id)
        assertEquals(2, all().size)
    }

    @Test
    fun `done early still moves the series on`() = runBlocking {
        val rent = add(trigger = now + 5 * 24 * hour, rule = RecurrenceRule.MONTHLY)
        repository.completeReminder(rent.id)
        assertEquals(2, all().size)
    }

    @Test
    fun `a second done never pays out twice`() = runBlocking {
        val daily = add(rule = RecurrenceRule.DAILY)
        repository.completeReminder(daily.id)
        repository.completeReminder(daily.id)
        assertEquals(2, all().size)
    }

    @Test
    fun `swiping away a one off keeps it in missed`() = runBlocking {
        val one = add()
        repository.onAlarmFired(one.id, now + hour)
        repository.dismissReminder(one.id)
        assertEquals(ReminderStatus.MISSED, repository.getReminderById(one.id)!!.status)
    }

    @Test
    fun `water every 2 hours skips quietly and carries on`() = runBlocking {
        val water = add(interval = 120)
        repository.onAlarmFired(water.id, now + hour)
        repository.dismissReminder(water.id)
        val rows = all()
        assertEquals(ReminderStatus.CANCELLED, rows.first { it.id == water.id }.status)
        assertEquals(ReminderStatus.SCHEDULED, rows.single { it.id != water.id }.status)
    }

    @Test
    fun `call mom every 7 days waits for you instead of stacking up`() = runBlocking {
        val mom = add(interval = 7 * 24 * 60)
        repository.onAlarmFired(mom.id, now + hour)
        repository.dismissReminder(mom.id)
        assertEquals(1, all().size)
        repository.completeReminder(mom.id)
        assertEquals(2, all().size)
    }

    @Test
    fun `medicine keeps asking up to the limit then stops`() = runBlocking {
        val tablet = add(keepAsking = true)
        var asks = 0
        var fired = repository.onAlarmFired(tablet.id, now + hour)
        while (fired != null) {
            asks++
            fired = repository.onAlarmFired(tablet.id, now + hour + asks * 10 * 60_000L)
        }
        assertEquals(ReminderEntity.MAX_ASKS + 1, asks)
        assertEquals(ReminderEntity.MAX_ASKS, repository.getReminderById(tablet.id)!!.askCount)
    }

    @Test
    fun `an ordinary reminder rings once`() = runBlocking {
        val one = add()
        assertTrue(repository.onAlarmFired(one.id, now + hour) != null)
        assertNull(repository.onAlarmFired(one.id, now + hour + 1000))
    }

    @Test
    fun `an alarm for something finished stays silent`() = runBlocking {
        val one = add()
        repository.completeReminder(one.id)
        assertNull(repository.onAlarmFired(one.id, now + hour))
        val gone = add()
        repository.deleteReminder(gone.id)
        assertNull(repository.onAlarmFired(gone.id, now + hour))
    }

    @Test
    fun `no time means no alarm and boot recovery leaves it alone`() = runBlocking {
        val milk = add(trigger = now, undated = true)
        assertFalse(milk.id in scheduler.armed)
        val report = repository.reconcileAndRescheduleAlarms(now + 10 * hour)
        assertEquals(0, report.markedMissedCount)
        assertEquals(ReminderStatus.SCHEDULED, repository.getReminderById(milk.id)!!.status)
    }

    @Test
    fun `giving an undated item a time arms it`() = runBlocking {
        val milk = add(trigger = now, undated = true)
        repository.rescheduleReminder(milk.id, now + hour)
        val updated = repository.getReminderById(milk.id)!!
        assertFalse(updated.undated)
        assertEquals(now + hour, scheduler.armed[milk.id])
    }

    @Test
    fun `boot recovery restores the future and owns up to the past`() = runBlocking {
        val future = add(trigger = now + hour)
        val past = add(trigger = now - hour)
        val pastMonthly = add(trigger = now - hour, rule = RecurrenceRule.MONTHLY)
        scheduler.armed.clear()

        val report = repository.reconcileAndRescheduleAlarms(now)

        assertEquals(1, report.rescheduledCount)
        assertEquals(2, report.markedMissedCount)
        assertTrue(future.id in scheduler.armed)
        assertEquals(ReminderStatus.MISSED, repository.getReminderById(past.id)!!.status)
        assertEquals(4, all().size) // the monthly one booked its next date
        assertEquals(ReminderStatus.MISSED, repository.getReminderById(pastMonthly.id)!!.status)
    }

    @Test
    fun `undo after done puts it back without a second next occurrence`() = runBlocking {
        val daily = add(rule = RecurrenceRule.DAILY)
        repository.completeReminder(daily.id)
        repository.restoreReminder(daily)
        repository.completeReminder(daily.id)
        assertEquals(2, all().size)
    }

    @Test
    fun `move to tomorrow takes one offs and leaves repeats alone`() = runBlocking {
        val todayOne = add(trigger = now + 1000)
        val missed = add(trigger = now - hour)
        repository.dismissReminder(missed.id)
        val daily = add(trigger = now + 1000, rule = RecurrenceRule.DAILY)

        assertEquals(2, repository.moveOpenToTomorrow(now))

        assertTrue(repository.getReminderById(todayOne.id)!!.triggerAtMillis > now + hour)
        assertEquals(ReminderStatus.SCHEDULED, repository.getReminderById(missed.id)!!.status)
        assertEquals(now + 1000, repository.getReminderById(daily.id)!!.triggerAtMillis)
    }

    @Test
    fun `kind travels down a series`() = runBlocking {
        val first = repository.createAndScheduleReminder(
            title = "Tablet", triggerAtMillis = now + hour, kind = ReminderKind.MEDICINE,
            recurrenceRule = RecurrenceRule.DAILY, keepAsking = true
        ).first
        repository.onAlarmFired(first.id, now + hour)
        val next = all().single { it.id != first.id }
        assertEquals(ReminderKind.MEDICINE, next.kind)
        assertTrue(next.keepAsking)
    }

    private fun call(person: String = "Mom", at: Long = now) = NoticedCandidate(
        type = NoticedType.MISSED_CALL, title = "$person called", person = person, appLabel = "Phone",
        packageName = "phone", dedupeKey = "call|$person", noticedAtMillis = at, surfaceAtMillis = at
    )

    @Test
    fun `calls from the same person add up`() = runBlocking {
        attention.ingest(call())
        attention.ingest(call(at = now + 1000))
        val rows = attention.noticed.first()
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().times)
    }

    @Test
    fun `a new call reopens one already handled`() = runBlocking {
        attention.ingest(call())
        attention.setNoticedStatus(attention.noticed.first().single(), NoticedStatus.HANDLED, now)
        assertTrue(attention.noticed.first().isEmpty())
        attention.ingest(call(at = now + hour))
        assertEquals(1, attention.noticed.first().single().times)
    }

    @Test
    fun `a handled bill stays handled`() = runBlocking {
        val bill = NoticedCandidate(
            type = NoticedType.BILL, title = "Bill", appLabel = "SMS", packageName = "sms",
            dedupeKey = "bill", noticedAtMillis = now, surfaceAtMillis = now
        )
        attention.ingest(bill)
        attention.setNoticedStatus(attention.noticed.first().single(), NoticedStatus.HANDLED, now)
        attention.ingest(bill)
        assertTrue(attention.noticed.first().isEmpty())
    }

    @Test
    fun `opening a message deletes it`() = runBlocking {
        val message = NoticedCandidate(
            type = NoticedType.UNREAD_MESSAGE, title = "Unread message from Aman", person = "Aman",
            appLabel = "WhatsApp", packageName = "wa", dedupeKey = "msg|aman",
            noticedAtMillis = now, surfaceAtMillis = now + hour
        )
        attention.ingest(message)
        attention.messageOpened("msg|aman")
        assertTrue(attention.noticed.first().isEmpty())
    }
}
