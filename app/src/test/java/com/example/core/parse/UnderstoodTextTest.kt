package com.example.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class UnderstoodTextTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val now: Long = Calendar.getInstance(zone, Locale.US).apply { clear(); set(2026, Calendar.NOVEMBER, 4, 11, 0, 0) }.timeInMillis

    private fun show(text: String) = UnderstoodText.describe(TellNooki.understand(text, now, zone), now, zone)

    @Test
    fun `shows what Nooki understood before saving`() {
        assertEquals("Doctor · tomorrow, 9 am", show("kal subah 9 baje doctor"))
        assertEquals("Buy milk · no time yet, Nooki will ask this evening", show("buy milk"))
        assertEquals("दवाई · tomorrow, 6 pm · asks again until you confirm", show("कल शाम 6 बजे दवाई"))
        assertEquals("Pay rent · 1 Dec, 9 am · every month · warns 3 days, 1 day before", show("pay rent on the 1st every month"))
        assertEquals("Remember: Passport → in the blue bag", show("passport is in the blue bag"))
        assertEquals("Rahul owes you ₹500", show("Rahul owes me 500"))
        assertEquals("Aman has your charger", show("lent charger to Aman"))
        assertEquals("Nooki will look for “passport”", show("where is my passport"))
        assertEquals("", show(""))
    }
}
