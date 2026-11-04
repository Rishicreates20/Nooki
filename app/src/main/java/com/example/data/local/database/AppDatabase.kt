package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.converter.ReminderTypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.MoneyDao
import com.example.data.local.dao.NoticedDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.SavedThingDao
import com.example.data.local.entity.MoneyEntryEntity
import com.example.data.local.entity.NoticedItemEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SavedThingEntity

/**
 * Primary Room [RoomDatabase] holder for Nooki: The Desk-Pet Reminder.
 * Exposes [reminderDao] and provides a thread-safe singleton [getInstance].
 */
@Database(
    entities = [
        ReminderEntity::class,
        SavedThingEntity::class,
        MoneyEntryEntity::class,
        NoticedItemEntity::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(ReminderTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun savedThingDao(): SavedThingDao
    abstract fun moneyDao(): MoneyDao
    abstract fun noticedDao(): NoticedDao

    companion object {
        const val DATABASE_NAME = "nooki_desk_pet.db"

        /**
         * Version 2 (the new brief): reminder kinds, warnings, repeats, no-time items, "You missed"
         * and medicine confirmation; plus saved things, money and noticed items. Every v1 reminder
         * is kept as a plain task. Proven by NookiDatabaseMigrationTest.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'TASK'")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `lead_minutes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `recurrence_rule` TEXT")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `has_time` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `missed_at_millis` INTEGER")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `confirm_attempts` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_things` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`thing` TEXT NOT NULL, `place` TEXT NOT NULL, `raw_input` TEXT NOT NULL, " +
                        "`created_at_millis` INTEGER NOT NULL, `updated_at_millis` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_things_thing` ON `saved_things` (`thing`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `money_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`person` TEXT NOT NULL, `direction` TEXT NOT NULL, `amount_paise` INTEGER, `thing` TEXT, " +
                        "`raw_input` TEXT NOT NULL, `created_at_millis` INTEGER NOT NULL, `settled_at_millis` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_entries_person` ON `money_entries` (`person`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_entries_settled_at_millis` ON `money_entries` (`settled_at_millis`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `noticed_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `who` TEXT NOT NULL, `amount_paise` INTEGER, `due_day` INTEGER, " +
                        "`due_month` INTEGER, `due_year` INTEGER, `noticed_at_millis` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`source_key_hash` TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_noticed_items_status` ON `noticed_items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_noticed_items_noticed_at_millis` ON `noticed_items` (`noticed_at_millis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_noticed_items_source_key_hash` ON `noticed_items` (`source_key_hash`)")
            }
        }

        /** Every migration, in order. Add new ones here; never a destructive fallback. */
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // No destructive fallback: user data is never wiped. Every version bump must
                    // ship a Migration (plus a migration test), or Room fails loudly instead.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
