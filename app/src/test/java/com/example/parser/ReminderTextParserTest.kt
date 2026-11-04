package com.example.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReminderTextParserTest {
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
    fun `relative minutes and hours`() {
        val r = parse("stretch in 45 mins")
        assertEquals("Stretch", r.title)
        assertEquals(now + 45 * 60_000L, r.triggerAtMillis)

        assertEquals(now + 2 * 3_600_000L, parse("in 2 hours check the oven").triggerAtMillis)
        assertEquals("Check the oven", parse("in 2 hours check the oven").title)
        assertEquals(now + 3_600_000L, parse("call back in an hour").triggerAtMillis)
        assertEquals(now + 30 * 60_000L, parse("tea in half an hour").triggerAtMillis)
        assertEquals(now + 90 * 60_000L, parse("standup in 1h 30m").triggerAtMillis)
        assertEquals(now + 20 * 60_000L, parse("water plants 20 minutes from now").triggerAtMillis)
    }

    @Test
    fun `tomorrow at a clock time with remind me prefix`() {
        val r = parse("Remind me to call mom tomorrow at 6pm")
        assertEquals("Call mom", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 18, 0), r.triggerAtMillis)
    }

    @Test
    fun `weekday with minutes and meridiem`() {
        val r = parse("Submit report on Friday at 3:15 PM")
        assertEquals("Submit report", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 15, 15), r.triggerAtMillis)
    }

    @Test
    fun `same weekday means next week once it has passed`() {
        // Today is Wednesday 11:00; Wednesday 9:00 has passed.
        assertEquals(at(2026, Calendar.NOVEMBER, 11, 9, 0), parse("gym wednesday").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 17, 0), parse("gym wednesday at 5pm").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 11, 17, 0), parse("gym next wednesday at 5pm").triggerAtMillis)
    }

    @Test
    fun `time only rolls to tomorrow when already past`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 15, 0), parse("meeting at 3pm").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 8, 30), parse("meeting at 8:30am").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 17, 30), parse("review at 17:30").triggerAtMillis)
    }

    @Test
    fun `bare at hour assumes afternoon for small numbers`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 16, 0), parse("pick up laundry at 4").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 21, 0), parse("call dad tonight at 9").triggerAtMillis)
    }

    @Test
    fun `parts of day and defaults`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), parse("dentist tomorrow morning").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), parse("dentist tomorrow").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 20, 0), parse("journal tonight").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 12, 0), parse("lunch at noon").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 9, 0), parse("bills day after tomorrow").triggerAtMillis)
    }

    @Test
    fun `month dates roll into next year when past`() {
        assertEquals(at(2026, Calendar.DECEMBER, 25, 9, 0), parse("buy gifts dec 25").triggerAtMillis)
        assertEquals(at(2027, Calendar.MARCH, 3, 10, 0), parse("renew passport 3rd march at 10am").triggerAtMillis)
        assertEquals("Renew passport", parse("renew passport 3rd march at 10am").title)
        assertEquals(at(2027, Calendar.JANUARY, 2, 9, 0), parse("taxes on 1/2").triggerAtMillis)
    }

    @Test
    fun `no time leaves trigger empty and keeps the title`() {
        val r = parse("buy 2 apples a day")
        assertEquals("Buy 2 apples a day", r.title)
        assertNull(r.triggerAtMillis)
    }

    @Test
    fun `today without a time asks for one`() {
        val r = parse("dentist today")
        assertEquals("Dentist", r.title)
        assertNull(r.triggerAtMillis)
    }

    @Test
    fun `title words that look like days are kept`() {
        assertEquals("Sit in the sun", parse("sit in the sun at 4pm").title)
        assertEquals("Evening walk", parse("evening walk at 6pm").title)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 18, 0), parse("evening walk at 6pm").triggerAtMillis)
    }

    @Test
    fun `priority words are stripped`() {
        val urgent = parse("URGENT pay rent today at 5pm!!")
        assertEquals("Pay rent", urgent.title)
        assertEquals(PriorityHint.URGENT, urgent.priorityHint)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 17, 0), urgent.triggerAtMillis)

        val important = parse("important: email Priya in 10 min")
        assertEquals(PriorityHint.HIGH, important.priorityHint)
        assertEquals("Email Priya", important.title)
    }

    @Test
    fun `input that is only a time keeps something readable`() {
        val r = parse("remind me tomorrow at 7am")
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 7, 0), r.triggerAtMillis)
        assertEquals("Tomorrow at 7am", r.title)
    }
}
