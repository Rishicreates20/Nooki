package com.example.presentation.mvi

import com.example.attention.TodayDigest
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderSource
import com.example.pet.PetMood
import com.example.scheduler.SchedulerDiagnosticEntry
import com.example.settings.NookiPrefs
import com.example.settings.Persona

enum class NookiTab(val label: String) {
    TODAY("Today"),
    REMEMBER("Remember"),
    NOOKI("Nooki")
}

data class CaptureDraft(
    val prefill: String = "",
    val source: ReminderSource = ReminderSource.TYPED
)

/** What the phone currently allows, read again each time the app comes back to the front. */
data class Readiness(
    val canPostNotifications: Boolean = true,
    val canScheduleExact: Boolean = true,
    val noticingEnabled: Boolean = false,
    val calendarAllowed: Boolean = false
)

/** Single source of truth for the screens. */
data class NookiUiState(
    val isLoaded: Boolean = false,
    val prefs: NookiPrefs = NookiPrefs(),
    val tab: NookiTab = NookiTab.TODAY,
    val digest: TodayDigest = TodayDigest(),
    val mood: PetMood = PetMood.SLEEPING,
    val petReaction: Int = 0,
    val memories: List<MemoryEntity> = emptyList(),
    val looseEnds: List<LooseEndEntity> = emptyList(),
    val level: Int = 1,
    val totalXp: Int = 0,
    val xpInLevel: Int = 0,
    val xpForNextLevel: Int = 50,
    val completedCount: Int = 0,
    val caughtThisWeek: Int = 0,
    val readiness: Readiness = Readiness(),
    val nowMillis: Long = System.currentTimeMillis(),
    val capture: CaptureDraft? = null,
    val openItem: ReminderEntity? = null,
    val telemetry: List<SchedulerDiagnosticEntry> = emptyList()
)

data class CaptureRequest(
    val rawInput: String,
    /** A time picked by hand, for a line that came with none. */
    val triggerOverride: Long?,
    val source: ReminderSource
)

/** Everything a person can do. */
sealed interface NookiIntent {
    data class SelectTab(val tab: NookiTab) : NookiIntent
    data class OpenCapture(val draft: CaptureDraft = CaptureDraft()) : NookiIntent
    data object CloseCapture : NookiIntent
    data class SubmitCapture(val request: CaptureRequest) : NookiIntent

    data class OpenItem(val reminder: ReminderEntity) : NookiIntent
    data object CloseItem : NookiIntent
    data class Complete(val reminder: ReminderEntity) : NookiIntent
    data class Snooze(val reminder: ReminderEntity, val minutes: Int) : NookiIntent
    data class Reschedule(val reminder: ReminderEntity, val atMillis: Long) : NookiIntent
    data class Delete(val reminder: ReminderEntity) : NookiIntent
    data class SetKeepAsking(val reminder: ReminderEntity, val keepAsking: Boolean) : NookiIntent
    data class RestoreReminder(val reminder: ReminderEntity) : NookiIntent

    data class NoticedHandled(val item: NoticedEntity) : NookiIntent
    data class NoticedIgnored(val item: NoticedEntity) : NookiIntent

    /** Turns something noticed into a reminder. For a call or message, in [minutes] from now. */
    data class NoticedTrack(val item: NoticedEntity, val minutes: Int = 60) : NookiIntent
    data class RestoreNoticed(val item: NoticedEntity) : NookiIntent

    data class SettleLooseEnd(val end: LooseEndEntity) : NookiIntent
    data class ReopenLooseEnd(val end: LooseEndEntity) : NookiIntent
    data class DeleteLooseEnd(val end: LooseEndEntity) : NookiIntent
    data class RestoreLooseEnd(val end: LooseEndEntity) : NookiIntent

    data class DeleteMemory(val memory: MemoryEntity) : NookiIntent
    data class RestoreMemory(val memory: MemoryEntity) : NookiIntent
    data class SetMemoryPinned(val memory: MemoryEntity, val pinned: Boolean) : NookiIntent

    data class UpdatePrefs(val transform: (NookiPrefs) -> NookiPrefs) : NookiIntent
    data class FinishOnboarding(val name: String, val persona: Persona, val starters: List<String>) : NookiIntent
    data object ClearNoticedHistory : NookiIntent

    data object PetNooki : NookiIntent
    data object Refresh : NookiIntent

    // Developer tools, shown only in debug builds.
    data object TestAlarm : NookiIntent
    data object SimulateBootRecovery : NookiIntent
    data object ClearCompleted : NookiIntent
    data object LoadSampleDay : NookiIntent
}

/** One-shot effects the screen carries out. */
sealed interface NookiEffect {
    data class Message(val text: String, val undo: NookiIntent? = null) : NookiEffect
    data class CancelNotification(val reminderId: Long) : NookiEffect
    data object Tap : NookiEffect
    data object Confirm : NookiEffect
    data object Celebrate : NookiEffect
}
