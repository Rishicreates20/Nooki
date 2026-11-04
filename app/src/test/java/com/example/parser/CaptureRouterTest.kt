package com.example.parser

import com.example.data.local.entity.LooseEndDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CaptureRouterTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val now: Long = Calendar.getInstance(zone, Locale.US).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 4, 11, 0, 0)
    }.timeInMillis

    private fun route(text: String) = CaptureRouter.route(text, now, zone)

    private fun hold(text: String) = route(text) as Capture.Hold
    private fun lend(text: String) = route(text) as Capture.Lend

    @Test
    fun `where things are goes to remember`() {
        val passport = hold("passport is in the blue bag").draft
        assertEquals("Passport is in the blue bag", passport.text)
        assertEquals("passport", passport.subject)
        assertEquals("the blue bag", passport.place)

        assertEquals("Spare key", hold("my Spare key is with Sharma aunty").draft.subject)
        assertEquals("B2, pillar 14", hold("Car parked at B2, pillar 14").draft.place)
        assertEquals("charger", hold("I kept the charger in the top drawer").draft.subject)
        assertEquals("neeli bag mein", hold("passport neeli bag mein rakha hai").draft.place)
    }

    @Test
    fun `a sentence with a time is a reminder even if it looks like a place`() {
        assertTrue(route("meeting is at 5") is Capture.Remind)
        assertTrue(route("interview is on friday at 10am") is Capture.Remind)
    }

    @Test
    fun `plain note`() {
        assertEquals("Wifi name is NookiNet", hold("note: wifi name is NookiNet").draft.text)
        assertEquals("The shop closes on tuesdays", hold("remember that the shop closes on tuesdays").draft.text)
    }

    @Test
    fun `remember to is still a reminder`() {
        assertTrue(route("remember to call mom tomorrow") is Capture.Remind)
    }

    @Test
    fun `money owed to me`() {
        val d = lend("Rahul owes me 500").draft
        assertEquals(LooseEndDirection.THEY_HAVE, d.direction)
        assertEquals("Rahul", d.person)
        assertEquals(500L, d.amountRupees)

        assertEquals(1200L, lend("priya owes me ₹1,200").draft.amountRupees)
        assertEquals(2000L, lend("aman owes me 2k").draft.amountRupees)
        assertEquals("Priya", lend("priya owes me ₹1,200").draft.person)
    }

    @Test
    fun `money i owe`() {
        val d = lend("I owe Priya 200").draft
        assertEquals(LooseEndDirection.I_HAVE, d.direction)
        assertEquals(200L, d.amountRupees)
        assertEquals(LooseEndDirection.I_HAVE, lend("borrowed 300 from Aman").draft.direction)
    }

    @Test
    fun `things lent`() {
        val d = lend("lent charger to Aman").draft
        assertEquals(LooseEndDirection.THEY_HAVE, d.direction)
        assertEquals("Aman", d.person)
        assertEquals("charger", d.what)
        assertNull(d.amountRupees)
        assertEquals("cricket bat", lend("Rohan borrowed my cricket bat").draft.what)
    }

    @Test
    fun `hinglish lending`() {
        assertEquals(LooseEndDirection.THEY_HAVE, lend("Rahul ko 500 diye").draft.direction)
        assertEquals(500L, lend("Rahul ko 500 diye").draft.amountRupees)
        assertEquals(LooseEndDirection.I_HAVE, lend("Aman se 200 liye").draft.direction)
    }

    @Test
    fun `questions`() {
        assertEquals("passport", (route("where is my passport?") as Capture.Ask).query)
        assertEquals("car", (route("where did I park the car") as Capture.Ask).query)
        assertEquals("keys", (route("where's my keys") as Capture.Ask).query)
        assertEquals("passport", (route("mera passport kahan hai") as Capture.Ask).query)
    }

    @Test
    fun `anything else is a reminder with no time`() {
        val r = route("buy milk") as Capture.Remind
        assertEquals("Buy milk", r.parsed.title)
        assertNull(r.parsed.triggerAtMillis)
    }

    @Test
    fun `rupee amounts`() {
        assertEquals(500L, CaptureRouter.parseRupees("rs 500"))
        assertEquals(500L, CaptureRouter.parseRupees("500 rupees"))
        assertEquals(1500L, CaptureRouter.parseRupees("1,500/-"))
        assertNull(CaptureRouter.parseRupees("charger"))
    }
}
