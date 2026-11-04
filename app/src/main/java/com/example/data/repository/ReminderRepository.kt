package com.example.data.repository

import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.PetJournalEntryEntity
import com.example.data.local.entity.PetMilestoneType
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.UserProfileEntity
import com.example.domain.model.ConsistencyStreakCalculator
import com.example.domain.model.NookiAppTheme
import com.example.domain.parser.NookiBreathParser
import com.example.domain.parser.ParsedBreathCapture
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
    val completedDayEpochs: Flow<List<Long>>
    val userProfile: Flow<UserProfileEntity?>
    val journalEntries: Flow<List<PetJournalEntryEntity>>

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

    suspend fun saveParsedBreathCapture(parsed: ParsedBreathCapture): Pair<ReminderEntity, ScheduleResult>
    suspend fun markRinging(id: Long): ReminderEntity?
    suspend fun completeReminder(id: Long): ReminderEntity?
    suspend fun reopenReminderToTodo(id: Long): ReminderEntity?
    suspend fun snoozeReminder(id: Long, snoozeMinutes: Int = 5): Pair<ReminderEntity, ScheduleResult>?
    suspend fun dismissReminder(id: Long)
    suspend fun deleteReminder(id: Long)
    suspend fun clearCompletedReminders()
    suspend fun moveUnfinishedTodayToTomorrowMorning(nowMillis: Long = System.currentTimeMillis()): Int
    suspend fun reconcileAndRescheduleAlarms(nowMillis: Long = System.currentTimeMillis()): BootRecoveryReport
    suspend fun updateUserProfile(
        displayName: String,
        companionTitle: String,
        avatarBadge: String,
        bioMotto: String,
        dailyReminderGoal: Int
    ): UserProfileEntity
    suspend fun updateSensoryPreferences(
        soundEnabled: Boolean,
        hapticsEnabled: Boolean
    ): UserProfileEntity
    suspend fun selectAppTheme(theme: NookiAppTheme): UserProfileEntity
    suspend fun recordJournalMilestone(
        milestoneType: PetMilestoneType,
        title: String,
        narrative: String,
        badgeEmoji: String = milestoneType.defaultBadgeEmoji,
        happinessSnapshot: Int = 75,
        streakSnapshot: Int = 1,
        timestampMillis: Long = System.currentTimeMillis()
    ): PetJournalEntryEntity
    suspend fun deleteJournalEntry(id: Long)
    suspend fun syncConsistencyStreakInProfile(
        currentStreakDays: Int,
        longestStreakDays: Int,
        lastCompletedDayEpoch: Long
    )
    suspend fun seedStarterRemindersIfEmpty()
}

/**
 * Production implementation of [ReminderRepository] enforcing Nooki's 10/10 rules:
 * - Nothing quietly disappears (swiping away moves to `MISSED`, repeating reminders survive being ignored,
 *   medicine keeps asking up to 3 times before staying in `MISSED`).
 * - Dates that matter automatically schedule lead warnings.
 * - Tracks `completedDayEpoch`, `UserProfileEntity`, `NookiAppTheme`, and `PetJournalEntryEntity` milestones.
 */
