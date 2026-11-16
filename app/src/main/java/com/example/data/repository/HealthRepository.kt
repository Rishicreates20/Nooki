package com.example.data.repository

import com.example.data.local.dao.HealthDao
import com.example.data.local.entity.HealthDayLogEntity
import com.example.domain.health.HealthGoals
import com.example.domain.health.StepTally
import com.example.domain.model.ConsistencyStreakCalculator
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the signed-in user's daily health logs.
 */
class HealthRepository(private val healthDao: HealthDao) {

    fun todayEpoch(nowMillis: Long = System.currentTimeMillis()): Long =
        ConsistencyStreakCalculator.toLocalDayEpoch(nowMillis)

    /** The last [days] days of logs (today included), oldest first. Missing days are not returned. */
    fun observeRecent(days: Int, nowMillis: Long = System.currentTimeMillis()): Flow<List<HealthDayLogEntity>> =
        healthDao.observeFrom(todayEpoch(nowMillis) - (days - 1))

    private suspend fun today(): HealthDayLogEntity {
        val day = todayEpoch()
        return healthDao.get(day) ?: HealthDayLogEntity(
            dayEpoch = day,
            // Carry the last counter value over from the previous day so steps keep adding up
            // from where Nooki last looked.
            lastStepCounterValue = healthDao.getLatestBefore(day)?.lastStepCounterValue ?: -1L
        )
    }

    private suspend fun save(log: HealthDayLogEntity) {
        healthDao.upsert(log.copy(updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun recordStepCounter(reading: Long) {
        val log = today()
        val result = StepTally.apply(log.steps, log.lastStepCounterValue, reading)
        if (result.steps != log.steps || result.lastCounterValue != log.lastStepCounterValue) {
            save(log.copy(steps = result.steps, lastStepCounterValue = result.lastCounterValue))
        }
    }

    suspend fun addSteps(delta: Int) {
        val log = today()
        save(log.copy(steps = (log.steps + delta).coerceIn(0, 200_000)))
    }

    suspend fun changeWater(delta: Int) {
        val log = today()
        save(log.copy(waterGlasses = (log.waterGlasses + delta).coerceIn(0, HealthGoals.MAX_WATER_GLASSES)))
    }

    suspend fun setSleepMinutes(minutes: Int) {
        val log = today()
        save(log.copy(sleepMinutes = minutes.coerceIn(0, HealthGoals.MAX_SLEEP_MINUTES)))
    }

    suspend fun setMood(score: Int) {
        val log = today()
        save(log.copy(moodScore = score.coerceIn(0, 5)))
    }
}
