package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.HealthDayLogEntity
import com.example.data.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HealthUiState(
    val today: HealthDayLogEntity = HealthDayLogEntity(dayEpoch = 0L),
    /** Exactly 7 entries, oldest first, ending today. Days with nothing logged are zeros. */
    val lastSevenDays: List<HealthDayLogEntity> = emptyList()
) {
    val weekSteps: Int get() = lastSevenDays.sumOf { it.steps }
    fun daysStepGoalMet(goal: Int): Int = lastSevenDays.count { it.steps >= goal }
}

class HealthViewModel(private val repository: HealthRepository) : ViewModel() {

    val uiState: StateFlow<HealthUiState> = repository.observeRecent(days = 7)
        .map { logs ->
            val todayEpoch = repository.todayEpoch()
            val byDay = logs.associateBy { it.dayEpoch }
            val week = (6 downTo 0).map { back ->
                val day = todayEpoch - back
                byDay[day] ?: HealthDayLogEntity(dayEpoch = day, updatedAtMillis = 0L)
            }
            HealthUiState(today = week.last(), lastSevenDays = week)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    private var lastSavedReading = -1L
    private var lastSavedAtMillis = 0L

    /** The sensor can fire on every step, so readings are saved at most every 20 steps or 30 s. */
    fun onStepCounterReading(reading: Long) {
        val now = System.currentTimeMillis()
        val bigJump = lastSavedReading < 0 || kotlin.math.abs(reading - lastSavedReading) >= 20
        if (!bigJump && now - lastSavedAtMillis < 30_000L) return
        lastSavedReading = reading
        lastSavedAtMillis = now
        launch { repository.recordStepCounter(reading) }
    }
    fun addSteps(delta: Int) = launch { repository.addSteps(delta) }
    fun changeWater(delta: Int) = launch { repository.changeWater(delta) }
    fun setSleepMinutes(minutes: Int) = launch { repository.setSleepMinutes(minutes) }
    fun setMood(score: Int) = launch { repository.setMood(score) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthViewModel(repository) as T
    }
}
