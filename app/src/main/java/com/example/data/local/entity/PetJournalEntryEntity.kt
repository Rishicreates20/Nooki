package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Categories of significant pet milestones recorded in Nooki's Journal.
 */
enum class PetMilestoneType(
    val displayLabel: String,
    val defaultBadgeEmoji: String
) {
    FIRST_HIGH_STREAK(
        displayLabel = "High-Streak Achievement",
        defaultBadgeEmoji = "🔥"
    ),
    PERSONALITY_SHIFT(
        displayLabel = "Pet Personality Shift",
        defaultBadgeEmoji = "✨"
    ),
    BONDING_MOMENT(
        displayLabel = "3D Bonding Milestone",
        defaultBadgeEmoji = "🦊"
    ),
    THEME_ATMOSPHERE(
        displayLabel = "Atmosphere Shift",
        defaultBadgeEmoji = "🎨"
    ),
    TASK_CHAMPION(
        displayLabel = "Thread Milestone",
        defaultBadgeEmoji = "🏆"
    );

    val categoryLabel: String
        get() = displayLabel
}

/**
 * Room Entity storing significant pet milestones in the `pet_journal_entries` table
 * (e.g., first high-streak achievement, pet personality shifts, bonding moments, and custom reflections).
 */
@Entity(
    tableName = "pet_journal_entries",
    indices = [
        Index(value = ["timestamp_millis"]),
        Index(value = ["milestone_type"])
    ]
)
data class PetJournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "milestone_type")
    val milestoneType: PetMilestoneType = PetMilestoneType.PERSONALITY_SHIFT,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "narrative")
    val narrative: String,

    @ColumnInfo(name = "badge_emoji")
    val badgeEmoji: String = milestoneType.defaultBadgeEmoji,

    @ColumnInfo(name = "happiness_snapshot")
    val happinessSnapshot: Int = 75,

    @ColumnInfo(name = "streak_snapshot")
    val streakSnapshot: Int = 1,

    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long = System.currentTimeMillis()
)
