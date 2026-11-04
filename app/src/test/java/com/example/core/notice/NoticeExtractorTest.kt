package com.example.core.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeExtractorTest {

    private val now = 1_762_000_000_000L

    private fun sms(title: String, text: String) =
        RawNotification(appPackage = "com.google.android.apps.messaging", category = "msg", title = title, text = text, postedAtMillis = now)

    // ---- OTPs and other secrets never get through -------------------------------------------

    @Test
    fun `OTP messages are dropped in English, Hinglish and Hindi`() {
        val otps = listOf(
            sms("AX-HDFCBK", "123456 is your OTP for txn of Rs 2,000 at Amazon. Do not share it with anyone."),
            sms("VM-SWIGGY", "Your verification code is 4821"),
            sms("JD-PAYTMB", "Use 902211 as one time password to login. Valid for 10 mins."),
            sms("Google", "G-582913 is your Google verification code."),
            sms("AD-AIRTEL", "Aapka OTP 7781 hai. Kisi ke saath share na karein."),
            sms("BP-SBIINB", "आपका ओटीपी 556677 है। इसे किसी से साझा न करें।"),
            sms("Bank", "Your UPI PIN was changed successfully"),
            sms("Netflix", "Your password was reset"),
        )
        otps.forEach { raw ->
            assertNull("Should drop: ${raw.text}", NoticeExtractor.extract(raw))
        }
    }

    @Test
    fun `bank balances are dropped even when they look like bills`() {
        val raw = sms("VK-ICICIB", "Rs 500.00 debited from A/c XX1234 for EMI. Avl Bal Rs 12,345.67")
        assertNull(NoticeExtractor.extract(raw))
        assertNull(NoticeExtractor.extract(sms("BOI", "Your a/c balance is INR 4,200")))
    }

    @Test
    fun `an OTP inside a missed call style notification is still dropped`() {
        val raw = RawNotification("com.android.dialer", "call", "Missed call", "Your OTP is 1234", now)
        assertNull(NoticeExtractor.extract(raw))
    }

    // ---- Missed calls -------------------------------------------------------------------------

    @Test
    fun `missed call keeps only who and when`() {
        val raw = RawNotification("com.google.android.dialer", "missed_call", "Missed call from Mom", "Tap to call back", now)
        assertEquals(Noticed.MissedCall(who = "Mom", atMillis = now), NoticeExtractor.extract(raw))
    }

    @Test
    fun `missed call with caller in the text`() {
        val raw = RawNotification("com.samsung.android.dialer", "call", "2 missed calls", "Rahul Sharma", now)
        assertEquals(Noticed.MissedCall(who = "Rahul Sharma", atMillis = now), NoticeExtractor.extract(raw))
    }

    // ---- Bills ----------------------------------------------------------------------------------

    @Test
    fun `bill keeps only name, amount and date`() {
        val raw = sms("AD-AIRTEL", "Your Airtel postpaid bill of Rs. 1,499.50 is due on 12 Nov 2026. Pay via app.")
        val noticed = NoticeExtractor.extract(raw)
        assertEquals(
            Noticed.Bill(name = "AD-AIRTEL", amountPaise = 149_950L, due = DueDay(12, 11, 2026), atMillis = now),
            noticed,
        )
        assertFalse("Message body must not be kept", noticed.toString().contains("postpaid"))
    }

    @Test
    fun `bill dates in common formats`() {
        assertEquals(DueDay(5, 12, null), NoticeExtractor.parseDueDay("pay by 05/12"))
        assertEquals(DueDay(1, 3, 2027), NoticeExtractor.parseDueDay("last date 1-3-27"))
        assertEquals(DueDay(7, 1, null), NoticeExtractor.parseDueDay("due Jan 7th"))
        assertEquals(DueDay(30, 9, null), NoticeExtractor.parseDueDay("due on 30th September"))
        assertNull(NoticeExtractor.parseDueDay("no date here"))
        // Impossible dates are refused rather than rolled into the next month.
        assertNull(NoticeExtractor.parseDueDay("due 31 Apr"))
        assertNull(NoticeExtractor.parseDueDay("due 29/02/2027"))
        assertEquals(DueDay(29, 2, 2028), NoticeExtractor.parseDueDay("due 29/02/2028"))
    }

    @Test
    fun `amounts in rupee formats`() {
        assertEquals(50_000L, NoticeExtractor.parseAmountPaise("₹500"))
        assertEquals(1_234_500L, NoticeExtractor.parseAmountPaise("INR 12,345"))
        assertEquals(9_950L, NoticeExtractor.parseAmountPaise("Rs.99.5 due"))
        assertNull(NoticeExtractor.parseAmountPaise("five hundred"))
        assertNull(NoticeExtractor.parseAmountPaise("Rs 99999999999999999999"))
    }

    // ---- Unread messages -----------------------------------------------------------------------

    @Test
    fun `chat message keeps only the sender, never the content`() {
        val raw = RawNotification("com.whatsapp", "msg", "Aman", "Bro can you return my charger tomorrow", now)
        val noticed = NoticeExtractor.extract(raw)
        assertEquals(Noticed.UnreadMessage(who = "Aman", atMillis = now), noticed)
        assertFalse(noticed.toString().contains("charger"))
    }

    @Test
    fun `notifications without a useful category are ignored`() {
        assertNull(NoticeExtractor.extract(RawNotification("com.some.game", null, "Daily reward", "Claim now", now)))
    }

    // ---- Off means off -------------------------------------------------------------------------

    @Test
    fun `each kind of noticing can be turned off`() {
        val off = NoticeSettings(missedCalls = false, unreadMessages = false, bills = false)
        assertNull(NoticeExtractor.extract(RawNotification("dialer", "missed_call", "Missed call from Mom", null, now), off))
        assertNull(NoticeExtractor.extract(sms("JIO", "Bill of Rs 299 due on 3 Dec"), off))
        assertNull(NoticeExtractor.extract(RawNotification("com.whatsapp", "msg", "Aman", "hi", now), off))
    }

    @Test
    fun `sensitive check handles empty input`() {
        assertFalse(SensitiveContent.isSensitive(null, ""))
        assertTrue(SensitiveContent.isSensitive("OTP"))
    }

    @Test
    fun `a bare code in a title is never kept as a sender`() {
        assertTrue(SensitiveContent.isSensitive("Code: 123456", null))
        assertTrue(SensitiveContent.isSensitive("482193 is your Swiggy login", null))
        assertNull(NoticeExtractor.extract(RawNotification("com.whatsapp", "msg", "Code: 123456", "hi", 0L), NoticeSettings()))
    }
}
