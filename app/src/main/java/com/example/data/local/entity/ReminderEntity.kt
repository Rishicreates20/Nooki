package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Reminder

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
    DESK(label = "Task", badgeText = "TASK"),
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
 * Room Entity representing a scheduled desk-pet reminder in the Data Layer (`reminders` table).
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
    val petMoodImpact: Int = priority.xpReward,

    /** Exam, deadline, AI limit reset, bill or habit. Added in database version 2. */
    @ColumnInfo(name = "kind", defaultValue = "TASK")
    val kind: ReminderKind = ReminderKind.TASK,

    /** Heads-ups before [triggerAtMillis], as minutes, comma separated ("10080,1440,120"). */
    @ColumnInfo(name = "lead_times", defaultValue = "")
    val leadTimes: String = "",

    /** Calendar repeat; [recurrenceMinutes] stays the fixed-interval repeat. */
    @ColumnInfo(name = "recurrence_rule")
    val recurrenceRule: RecurrenceRule? = null,

    /** No time was given. It stays on the list without an alarm and comes up in the evening wrap. */
    @ColumnInfo(name = "undated", defaultValue = "0")
    val undated: Boolean = false,

    /** Medicine and critical things ring again until confirmed, up to [MAX_ASKS] times. */
    @ColumnInfo(name = "keep_asking", defaultValue = "0")
    val keepAsking: Boolean = false,

    @ColumnInfo(name = "ask_count", defaultValue = "0")
    val askCount: Int = 0,

    @ColumnInfo(name = "source", defaultValue = "TYPED")
    val source: ReminderSource = ReminderSource.TYPED,

    /** True once the following occurrence exists, so a series never forks or dies. */
    @ColumnInfo(name = "next_spawned", defaultValue = "0")
    val nextSpawned: Boolean = false
) {
    val isActive: Boolean
        get() = status == ReminderStatus.SCHEDULED ||
            status == ReminderStatus.SNOOZED ||
            status == ReminderStatus.RINGING

    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !undated && (
            status == ReminderStatus.MISSED ||
                (status == ReminderStatus.RINGING && nowMillis - triggerAtMillis > 5 * 60_000L) ||
                ((status == ReminderStatus.SCHEDULED || status == ReminderStatus.SNOOZED) && triggerAtMillis < nowMillis)
            )

    companion object {
        const val MAX_ASKS = 3
    }
}

/**
 * Clean Architecture Mapper: Data Layer [ReminderEntity] -> Domain Layer [Reminder].
 */
fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    title = title,
    notes = notes,
    rawInput = rawInput,
    triggerAtMillis = triggerAtMillis,
    createdAtMillis = createdAtMillis,
    completedAtMillis = completedAtMillis,
    priority = priority,
    category = category,
    status = status,
    snoozeCount = snoozeCount,
    recurrenceMinutes = recurrenceMinutes,
    petMoodImpact = petMoodImpact,
    kind = kind,
    leadTimes = leadTimes,
    recurrenceRule = recurrenceRule,
    undated = undated,
    keepAsking = keepAsking,
    askCount = askCount,
    source = source,
    nextSpawned = nextSpawned
)

/**
 * Clean Architecture Mapper: Domain Layer [Reminder] -> Data Layer [ReminderEntity].
 */
fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    title = title,
    notes = notes,
    rawInput = rawInput,
    triggerAtMillis = triggerAtMillis,
    createdAtMillis = createdAtMillis,
    completedAtMillis = completedAtMillis,
    priority = priority,
    category = category,
    status = status,
    snoozeCount = snoozeCount,
    recurrenceMinutes = recurrenceMinutes,
    petMoodImpact = petMoodImpact,
    kind = kind,
    leadTimes = leadTimes,
    recurrenceRule = recurrenceRule,
    undated = undated,
    keepAsking = keepAsking,
    askCount = askCount,
    source = source,
    nextSpawned = nextSpawned
)
