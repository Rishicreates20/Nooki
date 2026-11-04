package com.example.presentation.mvi

import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.domain.model.NookiPetMood
import com.example.scheduler.SchedulerDiagnosticEntry

/**
 * Single source-of-truth UI state for Nooki's MVI presentation layer.
 */
data class DeskUiState(
    val petMood: NookiPetMood = NookiPetMood.IDLE_COZY,
    val speechBubbleText: String = NookiPetMood.IDLE_COZY.defaultSpeech,
    val level: Int = 1,
    val totalXp: Int = 0,
    val xpInCurrentLevel: Int = 0,
    val xpForNextLevel: Int = 50,
    val petInteractionCombo: Int = 0,
    val isAutoRotate3DEnabled: Boolean = true,
    val allReminders: List<ReminderEntity> = emptyList(),
    val activeReminders: List<ReminderEntity> = emptyList(),
    val ringingReminders: List<ReminderEntity> = emptyList(),
    val overdueReminders: List<ReminderEntity> = emptyList(),
    val completedCount: Int = 0,
    val nowMillis: Long = System.currentTimeMillis(),
    val canScheduleExactAlarms: Boolean = true,
    val telemetryLogs: List<SchedulerDiagnosticEntry> = emptyList(),
    val isComposerSheetOpen: Boolean = false,
    val isQueueSheetOpen: Boolean = false
)

/**
 * User & system intents processed by [com.example.presentation.viewmodel.DeskPetViewModel].
 */
sealed interface DeskIntent {
    data object PetNookiHead : DeskIntent
    data object ToggleAutoRotate3D : DeskIntent
    data class SetComposerSheetOpen(val open: Boolean) : DeskIntent
    data class SetQueueSheetOpen(val open: Boolean) : DeskIntent
    data class ScheduleReminder(
        val title: String,
        val notes: String,
        val delayMinutes: Int,
        val priority: ReminderPriority,
        val category: ReminderCategory
    ) : DeskIntent
    data class CompleteReminder(val reminder: ReminderEntity) : DeskIntent
    data class SnoozeReminder(val reminder: ReminderEntity, val minutes: Int = 5) : DeskIntent
    data class DeleteReminder(val reminder: ReminderEntity) : DeskIntent
    data object Schedule5SecondTestAlarm : DeskIntent
    data object SimulateBootRecovery : DeskIntent
    data object ClearCompleted : DeskIntent
}

/**
 * One-shot side effects emitted by the ViewModel.
 */
sealed interface DeskSideEffect {
    data class ShowMessage(val message: String) : DeskSideEffect
    data object TriggerHapticPulse : DeskSideEffect
}
