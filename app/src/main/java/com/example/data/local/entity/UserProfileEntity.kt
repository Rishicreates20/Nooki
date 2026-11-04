package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity storing the user's local profile, companion preferences, and persisted
 * Consistency Streak milestones (`user_profile` table).
 * 100% on-device in accordance with Nooki's zero-internet privacy architecture.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_PROFILE_ID,

    @ColumnInfo(name = "display_name")
    val displayName: String = "Aarav",

    @ColumnInfo(name = "companion_title")
    val companionTitle: String = "Keeper of the Thread",

    @ColumnInfo(name = "avatar_badge")
    val avatarBadge: String = "🦊",

    @ColumnInfo(name = "bio_motto")
    val bioMotto: String = "One thread, calm mind • English, हिंग्लिश & हिंदी",

    @ColumnInfo(name = "daily_reminder_goal")
    val dailyReminderGoal: Int = 3,

    @ColumnInfo(name = "current_streak_days")
    val currentStreakDays: Int = 0,

    @ColumnInfo(name = "longest_streak_days")
    val longestStreakDays: Int = 0,

    @ColumnInfo(name = "last_completed_day_epoch")
    val lastCompletedDayEpoch: Long = 0L,

    @ColumnInfo(name = "sound_enabled", defaultValue = "1")
    val soundEnabled: Boolean = true,

    @ColumnInfo(name = "haptics_enabled", defaultValue = "1")
    val hapticsEnabled: Boolean = true,

    @ColumnInfo(name = "selected_theme", defaultValue = "'MIDNIGHT_COZY'")
    val selectedThemeId: String = "MIDNIGHT_COZY",

    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_PROFILE_ID = 1
    }
}
