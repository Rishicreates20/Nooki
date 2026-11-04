package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.attention.AttentionEngine
import com.example.attention.CalendarEvent
import com.example.attention.TimeWords
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderSource
import com.example.demo.SampleDay
import com.example.parser.Capture
import com.example.parser.CaptureRouter
import com.example.parser.ParsedReminder
import com.example.parser.PriorityHint
import com.example.pet.PetMood
import com.example.pet.PetMoodResolver
import com.example.presentation.mvi.CaptureRequest
import com.example.presentation.mvi.NookiEffect
import com.example.presentation.mvi.NookiIntent
import com.example.presentation.mvi.NookiTab
import com.example.presentation.mvi.NookiUiState
import com.example.presentation.mvi.Readiness
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.SchedulerDiagnosticEntry
import com.example.settings.NookiPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MVI ViewModel. Room and DataStore are the source of truth; [LocalState] holds only what lasts as
 * long as the screen does. Screens send [NookiIntent]s and render [NookiUiState].
 */
class NookiViewModel(
    private val graph: AppContainer,
    private val readReadiness: () -> Readiness,
    private val refreshWidget: suspend () -> Unit
) : ViewModel() {

    private data class LocalState(
        val tab: NookiTab = NookiTab.TODAY,
        val capture: com.example.presentation.mvi.CaptureDraft? = null,
        val openItem: ReminderEntity? = null,
        val petReaction: Int = 0,
        val events: List<CalendarEvent> = emptyList(),
        val readiness: Readiness = Readiness()
    )

    private data class Stored(
        val reminders: List<ReminderEntity>,
        val noticed: List<NoticedEntity>,
        val looseEnds: List<LooseEndEntity>,
        val memories: List<MemoryEntity>
    )

    private data class Totals(
        val completed: Int,
        val xp: Int,
        val caught: Int,
        val prefs: NookiPrefs,
        val telemetry: List<SchedulerDiagnosticEntry>
    )

    private val local = MutableStateFlow(LocalState())
    private val effects = Channel<NookiEffect>(Channel.BUFFERED)
    val sideEffects: Flow<NookiEffect> = effects.receiveAsFlow()

    private val tick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    private val stored = combine(
        graph.reminders.allReminders,
        graph.attention.noticed,
        graph.attention.looseEnds,
        graph.attention.memories,
        ::Stored
    )

    private val totals = combine(
        graph.reminders.completedCount,
        graph.reminders.totalEarnedXp,
        graph.attention.caughtSince(System.currentTimeMillis() - WEEK_MILLIS),
        graph.settings.prefs,
        NookiSchedulerTelemetry.logs
    ) { completed, xp, caught, prefs, logs -> Totals(completed, xp, caught, prefs, logs) }

    val uiState: StateFlow<NookiUiState> = combine(stored, totals, local, tick) { data, sum, ui, now ->
        val digest = AttentionEngine.build(data.reminders, data.noticed, data.looseEnds, ui.events, now)
        val base = PetMoodResolver.resolve(data.reminders, now)
        val mood = if (base != PetMood.OVERWHELMED && digest.missed.size + digest.forgot.size >= PILE_UP) {
            PetMood.OVERWHELMED
        } else if (base == PetMood.SLEEPING && digest.needsAttentionCount > 0) {
            PetMood.ALERT
        } else {
            base
        }
        NookiUiState(
            isLoaded = true,
            prefs = sum.prefs,
            tab = ui.tab,
            digest = digest,
            mood = mood,
            petReaction = ui.petReaction,
            memories = data.memories,
            looseEnds = data.looseEnds,
            level = sum.xp / XP_PER_LEVEL + 1,
            totalXp = sum.xp,
            xpInLevel = sum.xp % XP_PER_LEVEL,
            xpForNextLevel = XP_PER_LEVEL,
            completedCount = sum.completed,
            caughtThisWeek = sum.caught,
            readiness = ui.readiness,
            nowMillis = now,
            capture = ui.capture,
            openItem = ui.openItem?.let { open -> data.reminders.firstOrNull { it.id == open.id } ?: open },
            telemetry = sum.telemetry
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NookiUiState())

    init {
        refresh()
        viewModelScope.launch { graph.rhythm.arm(graph.settings.current()) }
    }

    private fun refresh() {
        local.update { it.copy(readiness = readReadiness()) }
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) { graph.upcomingEvents(System.currentTimeMillis()) }
            local.update { it.copy(events = events) }
        }
    }

    private fun send(effect: NookiEffect) {
        viewModelScope.launch { effects.send(effect) }
    }

    private fun afterChange() {
        viewModelScope.launch { refreshWidget() }
    }

    fun onIntent(intent: NookiIntent) {
        when (intent) {
            is NookiIntent.SelectTab -> local.update { it.copy(tab = intent.tab) }
            is NookiIntent.OpenCapture -> local.update { it.copy(capture = intent.draft) }
            NookiIntent.CloseCapture -> local.update { it.copy(capture = null) }
            is NookiIntent.SubmitCapture -> submitCapture(intent.request)

            is NookiIntent.OpenItem -> local.update { it.copy(openItem = intent.reminder) }
            NookiIntent.CloseItem -> local.update { it.copy(openItem = null) }
            is NookiIntent.Complete -> complete(intent.reminder)
            is NookiIntent.Snooze -> launchChange {
                graph.reminders.snoozeReminder(intent.reminder.id, intent.minutes)
                closeItem()
                effects.send(NookiEffect.CancelNotification(intent.reminder.id))
                effects.send(NookiEffect.Tap)
                effects.send(NookiEffect.Message("Nooki will bring this back.", NookiIntent.RestoreReminder(intent.reminder)))
            }
            is NookiIntent.Reschedule -> launchChange {
                graph.reminders.rescheduleReminder(intent.reminder.id, intent.atMillis)
                closeItem()
                effects.send(NookiEffect.CancelNotification(intent.reminder.id))
                effects.send(
                    NookiEffect.Message(
                        "Moved to ${TimeWords.moment(intent.atMillis, System.currentTimeMillis())}.",
                        NookiIntent.RestoreReminder(intent.reminder)
                    )
                )
            }
            is NookiIntent.Delete -> launchChange {
                graph.reminders.deleteReminder(intent.reminder.id)
                closeItem()
                effects.send(NookiEffect.CancelNotification(intent.reminder.id))
                effects.send(NookiEffect.Message("Deleted.", NookiIntent.RestoreReminder(intent.reminder)))
            }
            is NookiIntent.SetKeepAsking -> launchChange {
                graph.reminders.setKeepAsking(intent.reminder.id, intent.keepAsking)
            }
            is NookiIntent.RestoreReminder -> launchChange {
                graph.reminders.restoreReminder(intent.reminder)
                effects.send(NookiEffect.Message("Put back."))
            }

            is NookiIntent.NoticedHandled -> launchChange {
                graph.attention.setNoticedStatus(intent.item, NoticedStatus.HANDLED, System.currentTimeMillis())
                effects.send(NookiEffect.Celebrate)
                effects.send(NookiEffect.Message("Marked as handled.", NookiIntent.RestoreNoticed(intent.item)))
            }
            is NookiIntent.NoticedIgnored -> launchChange {
                graph.attention.setNoticedStatus(intent.item, NoticedStatus.IGNORED, System.currentTimeMillis())
                effects.send(NookiEffect.Message("Ignored.", NookiIntent.RestoreNoticed(intent.item)))
            }
            is NookiIntent.NoticedTrack -> trackNoticed(intent.item, intent.minutes)
            is NookiIntent.RestoreNoticed -> launchChange { graph.attention.restoreNoticed(intent.item) }

            is NookiIntent.SettleLooseEnd -> launchChange {
                graph.attention.settleLooseEnd(intent.end, System.currentTimeMillis())
                effects.send(NookiEffect.Celebrate)
                effects.send(NookiEffect.Message("Settled with ${intent.end.person}.", NookiIntent.ReopenLooseEnd(intent.end)))
            }
            is NookiIntent.ReopenLooseEnd -> launchChange { graph.attention.reopenLooseEnd(intent.end) }
            is NookiIntent.DeleteLooseEnd -> launchChange {
                graph.attention.deleteLooseEnd(intent.end)
                effects.send(NookiEffect.Message("Deleted.", NookiIntent.RestoreLooseEnd(intent.end)))
            }
            is NookiIntent.RestoreLooseEnd -> launchChange { graph.attention.restoreLooseEnd(intent.end) }

            is NookiIntent.DeleteMemory -> launchChange {
                graph.attention.deleteMemory(intent.memory)
                effects.send(NookiEffect.Message("Deleted.", NookiIntent.RestoreMemory(intent.memory)))
            }
            is NookiIntent.RestoreMemory -> launchChange { graph.attention.restoreMemory(intent.memory) }
            is NookiIntent.SetMemoryPinned -> launchChange {
                graph.attention.setMemoryPinned(intent.memory, intent.pinned)
            }

            is NookiIntent.UpdatePrefs -> viewModelScope.launch {
                graph.settings.update(intent.transform)
                graph.rhythm.arm(graph.settings.current())
            }
            is NookiIntent.FinishOnboarding -> launchChange {
                val now = System.currentTimeMillis()
                intent.starters.forEach { line ->
                    saveLine(CaptureRequest(line, triggerOverride = null, source = ReminderSource.STARTER), now, quiet = true)
                }
                graph.settings.update { it.copy(onboarded = true, name = intent.name.trim(), persona = intent.persona) }
                graph.rhythm.arm(graph.settings.current())
            }
            NookiIntent.ClearNoticedHistory -> launchChange {
                graph.attention.deleteAllNoticed()
                effects.send(NookiEffect.Message("Everything Nooki noticed is deleted."))
            }

            NookiIntent.PetNooki -> {
                local.update { it.copy(petReaction = it.petReaction + 1) }
                send(NookiEffect.Tap)
            }
            NookiIntent.Refresh -> refresh()

            NookiIntent.TestAlarm -> launchChange {
                val line = "Test reminder from the developer screen"
                graph.reminders.createAndScheduleReminder(
                    title = line,
                    rawInput = line,
                    triggerAtMillis = System.currentTimeMillis() + 5_000L,
                    priority = ReminderPriority.URGENT
                )
                effects.send(NookiEffect.Message("Rings in 5 seconds."))
            }
            NookiIntent.SimulateBootRecovery -> launchChange {
                val report = graph.reminders.reconcileAndRescheduleAlarms()
                NookiSchedulerTelemetry.record(
                    source = "BootRecoverySim",
                    message = "rescheduled=${report.rescheduledCount}, missed=${report.markedMissedCount}"
                )
                effects.send(NookiEffect.Message("${report.rescheduledCount} restored, ${report.markedMissedCount} missed."))
            }
            NookiIntent.ClearCompleted -> launchChange { graph.reminders.clearCompletedReminders() }
            NookiIntent.LoadSampleDay -> launchChange {
                SampleDay.seed(graph, System.currentTimeMillis())
                effects.send(NookiEffect.Message("Sample day loaded."))
            }
        }
    }

    private fun launchChange(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            afterChange()
        }
    }

    private fun closeItem() = local.update { it.copy(openItem = null) }

    private fun complete(reminder: ReminderEntity) = launchChange {
        graph.reminders.completeReminder(reminder.id)
        closeItem()
        local.update { it.copy(petReaction = it.petReaction + 1) }
        effects.send(NookiEffect.CancelNotification(reminder.id))
        effects.send(NookiEffect.Celebrate)
        effects.send(NookiEffect.Message("Done. +${reminder.petMoodImpact} XP", NookiIntent.RestoreReminder(reminder)))
    }

    private fun submitCapture(request: CaptureRequest) = launchChange {
        local.update { it.copy(capture = null) }
        saveLine(request, System.currentTimeMillis(), quiet = false)
        local.update { it.copy(petReaction = it.petReaction + 1) }
    }

    /** Saves one line wherever it belongs. */
    private suspend fun saveLine(request: CaptureRequest, nowMillis: Long, quiet: Boolean) {
        when (val capture = CaptureRouter.route(request.rawInput, nowMillis)) {
            is Capture.Ask -> Unit
            is Capture.Hold -> {
                val saved = graph.attention.saveMemory(capture.draft)
                if (!quiet) {
                    effects.send(NookiEffect.Confirm)
                    effects.send(NookiEffect.Message("Saved to Remember.", NookiIntent.DeleteMemory(saved)))
                }
            }
            is Capture.Lend -> {
                val saved = graph.attention.saveLooseEnd(capture.draft, request.rawInput)
                if (!quiet) {
                    effects.send(NookiEffect.Confirm)
                    effects.send(NookiEffect.Message("Added to money and things.", NookiIntent.DeleteLooseEnd(saved)))
                }
            }
            is Capture.Remind -> {
                val parsed = capture.parsed
                val at = request.triggerOverride ?: parsed.triggerAtMillis
                val saved = saveReminder(parsed, request, at, nowMillis)
                if (!quiet) {
                    effects.send(NookiEffect.Confirm)
                    val message = if (at == null) {
                        "Saved. It's on today's list."
                    } else {
                        "Saved for ${TimeWords.moment(at, nowMillis).replaceFirstChar { it.lowercase() }}."
                    }
                    effects.send(NookiEffect.Message(message, NookiIntent.Delete(saved)))
                }
            }
        }
    }

    private suspend fun saveReminder(
        parsed: ParsedReminder,
        request: CaptureRequest,
        at: Long?,
        nowMillis: Long
    ): ReminderEntity {
        val priority = when (parsed.priorityHint) {
            PriorityHint.URGENT -> ReminderPriority.URGENT
            PriorityHint.HIGH -> ReminderPriority.HIGH
            null -> ReminderPriority.MEDIUM
        }
        return graph.reminders.createAndScheduleReminder(
            title = parsed.title,
            rawInput = request.rawInput,
            triggerAtMillis = at ?: nowMillis,
            priority = priority,
            category = ReminderCategory.DESK,
            recurrenceMinutes = parsed.recurrenceMinutes,
            kind = parsed.kind,
            leadMinutes = parsed.leadMinutes,
            recurrenceRule = parsed.recurrenceRule,
            undated = at == null,
            keepAsking = parsed.keepAsking || priority == ReminderPriority.URGENT,
            source = request.source
        ).first
    }

    private fun trackNoticed(item: NoticedEntity, minutes: Int) = launchChange {
        val now = System.currentTimeMillis()
        val title = when (item.type) {
            NoticedType.MISSED_CALL -> "Call back ${item.person ?: ""}".trim()
            NoticedType.UNREAD_MESSAGE -> "Reply to ${item.person ?: ""}".trim()
            else -> item.title
        }
        val kind = when (item.type) {
            NoticedType.BILL -> ReminderKind.BILL
            else -> ReminderKind.TASK
        }
        val due = item.dueAtMillis?.takeIf { it > now }
        graph.reminders.createAndScheduleReminder(
            title = title,
            notes = item.detail,
            rawInput = title,
            triggerAtMillis = due ?: (now + minutes * 60_000L),
            kind = kind,
            source = ReminderSource.NOTICED
        )
        graph.attention.setNoticedStatus(item, NoticedStatus.TRACKED, now)
        effects.send(NookiEffect.Confirm)
        effects.send(NookiEffect.Message("Nooki will remind you."))
    }

    class Factory(
        private val graph: AppContainer,
        private val readReadiness: () -> Readiness,
        private val refreshWidget: suspend () -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NookiViewModel(graph, readReadiness, refreshWidget) as T
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val WEEK_MILLIS = 7L * 24 * 60 * 60_000L
        const val XP_PER_LEVEL = 50
        const val PILE_UP = 5
    }
}
