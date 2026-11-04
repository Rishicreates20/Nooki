package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.ReminderTypeConverters
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.ReminderEntity

/**
 * Room Database for Nooki: The Desk-Pet Reminder.
 */
@Database(
    entities = [ReminderEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(ReminderTypeConverters::class)
abstract class NookiDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "nooki_desk_pet.db"

        /** Version 2 adds workflow kinds, heads-up lead times and calendar repeats, keeping every row. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN kind TEXT NOT NULL DEFAULT 'TASK'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN lead_times TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN recurrence_rule TEXT")
            }
        }

        @Volatile
        private var INSTANCE: NookiDatabase? = null

        fun getInstance(context: Context): NookiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NookiDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