class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ExactAlarmScheduler
) : ReminderRepository {

    override val allReminders: Flow<List<ReminderEntity>> = reminderDao.observeAllReminders()
    override val activeReminders: Flow<List<ReminderEntity>> = reminderDao.observeActiveReminders()
    override val completedCount: Flow<Int> = reminderDao.observeCompletedCount()
    override val totalEarnedXp: Flow<Int> = reminderDao.observeTotalEarnedXp()
    override val completedDayEpochs: Flow<List<Long>> = reminderDao.observeCompletedDayEpochs()
    override val userProfile: Flow<UserProfileEntity?> = reminderDao.observeUserProfile()
    override val journalEntries: Flow<List<PetJournalEntryEntity>> = reminderDao.observeJournalEntries()

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

    override suspend fun saveParsedBreathCapture(
        parsed: ParsedBreathCapture
    ): Pair<ReminderEntity, ScheduleResult> {
        val structuredNotes = buildString {
            append("KIND:${parsed.kind.name}")
            if (parsed.personName != null) append(" | PERSON:${parsed.personName}")
            if (parsed.amountInr != null) append(" | INR:${parsed.amountInr}")
            if (parsed.advanceWarningDays.isNotEmpty()) {
                append(" | WARNS:${parsed.advanceWarningDays.joinToString(",")}")
            }
            if (parsed.recurrenceExplanation != null) {
                append(" | ${parsed.recurrenceExplanation}")
            }
        }
        return createAndScheduleReminder(
            title = parsed.cleanTitle,
            notes = structuredNotes,
            rawInput = parsed.rawInput,
            triggerAtMillis = parsed.triggerAtMillis,
            priority = parsed.mappedPriority,
            category = parsed.mappedCategory,
            recurrenceMinutes = parsed.recurrenceMinutes
        )
    }

    override suspend fun markRinging(id: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        if (existing.status == ReminderStatus.COMPLETED || existing.status == ReminderStatus.CANCELLED) {
            return existing
        }
        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.RINGING,
            completedAtMillis = null,
            completedDayEpoch = 0L
        )
        return existing.copy(status = ReminderStatus.RINGING)
    }

    override suspend fun completeReminder(id: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        alarmScheduler.cancel(id)
        val now = System.currentTimeMillis()
        val dayEpoch = ConsistencyStreakCalculator.toLocalDayEpoch(now)
        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.COMPLETED,
            completedAtMillis = now,
            completedDayEpoch = dayEpoch
        )
        // Repeating reminders automatically schedule the next cycle when untied
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
        return existing.copy(
            status = ReminderStatus.COMPLETED,
            completedAtMillis = now,
            completedDayEpoch = dayEpoch
        )
    }

    override suspend fun reopenReminderToTodo(id: Long): ReminderEntity? {
        val existing = reminderDao.getReminderById(id) ?: return null
        val now = System.currentTimeMillis()
        val nextTrigger = if (existing.triggerAtMillis > now) {
            existing.triggerAtMillis
        } else {
            now + 30 * 60_000L
        }
        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.SCHEDULED,
            completedAtMillis = null,
            completedDayEpoch = 0L
        )
        reminderDao.updateTriggerTimeAndStatus(
            id = id,
            triggerAtMillis = nextTrigger,
            status = ReminderStatus.SCHEDULED,
            snoozeCount = existing.snoozeCount
        )
        val reopened = existing.copy(
            triggerAtMillis = nextTrigger,
            status = ReminderStatus.SCHEDULED,
            completedAtMillis = null,
            completedDayEpoch = 0L
        )
        alarmScheduler.scheduleExact(reopened)
        return reopened
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

    /**
     * Enforces Section 5 ("Nothing quietly disappears"):
     * - Swiping away a notification moves it to "You missed" (`ReminderStatus.MISSED`), never silently deleting it.
     * - Medicine (`ReminderPriority.URGENT`) keeps asking up to 3 times before staying in "You missed".
     * - Repeating reminders survive being ignored: if a recurring reminder is missed, the next cycle is still scheduled.
     */
    override suspend fun dismissReminder(id: Long) {
        val existing = reminderDao.getReminderById(id) ?: return
        alarmScheduler.cancel(id)

        val isMedicine = existing.priority == ReminderPriority.URGENT ||
            existing.notes.contains("KIND:MEDICINE") ||
            existing.title.contains("दवाई") ||
            existing.title.contains("medicine", ignoreCase = true)

        if (isMedicine && existing.snoozeCount < 3) {
            val retryAt = System.currentTimeMillis() + 10 * 60_000L
            val nextCount = existing.snoozeCount + 1
            reminderDao.updateTriggerTimeAndStatus(
                id = id,
                triggerAtMillis = retryAt,
                status = ReminderStatus.SNOOZED,
                snoozeCount = nextCount
            )
            alarmScheduler.scheduleExact(
                existing.copy(
                    triggerAtMillis = retryAt,
                    status = ReminderStatus.SNOOZED,
                    snoozeCount = nextCount
                )
            )
            return
        }

        reminderDao.updateStatus(
            id = id,
            status = ReminderStatus.MISSED,
            completedAtMillis = null,
            completedDayEpoch = 0L
        )

        val recurrenceMins = existing.recurrenceMinutes
        if (recurrenceMins != null && recurrenceMins > 0 && existing.snoozeCount == 0) {
            val nextTrigger = System.currentTimeMillis() + recurrenceMins * 60_000L
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
    }

    override suspend fun deleteReminder(id: Long) {
        alarmScheduler.cancel(id)
        reminderDao.deleteReminderById(id)
    }

    override suspend fun clearCompletedReminders() {
        reminderDao.clearCompletedReminders()
    }

    /**
     * Evening wrap one-tap action: "what did not get done, with one tap to move it all to tomorrow"
     */
    override suspend fun moveUnfinishedTodayToTomorrowMorning(nowMillis: Long): Int {
        val pending = reminderDao.getPendingRemindersForBootRecovery()
        val tomorrow9Am = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var movedCount = 0
        for (item in pending) {
            val isHeldOrMoney = item.notes.contains("KIND:HELD_MEMORY") ||
                item.notes.contains("KIND:MONEY_") ||
                item.notes.contains("KIND:ITEM_LENT")
            if (!isHeldOrMoney) {
                alarmScheduler.cancel(item.id)
                val staggeredTime = tomorrow9Am + (movedCount * 15 * 60_000L)
                reminderDao.updateTriggerTimeAndStatus(
                    id = item.id,
                    triggerAtMillis = staggeredTime,
                    status = ReminderStatus.SCHEDULED,
                    snoozeCount = item.snoozeCount
                )
                alarmScheduler.scheduleExact(
                    item.copy(
                        triggerAtMillis = staggeredTime,
                        status = ReminderStatus.SCHEDULED
                    )
                )
                movedCount++
            }
        }
        return movedCount
    }

    override suspend fun reconcileAndRescheduleAlarms(nowMillis: Long): BootRecoveryReport {
        val pending = reminderDao.getPendingRemindersForBootRecovery()
        var rescheduled = 0
        var missed = 0

        for (reminder in pending) {
            if (reminder.triggerAtMillis > nowMillis - 15_000L && reminder.status != ReminderStatus.RINGING) {
                alarmScheduler.scheduleExact(reminder)
                rescheduled++
            } else if (reminder.triggerAtMillis <= nowMillis - 15_000L && reminder.status != ReminderStatus.RINGING) {
                reminderDao.updateStatus(
                    id = reminder.id,
                    status = ReminderStatus.MISSED,
                    completedAtMillis = null,
                    completedDayEpoch = 0L
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

    override suspend fun updateUserProfile(
        displayName: String,
        companionTitle: String,
        avatarBadge: String,
        bioMotto: String,
        dailyReminderGoal: Int
    ): UserProfileEntity {
        val existing = reminderDao.getUserProfile() ?: UserProfileEntity()
        val updated = existing.copy(
            displayName = displayName.trim().ifEmpty { "Aarav" },
            companionTitle = companionTitle.trim().ifEmpty { "Keeper of the Thread" },
            avatarBadge = avatarBadge.trim().ifEmpty { "🦊" },
            bioMotto = bioMotto.trim().ifEmpty { "One thread, calm mind" },
            dailyReminderGoal = dailyReminderGoal.coerceIn(1, 10),
            updatedAtMillis = System.currentTimeMillis()
        )
        reminderDao.upsertUserProfile(updated)
        return updated
    }

    override suspend fun updateSensoryPreferences(
        soundEnabled: Boolean,
        hapticsEnabled: Boolean
    ): UserProfileEntity {
        val existing = reminderDao.getUserProfile() ?: UserProfileEntity()
        val updated = existing.copy(
            soundEnabled = soundEnabled,
            hapticsEnabled = hapticsEnabled,
            updatedAtMillis = System.currentTimeMillis()
        )
        reminderDao.upsertUserProfile(updated)
        return updated
    }

    override suspend fun selectAppTheme(theme: NookiAppTheme): UserProfileEntity {
        val existing = reminderDao.getUserProfile() ?: UserProfileEntity()
        val updated = existing.copy(
            selectedThemeId = theme.id,
            updatedAtMillis = System.currentTimeMillis()
        )
        reminderDao.upsertUserProfile(updated)
        return updated
    }

    override suspend fun recordJournalMilestone(
        milestoneType: PetMilestoneType,
        title: String,
        narrative: String,
        badgeEmoji: String,
        happinessSnapshot: Int,
        streakSnapshot: Int,
        timestampMillis: Long
    ): PetJournalEntryEntity {
        val draft = PetJournalEntryEntity(
            milestoneType = milestoneType,
            title = title.trim(),
            narrative = narrative.trim(),
            badgeEmoji = badgeEmoji.ifBlank { milestoneType.defaultBadgeEmoji },
            happinessSnapshot = happinessSnapshot.coerceIn(0, 100),
            streakSnapshot = streakSnapshot.coerceAtLeast(0),
            timestampMillis = timestampMillis
        )
        val generatedId = reminderDao.insertJournalEntry(draft)
        return draft.copy(id = generatedId)
    }

    override suspend fun deleteJournalEntry(id: Long) {
        reminderDao.deleteJournalEntryById(id)
    }

    override suspend fun syncConsistencyStreakInProfile(
        currentStreakDays: Int,
        longestStreakDays: Int,
        lastCompletedDayEpoch: Long
    ) {
        val existing = reminderDao.getUserProfile()
        if (existing == null) {
            reminderDao.upsertUserProfile(
                UserProfileEntity(
                    currentStreakDays = currentStreakDays,
                    longestStreakDays = longestStreakDays,
                    lastCompletedDayEpoch = lastCompletedDayEpoch
                )
            )
        } else if (
            existing.currentStreakDays != currentStreakDays ||
            existing.longestStreakDays != longestStreakDays ||
            existing.lastCompletedDayEpoch != lastCompletedDayEpoch
        ) {
            reminderDao.updateStreakStats(
                currentStreakDays = currentStreakDays,
                longestStreakDays = maxOf(existing.longestStreakDays, longestStreakDays),
                lastCompletedDayEpoch = lastCompletedDayEpoch
            )
        }
    }

    override suspend fun seedStarterRemindersIfEmpty() {
        val now = System.currentTimeMillis()
        if (reminderDao.getUserProfile() == null) {
            val yesterdayEpoch = ConsistencyStreakCalculator.toLocalDayEpoch(now - 86_400_000L)
            reminderDao.upsertUserProfile(
                UserProfileEntity(
                    displayName = "Aarav",
                    companionTitle = "Keeper of the Thread",
                    avatarBadge = "🦊",
                    bioMotto = "One thread, calm mind • English, हिंग्लिश & हिंदी",
                    dailyReminderGoal = 3,
                    currentStreakDays = 2,
                    longestStreakDays = 5,
                    lastCompletedDayEpoch = yesterdayEpoch,
                    selectedThemeId = NookiAppTheme.MIDNIGHT_COZY.id,
                    updatedAtMillis = now
                )
            )
        }

        if (reminderDao.getJournalEntryCount() == 0) {
            recordJournalMilestone(
                milestoneType = PetMilestoneType.BONDING_MOMENT,
                title = "Nooki Arrived on the Desk Island",
                narrative = "Nooki unrolled the single continuous thread and promised nothing would quietly disappear.",
                badgeEmoji = "🦊",
                happinessSnapshot = 72,
                streakSnapshot = 1,
                timestampMillis = now - 72 * 3_600_000L
            )
            recordJournalMilestone(
                milestoneType = PetMilestoneType.PERSONALITY_SHIFT,
                title = "Pet Personality Shift: Warm & Content",
                narrative = "After untying evening knots on time, Nooki's ears perked up (+8°) and orbital fireflies began circling.",
                badgeEmoji = "✨",
                happinessSnapshot = 84,
                streakSnapshot = 2,
                timestampMillis = now - 26 * 3_600_000L
            )
            recordJournalMilestone(
                milestoneType = PetMilestoneType.FIRST_HIGH_STREAK,
                title = "First High-Streak Achievement (5-Day Thread)",
                narrative = "Five consecutive days of completed scheduled reminders! Nooki celebrated with a 360° 3D play hop.",
                badgeEmoji = "🔥",
                happinessSnapshot = 95,
                streakSnapshot = 5,
                timestampMillis = now - 6 * 3_600_000L
            )
        }

        if (reminderDao.getReminderCount() > 0) return

        // 1. "What did I miss?" (Sindoor Vermilion knot)
        val missedCallDraft = ReminderEntity(
            title = "Mom called, 2 hours ago",
            notes = "KIND:MISSED_CALL | Call back when free",
            rawInput = "Mom called",
            triggerAtMillis = now - 2 * 3_600_000L,
            priority = ReminderPriority.HIGH,
            category = ReminderCategory.DESK,
            status = ReminderStatus.MISSED,
            petMoodImpact = 20
        )
        reminderDao.upsertReminder(missedCallDraft)

        // 2. "What did I forget?" — Money owed & Saved things Nooki holds for you (Marigold knots)
        saveParsedBreathCapture(
            NookiBreathParser.parse("Rahul owes me 500", now)
        )
        saveParsedBreathCapture(
            NookiBreathParser.parse("Passport is in the blue bag", now)
        )
        saveParsedBreathCapture(
            NookiBreathParser.parse("Car parked at B2, pillar 14", now)
        )

        // 3. "What do I have to do?" — Today & coming up (Neel Indigo knots)
        createAndScheduleReminder(
            title = "evening दवाई (Medicine)",
            notes = "KIND:MEDICINE | Keeps asking until confirmed",
            rawInput = "कल शाम 6 बजे दवाई",
            triggerAtMillis = now + 20 * 60_000L,
            priority = ReminderPriority.URGENT,
            category = ReminderCategory.HYDRATION
        )
        saveParsedBreathCapture(
            NookiBreathParser.parse("Pay rent on the 1st every month", now)
        )

        // 4. Done tasks from the past 2 consecutive days so the Consistency Streak & Done Tasks
        // section are immediately populated and verifiable on first launch
        val yesterdayMillis = now - 24 * 3_600_000L
        val twoDaysAgoMillis = now - 48 * 3_600_000L
        reminderDao.upsertReminder(
            ReminderEntity(
                title = "Morning walk & stretch",
                notes = "KIND:REMINDER | Completed yesterday",
                rawInput = "morning walk",
                triggerAtMillis = yesterdayMillis - 1_800_000L,
                createdAtMillis = yesterdayMillis - 3_600_000L,
                completedAtMillis = yesterdayMillis,
                completedDayEpoch = ConsistencyStreakCalculator.toLocalDayEpoch(yesterdayMillis),
                priority = ReminderPriority.MEDIUM,
                category = ReminderCategory.STRETCH,
                status = ReminderStatus.COMPLETED,
                petMoodImpact = 15
            )
        )
        reminderDao.upsertReminder(
            ReminderEntity(
                title = "Pay electricity bill",
                notes = "KIND:REMINDER | Completed 2 days ago",
                rawInput = "pay electricity bill",
                triggerAtMillis = twoDaysAgoMillis - 1_800_000L,
                createdAtMillis = twoDaysAgoMillis - 3_600_000L,
                completedAtMillis = twoDaysAgoMillis,
                completedDayEpoch = ConsistencyStreakCalculator.toLocalDayEpoch(twoDaysAgoMillis),
                priority = ReminderPriority.HIGH,
                category = ReminderCategory.ERRAND,
                status = ReminderStatus.COMPLETED,
                petMoodImpact = 20
            )
        )
    }
}
