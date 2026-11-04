package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MoneyEntryEntity
import com.example.data.local.entity.NoticedItemEntity
import com.example.data.local.entity.SavedThingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedThingDao {
    @Insert
    suspend fun insert(thing: SavedThingEntity): Long

    @Update
    suspend fun update(thing: SavedThingEntity)

    @Query("DELETE FROM saved_things WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM saved_things ORDER BY updated_at_millis DESC")
    fun observeAll(): Flow<List<SavedThingEntity>>

    /** "where is my passport": matches the thing or the place, newest first. */
    @Query(
        "SELECT * FROM saved_things WHERE thing LIKE '%' || :query || '%' OR place LIKE '%' || :query || '%' " +
            "ORDER BY updated_at_millis DESC"
    )
    suspend fun search(query: String): List<SavedThingEntity>
}

@Dao
interface MoneyDao {
    @Insert
    suspend fun insert(entry: MoneyEntryEntity): Long

    @Query("UPDATE money_entries SET settled_at_millis = :atMillis WHERE id = :id")
    suspend fun settle(id: Long, atMillis: Long)

    @Query("DELETE FROM money_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM money_entries WHERE settled_at_millis IS NULL ORDER BY created_at_millis ASC")
    fun observeOpen(): Flow<List<MoneyEntryEntity>>

    @Query("SELECT * FROM money_entries WHERE settled_at_millis IS NULL ORDER BY created_at_millis ASC")
    suspend fun getOpen(): List<MoneyEntryEntity>
}

@Dao
interface NoticedDao {
    @Insert
    suspend fun insert(item: NoticedItemEntity): Long

    @Query("UPDATE noticed_items SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("SELECT * FROM noticed_items WHERE status = 'SUGGESTED' ORDER BY noticed_at_millis DESC")
    fun observeSuggested(): Flow<List<NoticedItemEntity>>

    @Query("SELECT * FROM noticed_items ORDER BY noticed_at_millis DESC")
    suspend fun getAll(): List<NoticedItemEntity>

    /** Collapse repeats: the same caller noticed again within a window is one item. */
    @Query(
        "SELECT COUNT(*) FROM noticed_items WHERE type = :type AND who = :who AND noticed_at_millis >= :sinceMillis"
    )
    suspend fun countSince(type: String, who: String, sinceMillis: Long): Int
}
