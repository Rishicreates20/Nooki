package com.example.core.parse

import com.example.core.model.RecurrenceRule
import com.example.core.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class WorkflowParsingTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    // Wednesday 4 November 2026, 11:00 local time.
    private val now: Long = at(2026, Calendar.NOVEMBER, 4, 11, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun parse(text: String) = ReminderTextParser.parse(text, now, zone)

    @Test
    fun `exam on friday`() {
        val r = parse("exam on friday")
        assertEquals(ReminderKind.EXAM, r.kind)
        assertEquals("Exam", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 9, 0), r.triggerAtMillis)
        assertEquals(listOf(7 * 24 * 60, 24 * 60, 120), r.leadMinutes)
    }

    @Test
    fun `physics exam with a time`() {
        val r = parse("Physics exam on Friday at 10am")
        assertEquals(ReminderKind.EXAM, r.kind)
        assertEquals("Physics exam", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 10, 0), r.triggerAtMillis)
    }

    @Test
    fun `claude limit resets in 5h`() {
        val r = parse("claude limit resets in 5h")
        assertEquals(ReminderKind.AI_LIMIT, r.kind)
        assertEquals("Claude limit resets", r.title)
        assertEquals(now + 5 * 3_600_000L, r.triggerAtMillis)
        assertEquals(emptyList<Int>(), r.leadMinutes)
    }

    @Test
    fun `other ai tools and phrasings`() {
        assertEquals(ReminderKind.AI_LIMIT, parse("chatgpt quota back at 3pm").kind)
        assertEquals(ReminderKind.AI_LIMIT, parse("Cursor credits reset on the 1st").kind)
        assertEquals(ReminderKind.AI_LIMIT, parse("gemini usage cap resets tomorrow 9am").kind)
        // An AI word without a limit word is just a task.
        assertEquals(ReminderKind.TASK, parse("read the claude docs tomorrow").kind)
    }

    @Test
    fun `assignment due monday is a deadline`() {
        val r = parse("Assignment due Monday 11:59pm")
        assertEquals(ReminderKind.DEADLINE, r.kind)
        assertEquals("Assignment due", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 9, 23, 59), r.triggerAtMillis)
        assertEquals(listOf(3 * 24 * 60, 24 * 60, 180), r.leadMinutes)
    }

    @Test
    fun `rent on the 1st every month`() {
        val r = parse("Pay rent on the 1st every month")
        assertEquals(ReminderKind.BILL, r.kind)
        assertEquals("Pay rent", r.title)
        assertEquals(RecurrenceRule.MONTHLY, r.recurrenceRule)
        assertEquals(at(2026, Calendar.DECEMBER, 1, 9, 0), r.triggerAtMillis)
    }

    @Test
    fun `day of month later this month`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 15, 9, 0), parse("netflix renews on the 15th").triggerAtMillis)
        assertEquals(ReminderKind.BILL, parse("netflix renews on the 15th").kind)
        // "the 31st" in a 30-day month lands on the last day.
        assertEquals(at(2026, Calendar.NOVEMBER, 30, 9, 0), parse("submit form on the 31st").triggerAtMillis)
    }

    @Test
    fun `custom heads-ups replace the defaults`() {
        val r = parse("maths exam on dec 3, remind me 2 days before and 1 hour before")
        assertEquals(ReminderKind.EXAM, r.kind)
        assertEquals("Maths exam", r.title)
        assertEquals(listOf(2 * 24 * 60, 60), r.leadMinutes)
        assertEquals(at(2026, Calendar.DECEMBER, 3, 9, 0), r.triggerAtMillis)

        assertEquals(listOf(24 * 60), parse("project deadline friday, a day before").leadMinutes)
        assertEquals(listOf(24 * 60), parse("submit essay friday 5pm remind me the day before").leadMinutes)
    }

    @Test
    fun `daily and interval habits`() {
        val meds = parse("take vitamins every day at 9pm")
        assertEquals(ReminderKind.HABIT, meds.kind)
        assertEquals("Take vitamins", meds.title)
        assertEquals(RecurrenceRule.DAILY, meds.recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 21, 0), meds.triggerAtMillis)

        val water = parse("Drink water every 2 hours")
        assertEquals(ReminderKind.HABIT, water.kind)
        assertEquals("Drink water", water.title)
        assertEquals(120, water.recurrenceMinutes)
        assertEquals(now + 2 * 3_600_000L, water.triggerAtMillis)

        val morning = parse("journal every morning")
        assertEquals(RecurrenceRule.DAILY, morning.recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), morning.triggerAtMillis)

        // A daily habit with no time asks for one rather than guessing.
        assertNull(parse("stretch daily").triggerAtMillis)
    }

    @Test
    fun `weekly on a named day`() {
        val r = parse("water the plants every saturday at 10am")
        assertEquals(RecurrenceRule.WEEKLY, r.recurrenceRule)
        assertEquals("Water the plants", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 7, 10, 0), r.triggerAtMillis)

        assertEquals(RecurrenceRule.WEEKDAYS, parse("standup every weekday at 9:30am").recurrenceRule)
    }

    @Test
    fun `plain reminders are unchanged`() {
        val r = parse("call mom tomorrow at 6pm")
        assertEquals(ReminderKind.TASK, r.kind)
        assertNull(r.recurrenceRule)
        assertNull(r.recurrenceMinutes)
        assertEquals(emptyList<Int>(), r.leadMinutes)
    }
}
