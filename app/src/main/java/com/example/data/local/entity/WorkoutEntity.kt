package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged workout (`workouts`), in the signed-in user's own database.
 * [type] is a [com.example.domain.health.WorkoutType] name.
 */
@Entity(
    tableName = "workouts",
    indices = [Index(value = ["day_epoch"])]
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "day_epoch")
    val dayEpoch: Long,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,

    @ColumnInfo(name = "calories")
    val calories: Int,

    @ColumnInfo(name = "started_at_millis")
    val startedAtMillis: Long,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis()
)
