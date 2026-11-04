package com.example.domain.model

import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderSource
import com.example.data.local.entity.ReminderStatus

/**
 * Pure Kotlin Domain Model representing a Nooki Desk-Pet Reminder in the Clean Architecture
 * Domain Layer (free of Android/Room framework annotations).
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val rawInput: String = "",
    val triggerAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val category: ReminderCategory = ReminderCategory.DESK,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
    val snoozeCount: Int = 0,
    val recurrenceMinutes: Int? = null,
    val petMoodImpact: Int = priority.xpReward,
    val kind: ReminderKind = ReminderKind.TASK,
    val leadTimes: String = "",
    val recurrenceRule: RecurrenceRule? = null,
    val undated: Boolean = false,
    val keepAsking: Boolean = false,
    val askCount: Int = 0,
    val source: ReminderSource = ReminderSource.TYPED,
    val nextSpawned: Boolean = false
) {
    val isActive: Boolean
        get() = status == ReminderStatus.SCHEDULED ||
            status == ReminderStatus.SNOOZED ||
            status == ReminderStatus.RINGING

    val isCompleted: Boolean
        get() = status == ReminderStatus.COMPLETED

    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !undated && (
            status == ReminderStatus.MISSED ||
                (status == ReminderStatus.RINGING && nowMillis - triggerAtMillis > 5 * 60_000L) ||
                ((status == ReminderStatus.SCHEDULED || status == ReminderStatus.SNOOZED) && triggerAtMillis < nowMillis)
            )
}
