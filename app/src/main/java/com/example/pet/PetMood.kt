package com.example.pet

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus

/**
 * Nooki's emotional state. It is derived purely from what Room says about the reminders (the single
 * source of truth), so the 3D pet, the card colors and later the widget always agree.
 */
enum class PetMood(val label: String, val caption: String) {
    SLEEPING("Sleeping", "Nothing due soon. Nooki is napping on the desk."),
    HAPPY("Happy", "Everything's on track."),
    ALERT("Alert", "Something needs you right now."),
    OVERWHELMED("Overwhelmed", "Tasks are piling up. Help Nooki out!")
}

object PetMoodResolver {
    /** A reminder due within this window keeps Nooki awake. */
    const val AWAKE_WINDOW_MILLIS = 60 * 60_000L

    /** Nooki stays cheerful for a while after you complete something. */
    const val CELEBRATION_WINDOW_MILLIS = 15 * 60_000L

    /** A reminder due this soon makes Nooki look up. */
    const val HEADS_UP_WINDOW_MILLIS = 5 * 60_000L

    fun resolve(reminders: List<ReminderEntity>, nowMillis: Long): PetMood {
        val overdue = reminders.filter { it.isOverdue(nowMillis) }
        val ringing = reminders.count { it.status == ReminderStatus.RINGING }
        val active = reminders.filter { it.isActive }

        val urgentOverdue = overdue.any { it.priority == ReminderPriority.URGENT }
        if (overdue.size >= 3 || (overdue.size >= 2 && urgentOverdue) || (ringing >= 2 && overdue.isNotEmpty())) {
            return PetMood.OVERWHELMED
        }

        val dueSoon = active.any {
            it.status != ReminderStatus.RINGING &&
                it.triggerAtMillis >= nowMillis &&
                it.triggerAtMillis - nowMillis <= HEADS_UP_WINDOW_MILLIS
        }
        if (ringing > 0 || overdue.isNotEmpty() || dueSoon) return PetMood.ALERT

        val recentlyCompleted = reminders.any {
            it.status == ReminderStatus.COMPLETED &&
                it.completedAtMillis != null &&
                nowMillis - it.completedAtMillis in 0..CELEBRATION_WINDOW_MILLIS
        }
        val upcomingWithinHour = active.any { it.triggerAtMillis - nowMillis in 0..AWAKE_WINDOW_MILLIS }
        if (recentlyCompleted || upcomingWithinHour) return PetMood.HAPPY

        return PetMood.SLEEPING
    }
}
