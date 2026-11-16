package com.example.health

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.HealthDayLogEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves MIGRATION_4_5 keeps existing reminders and produces exactly the schema Room expects.
 *
 * A v5 database is created, then turned back into a v4 one (health table dropped, version set to 4)
 * and reopened, so Room runs the migration and validates the resulting schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HealthMigrationTest {
    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `migration 4 to 5 keeps reminders and adds health logs`() = runBlocking {
        // 1. A database with one reminder in it.
        open().also { db ->
            db.reminderDao().upsertReminder(
                ReminderEntity(title = "Drink water", triggerAtMillis = 1_000L, status = ReminderStatus.SCHEDULED)
            )
            db.close()
        }

        // 2. Make it look like a version 4 database (before the health table existed).
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { raw ->
                raw.execSQL("DROP TABLE IF EXISTS `health_day_logs`")
                raw.version = 4
            }

        // 3. Reopen: Room runs MIGRATION_4_5 and checks the schema matches the entities.
        val migrated = open()
        try {
            val reminders = migrated.reminderDao().getPendingRemindersForBootRecovery()
            assertEquals(listOf("Drink water"), reminders.map { it.title })

            migrated.healthDao().upsert(HealthDayLogEntity(dayEpoch = 20_000L, steps = 4_321, waterGlasses = 3))
            val log = migrated.healthDao().get(20_000L)
            assertNotNull(log)
            assertEquals(4_321, log!!.steps)
            assertEquals(-1L, log.lastStepCounterValue)
        } finally {
            migrated.close()
        }
    }
}
