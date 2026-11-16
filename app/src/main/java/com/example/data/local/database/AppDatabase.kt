package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.ReminderTypeConverters
import com.example.auth.SessionStore
import com.example.data.local.dao.HealthDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.HealthDayLogEntity
import com.example.data.local.entity.WorkoutEntity
import com.example.data.local.entity.PetJournalEntryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserProfileEntity

/**
 * Primary Room [RoomDatabase] holder for Nooki.
 * Rule from specification: "Every database change ships with a tested migration. User data is never wiped."
 */
@Database(
    entities = [
        ReminderEntity::class,
        UserProfileEntity::class,
        PetJournalEntryEntity::class,
        HealthDayLogEntity::class,
        WorkoutEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(ReminderTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    abstract fun healthDao(): HealthDao

    companion object {
        const val DATABASE_NAME = "nooki_desk_pet.db"

        /**
         * Non-destructive migration path from v1 -> v2 preserving 100% of user reminders
         * while adding `completed_day_epoch` for Consistency Streak tracking and `user_profile`.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `reminders` ADD COLUMN `completed_day_epoch` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_created_at_millis` ON `reminders` (`created_at_millis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_completed_day_epoch` ON `reminders` (`completed_day_epoch`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `companion_title` TEXT NOT NULL,
                        `avatar_badge` TEXT NOT NULL,
                        `bio_motto` TEXT NOT NULL,
                        `daily_reminder_goal` INTEGER NOT NULL,
                        `current_streak_days` INTEGER NOT NULL,
                        `longest_streak_days` INTEGER NOT NULL,
                        `last_completed_day_epoch` INTEGER NOT NULL,
                        `updated_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Non-destructive migration path from v2 -> v3 adding `sound_enabled` and `haptics_enabled`
         * sensory preferences to `user_profile`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `user_profile` ADD COLUMN `sound_enabled` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `user_profile` ADD COLUMN `haptics_enabled` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * Non-destructive migration path from v3 -> v4 adding `selected_theme` to `user_profile`
         * and creating `pet_journal_entries` for Nooki's Milestone Journal.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `user_profile` ADD COLUMN `selected_theme` TEXT NOT NULL DEFAULT 'MIDNIGHT_COZY'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pet_journal_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `milestone_type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `narrative` TEXT NOT NULL,
                        `badge_emoji` TEXT NOT NULL,
                        `happiness_snapshot` INTEGER NOT NULL,
                        `streak_snapshot` INTEGER NOT NULL,
                        `timestamp_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pet_journal_entries_timestamp_millis` ON `pet_journal_entries` (`timestamp_millis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pet_journal_entries_milestone_type` ON `pet_journal_entries` (`milestone_type`)"
                )
            }
        }

        /**
         * Non-destructive migration path from v4 -> v5 adding `health_day_logs` for the Health
         * section (steps, water, sleep, mood) and `workouts` for fitness tracking.
         * Existing tables are untouched.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_day_logs` (
                        `day_epoch` INTEGER NOT NULL,
                        `steps` INTEGER NOT NULL DEFAULT 0,
                        `last_step_counter_value` INTEGER NOT NULL DEFAULT -1,
                        `water_glasses` INTEGER NOT NULL DEFAULT 0,
                        `sleep_minutes` INTEGER NOT NULL DEFAULT 0,
                        `mood_score` INTEGER NOT NULL DEFAULT 0,
                        `updated_at_millis` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`day_epoch`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workouts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `day_epoch` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `duration_minutes` INTEGER NOT NULL,
                        `calories` INTEGER NOT NULL,
                        `started_at_millis` INTEGER NOT NULL,
                        `created_at_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_day_epoch` ON `workouts` (`day_epoch`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var INSTANCE_NAME: String? = null

        /**
         * Name of the database file that belongs to whoever is signed in, or null when nobody is.
         * Before the first account exists (an install from before login was added), the original
         * [DATABASE_NAME] file is used so alarms keep working; the first account to sign up adopts it.
         */
        fun activeDatabaseName(context: Context): String? {
            val session = SessionStore(context)
            return when {
                session.isSignedIn -> session.activeDatabaseName
                !session.hasAnyAccount -> DATABASE_NAME
                else -> null
            }
        }

        /** The signed-in user's database, or null when signed out (receivers then do nothing). */
        fun getActiveOrNull(context: Context): AppDatabase? {
            val name = activeDatabaseName(context) ?: return null
            synchronized(this) {
                val existing = INSTANCE
                if (existing != null && INSTANCE_NAME == name) return existing
                existing?.close()
                return build(context, name).also {
                    INSTANCE = it
                    INSTANCE_NAME = name
                }
            }
        }

        /** The signed-in user's database. Only call this when someone is signed in. */
        fun getInstance(context: Context): AppDatabase =
            checkNotNull(getActiveOrNull(context)) { "No Nooki account is signed in" }

        /** Closes the cached database, e.g. on logout or before switching accounts. */
        fun closeActive() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                INSTANCE_NAME = null
            }
        }

        /** Opens a specific account's database (not cached). Caller closes it. */
        fun openStandalone(context: Context, databaseName: String): AppDatabase =
            build(context, databaseName)

        private fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                name
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
