package com.example.parser

import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class HinglishParsingTest {
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
    fun `kal subah 9 baje doctor`() {
        val r = parse("kal subah 9 baje doctor")
        assertEquals("Doctor", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), r.triggerAtMillis)
    }

    @Test
    fun `parso shaam with a task in hindi`() {
        val r = parse("parso shaam 6 baje gas cylinder book karna")
        assertEquals("Gas cylinder book karna", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 18, 0), r.triggerAtMillis)
    }

    @Test
    fun `aaj raat medicine keeps asking`() {
        val r = parse("aaj raat 10 baje dawai")
        assertEquals("Dawai", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 22, 0), r.triggerAtMillis)
        assertEquals(ReminderKind.MEDICINE, r.kind)
        assertTrue(r.keepAsking)
    }

    @Test
    fun `raat after midnight stays in the early morning`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 2, 0), parse("raat 2 baje alarm check").triggerAtMillis)
    }

    @Test
    fun `sawa sadhe and paune`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 17, 30), parse("sadhe 5 baje meeting").triggerAtMillis)
        assertEquals("Meeting", parse("sadhe 5 baje meeting").title)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 7, 15), parse("kal subah sawa 7 baje walk").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 17, 45), parse("paune 6 baje chai").triggerAtMillis)
    }

    @Test
    fun `relative hindi durations`() {
        assertEquals(now + 2 * 3_600_000L, parse("2 ghante baad chai").triggerAtMillis)
        assertEquals("Chai", parse("2 ghante baad chai").title)
        assertEquals(now + 30 * 60_000L, parse("aadhe ghante baad call karna").triggerAtMillis)
        assertEquals(now + 20 * 60_000L, parse("20 minute baad doodh dekhna").triggerAtMillis)
        assertEquals(now + 3 * 24 * 3_600_000L, parse("teen din baad bank jana").triggerAtMillis)
    }

    @Test
    fun `hindi repeats`() {
        val gym = parse("har somvar subah 7 baje gym")
        assertEquals(RecurrenceRule.WEEKLY, gym.recurrenceRule)
        assertEquals("Gym", gym.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 9, 7, 0), gym.triggerAtMillis)

        val walk = parse("roz shaam 7 baje walk")
        assertEquals(RecurrenceRule.DAILY, walk.recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 19, 0), walk.triggerAtMillis)

        val morning = parse("har subah 6 baje yoga")
        assertEquals(RecurrenceRule.DAILY, morning.recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 6, 0), morning.triggerAtMillis)

        assertEquals(120, parse("har 2 ghante paani peena").recurrenceMinutes)
        assertEquals(RecurrenceRule.MONTHLY, parse("har mahine 5 tarikh ko school fees").recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), parse("har mahine 5 tarikh ko school fees").triggerAtMillis)
    }

    @Test
    fun `hindi weekdays and next`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 9, 0), parse("shukravar ko exam").triggerAtMillis)
        assertEquals(ReminderKind.EXAM, parse("shukravar ko exam").kind)
        assertEquals(at(2026, Calendar.NOVEMBER, 9, 18, 0), parse("agle somvar shaam 6 baje meeting").triggerAtMillis)
    }

    @Test
    fun `devanagari from voice input`() {
        val r = parse("कल सुबह 9 बजे डॉक्टर")
        assertEquals("डॉक्टर", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), r.triggerAtMillis)

        val tablet = parse("आज रात १० बजे दवाई")
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 22, 0), tablet.triggerAtMillis)
        assertEquals(ReminderKind.MEDICINE, tablet.kind)

        assertEquals(RecurrenceRule.WEEKLY, parse("हर सोमवार शाम 6 बजे क्लास").recurrenceRule)
        assertEquals(now + 2 * 3_600_000L, parse("2 घंटे बाद चाय").triggerAtMillis)
    }

    @Test
    fun `a word that only contains a hindi word is left alone`() {
        val r = parse("call Kalpana tomorrow at 5pm")
        assertEquals("Call Kalpana", r.title)
    }

    @Test
    fun `reminder filler in hindi is removed`() {
        assertEquals("Bijli ka bill", parse("mujhe yaad dilana kal bijli ka bill").title)
    }

    @Test
    fun `english every morning keeps the morning`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 6, 0), parse("run every morning at 6").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 8, 30), parse("journal every morning at 8:30am").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 19, 0), parse("walk every evening at 7").triggerAtMillis)
    }

    @Test
    fun `new kinds set themselves up`() {
        val birthday = parse("maa's birthday on 12 march")
        assertEquals(ReminderKind.BIRTHDAY, birthday.kind)
        assertEquals(RecurrenceRule.YEARLY, birthday.recurrenceRule)
        assertEquals(listOf(7 * 24 * 60, 24 * 60), birthday.leadMinutes)

        val insurance = parse("car insurance expires on 12 january")
        assertEquals(ReminderKind.EXPIRY, insurance.kind)
        assertEquals(listOf(30 * 24 * 60, 7 * 24 * 60, 24 * 60), insurance.leadMinutes)
        assertEquals("Car insurance expires", insurance.title)

        // Shopping for medicine is an errand, not a dose.
        assertEquals(ReminderKind.TASK, parse("buy tablets tomorrow").kind)
    }

    @Test
    fun `keeping in touch counts from the last time`() {
        assertEquals(7 * 24 * 60, parse("call mom every week").recurrenceMinutes)
        assertEquals(null, parse("call mom every week").recurrenceRule)
        assertEquals(7 * 24 * 60, parse("call mom every 7 days").recurrenceMinutes)
        assertEquals(14 * 24 * 60, parse("visit dadi every 2 weeks").recurrenceMinutes)
        // A named day stays on the calendar.
        assertEquals(RecurrenceRule.WEEKLY, parse("call home every sunday at 7pm").recurrenceRule)
    }

    @Test
    fun `indian dates are day first and can carry a year`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 12, 9, 0), parse("submit form on 12/11").triggerAtMillis)
        assertEquals(at(2026, Calendar.DECEMBER, 25, 9, 0), parse("party on 12/25").triggerAtMillis)
        assertEquals(at(2027, Calendar.MARCH, 15, 9, 0), parse("passport expires 15/03/2027").triggerAtMillis)
        assertEquals(at(2028, Calendar.JANUARY, 2, 9, 0), parse("renew licence on 2 jan 2028").triggerAtMillis)
        assertEquals("Renew licence", parse("renew licence on 2 jan 2028").title)
    }
}
