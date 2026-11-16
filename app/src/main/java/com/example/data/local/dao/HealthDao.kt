package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.HealthDayLogEntity
import com.example.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_day_logs WHERE day_epoch >= :fromDayEpoch ORDER BY day_epoch ASC")
    fun observeFrom(fromDayEpoch: Long): Flow<List<HealthDayLogEntity>>

    @Query("SELECT * FROM health_day_logs WHERE day_epoch = :dayEpoch LIMIT 1")
    suspend fun get(dayEpoch: Long): HealthDayLogEntity?

    @Query("SELECT * FROM health_day_logs WHERE day_epoch < :dayEpoch ORDER BY day_epoch DESC LIMIT 1")
    suspend fun getLatestBefore(dayEpoch: Long): HealthDayLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HealthDayLogEntity)

    @Query("SELECT * FROM workouts WHERE day_epoch >= :fromDayEpoch ORDER BY started_at_millis DESC")
    fun observeWorkoutsFrom(fromDayEpoch: Long): Flow<List<WorkoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)
}
