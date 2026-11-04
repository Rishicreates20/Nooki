package com.example.presentation.mvi

import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.domain.model.MoodState
import com.example.domain.model.NookiPetMood
import com.example.domain.model.NookiPetState
import com.example.domain.notice.NoticeFilterSettings
import com.example.domain.notice.NoticeInspectionResult
import com.example.domain.notice.NoticeSourceKind
import com.example.domain.parser.NookiBreathParser
import com.example.domain.parser.ParsedBreathCapture
import com.example.scheduler.SchedulerDiagnosticEntry

/**
 * Immutable UI State emitted by [com.example.presentation.viewmodel.DeskPetViewModel]'s
 * [kotlinx.coroutines.flow.StateFlow], including [moodState] computed from reminder completion frequency.
 */
data class DeskUiState(
    val petState: NookiPetState = NookiPetState.IdleCozy(),
    val petMood: NookiPetMood = NookiPetMood.IDLE_COZY,
    val moodState: MoodState = MoodState(),
    val speechBubbleText: String = NookiPetMood.IDLE_COZY.defaultSpeech,
    val stateTransitionSummary: String = "STATE_IDLE_COZY • Listening to Room Flow",
    val level: Int = 1,
    val totalXp: Int = 0,
    val xpInCurrentLevel: Int = 0,
    val xpForNextLevel: Int = 50,
    val petInteractionCombo: Int = 0,
    val isAutoRotate3DEnabled: Boolean = true,

    // Live "Tell Nooki: capture in one breath" state
    val breathInputText: String = "",
    val liveParsedCapture: ParsedBreathCapture = NookiBreathParser.parse(""),

    // Raw & categorized lists for "The Three Questions" on The Thread
    val allReminders: List<ReminderEntity> = emptyList(),
    val activeReminders: List<ReminderEntity> = emptyList(),
    val ringingReminders: List<ReminderEntity> = emptyList(),
    val overdueReminders: List<ReminderEntity> = emptyList(),

    // 1. What did I miss? (Sindoor #E23B3B)
    val youMissedKnots: List<ReminderEntity> = emptyList(),
    // 2. What did I forget? (Marigold #FFAE1A — Money, Held Things, Approaching Dates)
    val youForgotAndHeldKnots: List<ReminderEntity> = emptyList(),
    val netMoneyOwedToYouInr: Int = 0,
    val netMoneyYouOweInr: Int = 0,
    // 3. What do I have to do? (Indigo #1C2254 — Today & Coming Up)
    val todayAndComingUpKnots: List<ReminderEntity> = emptyList(),
    // Untied / Done knots (Leaf #1F9D6B)
    val doneKnots: List<ReminderEntity> = emptyList(),

    // Nooki Notices (Privacy-safe suggestions; nothing becomes a reminder until tapped)
    val noticeSettings: NoticeFilterSettings = NoticeFilterSettings(),
    val noticeSuggestions: List<NoticeInspectionResult.SafeSuggestion> = emptyList(),
    val lastDroppedNoticeAudit: String? = null,

    val completedCount: Int = 0,
    val nowMillis: Long = System.currentTimeMillis(),
    val canScheduleExactAlarms: Boolean = true,
    val telemetryLogs: List<SchedulerDiagnosticEntry> = emptyList(),
    val isComposerSheetOpen: Boolean = false,
    val isQueueSheetOpen: Boolean = false
)

/**
 * User and system intents dispatched to the reactive MVI state machine.
 */
sealed interface DeskIntent {
    data object PetNookiHead : DeskIntent
    data object ToggleAutoRotate3D : DeskIntent
    data class UpdateBreathInput(val text: String) : DeskIntent
    data object SaveBreathInput : DeskIntent
    data class AcceptNoticeSuggestion(val suggestion: NoticeInspectionResult.SafeSuggestion) : DeskIntent
    data class DismissNoticeSuggestion(val suggestion: NoticeInspectionResult.SafeSuggestion) : DeskIntent
    data class ToggleNoticeKind(val kind: NoticeSourceKind) : DeskIntent
    data class SimulateIncomingNotification(
        val packageName: String,
        val senderOrTitle: String,
        val rawBody: String
    ) : DeskIntent
    data object EveningWrapMoveAllToTomorrow : DeskIntent

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
 * One-shot UI side effects (Snackbars, Haptics) emitted via a buffered Channel.
 */
sealed interface DeskSideEffect {
    data class ShowMessage(val message: String) : DeskSideEffect
    data object TriggerHapticPulse : DeskSideEffect
}
