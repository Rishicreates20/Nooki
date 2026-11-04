package com.example.core.today

import com.example.core.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TodayBuilderTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val now = at(2026, Calendar.NOVEMBER, 4, 11, 0)
    private val hour = 3_600_000L

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance(zone, Locale.US).apply { clear(); set(y, mo, d, h, mi, 0) }.timeInMillis

    private fun r(
        id: Long, title: String, at: Long, kind: ReminderKind = ReminderKind.TASK, hasTime: Boolean = true,
        done: Boolean = false, completedAt: Long? = null, missed: Boolean = false, snoozes: Int = 0,
    ) = TodayReminder(id, title, kind, at, hasTime, done, completedAt, missed, snoozes, now - 10 * hour)

    private fun build(
        reminders: List<TodayReminder> = emptyList(),
        money: List<TodayMoney> = emptyList(),
        notices: List<TodayNotice> = emptyList(),
    ) = TodayBuilder.build(reminders, money, notices, now, zone)

    @Test
    fun `nothing at all is calm and Nooki sleeps`() {
        val b = build()
        assertTrue(b.isCalm)
        assertEquals(NookiMood.SLEEPING, b.mood)
    }

    @Test
    fun `reminders land in the three questions`() {
        val b = build(
            listOf(
                r(1, "Call bank", now - 2 * hour),
                r(2, "Doctor", now + 3 * hour),
                r(3, "Gym", now + 30 * hour),
                r(4, "Buy milk", now, hasTime = false),
                r(5, "Pay electricity", now + 48 * hour, kind = ReminderKind.BILL),
                r(6, "Stretch", now + 2 * hour, snoozes = 3),
                r(7, "Water plants", now - 3 * hour, done = true, completedAt = now - hour),
            )
        )
        assertEquals(listOf("Call bank"), b.missed.map { it.line })
        assertEquals(listOf("Doctor"), b.todo.map { it.line })
        assertEquals(listOf("Gym"), b.later.map { it.line })
        assertEquals(setOf("Buy milk", "Pay electricity", "Stretch"), b.forgot.map { it.line }.toSet())
        assertEquals(listOf("Water plants"), b.done.map { it.line })
        assertEquals("2 pm", b.todo.single().detail)
        assertEquals(1, b.noTimeCount)
    }

    @Test
    fun `a missed call shows up in You missed`() {
        val b = build(notices = listOf(TodayNotice(1, TodayNoticeType.MISSED_CALL, "Mom", null, null, now - 2 * hour)))
        assertEquals("Mom called", b.missed.single().line)
        assertEquals("2 hours ago", b.missed.single().detail)
        assertEquals(NookiMood.LOOKING_UP, b.mood)
    }

    @Test
    fun `unread messages wait two hours before counting as missed`() {
        val fresh = TodayNotice(1, TodayNoticeType.UNREAD_MESSAGE, "Aman", null, null, now - hour)
        val stale = TodayNotice(2, TodayNoticeType.UNREAD_MESSAGE, "Aman", null, null, now - 3 * hour)
        assertTrue(build(notices = listOf(fresh)).missed.isEmpty())
        assertEquals("Unread message from Aman", build(notices = listOf(stale)).missed.single().line)
    }

    @Test
    fun `money out with people is something you forgot`() {
        val b = build(
            money = listOf(
                TodayMoney(1, "Rahul", theyOweMe = true, amountPaise = 50_000, thing = null, sinceMillis = now - 3 * 24 * hour),
                TodayMoney(2, "Aman", theyOweMe = true, amountPaise = null, thing = "charger", sinceMillis = now),
                TodayMoney(3, "Priya", theyOweMe = false, amountPaise = 123_456_700, thing = null, sinceMillis = now),
            )
        )
        val lines = b.forgot.map { it.line }
        assertTrue(lines.contains("Rahul owes you ₹500"))
        assertTrue(lines.contains("Aman has your charger"))
        assertTrue(lines.contains("You owe Priya ₹12,34,567"))
        assertEquals("Since 3 days", b.forgot.first { it.line.startsWith("Rahul") }.detail)
    }

    @Test
    fun `missed medicine flusters Nooki`() {
        val b = build(listOf(r(1, "दवाई", now - 3 * hour, kind = ReminderKind.MEDICINE, missed = true)))
        assertEquals(NookiMood.FLUSTERED, b.mood)
    }

    @Test
    fun `something within the hour makes Nooki look up`() {
        assertEquals(NookiMood.LOOKING_UP, build(listOf(r(1, "Meeting", now + 30 * 60_000L))).mood)
        assertEquals(NookiMood.CONTENT, build(listOf(r(1, "Meeting", now + 5 * hour))).mood)
    }
}
