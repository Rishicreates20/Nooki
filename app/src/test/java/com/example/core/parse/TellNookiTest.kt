package com.example.core.parse

import com.example.core.model.RecurrenceRule
import com.example.core.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Every example from the product brief's "Tell Nooki" section, plus close variants. */
class TellNookiTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    // Wednesday 4 November 2026, 11:00 local time.
    private val now: Long = at(2026, Calendar.NOVEMBER, 4, 11, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun tell(text: String) = TellNooki.understand(text, now, zone)

    private fun reminder(text: String): ParsedReminder {
        val u = tell(text)
        assertTrue("Expected a reminder for '$text' but got $u", u is Understood.Reminder)
        return (u as Understood.Reminder).reminder
    }

    // ---- English ------------------------------------------------------------------------------

    @Test
    fun `pay rent on the 1st every month`() {
        val r = reminder("pay rent on the 1st every month")
        assertEquals("Pay rent", r.title)
        assertEquals(ReminderKind.BILL, r.kind)
        assertEquals(RecurrenceRule.MONTHLY, r.recurrenceRule)
        assertEquals(at(2026, Calendar.DECEMBER, 1, 9, 0), r.triggerAtMillis)
        assertEquals(listOf(3 * 24 * 60, 24 * 60), r.leadMinutes)
    }

    @Test
    fun `a thought without a time is still kept`() {
        val r = reminder("buy milk")
        assertEquals("Buy milk", r.title)
        assertNull(r.triggerAtMillis)
    }

    // ---- Hinglish -----------------------------------------------------------------------------

    @Test
    fun `kal subah 9 baje doctor`() {
        val r = reminder("kal subah 9 baje doctor")
        assertEquals("Doctor", r.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), r.triggerAtMillis)
    }

    @Test
    fun `har somvar gym`() {
        val r = reminder("har somvar gym")
        assertEquals("Gym", r.title)
        assertEquals(RecurrenceRule.WEEKLY, r.recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 9, 9, 0), r.triggerAtMillis)
    }

    @Test
    fun `more Hinglish times`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 19, 0), reminder("shaam 7 baje mummy ko call").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 21, 30), reminder("parso raat saade 9 baje movie").triggerAtMillis)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 8, 45), reminder("kal subah pone 9 baje doctor").triggerAtMillis)
        assertEquals(now + 2 * 3_600_000L, reminder("2 ghante baad chai").triggerAtMillis)
        assertEquals(RecurrenceRule.DAILY, reminder("roz raat 10 baje dawai").recurrenceRule)
        val rent = reminder("har mahine 5 tarikh ko rent")
        assertEquals(RecurrenceRule.MONTHLY, rent.recurrenceRule)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 9, 0), rent.triggerAtMillis)
    }

    // ---- Hindi (what voice input produces) -----------------------------------------------------

    @Test
    fun `Hindi medicine tomorrow evening at 6`() {
        val r = reminder("कल शाम 6 बजे दवाई")
        assertEquals("दवाई", r.title)
        assertEquals(ReminderKind.MEDICINE, r.kind)
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 18, 0), r.triggerAtMillis)
    }

    @Test
    fun `Devanagari digits`() {
        assertEquals(at(2026, Calendar.NOVEMBER, 5, 18, 0), reminder("कल शाम ६ बजे दवाई").triggerAtMillis)
    }

    // ---- Things to hold -----------------------------------------------------------------------

    @Test
    fun `passport is in the blue bag`() {
        assertEquals(Understood.SavedThing("Passport", "in the blue bag"), tell("passport is in the blue bag"))
    }

    @Test
    fun `saved things from the brief`() {
        assertEquals(Understood.SavedThing("Car", "B2, pillar 14"), tell("Car parked at B2, pillar 14"))
        assertEquals(Understood.SavedThing("Spare key", "with Sharma aunty"), tell("Spare key is with Sharma aunty"))
        assertEquals(Understood.SavedThing("Passport", "almirah mein"), tell("passport almirah mein rakha hai"))
        assertEquals(Understood.SavedThing("Charger", "in the drawer"), tell("kept my charger in the drawer"))
    }

    @Test
    fun `a time makes it a reminder, not a saved thing`() {
        assertTrue(tell("meeting is at 5pm") is Understood.Reminder)
    }

    // ---- Money and lending --------------------------------------------------------------------

    @Test
    fun `Rahul owes me 500`() {
        assertEquals(Understood.Money("Rahul", MoneyDirection.THEY_OWE_ME, 50_000L, null), tell("Rahul owes me 500"))
    }

    @Test
    fun `lent charger to Aman`() {
        assertEquals(Understood.Money("Aman", MoneyDirection.THEY_OWE_ME, null, "charger"), tell("lent charger to Aman"))
    }

    @Test
    fun `more money phrasings`() {
        assertEquals(Understood.Money("roommate", MoneyDirection.THEY_OWE_ME, 30_000L, null), tell("roommate owes ₹300"))
        assertEquals(Understood.Money("Priya", MoneyDirection.I_OWE_THEM, 20_000L, null), tell("I owe Priya 200"))
        assertEquals(Understood.Money("Sam", MoneyDirection.I_OWE_THEM, 150_050L, null), tell("borrowed Rs 1,500.50 from Sam"))
        assertEquals(Understood.Money("Aman", MoneyDirection.THEY_OWE_ME, 100_000L, null), tell("lent 1000 to Aman"))
        assertEquals(Understood.Money("Rahul", MoneyDirection.THEY_OWE_ME, 50_000L, null), tell("Rahul ko 500 diye"))
        assertEquals(Understood.Money("Neha", MoneyDirection.I_OWE_THEM, 20_000L, null), tell("Neha se 200 liye"))
        assertEquals(Understood.Money("Aman", MoneyDirection.I_OWE_THEM, null, "book"), tell("borrowed a book from Aman"))
    }

    // ---- Questions ----------------------------------------------------------------------------

    @Test
    fun `where is my passport`() {
        assertEquals(Understood.Question("passport", aboutMoney = false), tell("where is my passport"))
        assertEquals(Understood.Question("passport", aboutMoney = false), tell("Where's my passport?"))
        assertEquals(Understood.Question("passport", aboutMoney = false), tell("mera passport kahan hai"))
        assertEquals(Understood.Question("car keys", aboutMoney = false), tell("where did i put the car keys"))
    }

    @Test
    fun `who owes me`() {
        assertEquals(Understood.Question("", aboutMoney = true), tell("who owes me money?"))
    }

    // ---- Dates that matter --------------------------------------------------------------------

    @Test
    fun `birthdays repeat every year and warn 7 and 1 days before`() {
        val r = reminder("Mom's birthday on 12 March")
        assertEquals(ReminderKind.BIRTHDAY, r.kind)
        assertEquals(RecurrenceRule.YEARLY, r.recurrenceRule)
        assertEquals(listOf(7 * 24 * 60, 24 * 60), r.leadMinutes)
    }

    @Test
    fun `expiries warn 30, 7 and 1 days before`() {
        val r = reminder("passport expires on 3 march")
        assertEquals(ReminderKind.EXPIRY, r.kind)
        assertEquals(listOf(30 * 24 * 60, 7 * 24 * 60, 24 * 60), r.leadMinutes)
    }

    @Test
    fun `empty input`() {
        assertEquals(Understood.Empty, tell("   "))
    }
}
