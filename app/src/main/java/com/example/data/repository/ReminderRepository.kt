package com.example.data.repository

import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.scheduler.ExactAlarmScheduler
import com.example.scheduler.ScheduleResult
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of a Boot / TimeChange recovery pass across persisted reminders.
 */
data class BootRecoveryReport(
    val rescheduledCount: Int,
    val markedMissedCount: Int,
    val exactAlarmsPermitted: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Repository contract coordinating local Room persistence and OS-level ExactAlarmScheduler.
 */
interface ReminderRepository {
    val allReminders: Flow<List<ReminderEntity>>
    val activeReminders: Flow<List<ReminderEntity>>
    val completedCount: Flow<Int>
    val totalEarnedXp: Flow<Int>

    suspend fun getReminderById(id: Long): ReminderEntity?
    suspend fun createAndScheduleReminder(
        title: String,
        notes: String = "",
        rawInput: String = title,
        triggerAtMillis: Long,
        priority: ReminderPriority = ReminderPriority.MEDIUM,
        category: ReminderCategory = ReminderCategory.DESK,
        recurrenceMinutes: Int? = null
    ): Pair<ReminderEntity, ScheduleResult>

    suspend fun markRinging(id: Long): ReminderEntity?
    suspend fun completeReminder(id: Long): ReminderEntity?
    suspend fun snoozeReminder(id: Long, snoozeMinutes: Int = 5): Pair<ReminderEntity, ScheduleResult>?
    suspend fun dismissReminder(id: Long)
    suspend fun deleteReminder(id: Long)
    suspend fun clearCompletedReminders()
    suspend fun reconcileAndRescheduleAlarms(nowMillis: Long = System.currentTimeMillis()): BootRecoveryReport
    suspend fun seedStarterRemindersIfEmpty()
}

/**
 * Production implementation of [ReminderRepository] that guarantees atomic coordination
 * between [ReminderDao] and [ExactAlarmScheduler].
 */
class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ExactAlarmScheduler
) : ReminderRepository {

    override val allReminders: Flow<List<ReminderEntity>> = reminderDao.observeAllReminders()
    override val activeReminders: Flow<List<ReminderEntity>> = reminderDao.observeActiveReminders()
    override val completedCount: Flow<Int> = reminderDao.observeCompletedCount()
    override val totalEarnedXp: Flow<Int> = reminderDao.observeTotalEarnedXp()

    override suspend fun getReminderById(id: Long): ReminderEntity? =
        reminderDao.getReminderById(id)

    override suspend fun createAndScheduleReminder(
        title: String,
        notes: String,
        rawInput: String,
        triggerAtMillis: Long,
        priority: ReminderPriority,
        category: ReminderCategory,
        recurrenceMinutes: Int?
    ): Pair<ReminderEntity, ScheduleResult> {
        val draft = ReminderEntity(
            title = title.trim(),
            notes = notes.trim(),
            rawInput = rawInput.trim(),
            triggerAtMillis = triggerAtMillis,
            priority = priority,
            category = category,
            status = ReminderStatus.SCHEDULED,
            recurrenceMinutes = recurrenceMinutes,
            petMoodImpact = priority.xpReward
        )
        val generatedId = reminderDao.upsertReminder(draft)
        val persisted = draft.copy(id = generatedId)
        val scheduleResult = alarmScheduler.scheduleExact(persisted)
        return persisted to scheduleResult
    }

    override suspend fun markRinging(id: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        if (existing.status == ReminderStatus.COMPLETED || existing.status == ReminderStatus.CANCELLED) {
            return existing
        }
        reminderDao.updateStatus(id = id, status = ReminderStatus.RINGING, completedAtMillis = null)
        return existing.copy(status = ReminderStatus.RINGING)
    }

    override suspend fun completeReminder(id: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        // Done can be tapped from the notification, the card and Nooki's chip; only the first
        // tap may award XP and spawn the next recurring cycle.
        if (existing.status == ReminderStatus.COMPLETED) return existing
        alarmScheduler.cancel(id)
        val now = System.currentTimeMillis()
        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.COMPLETED,
            completedAtMillis = now
        )
        // If this reminder has a recurring desk interval, automatically schedule the next cycle
        val recurrenceMins = existing.recurrenceMinutes
        if (recurrenceMins != null && recurrenceMins > 0) {
            val nextTrigger = now + recurrenceMins * 60_000L
            createAndScheduleReminder(
                title = existing.title,
                notes = existing.notes,
                rawInput = existing.rawInput,
                triggerAtMillis = nextTrigger,
                priority = existing.priority,
                category = existing.category,
                recurrenceMinutes = recurrenceMins
            )
        }
        return existing.copy(status = ReminderStatus.COMPLETED, completedAtMillis = now)
    }

    override suspend fun snoozeReminder(
        id: Long,
        snoozeMinutes: Int
    ): Pair<ReminderEntity, ScheduleResult>? {
        val existing = reminderDao.getReminderById(id) ?: return null
        alarmScheduler.cancel(id)
        val newTriggerAt = System.currentTimeMillis() + (snoozeMinutes.coerceAtLeast(1) * 60_000L)
        val newSnoozeCount = existing.snoozeCount + 1
        reminderDao.updateTriggerTimeAndStatus(
            id = id,
            triggerAtMillis = newTriggerAt,
            status = ReminderStatus.SNOOZED,
            snoozeCount = newSnoozeCount
        )
        val updated = existing.copy(
            triggerAtMillis = newTriggerAt,
            status = ReminderStatus.SNOOZED,
            snoozeCount = newSnoozeCount
        )
        val result = alarmScheduler.scheduleExact(updated)
        return updated to result
    }

    override suspend fun dismissReminder(id: Long) {
        alarmScheduler.cancel(id)
        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.CANCELLED,
            completedAtMillis = null
        )
    }

    override suspend fun deleteReminder(id: Long) {
        alarmScheduler.cancel(id)
        reminderDao.deleteReminderById(id)
    }

    override suspend fun clearCompletedReminders() {
        reminderDao.clearCompletedReminders()
    }

    override suspend fun reconcileAndRescheduleAlarms(nowMillis: Long): BootRecoveryReport {
        val pending = reminderDao.getPendingRemindersForBootRecovery()
        var rescheduled = 0
        var missed = 0

        for (reminder in pending) {
            // Grace window of 15 seconds for alarms right around current time
            if (reminder.triggerAtMillis > nowMillis - 15_000L && reminder.status != ReminderStatus.RINGING) {
                alarmScheduler.scheduleExact(reminder)
                rescheduled++
            } else if (reminder.triggerAtMillis <= nowMillis - 15_000L && reminder.status != ReminderStatus.RINGING) {
                reminderDao.updateStatus(
                    id = reminder.id,
                    status = ReminderStatus.MISSED,
                    completedAtMillis = null
                )
                missed++
            }
        }

        return BootRecoveryReport(
            rescheduledCount = rescheduled,
            markedMissedCount = missed,
            exactAlarmsPermitted = alarmScheduler.canScheduleExactAlarms(),
            timestampMillis = nowMillis
        )
    }

    override suspend fun seedStarterRemindersIfEmpty() {
        if (reminderDao.getReminderCount() > 0) return
        val now = System.currentTimeMillis()
        createAndScheduleReminder(
            title = "Sip a glass of water with Nooki",
            notes = "Stay hydrated while coding! Nooki gets +10 XP when you complete this.",
            rawInput = "Remind me to sip water in 10 minutes",
            triggerAtMillis = now + 10 * 60_000L,
            priority = ReminderPriority.MEDIUM,
            category = ReminderCategory.HYDRATION,
            recurrenceMinutes = 60
        )
        createAndScheduleReminder(
            title = "Roll shoulders & look 20ft away",
            notes = "20-20-20 eye rest rule for desk longevity.",
            rawInput = "Stretch shoulders in 25 minutes",
            triggerAtMillis = now + 25 * 60_000L,
            priority = ReminderPriority.HIGH,
            category = ReminderCategory.STRETCH
        )
        createAndScheduleReminder(
            title = "Review afternoon sprint notes",
            notes = "Check off completed deliverables before wrap-up.",
            rawInput = "Review sprint notes in 45 minutes",
            triggerAtMillis = now + 45 * 60_000L,
            priority = ReminderPriority.LOW,
            category = ReminderCategory.FOCUS
        )
    }
}
