package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Priority level of a desk reminder, influencing Nooki's urgency animation and XP reward.
 */
enum class ReminderPriority(val label: String, val xpReward: Int) {
    LOW(label = "Cozy", xpReward = 5),
    MEDIUM(label = "Normal", xpReward = 10),
    HIGH(label = "Important", xpReward = 20),
    URGENT(label = "Critical", xpReward = 35)
}

/**
 * Desk-pet reminder categories aligned with Nooki's animations and desk companions.
 */
enum class ReminderCategory(val label: String, val badgeText: String) {
    DESK(label = "Desk Task", badgeText = "DESK"),
    HYDRATION(label = "Hydration", badgeText = "SIP"),
    STRETCH(label = "Posture & Stretch", badgeText = "MOVE"),
    FOCUS(label = "Deep Focus", badgeText = "FOCUS"),
    ERRAND(label = "Quick Errand", badgeText = "GO"),
    CUSTOM(label = "Custom Note", badgeText = "NOTE")
}

/**
 * Lifecycle state of a scheduled reminder in Room and AlarmManager.
 */
enum class ReminderStatus(val label: String) {
    SCHEDULED("Scheduled"),
    RINGING("Ringing!"),
    SNOOZED("Snoozed"),
    COMPLETED("Completed"),
    MISSED("Missed"),
    CANCELLED("Cancelled")
}

/**
 * Room Entity representing a scheduled desk-pet reminder.
 * Indexed by [triggerAtMillis] and [status] for fast boot recovery and reactive timeline queries.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["trigger_at_millis"]),
        Index(value = ["status"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "raw_input")
    val rawInput: String = "",

    @ColumnInfo(name = "trigger_at_millis")
    val triggerAtMillis: Long,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at_millis")
    val completedAtMillis: Long? = null,

    @ColumnInfo(name = "priority")
    val priority: ReminderPriority = ReminderPriority.MEDIUM,

    @ColumnInfo(name = "category")
    val category: ReminderCategory = ReminderCategory.DESK,

    @ColumnInfo(name = "status")
    val status: ReminderStatus = ReminderStatus.SCHEDULED,

    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int = 0,

    @ColumnInfo(name = "recurrence_minutes")
    val recurrenceMinutes: Int? = null,

    @ColumnInfo(name = "pet_mood_impact")
    val petMoodImpact: Int = priority.xpReward
) {
    val isActive: Boolean
        get() = status == ReminderStatus.SCHEDULED ||
            status == ReminderStatus.SNOOZED ||
            status == ReminderStatus.RINGING

    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        status == ReminderStatus.MISSED ||
            (status == ReminderStatus.RINGING && nowMillis - triggerAtMillis > 5 * 60_000L) ||
            ((status == ReminderStatus.SCHEDULED || status == ReminderStatus.SNOOZED) && triggerAtMillis < nowMillis)
}
