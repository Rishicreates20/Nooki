package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.repository.ReminderRepository
import com.example.domain.model.HappinessTier
import com.example.domain.model.NookiPetMood
import com.example.domain.model.NookiStateMachine
import com.example.domain.notice.NookiNoticeFilter
import com.example.domain.notice.NoticeFilterSettings
import com.example.domain.notice.NoticeInspectionResult
import com.example.domain.notice.NoticeSourceKind
import com.example.domain.parser.NookiBreathParser
import com.example.domain.parser.NookiCaptureKind
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskSideEffect
import com.example.presentation.mvi.DeskUiState
import com.example.scheduler.ExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
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
 * Expands the reactive state machine with [com.example.domain.model.MoodState] so Nooki's
 * happiness score, tier (`RADIANT`, `CONTENT`, `CALM`, `SLEEPY_PEACEFUL`, `FLUSTERED`),
 * and 3D posture respond dynamically to reminder completion frequency in Room.
 */
class DeskPetViewModel(
    private val repository: ReminderRepository,
    private val alarmScheduler: ExactAlarmScheduler
) : ViewModel() {

    private data class EphemeralInteractionState(
        val nowMillis: Long = System.currentTimeMillis(),
        val breathInputText: String = "",
        val celebratingUntilMillis: Long = 0L,
        val celebrationSpeech: String? = null,
        val lastEarnedXp: Int = 0,
        val petInteractionCombo: Int = 0,
        val bonusPetXp: Int = 0,
        val isAutoRotate3DEnabled: Boolean = true,
        val noticeSettings: NoticeFilterSettings = NoticeFilterSettings(),
        val noticeSuggestions: List<NoticeInspectionResult.SafeSuggestion> = listOf(
            NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.UNREAD_MESSAGE,
                sanitizedTitle = "Unread message from Aman",
                sanitizedSubtitle = "Message content never stored • Tap to add a knot",
                timestampMillis = System.currentTimeMillis() - 2 * 3_600_000L
            ),
            NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.PARCEL_DELIVERY,
                sanitizedTitle = "Parcel arriving today",
                sanitizedSubtitle = "Delivery noticed • Tap to keep on today's thread",
                timestampMillis = System.currentTimeMillis() - 45 * 60_000L
            )
        ),
        val lastDroppedNoticeAudit: String? = null,
        val isComposerSheetOpen: Boolean = false,
        val isQueueSheetOpen: Boolean = false
    )

    private val ephemeralState = MutableStateFlow(EphemeralInteractionState())

    private val _sideEffects = Channel<DeskSideEffect>(Channel.BUFFERED)
    val sideEffects: Flow<DeskSideEffect> = _sideEffects.receiveAsFlow()

    private var lastLoggedMood: NookiPetMood? = null
    private var lastLoggedHappinessTier: HappinessTier? = null

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

        // 1. "You missed" (Vermilion / Sindoor): MISSED status or past-due unacknowledged reminders
        val youMissed = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                !isHeldOrMoneyNote(item) &&
                (item.status == ReminderStatus.MISSED || item.triggerAtMillis < now)
        }

        // 2. "What did I forget? & Nooki holds for you" (Marigold)
        val youForgotAndHeld = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                item !in youMissed &&
                (isHeldOrMoneyNote(item) || item.snoozeCount >= 2 || item.notes.contains("WARNS:"))
        }

        // Running money totals
        var owedToYouTotal = 0
        var youOweTotal = 0
        for (item in allReminders) {
            if (item.status != ReminderStatus.COMPLETED && item.status != ReminderStatus.CANCELLED) {
                val amount = extractInrAmount(item.notes)
                if (amount != null) {
                    if (item.notes.contains("KIND:MONEY_OWED_TO_ME")) {
                        owedToYouTotal += amount
                    } else if (item.notes.contains("KIND:MONEY_I_OWE")) {
                        youOweTotal += amount
                    }
                }
            }
        }

        // 3. "Today & coming up" (Neel Indigo)
        val todayAndComingUp = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                item !in youMissed &&
                item !in youForgotAndHeld
        }

        // Done / Untied knots (Leaf Green)
        val doneKnots = allReminders.filter { it.status == ReminderStatus.COMPLETED }

        // Evaluate both the discrete NookiPetState and the completion-frequency MoodState
        val machineOutput = NookiStateMachine.reduceWithMood(
            allReminders = allReminders,
            activeReminders = todayAndComingUp,
            completedCount = completedCount,
            nowMillis = now,
            celebratingUntilMillis = local.celebratingUntilMillis,
            celebrationSpeech = local.celebrationSpeech,
            lastEarnedXp = local.lastEarnedXp,
            bonusPetTouches = local.petInteractionCombo
        )
        val resolvedPetState = machineOutput.petState
        val resolvedMoodState = machineOutput.moodState

        // Live parse of current one-breath input
        val liveParsed = NookiBreathParser.parse(
            rawInput = local.breathInputText,
            nowMillis = now,
            existingItems = allReminders
        )

        val combinedXp = dbXp + local.bonusPetXp
        val level = (combinedXp / 50) + 1
        val xpInLevel = combinedXp % 50

        DeskUiState(
            petState = resolvedPetState,
            petMood = resolvedPetState.mood,
            moodState = resolvedMoodState,
            speechBubbleText = resolvedPetState.speechText,
            stateTransitionSummary = "${resolvedPetState.mood.stateCode} • ${resolvedMoodState.happinessTier.label} (${resolvedMoodState.happinessScore}%)",
            level = level,
            totalXp = combinedXp,
            xpInCurrentLevel = xpInLevel,
            xpForNextLevel = 50,
            petInteractionCombo = local.petInteractionCombo,
            isAutoRotate3DEnabled = local.isAutoRotate3DEnabled,
            breathInputText = local.breathInputText,
            liveParsedCapture = liveParsed,
            allReminders = allReminders,
            activeReminders = activeReminders,
            ringingReminders = ringing,
            overdueReminders = youMissed,
            youMissedKnots = youMissed,
            youForgotAndHeldKnots = youForgotAndHeld,
            netMoneyOwedToYouInr = owedToYouTotal,
            netMoneyYouOweInr = youOweTotal,
            todayAndComingUpKnots = todayAndComingUp,
            doneKnots = doneKnots,
            noticeSettings = local.noticeSettings,
            noticeSuggestions = local.noticeSuggestions,
            lastDroppedNoticeAudit = local.lastDroppedNoticeAudit,
            completedCount = completedCount,
            nowMillis = now,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
            telemetryLogs = logs,
            isComposerSheetOpen = local.isComposerSheetOpen,
            isQueueSheetOpen = local.isQueueSheetOpen
        )
    }
        .onEach { state ->
            val moodChanged = lastLoggedMood != state.petMood
            val tierChanged = lastLoggedHappinessTier != state.moodState.happinessTier
            if (moodChanged || tierChanged) {
                val prevMood = lastLoggedMood?.stateCode ?: "INIT"
                lastLoggedMood = state.petMood
                lastLoggedHappinessTier = state.moodState.happinessTier
                NookiSchedulerTelemetry.record(
                    source = "MoodStateMachine",
                    message = "State: $prevMood ➔ ${state.petMood.stateCode} | Happiness: ${state.moodState.happinessScore}% (${state.moodState.happinessTier.label}, ${state.moodState.completedLast24Hours}/24h)"
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
                    "Tap any knot on my thread to untie it when you're done!",
                    "Tell me anything in English, Hinglish, or हिंदी — I'll tie a knot for you!",
                    "I'm watching what you missed, what you forgot, and what's on today!",
                    "Boop! Every knot you untie boosts my happiness and your XP!"
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

            is DeskIntent.UpdateBreathInput -> {
                ephemeralState.update { it.copy(breathInputText = intent.text) }
            }

            DeskIntent.SaveBreathInput -> {
                val currentInput = ephemeralState.value.breathInputText.trim()
                if (currentInput.isEmpty()) return
                viewModelScope.launch {
                    val now = System.currentTimeMillis()
                    val parsed = NookiBreathParser.parse(
                        rawInput = currentInput,
                        nowMillis = now,
                        existingItems = uiState.value.allReminders
                    )
                    if (parsed.kind == NookiCaptureKind.QUESTION) {
                        val answer = parsed.instantAnswerFromMemory ?: "I couldn't find that in your saved knots yet."
                        ephemeralState.update {
                            it.copy(
                                celebratingUntilMillis = now + 4_000L,
                                celebrationSpeech = answer
                            )
                        }
                        _sideEffects.send(DeskSideEffect.ShowMessage(answer))
                        return@launch
                    }

                    val (entity, _) = repository.saveParsedBreathCapture(parsed)
                    ephemeralState.update {
                        it.copy(
                            breathInputText = "",
                            celebratingUntilMillis = now + 3_200L,
                            celebrationSpeech = "Tied a knot for '${entity.title}' (${parsed.timeExplanation})!"
                        )
                    }
                    _sideEffects.send(DeskSideEffect.TriggerHapticPulse)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Nooki tied a knot: ${entity.title}")
                    )
                }
            }

            is DeskIntent.AcceptNoticeSuggestion -> {
                viewModelScope.launch {
                    val suggestion = intent.suggestion
                    val parsed = NookiBreathParser.parse(
                        rawInput = suggestion.sanitizedTitle,
                        nowMillis = System.currentTimeMillis()
                    )
                    repository.saveParsedBreathCapture(parsed)
                    ephemeralState.update { state ->
                        state.copy(
                            noticeSuggestions = state.noticeSuggestions.filterNot { it == suggestion },
                            celebratingUntilMillis = System.currentTimeMillis() + 2_800L,
                            celebrationSpeech = "Added '${suggestion.sanitizedTitle}' to your thread!"
                        )
                    }
                    _sideEffects.send(DeskSideEffect.TriggerHapticPulse)
                }
            }

            is DeskIntent.DismissNoticeSuggestion -> {
                ephemeralState.update { state ->
                    state.copy(
                        noticeSuggestions = state.noticeSuggestions.filterNot { it == intent.suggestion }
                    )
                }
            }

            is DeskIntent.ToggleNoticeKind -> {
                ephemeralState.update { state ->
                    val currentSet = state.noticeSettings.enabledKinds.toMutableSet()
                    if (intent.kind in currentSet) {
                        currentSet.remove(intent.kind)
                    } else {
                        currentSet.add(intent.kind)
                    }
                    state.copy(
                        noticeSettings = state.noticeSettings.copy(enabledKinds = currentSet)
                    )
                }
            }

            is DeskIntent.SimulateIncomingNotification -> {
                val result = NookiNoticeFilter.inspectNotification(
                    packageName = intent.packageName,
                    senderOrTitle = intent.senderOrTitle,
                    rawText = intent.rawBody,
                    settings = ephemeralState.value.noticeSettings
                )
                when (result) {
                    is NoticeInspectionResult.DroppedSensitive -> {
                        NookiSchedulerTelemetry.record(
                            source = "NookiNoticeFilter",
                            message = "PRIVACY GUARD: ${result.reason}",
                            isWarning = true
                        )
                        ephemeralState.update {
                            it.copy(
                                lastDroppedNoticeAudit = "✅ Blocked OTP/Bank notification before DB: \"${result.reason}\""
                            )
                        }
                        viewModelScope.launch {
                            _sideEffects.send(DeskSideEffect.ShowMessage("Blocked OTP/sensitive notification before saving!"))
                        }
                    }

                    is NoticeInspectionResult.DroppedDisabledOrIrrelevant -> {
                        ephemeralState.update {
                            it.copy(lastDroppedNoticeAudit = "Ignored notification: ${result.reason}")
                        }
                    }

                    is NoticeInspectionResult.SafeSuggestion -> {
                        NookiSchedulerTelemetry.record(
                            source = "NookiNoticeFilter",
                            message = "Noticed safe suggestion: '${result.sanitizedTitle}' (message body never stored)"
                        )
                        ephemeralState.update { state ->
                            state.copy(
                                noticeSuggestions = listOf(result) + state.noticeSuggestions,
                                lastDroppedNoticeAudit = "Noticed '${result.sanitizedTitle}' (raw content stripped)"
                            )
                        }
                    }
                }
            }

            DeskIntent.EveningWrapMoveAllToTomorrow -> {
                viewModelScope.launch {
                    val count = repository.moveUnfinishedTodayToTomorrowMorning()
                    val now = System.currentTimeMillis()
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = now + 3_500L,
                            celebrationSpeech = "Evening wrap complete! Moved $count item(s) to tomorrow morning. Don't forget to charge your phone!"
                        )
                    }
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Moved $count unfinished knot(s) to tomorrow 9:00 AM.")
                    )
                }
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
                    val (entity, _) = repository.createAndScheduleReminder(
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
                            celebrationSpeech = "Tied a knot for '${entity.title}'!",
                            lastEarnedXp = 0
                        )
                    }
                    _sideEffects.send(DeskSideEffect.ShowMessage("Tied knot: '${entity.title}'"))
                }
            }

            is DeskIntent.CompleteReminder -> {
                viewModelScope.launch {
                    repository.completeReminder(intent.reminder.id)
                    val now = System.currentTimeMillis()
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = now + 3_500L,
                            celebrationSpeech = "Knot untied! '${intent.reminder.title}' is done! +${intent.reminder.petMoodImpact} XP & +8% Happiness!",
                            lastEarnedXp = intent.reminder.petMoodImpact
                        )
                    }
                    _sideEffects.send(DeskSideEffect.TriggerHapticPulse)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Knot untied! +${intent.reminder.petMoodImpact} XP")
                    )
                }
            }

            is DeskIntent.SnoozeReminder -> {
                viewModelScope.launch {
                    repository.snoozeReminder(intent.reminder.id, intent.minutes)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Pushed '${intent.reminder.title}' by ${intent.minutes}m.")
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
                        title = "Evening दवाई (5s Test)",
                        notes = "KIND:MEDICINE | Critical exact alarm test",
                        rawInput = "कल शाम 6 बजे दवाई",
                        triggerAtMillis = triggerAt,
                        priority = ReminderPriority.URGENT,
                        category = ReminderCategory.HYDRATION
                    )
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = System.currentTimeMillis() + 2_500L,
                            celebrationSpeech = "Watch my 3D bell ring in 5 seconds!",
                            lastEarnedXp = 0
                        )
                    }
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Scheduled '${entity.title}' for +5 seconds!")
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
                            "Reboot check: ${report.rescheduledCount} restored, ${report.markedMissedCount} moved to 'You missed'."
                        )
                    )
                }
            }

            DeskIntent.ClearCompleted -> {
                viewModelScope.launch {
                    repository.clearCompletedReminders()
                    _sideEffects.send(DeskSideEffect.ShowMessage("Cleared untied knots."))
                }
            }
        }
    }

    private fun isHeldOrMoneyNote(item: ReminderEntity): Boolean {
        return item.notes.contains("KIND:HELD_MEMORY") ||
            item.notes.contains("KIND:MONEY_OWED_TO_ME") ||
            item.notes.contains("KIND:MONEY_I_OWE") ||
            item.notes.contains("KIND:ITEM_LENT")
    }

    private fun extractInrAmount(notes: String): Int? {
        val match = Regex("""INR:(\d+)""").find(notes) ?: return null
        return match.groupValues[1].toIntOrNull()
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
