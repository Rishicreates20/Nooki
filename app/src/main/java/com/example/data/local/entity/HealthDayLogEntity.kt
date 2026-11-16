package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per local calendar day of health tracking (`health_day_logs`), stored in the signed-in
 * user's own database.
 *
 * [lastStepCounterValue] remembers the phone's cumulative step-counter reading the last time Nooki
 * looked, so new steps can be added as a difference (see [com.example.domain.health.StepTally]).
 * It is -1 until the first reading.
 */
@Entity(tableName = "health_day_logs")
data class HealthDayLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "day_epoch")
    val dayEpoch: Long,

    @ColumnInfo(name = "steps", defaultValue = "0")
    val steps: Int = 0,

    @ColumnInfo(name = "last_step_counter_value", defaultValue = "-1")
    val lastStepCounterValue: Long = -1L,

    @ColumnInfo(name = "water_glasses", defaultValue = "0")
    val waterGlasses: Int = 0,

    @ColumnInfo(name = "sleep_minutes", defaultValue = "0")
    val sleepMinutes: Int = 0,

    /** 0 = not logged, 1 (low) .. 5 (great). */
    @ColumnInfo(name = "mood_score", defaultValue = "0")
    val moodScore: Int = 0,

    @ColumnInfo(name = "updated_at_millis", defaultValue = "0")
    val updatedAtMillis: Long = System.currentTimeMillis()
)
