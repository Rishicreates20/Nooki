package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.NookiDatabase
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 2 adds workflow columns. People who installed version 1 must keep their reminders, so
 * this builds a real version 1 file and opens it with the current Room schema, which also makes
 * Room validate that the migrated table matches the entity exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NookiDatabaseMigrationTest {

    @Test
    fun `version 1 reminders survive the upgrade to the current schema`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `notes` TEXT NOT NULL, `raw_input` TEXT NOT NULL, " +
                    "`trigger_at_millis` INTEGER NOT NULL, `created_at_millis` INTEGER NOT NULL, " +
                    "`completed_at_millis` INTEGER, `priority` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `snooze_count` INTEGER NOT NULL, `recurrence_minutes` INTEGER, " +
                    "`pet_mood_impact` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_trigger_at_millis` ON `reminders` (`trigger_at_millis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_status` ON `reminders` (`status`)")
            db.execSQL(
                "INSERT INTO reminders (id, title, notes, raw_input, trigger_at_millis, created_at_millis, " +
                    "priority, category, status, snooze_count, pet_mood_impact) " +
                    "VALUES (7, 'Old reminder', '', 'old', 1000, 500, 'MEDIUM', 'DESK', 'SCHEDULED', 0, 10)"
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, NookiDatabase::class.java, name)
            .addMigrations(*NookiDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = room.reminderDao()
                val old = dao.getReminderById(7)!!
                assertEquals("Old reminder", old.title)
                assertEquals(false, old.undated)
                assertEquals(false, old.keepAsking)
                assertEquals(com.example.data.local.entity.ReminderSource.TYPED, old.source)
                assertEquals(ReminderKind.TASK, old.kind)
                assertEquals("", old.leadTimes)
                assertNull(old.recurrenceRule)

                val id = dao.upsertReminder(
                    ReminderEntity(
                        title = "Rent",
                        triggerAtMillis = 2000,
                        kind = ReminderKind.BILL,
                        leadTimes = "4320,1440",
                        recurrenceRule = RecurrenceRule.MONTHLY
                    )
                )
                val rent = dao.getReminderById(id)!!
                assertEquals(ReminderKind.BILL, rent.kind)
                assertEquals("4320,1440", rent.leadTimes)
                assertEquals(RecurrenceRule.MONTHLY, rent.recurrenceRule)

                // The tables added in version 3 are open for use.
                val attention = room.attentionDao()
                attention.upsertMemory(com.example.data.local.entity.MemoryEntity(text = "Keys in the drawer"))
                assertEquals(1, attention.observeMemories().first().size)
                assertEquals(0, attention.observeNewNoticed().first().size)
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
