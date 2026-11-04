package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedStatus
import kotlinx.coroutines.flow.Flow

/** Everything Nooki holds or notices that is not a reminder. */
@Dao
interface AttentionDao {

    @Query("SELECT * FROM memories ORDER BY pinned DESC, updated_at_millis DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity): Long

    @Query("UPDATE memories SET pinned = :pinned WHERE id = :id")
    suspend fun setMemoryPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query(
        """
        SELECT * FROM loose_ends
        ORDER BY (settled_at_millis IS NOT NULL) ASC, created_at_millis DESC
        """
    )
    fun observeLooseEnds(): Flow<List<LooseEndEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLooseEnd(end: LooseEndEntity): Long

    @Query("UPDATE loose_ends SET settled_at_millis = :settledAtMillis WHERE id = :id")
    suspend fun setLooseEndSettled(id: Long, settledAtMillis: Long?)

    @Query("DELETE FROM loose_ends WHERE id = :id")
    suspend fun deleteLooseEnd(id: Long)

    @Query("SELECT * FROM noticed WHERE status = 'NEW' ORDER BY noticed_at_millis DESC")
    fun observeNewNoticed(): Flow<List<NoticedEntity>>

    @Query("SELECT * FROM noticed WHERE dedupe_key = :dedupeKey LIMIT 1")
    suspend fun findNoticed(dedupeKey: String): NoticedEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoticed(item: NoticedEntity): Long

    @Update
    suspend fun updateNoticed(item: NoticedEntity)

    @Query("UPDATE noticed SET status = :status, resolved_at_millis = :atMillis WHERE id = :id")
    suspend fun setNoticedStatus(id: Long, status: NoticedStatus, atMillis: Long)

    /** A message that was opened no longer needs anyone's attention, so nothing about it is kept. */
    @Query("DELETE FROM noticed WHERE dedupe_key = :dedupeKey AND type = 'UNREAD_MESSAGE' AND status = 'NEW'")
    suspend fun deleteOpenedMessage(dedupeKey: String)

    /** Clears the summaries of everything already dealt with once they are old. */
    @Query("DELETE FROM noticed WHERE status != 'NEW' AND resolved_at_millis < :beforeMillis")
    suspend fun deleteResolvedBefore(beforeMillis: Long)

    @Query("DELETE FROM noticed")
    suspend fun deleteAllNoticed()

    @Query("SELECT COUNT(*) FROM noticed WHERE resolved_at_millis >= :sinceMillis AND status IN ('HANDLED', 'TRACKED')")
    fun observeCaughtSince(sinceMillis: Long): Flow<Int>
}
