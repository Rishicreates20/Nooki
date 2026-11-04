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
 * Primary Room [RoomDatabase] holder for Nooki.
 * Rule from specification: "Every database change ships with a tested migration. User data is never wiped."
 */
@Database(
    entities = [ReminderEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ReminderTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DATABASE_NAME = "nooki_desk_pet.db"

        /**
         * Non-destructive migration path preserving 100% of user reminders across schema updates.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_created_at_millis` ON `reminders` (`created_at_millis`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
