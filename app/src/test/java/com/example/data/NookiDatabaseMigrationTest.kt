package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.RecurrenceRule
import com.example.core.model.ReminderKind
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.MoneyDirectionColumn
import com.example.data.local.entity.MoneyEntryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.SavedThingEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrading never loses data. This builds a real version 1 database file, exactly as the shipped
 * app created it, then opens it with the current schema. Room runs the migrations and validates
 * that every migrated table matches its entity exactly, so a wrong migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NookiDatabaseMigrationTest {

    @Test
    fun `version 1 reminders survive the upgrade and new tables work`() {
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
                    "completed_at_millis, priority, category, status, snooze_count, recurrence_minutes, pet_mood_impact) " +
                    "VALUES (7, 'Old reminder', 'a note', 'old', 1000, 500, NULL, 'HIGH', 'ERRAND', 'SCHEDULED', 2, 60, 20)"
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = room.reminderDao()
                val old = dao.getReminderById(7)!!
                assertEquals("Old reminder", old.title)
                assertEquals("a note", old.notes)
                assertEquals(1000L, old.triggerAtMillis)
                assertEquals(ReminderStatus.SCHEDULED, old.status)
                assertEquals(2, old.snoozeCount)
                assertEquals(60, old.recurrenceMinutes)
                assertEquals(ReminderKind.TASK, old.kind)
                assertEquals("", old.leadMinutes)
                assertNull(old.recurrenceRule)
                assertTrue(old.hasTime)
                assertNull(old.missedAtMillis)
                assertEquals(0, old.confirmAttempts)

                val id = dao.upsertReminder(
                    ReminderEntity(
                        title = "Rent",
                        triggerAtMillis = 2000,
                        kind = ReminderKind.BILL,
                        leadMinutes = "4320,1440",
                        recurrenceRule = RecurrenceRule.MONTHLY,
                    )
                )
                val rent = dao.getReminderById(id)!!
                assertEquals(ReminderKind.BILL, rent.kind)
                assertEquals(listOf(4320, 1440), rent.leadMinuteList)
                assertEquals(RecurrenceRule.MONTHLY, rent.recurrenceRule)

                room.savedThingDao().insert(SavedThingEntity(thing = "Passport", place = "in the blue bag"))
                assertEquals("in the blue bag", room.savedThingDao().search("pass").single().place)

                room.moneyDao().insert(MoneyEntryEntity(person = "Rahul", direction = MoneyDirectionColumn.THEY_OWE_ME, amountPaise = 50_000))
                assertEquals(50_000L, room.moneyDao().getOpen().single().amountPaise)
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
