package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.ReminderTypeConverters
import com.example.data.local.dao.AttentionDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.ReminderEntity

/**
 * Room database for Nooki. Every version bump ships with a migration and a test, and there is no
 * destructive fallback: an app people trust with their life admin must never wipe it.
 */
@Database(
    entities = [
        ReminderEntity::class,
        MemoryEntity::class,
        LooseEndEntity::class,
        NoticedEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(ReminderTypeConverters::class)
abstract class NookiDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    abstract fun attentionDao(): AttentionDao

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

        /** Version 3 adds undated items, keep-asking, saved things, money out, and noticed items. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN undated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN keep_asking INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN ask_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN source TEXT NOT NULL DEFAULT 'TYPED'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN next_spawned INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, `subject` TEXT NOT NULL, `place` TEXT NOT NULL, " +
                        "`pinned` INTEGER NOT NULL, `created_at_millis` INTEGER NOT NULL, " +
                        "`updated_at_millis` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loose_ends` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`direction` TEXT NOT NULL, `person` TEXT NOT NULL, `what` TEXT NOT NULL, " +
                        "`amount_rupees` INTEGER, `raw_input` TEXT NOT NULL, " +
                        "`created_at_millis` INTEGER NOT NULL, `settled_at_millis` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `noticed` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `title` TEXT NOT NULL, `detail` TEXT NOT NULL, `person` TEXT, " +
                        "`amount_rupees` INTEGER, `due_at_millis` INTEGER, `app_label` TEXT NOT NULL, " +
                        "`package_name` TEXT NOT NULL, `dedupe_key` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`times` INTEGER NOT NULL, `noticed_at_millis` INTEGER NOT NULL, " +
                        "`surface_at_millis` INTEGER NOT NULL, `resolved_at_millis` INTEGER)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_noticed_dedupe_key` ON `noticed` (`dedupe_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_noticed_status` ON `noticed` (`status`)")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var INSTANCE: NookiDatabase? = null

        fun getInstance(context: Context): NookiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NookiDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
