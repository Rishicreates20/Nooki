package com.example.workflow

import com.example.data.local.entity.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class WorkflowScheduleTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun at(year: Int, month: Int, d: Int, h: Int, m: Int): Long =
        Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(year, month, d, h, m, 0)
        }.timeInMillis

    @Test
    fun `lead times round trip`() {
        assertEquals("10080,1440,120", WorkflowSchedule.encodeLeadTimes(listOf(120, 10080, 1440, 120)))
        assertEquals(listOf(10080, 1440, 120), WorkflowSchedule.decodeLeadTimes("120, 1440,10080,x,"))
        assertEquals(emptyList<Int>(), WorkflowSchedule.decodeLeadTimes(""))
        assertEquals(emptyList<Int>(), WorkflowSchedule.decodeLeadTimes(null))
    }

    @Test
    fun `next heads-up is the earliest one still ahead`() {
        val now = 0L
        val exam = 10 * day
        val leads = listOf(7 * 24 * 60, 24 * 60, 120)
        assertEquals(LeadAlert(exam - 7 * day, 7 * 24 * 60), WorkflowSchedule.nextLeadAlert(exam, leads, now))
        // After the week-before heads-up has fired, the day-before one is next.
        assertEquals(LeadAlert(exam - day, 24 * 60), WorkflowSchedule.nextLeadAlert(exam, leads, exam - 7 * day + 1_000))
        // An exam only 3 days away skips the week-before heads-up.
        assertEquals(24 * 60, WorkflowSchedule.nextLeadAlert(3 * day, leads, now)?.leadMinutes)
        // All passed.
        assertNull(WorkflowSchedule.nextLeadAlert(exam, leads, exam - hour))
        assertNull(WorkflowSchedule.nextLeadAlert(exam, emptyList(), now))
    }

    @Test
    fun `describe leads and countdowns`() {
        assertEquals("1 week", WorkflowSchedule.describeLead(7 * 24 * 60))
        assertEquals("2 days", WorkflowSchedule.describeLead(2 * 24 * 60))
        assertEquals("3 hours", WorkflowSchedule.describeLead(180))
        assertEquals("1 hour 30 min", WorkflowSchedule.describeLead(90))
        assertEquals("45 min", WorkflowSchedule.describeLead(45))

        assertEquals("6 days left", WorkflowSchedule.countdown(6 * day + hour, 0))
        assertEquals("1 day 5 h left", WorkflowSchedule.countdown(29 * hour, 0))
        assertEquals("1 day left", WorkflowSchedule.countdown(24 * hour, 0))
        assertEquals("5 h left", WorkflowSchedule.countdown(5 * hour, 0))
        assertEquals("2 h 15 min left", WorkflowSchedule.countdown(2 * hour + 15 * minute, 0))
        assertEquals("12 min left", WorkflowSchedule.countdown(12 * minute, 0))
        assertEquals("now", WorkflowSchedule.countdown(0, 5))
    }

    @Test
    fun `monthly keeps the date and clamps short months`() {
        val jan31 = at(2027, Calendar.JANUARY, 31, 9, 0)
        val feb = WorkflowSchedule.nextOccurrence(jan31, RecurrenceRule.MONTHLY, jan31 + minute, zone)
        assertEquals(at(2027, Calendar.FEBRUARY, 28, 9, 0), feb)
        // Back to the 31st in March, not stuck on the 28th.
        assertEquals(
            at(2027, Calendar.MARCH, 31, 9, 0),
            WorkflowSchedule.nextOccurrence(jan31, RecurrenceRule.MONTHLY, feb + minute, zone)
        )
    }

    @Test
    fun `daily weekly weekdays and yearly`() {
        val wed = at(2026, Calendar.NOVEMBER, 4, 21, 0)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 21, 0), WorkflowSchedule.nextOccurrence(wed, RecurrenceRule.DAILY, wed, zone))
        // Missed a few days: jump to the next one after now, not the day after the old one.
        assertEquals(
            at(2026, Calendar.NOVEMBER, 8, 21, 0),
            WorkflowSchedule.nextOccurrence(wed, RecurrenceRule.DAILY, at(2026, Calendar.NOVEMBER, 8, 10, 0), zone)
        )
        assertEquals(at(2026, Calendar.NOVEMBER, 11, 21, 0), WorkflowSchedule.nextOccurrence(wed, RecurrenceRule.WEEKLY, wed, zone))
        val fri = at(2026, Calendar.NOVEMBER, 6, 9, 30)
        assertEquals(at(2026, Calendar.NOVEMBER, 9, 9, 30), WorkflowSchedule.nextOccurrence(fri, RecurrenceRule.WEEKDAYS, fri, zone))
        val leap = at(2028, Calendar.FEBRUARY, 29, 9, 0)
        assertEquals(at(2029, Calendar.FEBRUARY, 28, 9, 0), WorkflowSchedule.nextOccurrence(leap, RecurrenceRule.YEARLY, leap, zone))
    }

    @Test
    fun `next daily lands later today or tomorrow`() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 4, 11, 0, 0)
        }
        val now = cal.timeInMillis
        assertEquals(now + 10 * 3_600_000L, WorkflowSchedule.nextDaily(21 * 60, now, zone))
        assertEquals(now + 21 * 3_600_000L, WorkflowSchedule.nextDaily(8 * 60, now, zone))
    }
}
