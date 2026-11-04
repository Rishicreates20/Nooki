package com.example.notice

import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class NotificationClassifierTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val now: Long = Calendar.getInstance(zone, Locale.US).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 4, 11, 0, 0)
    }.timeInMillis

    private fun notice(
        title: String,
        text: String,
        pkg: String = "com.google.android.apps.messaging",
        app: String = "Messages",
        category: String? = null,
        group: Boolean = false
    ) = IncomingNotice(pkg, app, title, text, category, isGroupConversation = group)

    private fun classify(n: IncomingNotice) = NotificationClassifier.classify(n, now, ownPackage = "nooki", zone = zone)

    @Test
    fun `secrets are dropped no matter which app sent them`() {
        val secrets = listOf(
            "Your OTP is 482913. Do not share it with anyone.",
            "482913 is your one time password for HDFC Bank",
            "Your verification code is 5521",
            "Use login code 88123 to sign in",
            "Your UPI PIN was changed",
            "Your password has been reset",
            "Rs.2,000 debited from a/c XX1234. Avl Bal Rs.45,000",
            "Rs 5,000 credited to your account",
            "CVV is required for this payment",
            "आपका ओटीपी 1234 है"
        )
        secrets.forEach { text ->
            assertNull(text, classify(notice("AD-HDFCBK", text)))
            assertNull(text, classify(notice("Aman", text, pkg = "com.whatsapp", app = "WhatsApp", category = "msg")))
        }
    }

    @Test
    fun `a saved candidate can never carry the message`() {
        val text = "Hey, the surprise party plan is at 8, keep it secret"
        val candidate = classify(notice("Aman", text, pkg = "com.whatsapp", app = "WhatsApp", category = "msg"))!!
        val everything = listOf(candidate.title, candidate.detail, candidate.person, candidate.dedupeKey).joinToString(" ")
        assertFalse(everything.contains("surprise"))
        assertFalse(everything.contains("party"))
        // There is no column for it either.
        assertFalse(NoticedEntity::class.java.declaredFields.any { it.name.lowercase() in setOf("text", "body", "message", "content") })
    }

    @Test
    fun `missed call from the phone app`() {
        val c = classify(notice("Missed call", "Mom", pkg = "com.samsung.android.dialer", app = "Phone"))!!
        assertEquals(NoticedType.MISSED_CALL, c.type)
        assertEquals("Mom called", c.title)
        assertEquals("Mom", c.person)
        assertEquals(now, c.surfaceAtMillis)
    }

    @Test
    fun `missed whatsapp call and call category`() {
        assertEquals("Aman called", classify(notice("Aman", "Missed voice call", pkg = "com.whatsapp", app = "WhatsApp"))!!.title)
        assertEquals("Priya called", classify(notice("Priya", "Tap to call back", category = "missed_call"))!!.title)
    }

    @Test
    fun `a count of calls with no name is not useful`() {
        assertNull(classify(notice("Missed calls", "3 missed calls")))
    }

    @Test
    fun `unread message waits before it surfaces and only names the person`() {
        val c = classify(notice("Aman", "are you coming?", pkg = "com.whatsapp", app = "WhatsApp"))!!
        assertEquals(NoticedType.UNREAD_MESSAGE, c.type)
        assertEquals("Unread message from Aman", c.title)
        assertEquals(now + NotificationClassifier.MESSAGE_GRACE_MILLIS, c.surfaceAtMillis)
    }

    @Test
    fun `group chats and shortcodes are not people`() {
        assertNull(classify(notice("Family: Aman", "dinner?", pkg = "com.whatsapp", app = "WhatsApp")))
        assertNull(classify(notice("Friends", "hi", pkg = "com.whatsapp", app = "WhatsApp", group = true)))
        assertNull(classify(notice("AD-SWIGGY", "Your food is on the way")))
        assertNull(classify(notice("WhatsApp", "3 new messages", pkg = "com.whatsapp", app = "WhatsApp")))
    }

    @Test
    fun `opening a message clears it by the same key`() {
        val n = notice("Aman", "are you coming?", pkg = "com.whatsapp", app = "WhatsApp")
        assertEquals(classify(n)!!.dedupeKey, NotificationClassifier.messageKey(n))
    }

    @Test
    fun `electricity bill with amount and date`() {
        val c = classify(notice("VM-BESCOM", "Your electricity bill of Rs. 1,240 is due on 12 Nov 2026. Pay now."))!!
        assertEquals(NoticedType.BILL, c.type)
        assertEquals("Electricity bill", c.title)
        assertEquals(1240L, c.amountRupees)
        assertEquals("₹1,240, due 12 Nov", c.detail)
        assertNotNull(c.dueAtMillis)
    }

    @Test
    fun `numeric dates on bills are day first`() {
        val c = classify(notice("AX-AIRTEL", "Your Airtel bill of ₹799 is due by 05/12/2026"))!!
        val due = Calendar.getInstance(zone, Locale.US).apply { timeInMillis = c.dueAtMillis!! }
        assertEquals(5, due.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.DECEMBER, due.get(Calendar.MONTH))
    }

    @Test
    fun `a bill already paid or a sale is not a bill`() {
        assertNull(classify(notice("VM-BESCOM", "Thank you for your payment of Rs 1,240 towards your electricity bill due on 12 Nov")))
        assertNull(classify(notice("AD-SHOP", "Flat 50% off on your electricity bill due payment. Offer ends 12 Nov!")))
    }

    @Test
    fun `parcel arriving`() {
        val c = classify(notice("Your order", "Your package is out for delivery", pkg = "in.amazon.mShop.android.shopping", app = "Amazon"))!!
        assertEquals(NoticedType.DELIVERY, c.type)
        assertEquals("Amazon delivery today", c.title)
    }

    @Test
    fun `appointment with a date`() {
        val c = classify(notice("Apollo Clinic", "Your appointment is confirmed for 8 Nov at 5pm", pkg = "com.apollo", app = "Apollo"))!!
        assertEquals(NoticedType.EVENT, c.type)
        assertEquals("Appointment", c.title)
        assertEquals("8 Nov", c.detail)
    }

    @Test
    fun `an event with no date is ignored`() {
        assertNull(classify(notice("Clinic", "Book your appointment today for a free checkup", pkg = "com.apollo", app = "Apollo")))
    }

    @Test
    fun `its own notifications, ongoing ones and summaries are skipped`() {
        assertNull(classify(notice("Missed call", "Mom", pkg = "nooki")))
        assertNull(classify(IncomingNotice("x", "X", "Missed call", "Mom", isOngoing = true)))
        assertNull(classify(IncomingNotice("x", "X", "Missed call", "Mom", isGroupSummary = true)))
        assertNull(classify(notice("", "")))
    }

    @Test
    fun `ordinary notifications are ignored`() {
        assertNull(classify(notice("Weather", "Light rain expected", pkg = "com.weather", app = "Weather")))
        assertTrue(listOf("Battery low", "Update available").all { classify(notice(it, "", pkg = "android", app = "System")) == null })
    }
}
