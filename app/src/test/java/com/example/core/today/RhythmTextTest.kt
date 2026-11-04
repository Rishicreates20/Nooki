package com.example.core.today

import com.example.core.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RhythmTextTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val morning = Calendar.getInstance(zone, Locale.US).apply { clear(); set(2026, Calendar.NOVEMBER, 4, 8, 0, 0) }.timeInMillis
    private val hour = 3_600_000L

    private fun r(id: Long, title: String, at: Long, hasTime: Boolean = true, kind: ReminderKind = ReminderKind.TASK) =
        TodayReminder(id, title, kind, at, hasTime, false, null, false, 0, morning - hour)

    private fun board(now: Long, vararg rs: TodayReminder, money: List<TodayMoney> = emptyList()) =
        TodayBuilder.build(rs.toList(), money, emptyList(), now, zone)

    @Test
    fun `morning brief lists today, close bills and money`() {
        val b = board(
            morning,
            r(1, "Doctor", morning + hour),
            r(2, "Call Mom", morning + 10 * hour),
            r(3, "Electricity bill", morning + 50 * hour, kind = ReminderKind.BILL),
            money = listOf(TodayMoney(1, "Rahul", true, 50_000, null, morning)),
        )
        val note = RhythmText.morning(b)!!
        assertEquals("Good morning. 2 things today", note.title)
        assertTrue(note.body, note.body.startsWith("On today: Doctor at 9 am, and 1 more."))
        assertTrue(note.body.contains("Getting close: Electricity bill."))
        assertTrue(note.body.contains("Money out with people: 1 entry."))
    }

    @Test
    fun `nothing to say means no notification`() {
        assertNull(RhythmText.morning(board(morning)))
        assertNull(RhythmText.evening(board(morning + 12 * hour), batteryPercent = 80, charging = false))
    }

    @Test
    fun `evening wrap offers to move what's left and nudges charging`() {
        val evening = morning + 13 * hour
        val b = board(evening, r(1, "Gym", evening + hour), r(2, "Buy milk", evening, hasTime = false))
        val note = RhythmText.evening(b, batteryPercent = 25, charging = false)!!
        assertEquals("Evening wrap", note.title)
        assertTrue(note.offerMoveToTomorrow)
        assertTrue(note.body, note.body.startsWith("2 things didn't get done."))
        assertTrue(note.body.contains("1 note still needs a time."))
        assertTrue(note.body.contains("Your phone is at 25%."))
        assertTrue(!RhythmText.evening(b, 25, charging = true)!!.body.contains("Plug it in"))
    }
}
