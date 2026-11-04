package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.repository.ReminderRepository
import com.example.domain.model.NookiPetMood
import com.example.domain.model.NookiStateMachine
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskSideEffect
import com.example.presentation.mvi.DeskUiState
import com.example.scheduler.ExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.ScheduleResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reactive MVI ViewModel powered by Kotlin [StateFlow].
 * Combines Room database flows (`allReminders`, `activeReminders`, `completedCount`, `totalEarnedXp`)
 * with real-time clock ticks and interaction state through [NookiStateMachine.reduce] so that any
 * Room table mutation immediately updates the UI and centered 3D Nooki model.
 */
class DeskPetViewModel(
    private val repository: ReminderRepository,
    private val alarmScheduler: ExactAlarmScheduler
) : ViewModel() {

    private data class EphemeralInteractionState(
        val nowMillis: Long = System.currentTimeMillis(),
        val celebratingUntilMillis: Long = 0L,
        val celebrationSpeech: String? = null,
        val lastEarnedXp: Int = 0,
        val petInteractionCombo: Int = 0,
        val bonusPetXp: Int = 0,
        val isAutoRotate3DEnabled: Boolean = true,
        val isComposerSheetOpen: Boolean = false,
        val isQueueSheetOpen: Boolean = false
    )

    private val ephemeralState = MutableStateFlow(EphemeralInteractionState())

    private val _sideEffects = Channel<DeskSideEffect>(Channel.BUFFERED)
    val sideEffects: Flow<DeskSideEffect> = _sideEffects.receiveAsFlow()

    private var lastLoggedMood: NookiPetMood? = null

    /**
     * Reactive [StateFlow] combining Room DAO flows with ephemeral UI state.
     * Uses `SharingStarted.WhileSubscribed(5_000L)` for lifecycle-aware collection in Compose.
     */
    val uiState: StateFlow<DeskUiState> = combine(
        repository.allReminders.distinctUntilChanged(),
        repository.activeReminders.distinctUntilChanged(),
        repository.completedCount.distinctUntilChanged(),
        repository.totalEarnedXp.distinctUntilChanged(),
        NookiSchedulerTelemetry.logs,
        ephemeralState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allReminders = values[0] as List<ReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val activeReminders = values[1] as List<ReminderEntity>
        val completedCount = values[2] as Int
        val dbXp = values[3] as Int
        @Suppress("UNCHECKED_CAST")
        val logs = values[4] as List<com.example.scheduler.SchedulerDiagnosticEntry>
        val local = values[5] as EphemeralInteractionState

        val now = local.nowMillis
        val ringing = allReminders.filter { it.status == ReminderStatus.RINGING }
        val overdue = allReminders.filter {
            it.isOverdue(now) &&
                it.status != ReminderStatus.COMPLETED &&
                it.status != ReminderStatus.CANCELLED
        }

        // Run the pure deterministic State Machine Reducer on the latest Room DB emission
        val resolvedPetState = NookiStateMachine.reduce(
            allReminders = allReminders,
            activeReminders = activeReminders,
            completedCount = completedCount,
            nowMillis = now,
            celebratingUntilMillis = local.celebratingUntilMillis,
            celebrationSpeech = local.celebrationSpeech,
            lastEarnedXp = local.lastEarnedXp
        )

        val combinedXp = dbXp + local.bonusPetXp
        val level = (combinedXp / 50) + 1
        val xpInLevel = combinedXp % 50

        DeskUiState(
            petState = resolvedPetState,
            petMood = resolvedPetState.mood,
            speechBubbleText = resolvedPetState.speechText,
            stateTransitionSummary = "${resolvedPetState.mood.stateCode} • ${resolvedPetState.dbTriggerSummary}",
            level = level,
            totalXp = combinedXp,
            xpInCurrentLevel = xpInLevel,
            xpForNextLevel = 50,
            petInteractionCombo = local.petInteractionCombo,
            isAutoRotate3DEnabled = local.isAutoRotate3DEnabled,
            allReminders = allReminders,
            activeReminders = activeReminders,
            ringingReminders = ringing,
            overdueReminders = overdue,
            completedCount = completedCount,
            nowMillis = now,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
            telemetryLogs = logs,
            isComposerSheetOpen = local.isComposerSheetOpen,
            isQueueSheetOpen = local.isQueueSheetOpen
        )
    }
        .onEach { state ->
            // Record state machine mood transitions driven by Room updates into telemetry
            if (lastLoggedMood != state.petMood) {
                val previous = lastLoggedMood?.stateCode ?: "INIT"
                lastLoggedMood = state.petMood
                NookiSchedulerTelemetry.record(
                    source = "StateFlowMachine",
                    message = "Transition: $previous ➔ ${state.petMood.stateCode} (${state.petState.dbTriggerSummary})"
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DeskUiState()
        )

    init {
        viewModelScope.launch {
            repository.seedStarterRemindersIfEmpty()
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                ephemeralState.update { it.copy(nowMillis = System.currentTimeMillis()) }
            }
        }
    }

    fun onIntent(intent: DeskIntent) {
        when (intent) {
            DeskIntent.PetNookiHead -> {
                val now = System.currentTimeMillis()
                val reactions = listOf(
                    "Purr-fect! That head pat gave me +2 cozy XP!",
                    "Hehe! Spin me around in 3D or add a new desk reminder!",
                    "Nooki loves keeping your desk on schedule! +2 XP!",
                    "Boop! Ready for your next deep-focus sprint!"
                )
                ephemeralState.update { state ->
                    val nextCombo = state.petInteractionCombo + 1
                    state.copy(
                        nowMillis = now,
                        celebratingUntilMillis = now + 3_200L,
                        celebrationSpeech = reactions[nextCombo % reactions.size],
                        lastEarnedXp = 2,
                        petInteractionCombo = nextCombo,
                        bonusPetXp = state.bonusPetXp + 2
                    )
                }
                viewModelScope.launch {
                    _sideEffects.send(DeskSideEffect.TriggerHapticPulse)
                }
            }

            DeskIntent.ToggleAutoRotate3D -> {
                ephemeralState.update { it.copy(isAutoRotate3DEnabled = !it.isAutoRotate3DEnabled) }
            }

            is DeskIntent.SetComposerSheetOpen -> {
                ephemeralState.update { it.copy(isComposerSheetOpen = intent.open) }
            }

            is DeskIntent.SetQueueSheetOpen -> {
                ephemeralState.update { it.copy(isQueueSheetOpen = intent.open) }
            }

            is DeskIntent.ScheduleReminder -> {
                viewModelScope.launch {
                    val triggerAt = System.currentTimeMillis() + intent.delayMinutes * 60_000L
                    val (entity, result) = repository.createAndScheduleReminder(
                        title = intent.title,
                        notes = intent.notes,
                        rawInput = "${intent.title} in ${intent.delayMinutes}m",
                        triggerAtMillis = triggerAt,
                        priority = intent.priority,
                        category = intent.category
                    )
                    val now = System.currentTimeMillis()
                    ephemeralState.update {
                        it.copy(
                            isComposerSheetOpen = false,
                            celebratingUntilMillis = now + 2_800L,
                            celebrationSpeech = "Saved to Room! I'll ring my 3D bell for '${entity.title}'!",
                            lastEarnedXp = 0
                        )
                    }
                    val message = when (result) {
                        is ScheduleResult.ExactScheduled ->
                            "Scheduled exact alarm '${entity.title}'"
                        is ScheduleResult.InexactFallbackScheduled ->
                            "Scheduled '${entity.title}' (fallback mode)"
                        is ScheduleResult.Failed ->
                            "Saved to Room; alarm warning: ${result.errorMessage}"
                    }
                    _sideEffects.send(DeskSideEffect.ShowMessage(message))
                }
            }

            is DeskIntent.CompleteReminder -> {
                viewModelScope.launch {
                    repository.completeReminder(intent.reminder.id)
                    val now = System.currentTimeMillis()
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = now + 3_500L,
                            celebrationSpeech = "Awesome job completing '${intent.reminder.title}'! +${intent.reminder.petMoodImpact} XP!",
                            lastEarnedXp = intent.reminder.petMoodImpact
                        )
                    }
                    _sideEffects.send(DeskSideEffect.TriggerHapticPulse)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Nooki cheered! +${intent.reminder.petMoodImpact} XP earned!")
                    )
                }
            }

            is DeskIntent.SnoozeReminder -> {
                viewModelScope.launch {
                    repository.snoozeReminder(intent.reminder.id, intent.minutes)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Snoozed '${intent.reminder.title}' for ${intent.minutes}m.")
                    )
                }
            }

            is DeskIntent.DeleteReminder -> {
                viewModelScope.launch {
                    repository.deleteReminder(intent.reminder.id)
                }
            }

            DeskIntent.Schedule5SecondTestAlarm -> {
                viewModelScope.launch {
                    val triggerAt = System.currentTimeMillis() + 5_000L
                    val (entity, _) = repository.createAndScheduleReminder(
                        title = "5s 3D Bell Test Alarm",
                        notes = "Watch Room emit RINGING and transition the centered 3D Nooki model in 5 seconds!",
                        rawInput = "Test alarm in 5 seconds",
                        triggerAtMillis = triggerAt,
                        priority = ReminderPriority.URGENT,
                        category = ReminderCategory.DESK
                    )
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = System.currentTimeMillis() + 2_500L,
                            celebrationSpeech = "Countdown started! Watch my StateFlow transition in 5 seconds!",
                            lastEarnedXp = 0
                        )
                    }
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Scheduled #${entity.id} for +5 seconds!")
                    )
                }
            }

            DeskIntent.SimulateBootRecovery -> {
                viewModelScope.launch {
                    val report = repository.reconcileAndRescheduleAlarms()
                    NookiSchedulerTelemetry.record(
                        source = "BootRecoverySim",
                        message = "Boot recovery executed: rescheduled=${report.rescheduledCount}, missed=${report.markedMissedCount}"
                    )
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage(
                            "Boot recovery: ${report.rescheduledCount} rescheduled, ${report.markedMissedCount} missed."
                        )
                    )
                }
            }

            DeskIntent.ClearCompleted -> {
                viewModelScope.launch {
                    repository.clearCompletedReminders()
                    _sideEffects.send(DeskSideEffect.ShowMessage("Cleared completed reminders."))
                }
            }
        }
    }

    class Factory(
        private val repository: ReminderRepository,
        private val alarmScheduler: ExactAlarmScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeskPetViewModel(repository, alarmScheduler) as T
        }
    }
}
