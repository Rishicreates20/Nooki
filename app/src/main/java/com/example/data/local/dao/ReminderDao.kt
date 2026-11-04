package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.PetJournalEntryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Nooki reminders, Consistency Streak day epochs, User Profile,
 * and Pet Milestone Journal entries.
 */
@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY trigger_at_millis ASC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT * FROM reminders 
        WHERE status IN ('SCHEDULED', 'SNOOZED', 'RINGING', 'MISSED')
        ORDER BY 
            CASE status 
                WHEN 'RINGING' THEN 0 
                WHEN 'MISSED' THEN 1 
                ELSE 2 
            END ASC,
            trigger_at_millis ASC
        """
    )
    fun observeActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'COMPLETED' ORDER BY COALESCE(completed_at_millis, created_at_millis) DESC")
    fun observeCompletedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(pet_mood_impact), 0) FROM reminders WHERE status = 'COMPLETED'")
    fun observeTotalEarnedXp(): Flow<Int>

    @Query(
        """
        SELECT DISTINCT completed_day_epoch FROM reminders 
        WHERE status = 'COMPLETED' AND completed_day_epoch > 0 
        ORDER BY completed_day_epoch DESC
        """
    )
    fun observeCompletedDayEpochs(): Flow<List<Long>>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun observeUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query(
        """
        UPDATE user_profile 
        SET current_streak_days = :currentStreakDays,
            longest_streak_days = :longestStreakDays,
            last_completed_day_epoch = :lastCompletedDayEpoch,
            updated_at_millis = :updatedAtMillis
        WHERE id = 1
        """
    )
    suspend fun updateStreakStats(
        currentStreakDays: Int,
        longestStreakDays: Int,
        lastCompletedDayEpoch: Long,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE user_profile 
        SET selected_theme = :themeId,
            updated_at_millis = :updatedAtMillis
        WHERE id = 1
        """
    )
    suspend fun updateSelectedTheme(
        themeId: String,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM pet_journal_entries ORDER BY timestamp_millis DESC")
    fun observeJournalEntries(): Flow<List<PetJournalEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: PetJournalEntryEntity): Long

    @Query("SELECT COUNT(*) FROM pet_journal_entries")
    suspend fun getJournalEntryCount(): Int

    @Query("DELETE FROM pet_journal_entries WHERE id = :id")
    suspend fun deleteJournalEntryById(id: Long)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    /**
     * Used by [com.example.scheduler.receiver.BootCompletedReceiver] to recover all active
     * reminders after a device reboot, package update, or time-zone change.
     */
    @Query(
        """
        SELECT * FROM reminders 
        WHERE status IN ('SCHEDULED', 'SNOOZED', 'RINGING')
        ORDER BY trigger_at_millis ASC
        """
    )
    suspend fun getPendingRemindersForBootRecovery(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query(
        """
        UPDATE reminders 
        SET status = :status, 
            completed_at_millis = :completedAtMillis,
            completed_day_epoch = :completedDayEpoch
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: ReminderStatus,
        completedAtMillis: Long? = null,
        completedDayEpoch: Long = 0L
    )

    @Query(
        """
        UPDATE reminders 
        SET trigger_at_millis = :triggerAtMillis, 
            status = :status, 
            snooze_count = :snoozeCount 
        WHERE id = :id
        """
    )
    suspend fun updateTriggerTimeAndStatus(
        id: Long,
        triggerAtMillis: Long,
        status: ReminderStatus,
        snoozeCount: Int
    )

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders WHERE status IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearCompletedReminders()

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun getReminderCount(): Int
}
