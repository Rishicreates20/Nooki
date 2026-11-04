package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Nooki reminders.
 * Exposes reactive [Flow] streams for the MVI state machine and suspend functions for
 * AlarmManager & BroadcastReceiver mutations.
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

    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(pet_mood_impact), 0) FROM reminders WHERE status = 'COMPLETED'")
    fun observeTotalEarnedXp(): Flow<Int>

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
        SET status = :status, completed_at_millis = :completedAtMillis 
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: ReminderStatus,
        completedAtMillis: Long? = null
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
