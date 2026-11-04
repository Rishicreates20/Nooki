package com.example.data.repository

import com.example.attention.TimeWords
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderSource
import com.example.data.local.entity.ReminderStatus
import com.example.scheduler.ExactAlarmScheduler
import com.example.scheduler.ScheduleResult
import com.example.workflow.WorkflowSchedule
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
        recurrenceMinutes: Int? = null,
        kind: ReminderKind = ReminderKind.TASK,
        leadMinutes: List<Int> = kind.defaultLeadMinutes,
        recurrenceRule: RecurrenceRule? = null,
        undated: Boolean = false,
        keepAsking: Boolean = false,
        source: ReminderSource = ReminderSource.TYPED
    ): Pair<ReminderEntity, ScheduleResult>

    /** Schedules the earliest heads-up still ahead of [nowMillis], if the reminder has any left. */
    fun scheduleNextLeadAlert(reminder: ReminderEntity, nowMillis: Long = System.currentTimeMillis())

    /**
     * An alarm went off. Returns the reminder to ring, or null if it no longer needs to. A calendar
     * repeat books its next occurrence here, so ignoring this one can never end the series.
     */
    suspend fun onAlarmFired(id: Long, nowMillis: Long = System.currentTimeMillis()): ReminderEntity?
    suspend fun completeReminder(id: Long): ReminderEntity?
    suspend fun snoozeReminder(id: Long, snoozeMinutes: Int = 5): Pair<ReminderEntity, ScheduleResult>?
    suspend fun rescheduleReminder(id: Long, triggerAtMillis: Long): ReminderEntity?

    /** Swiping a notification away is not the same as doing the thing: it moves to "You missed". */
    suspend fun dismissReminder(id: Long)
    suspend fun deleteReminder(id: Long)

    /** Puts a reminder back after an undo. */
    suspend fun restoreReminder(snapshot: ReminderEntity)
    suspend fun setKeepAsking(id: Long, keepAsking: Boolean)

    /** The evening wrap's one-tap move. Returns how many moved. */
    suspend fun moveOpenToTomorrow(nowMillis: Long = System.currentTimeMillis()): Int
    suspend fun clearCompletedReminders()
    suspend fun reconcileAndRescheduleAlarms(nowMillis: Long = System.currentTimeMillis()): BootRecoveryReport
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
        recurrenceMinutes: Int?,
        kind: ReminderKind,
        leadMinutes: List<Int>,
        recurrenceRule: RecurrenceRule?,
        undated: Boolean,
        keepAsking: Boolean,
        source: ReminderSource
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
            petMoodImpact = priority.xpReward,
            kind = kind,
            leadTimes = WorkflowSchedule.encodeLeadTimes(leadMinutes),
            recurrenceRule = recurrenceRule,
            undated = undated,
            keepAsking = keepAsking,
            source = source
        )
        val generatedId = reminderDao.upsertReminder(draft)
        val persisted = draft.copy(id = generatedId)
        val scheduleResult = alarmScheduler.scheduleExact(persisted)
        scheduleNextLeadAlert(persisted)
        return persisted to scheduleResult
    }

    override fun scheduleNextLeadAlert(reminder: ReminderEntity, nowMillis: Long) {
        if (reminder.undated) return
        val next = WorkflowSchedule.nextLeadAlert(
            triggerAtMillis = reminder.triggerAtMillis,
            leadMinutes = WorkflowSchedule.decodeLeadTimes(reminder.leadTimes),
            nowMillis = nowMillis
        )
        if (next != null) alarmScheduler.scheduleLeadAlert(reminder, next) else alarmScheduler.cancelLeadAlert(reminder.id)
    }

    override suspend fun onAlarmFired(id: Long, nowMillis: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        val alreadyRinging = existing.status == ReminderStatus.RINGING
        val stillWanted = alreadyRinging ||
            existing.status == ReminderStatus.SCHEDULED ||
            existing.status == ReminderStatus.SNOOZED
        if (!stillWanted) return null
        if (alreadyRinging && (!existing.keepAsking || existing.askCount >= ReminderEntity.MAX_ASKS)) return null

        val askCount = if (alreadyRinging) existing.askCount + 1 else 0
        reminderDao.updateStatus(id = id, status = ReminderStatus.RINGING, completedAtMillis = null)
        reminderDao.updateAskCount(id, askCount)
        val ringing = existing.copy(status = ReminderStatus.RINGING, askCount = askCount)

        if (existing.recurrenceRule != null) continueSeries(existing, nowMillis)
        if (ringing.keepAsking && askCount < ReminderEntity.MAX_ASKS) {
            alarmScheduler.scheduleExact(ringing.copy(triggerAtMillis = nowMillis + ASK_AGAIN_MILLIS))
        }
        return ringing
    }

    override suspend fun completeReminder(id: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        // Done can be tapped from the notification, the row and the widget; only the first tap may
        // award XP and move the series on.
        if (existing.status == ReminderStatus.COMPLETED) return existing
        alarmScheduler.cancel(id)
        alarmScheduler.cancelLeadAlert(id)
        val now = System.currentTimeMillis()
        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.COMPLETED,
            completedAtMillis = now
        )
        continueSeries(existing, now)
        return existing.copy(status = ReminderStatus.COMPLETED, completedAtMillis = now)
    }

    /**
     * Books the next occurrence once and only once. A calendar rule keeps its date pattern ("rent on
     * the 1st" stays on the 1st), while a fixed interval ("every 2 hours", "every 7 days") counts
     * from now, so "call mom every 7 days" means 7 days after the last call.
     */
    private suspend fun continueSeries(existing: ReminderEntity, nowMillis: Long) {
        if (existing.nextSpawned) return
        val rule = existing.recurrenceRule
        val intervalMinutes = existing.recurrenceMinutes
        val nextTrigger = when {
            rule != null -> WorkflowSchedule.nextOccurrence(existing.triggerAtMillis, rule, nowMillis)
            intervalMinutes != null && intervalMinutes > 0 -> nowMillis + intervalMinutes * 60_000L
            else -> return
        }
        reminderDao.markNextSpawned(existing.id)
        createAndScheduleReminder(
            title = existing.title,
            notes = existing.notes,
            rawInput = existing.rawInput,
            triggerAtMillis = nextTrigger,
            priority = existing.priority,
            category = existing.category,
            recurrenceMinutes = intervalMinutes,
            kind = existing.kind,
            leadMinutes = WorkflowSchedule.decodeLeadTimes(existing.leadTimes),
            recurrenceRule = rule,
            keepAsking = existing.keepAsking,
            source = existing.source
        )
    }

    override suspend fun snoozeReminder(
        id: Long,
        snoozeMinutes: Int
    ): Pair<ReminderEntity, ScheduleResult>? {
        val existing = reminderDao.getReminderById(id) ?: return null
        alarmScheduler.cancel(id)
        alarmScheduler.cancelLeadAlert(id)
        val newTriggerAt = System.currentTimeMillis() + (snoozeMinutes.coerceAtLeast(1) * 60_000L)
        val newSnoozeCount = existing.snoozeCount + 1
        reminderDao.updateTriggerTimeAndStatus(
            id = id,
            triggerAtMillis = newTriggerAt,
            status = ReminderStatus.SNOOZED,
            snoozeCount = newSnoozeCount
        )
        reminderDao.updateAskCount(id, 0)
        val updated = existing.copy(
            triggerAtMillis = newTriggerAt,
            status = ReminderStatus.SNOOZED,
            snoozeCount = newSnoozeCount,
            askCount = 0
        )
        val result = alarmScheduler.scheduleExact(updated)
        return updated to result
    }

    override suspend fun rescheduleReminder(id: Long, triggerAtMillis: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        alarmScheduler.cancel(id)
        alarmScheduler.cancelLeadAlert(id)
        reminderDao.reschedule(id, triggerAtMillis)
        val updated = existing.copy(
            triggerAtMillis = triggerAtMillis,
            status = ReminderStatus.SCHEDULED,
            undated = false,
            askCount = 0
        )
        alarmScheduler.scheduleExact(updated)
        scheduleNextLeadAlert(updated)
        return updated
    }

    override suspend fun dismissReminder(id: Long) {
        val existing = reminderDao.getReminderById(id) ?: return
        val open = existing.status == ReminderStatus.RINGING ||
            existing.status == ReminderStatus.SCHEDULED ||
            existing.status == ReminderStatus.SNOOZED
        if (!open) return
        alarmScheduler.cancel(id)
        alarmScheduler.cancelLeadAlert(id)
        closeAsMissed(existing, System.currentTimeMillis())
    }

    /**
     * A thing that repeats within the day (water every 2 hours) is skipped and carries on, because
     * a list of ten missed glasses helps nobody. Everything else stays in "You missed" until it is
     * dealt with, and a calendar repeat still books its next date.
     */
    private suspend fun closeAsMissed(reminder: ReminderEntity, nowMillis: Long) {
        val interval = reminder.recurrenceMinutes
        if (interval != null && interval < MINUTES_PER_DAY) {
            reminderDao.updateStatus(id = reminder.id, status = ReminderStatus.CANCELLED, completedAtMillis = null)
            continueSeries(reminder, nowMillis)
        } else {
            reminderDao.updateStatus(id = reminder.id, status = ReminderStatus.MISSED, completedAtMillis = null)
            if (reminder.recurrenceRule != null) continueSeries(reminder, nowMillis)
        }
    }

    override suspend fun deleteReminder(id: Long) {
        alarmScheduler.cancel(id)
        alarmScheduler.cancelLeadAlert(id)
        reminderDao.deleteReminderById(id)
    }

    override suspend fun restoreReminder(snapshot: ReminderEntity) {
        // A series may have moved on since the snapshot; undoing must not book it a second time.
        val current = reminderDao.getReminderById(snapshot.id)
        val restored = snapshot.copy(nextSpawned = current?.nextSpawned ?: snapshot.nextSpawned)
        reminderDao.upsertReminder(restored)
        val waiting = restored.status == ReminderStatus.SCHEDULED || restored.status == ReminderStatus.SNOOZED
        if (waiting && !restored.undated && restored.triggerAtMillis > System.currentTimeMillis()) {
            alarmScheduler.scheduleExact(restored)
            scheduleNextLeadAlert(restored)
        }
    }

    override suspend fun setKeepAsking(id: Long, keepAsking: Boolean) {
        reminderDao.updateKeepAsking(id, keepAsking)
    }

    override suspend fun moveOpenToTomorrow(nowMillis: Long): Int {
        val endOfToday = TimeWords.startOfDay(nowMillis, offsetDays = 1)
        val tomorrowMorning = endOfToday + 9 * 60 * 60_000L
        val open = reminderDao.getOpenOneOffsUntil(endOfToday)
        open.forEach { rescheduleReminder(it.id, tomorrowMorning) }
        return open.size
    }

    override suspend fun clearCompletedReminders() {
        reminderDao.clearCompletedReminders()
    }

    override suspend fun reconcileAndRescheduleAlarms(nowMillis: Long): BootRecoveryReport {
        val pending = reminderDao.getPendingRemindersForBootRecovery()
        var rescheduled = 0
        var missed = 0

        for (reminder in pending) {
            if (reminder.status == ReminderStatus.RINGING) continue
            // Grace window of 15 seconds for alarms right around current time
            if (reminder.triggerAtMillis > nowMillis - 15_000L) {
                alarmScheduler.scheduleExact(reminder)
                scheduleNextLeadAlert(reminder, nowMillis)
                rescheduled++
            } else {
                closeAsMissed(reminder, nowMillis)
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

    companion object {
        const val ASK_AGAIN_MILLIS = 10 * 60_000L
        private const val MINUTES_PER_DAY = 24 * 60
    }
}
