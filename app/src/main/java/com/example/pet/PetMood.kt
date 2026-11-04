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

    /**
     * Workflow kinds change the rules (see [ReminderKind]):
     * - an exam or deadline keeps Nooki awake a day ahead and Alert in the last 3 hours;
     * - a missed exam, deadline or bill counts double toward Overwhelmed;
     * - an AI limit reset is good news: it never stresses Nooki, and while it rings Nooki is Happy.
     */
    fun resolve(reminders: List<ReminderEntity>, nowMillis: Long): PetMood {
        val (goodNews, chores) = reminders.partition { it.kind.overdueWeight == 0 }
        val overdue = chores.filter { it.isOverdue(nowMillis) }
        val ringing = chores.count { it.status == ReminderStatus.RINGING }
        val active = chores.filter { it.isActive }

        val overdueWeight = overdue.sumOf { it.kind.overdueWeight }
        val urgentOverdue = overdue.any { it.priority == ReminderPriority.URGENT }
        if (overdueWeight >= 3 || (overdue.size >= 2 && urgentOverdue) || (ringing >= 2 && overdue.isNotEmpty())) {
            return PetMood.OVERWHELMED
        }

        val dueSoon = active.any {
            it.status != ReminderStatus.RINGING &&
                it.triggerAtMillis >= nowMillis &&
                it.triggerAtMillis - nowMillis <= maxOf(HEADS_UP_WINDOW_MILLIS, it.kind.alertWindowMillis)
        }
        if (ringing > 0 || overdue.isNotEmpty() || dueSoon) return PetMood.ALERT

        val recentlyCompleted = reminders.any {
            it.status == ReminderStatus.COMPLETED &&
                it.completedAtMillis != null &&
                nowMillis - it.completedAtMillis in 0..CELEBRATION_WINDOW_MILLIS
        }
        val goodNewsArrived = goodNews.any { it.status == ReminderStatus.RINGING }
        val upcoming = (active + goodNews.filter { it.isActive }).any {
            it.triggerAtMillis - nowMillis in 0..maxOf(AWAKE_WINDOW_MILLIS, it.kind.awakeWindowMillis)
        }
        if (recentlyCompleted || goodNewsArrived || upcoming) return PetMood.HAPPY

        return PetMood.SLEEPING
    }
}
