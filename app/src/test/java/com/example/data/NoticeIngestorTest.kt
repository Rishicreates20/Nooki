package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.notice.NoticeSettings
import com.example.core.notice.RawNotification
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.notice.NoticeIngestor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An OTP never reaches the database. These tests drive real notifications through the only write
 * path into a real Room database, then read every row and column back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoticeIngestorTest {

    private lateinit var db: AppDatabase
    private lateinit var ingestor: NoticeIngestor
    private val now = 1_762_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ingestor = NoticeIngestor(db.noticedDao())
    }

    @After
    fun tearDown() = db.close()

    private fun sms(title: String, text: String, at: Long = now) =
        RawNotification("com.google.android.apps.messaging", "msg", title, text, at)

    /** Every value stored anywhere in noticed_items, as one string, to search for leaks. */
    private fun everythingStored(): String = db.query("SELECT * FROM noticed_items", null).use { c ->
        buildString {
            while (c.moveToNext()) for (i in 0 until c.columnCount) append(c.getString(i)).append('|')
        }
    }

    @Test
    fun `an OTP never reaches the database`() = runBlocking {
        val otps = listOf(
            sms("AX-HDFCBK", "482913 is your OTP for a transaction of Rs 2,000. Do not share."),
            sms("JD-PAYTM", "Your verification code is 771204"),
            sms("BP-SBIINB", "आपका ओटीपी 556677 है"),
            sms("VK-ICICIB", "Rs 500 debited from A/c XX1234 for EMI due on 5 Dec. Avl Bal Rs 12,345"),
        )
        otps.forEachIndexed { i, raw -> assertNull(ingestor.ingest(raw, NoticeSettings(), "key-$i")) }

        assertTrue(db.noticedDao().getAll().isEmpty())
        val stored = everythingStored()
        listOf("482913", "771204", "556677", "12,345", "OTP", "ओटीपी").forEach { assertFalse(stored.contains(it)) }
    }

    @Test
    fun `message content is never stored, only who and when`() = runBlocking {
        ingestor.ingest(RawNotification("com.whatsapp", "msg", "Aman", "Bro return my charger, also the pin is 4455", now), NoticeSettings(), "k1")
        ingestor.ingest(RawNotification("com.whatsapp", "msg", "Neha", "Dinner at 8? Bring the cake", now), NoticeSettings(), "k2")

        val rows = db.noticedDao().getAll()
        assertEquals(listOf("Neha"), rows.map { it.who }) // Aman's message mentions a PIN, so it was dropped
        val stored = everythingStored()
        listOf("Dinner", "cake", "charger", "4455").forEach { assertFalse("leaked: $it", stored.contains(it)) }
    }

    @Test
    fun `missed call and bill are kept with only the allowed fields`() = runBlocking {
        ingestor.ingest(RawNotification("com.google.android.dialer", "missed_call", "Missed call from Mom", "Tap to call back", now), NoticeSettings(), "call")
        ingestor.ingest(sms("AD-AIRTEL", "Your Airtel bill of Rs. 499 is due on 12 Nov. Pay via app."), NoticeSettings(), "bill")

        val rows = db.noticedDao().getAll().sortedBy { it.type.name }
        val bill = rows.single { it.type == NoticedType.BILL }
        assertEquals("AD-AIRTEL", bill.who)
        assertEquals(49_900L, bill.amountPaise)
        assertEquals(12, bill.dueDay)
        assertEquals(11, bill.dueMonth)
        assertEquals("Mom", rows.single { it.type == NoticedType.MISSED_CALL }.who)
        assertFalse(everythingStored().contains("Pay via app"))
        assertTrue(rows.all { it.status == NoticedStatus.SUGGESTED })
    }

    @Test
    fun `off means off`() = runBlocking {
        val off = NoticeSettings(missedCalls = false, unreadMessages = false, bills = false)
        ingestor.ingest(RawNotification("dialer", "missed_call", "Missed call from Mom", null, now), off, "a")
        ingestor.ingest(sms("JIO", "Bill of Rs 299 due on 3 Dec"), off, "b")
        ingestor.ingest(RawNotification("com.whatsapp", "msg", "Aman", "hi", now), off, "c")
        assertTrue(db.noticedDao().getAll().isEmpty())
    }

    @Test
    fun `updates to the same notification are one item, and opening it clears it`() = runBlocking {
        ingestor.ingest(RawNotification("com.whatsapp", "msg", "Aman", "hi", now), NoticeSettings(), "chat")
        ingestor.ingest(RawNotification("com.whatsapp", "msg", "Aman", "hello?", now + 60_000), NoticeSettings(), "chat")
        assertEquals(1, db.noticedDao().getAll().size)

        ingestor.onOpened("chat")
        assertEquals(NoticedStatus.ACCEPTED, db.noticedDao().getAll().single().status)
    }

    @Test
    fun `opening a bill SMS does not settle the bill`() = runBlocking {
        ingestor.ingest(sms("JIO", "Bill of Rs 299 due on 3 Dec"), NoticeSettings(), "bill")
        ingestor.onOpened("bill")
        assertEquals(NoticedStatus.SUGGESTED, db.noticedDao().getAll().single().status)
    }

    @Test
    fun `the raw notification key is never stored`() = runBlocking {
        ingestor.ingest(RawNotification("dialer", "missed_call", "Missed call from Mom", null, now), NoticeSettings(), "0|com.google.android.dialer|1|+919876543210|10123")
        assertFalse(everythingStored().contains("9876543210"))
    }
}
