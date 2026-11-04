package com.example.data.repository

import com.example.core.parse.ParsedReminder
import com.example.core.parse.PriorityHint
import com.example.core.schedule.WorkflowSchedule
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.scheduler.ExactAlarmScheduler
import com.example.scheduler.ScheduleResult
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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

    /** Saves what "Tell Nooki" understood. Kept even without a time; scheduled only when it has one. */
    suspend fun saveParsed(parsed: ParsedReminder, rawInput: String): Pair<ReminderEntity, ScheduleResult?>

    /** Evening wrap: move everything not done to tomorrow in one tap. Items with no time get 9 am. */
    suspend fun moveToTomorrow(ids: List<Long>, nowMillis: Long = System.currentTimeMillis())

    /**
     * The notification was swiped away. That does not mean done: it moves to "You missed", and a
     * repeating chain continues. Medicine asks again (up to [MEDICINE_MAX_ASKS]) before giving up.
     */
    suspend fun markMissed(id: Long, nowMillis: Long = System.currentTimeMillis())

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
/** Medicine asks again this many times before it stays in "You missed". */
const val MEDICINE_MAX_ASKS = 3
const val MEDICINE_ASK_AGAIN_MILLIS = 10 * 60_000L

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

    override suspend fun saveParsed(
        parsed: ParsedReminder,
        rawInput: String
    ): Pair<ReminderEntity, ScheduleResult?> {
        val now = System.currentTimeMillis()
        val trigger = parsed.triggerAtMillis
        val priority = when (parsed.priorityHint) {
            PriorityHint.URGENT -> ReminderPriority.URGENT
            PriorityHint.HIGH -> ReminderPriority.HIGH
            null -> ReminderPriority.MEDIUM
        }
        val draft = ReminderEntity(
            title = parsed.title.ifBlank { rawInput.trim() },
            rawInput = rawInput.trim(),
            triggerAtMillis = trigger ?: now,
            createdAtMillis = now,
            priority = priority,
            category = ReminderCategory.CUSTOM,
            status = ReminderStatus.SCHEDULED,
            recurrenceMinutes = parsed.recurrenceMinutes,
            petMoodImpact = priority.xpReward,
            kind = parsed.kind,
            leadMinutes = WorkflowSchedule.encodeLeadTimes(parsed.leadMinutes),
            recurrenceRule = parsed.recurrenceRule,
            hasTime = trigger != null
        )
        val id = reminderDao.upsertReminder(draft)
        val persisted = draft.copy(id = id)
        val result = if (persisted.hasTime) alarmScheduler.scheduleExact(persisted) else null
        return persisted to result
    }

    override suspend fun moveToTomorrow(ids: List<Long>, nowMillis: Long) {
        for (id in ids) {
            val existing = reminderDao.getReminderById(id) ?: continue
            if (existing.status == ReminderStatus.COMPLETED || existing.status == ReminderStatus.CANCELLED) continue
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (existing.hasTime) {
                val clock = Calendar.getInstance().apply { timeInMillis = existing.triggerAtMillis }
                cal.set(Calendar.HOUR_OF_DAY, clock.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, clock.get(Calendar.MINUTE))
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            alarmScheduler.cancel(id)
            val moved = existing.copy(
                triggerAtMillis = cal.timeInMillis,
                hasTime = true,
                status = ReminderStatus.SCHEDULED,
                missedAtMillis = null
            )
            reminderDao.updateReminder(moved)
            alarmScheduler.scheduleExact(moved)
        }
    }

    override suspend fun markMissed(id: Long, nowMillis: Long) {
        val existing = reminderDao.getReminderById(id) ?: return
        if (existing.status == ReminderStatus.COMPLETED || existing.status == ReminderStatus.CANCELLED) return
        alarmScheduler.cancel(id)
        if (existing.kind.needsConfirmation && existing.confirmAttempts < MEDICINE_MAX_ASKS) {
            val again = existing.copy(
                triggerAtMillis = nowMillis + MEDICINE_ASK_AGAIN_MILLIS,
                status = ReminderStatus.SNOOZED,
                confirmAttempts = existing.confirmAttempts + 1
            )
            reminderDao.updateReminder(again)
            alarmScheduler.scheduleExact(again)
            return
        }
        reminderDao.updateReminder(existing.copy(status = ReminderStatus.MISSED, missedAtMillis = nowMillis))
        scheduleNextOccurrence(existing, nowMillis)
    }

    /** Repeating reminders survive being done or ignored: the chain always continues. */
    private suspend fun scheduleNextOccurrence(existing: ReminderEntity, nowMillis: Long) {
        val rule = existing.recurrenceRule ?: return
        val next = WorkflowSchedule.nextOccurrence(existing.triggerAtMillis, rule, nowMillis)
        val draft = existing.copy(
            id = 0L,
            triggerAtMillis = next,
            createdAtMillis = nowMillis,
            completedAtMillis = null,
            status = ReminderStatus.SCHEDULED,
            snoozeCount = 0,
            missedAtMillis = null,
            confirmAttempts = 0
        )
        val id = reminderDao.upsertReminder(draft)
        alarmScheduler.scheduleExact(draft.copy(id = id))
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
        scheduleNextOccurrence(existing, now)
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
            // No time yet: nothing to ring. It waits for the evening wrap.
            if (!reminder.hasTime) continue
            // Grace window of 15 seconds for alarms right around current time
            if (reminder.triggerAtMillis > nowMillis - 15_000L && reminder.status != ReminderStatus.RINGING) {
                alarmScheduler.scheduleExact(reminder)
                rescheduled++
            } else if (reminder.triggerAtMillis <= nowMillis - 15_000L && reminder.status != ReminderStatus.RINGING) {
                reminderDao.updateReminder(
                    reminder.copy(status = ReminderStatus.MISSED, missedAtMillis = nowMillis)
                )
                // A missed rent reminder does not end the monthly chain.
                scheduleNextOccurrence(reminder, nowMillis)
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
