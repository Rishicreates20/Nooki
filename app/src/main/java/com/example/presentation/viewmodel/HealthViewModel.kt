package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.HealthDayLogEntity
import com.example.data.local.entity.WorkoutEntity
import com.example.data.repository.HealthRepository
import com.example.domain.health.WorkoutMath
import com.example.domain.health.WorkoutType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A workout whose timer is running right now. */
data class ActiveWorkout(val type: WorkoutType, val startedAtMillis: Long)

data class HealthUiState(
    val today: HealthDayLogEntity = HealthDayLogEntity(dayEpoch = 0L),
    /** Exactly 7 entries, oldest first, ending today. Days with nothing logged are zeros. */
    val lastSevenDays: List<HealthDayLogEntity> = emptyList(),
    /** Workouts from the last 7 days, newest first. */
    val recentWorkouts: List<WorkoutEntity> = emptyList(),
    val todayEpoch: Long = 0L,
    val activeWorkout: ActiveWorkout? = null
) {
    val weekSteps: Int get() = lastSevenDays.sumOf { it.steps }
    fun daysStepGoalMet(goal: Int): Int = lastSevenDays.count { it.steps >= goal }

    val todayWorkouts: List<WorkoutEntity> get() = recentWorkouts.filter { it.dayEpoch == todayEpoch }
    val activeMinutesToday: Int get() = todayWorkouts.sumOf { it.durationMinutes }
    val caloriesToday: Int get() = todayWorkouts.sumOf { it.calories }
    val activeMinutesThisWeek: Int get() = recentWorkouts.sumOf { it.durationMinutes }

    /** Active minutes for each of the last 7 days, oldest first, lined up with [lastSevenDays]. */
    val activeMinutesByDay: List<Int>
        get() = lastSevenDays.map { day ->
            recentWorkouts.filter { it.dayEpoch == day.dayEpoch }.sumOf { it.durationMinutes }
        }
}

class HealthViewModel(private val repository: HealthRepository) : ViewModel() {

    private val activeWorkout = MutableStateFlow<ActiveWorkout?>(null)

    val uiState: StateFlow<HealthUiState> = combine(
        repository.observeRecent(days = 7),
        repository.observeRecentWorkouts(days = 7),
        activeWorkout
    ) { logs, workouts, active ->
        val todayEpoch = repository.todayEpoch()
        val byDay = logs.associateBy { it.dayEpoch }
        val week = (6 downTo 0).map { back ->
            val day = todayEpoch - back
            byDay[day] ?: HealthDayLogEntity(dayEpoch = day, updatedAtMillis = 0L)
        }
        HealthUiState(
            today = week.last(),
            lastSevenDays = week,
            recentWorkouts = workouts,
            todayEpoch = todayEpoch,
            activeWorkout = active
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

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

    // ---- Fitness ----

    fun startWorkout(type: WorkoutType) {
        if (activeWorkout.value == null) {
            activeWorkout.value = ActiveWorkout(type, System.currentTimeMillis())
        }
    }

    /** Stops the running timer and saves it as a workout. */
    fun finishWorkout() {
        val active = activeWorkout.value ?: return
        activeWorkout.value = null
        val minutes = WorkoutMath.elapsedMinutes(active.startedAtMillis, System.currentTimeMillis())
        launch { repository.logWorkout(active.type, minutes, active.startedAtMillis) }
    }

    fun cancelWorkout() {
        activeWorkout.value = null
    }

    /** Logs a workout that already happened (ended now). */
    fun logWorkout(type: WorkoutType, minutes: Int) = launch {
        repository.logWorkout(type, minutes, System.currentTimeMillis() - minutes * 60_000L)
    }

    fun deleteWorkout(id: Long) = launch { repository.deleteWorkout(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthViewModel(repository) as T
    }
}
