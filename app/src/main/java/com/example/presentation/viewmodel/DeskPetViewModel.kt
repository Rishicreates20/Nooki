package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.PetJournalEntryEntity
import com.example.data.local.entity.PetMilestoneType
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.ReminderRepository
import com.example.domain.model.ConsistencyStreakCalculator
import com.example.domain.model.HappinessTier
import com.example.domain.model.LocalWeatherCalculator
import com.example.domain.model.LocalWeatherState
import com.example.domain.model.NookiAppTheme
import com.example.domain.model.NookiPetMood
import com.example.domain.model.NookiStateMachine
import com.example.domain.model.PetGestureType
import com.example.domain.model.SensoryCue
import com.example.domain.model.WeatherCondition
import com.example.domain.notice.NookiNoticeFilter
import com.example.domain.notice.NoticeFilterSettings
import com.example.domain.notice.NoticeInspectionResult
import com.example.domain.notice.NoticeSourceKind
import com.example.domain.parser.NookiBreathParser
import com.example.domain.parser.NookiCaptureKind
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskSideEffect
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.mvi.TaskBoardFilter
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
 * Manages Nooki's [com.example.domain.model.MoodState], [com.example.domain.model.ConsistencyStreakState],
 * To-Do / Done task sections, [UserProfileEntity], [NookiAppTheme], and [PetJournalEntryEntity] milestones.
 */
class DeskPetViewModel(
    private val repository: ReminderRepository,
    private val alarmScheduler: ExactAlarmScheduler
) : ViewModel() {

    private data class EphemeralInteractionState(
        val nowMillis: Long = System.currentTimeMillis(),
        val breathInputText: String = "",
        val selectedTaskFilter: TaskBoardFilter = TaskBoardFilter.TODO,
        val selectedJournalFilter: PetMilestoneType? = null,
        val weatherState: LocalWeatherState = LocalWeatherCalculator.computeFromLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            isFromLiveLocationApi = false,
            hasLocationPermission = false
        ),
        val lastLiveLocationWeather: LocalWeatherState? = null,
        val celebratingUntilMillis: Long = 0L,
        val celebrationSpeech: String? = null,
        val lastEarnedXp: Int = 0,
        val petInteractionCombo: Int = 0,
        val bonusPetXp: Int = 0,
        val bonusHappinessFromPlay: Int = 0,
        val activePlayGesture: PetGestureType? = null,
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
    private var lastSyncedStreakDays: Int = -1
    private var lastSyncedLongestStreak: Int = -1
    private var hasInitializedPersonalitySnapshot = false
    private val recordedStreakMilestones = mutableSetOf<Int>()

    val uiState: StateFlow<DeskUiState> = combine(
        repository.allReminders.distinctUntilChanged(),
        repository.activeReminders.distinctUntilChanged(),
        repository.completedCount.distinctUntilChanged(),
        repository.totalEarnedXp.distinctUntilChanged(),
        repository.userProfile.distinctUntilChanged(),
        repository.journalEntries.distinctUntilChanged(),
        NookiSchedulerTelemetry.logs,
        ephemeralState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allReminders = values[0] as List<ReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val activeReminders = values[1] as List<ReminderEntity>
        val completedCount = values[2] as Int
        val dbXp = values[3] as Int
        val profile = (values[4] as? UserProfileEntity) ?: UserProfileEntity()
        @Suppress("UNCHECKED_CAST")
        val journalEntries = values[5] as List<PetJournalEntryEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = values[6] as List<com.example.scheduler.SchedulerDiagnosticEntry>
        val local = values[7] as EphemeralInteractionState

        val now = local.nowMillis
        val ringing = allReminders.filter { it.status == ReminderStatus.RINGING }

        val youMissed = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                !isHeldOrMoneyNote(item) &&
                (item.status == ReminderStatus.MISSED || item.triggerAtMillis < now)
        }

        val youForgotAndHeld = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                item !in youMissed &&
                (isHeldOrMoneyNote(item) || item.snoozeCount >= 2 || item.notes.contains("WARNS:"))
        }

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

        val todayAndComingUp = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                item !in youMissed &&
                item !in youForgotAndHeld
        }

        val doneKnots = allReminders
            .filter { it.status == ReminderStatus.COMPLETED }
            .sortedByDescending { it.completedAtMillis ?: it.createdAtMillis }

        val todoTasks = allReminders
            .filter { it.status != ReminderStatus.COMPLETED && it.status != ReminderStatus.CANCELLED }
            .sortedWith(
                compareBy<ReminderEntity> {
                    when (it.status) {
                        ReminderStatus.RINGING -> 0
                        ReminderStatus.MISSED -> 1
                        else -> 2
                    }
                }.thenBy { it.triggerAtMillis }
            )

        val consistencyStreak = ConsistencyStreakCalculator.compute(
            allReminders = allReminders,
            nowMillis = now,
            persistedLongestStreak = profile.longestStreakDays,
            dailyGoalCount = profile.dailyReminderGoal
        )

        val machineOutput = NookiStateMachine.reduceWithMood(
            allReminders = allReminders,
            activeReminders = todayAndComingUp,
            completedCount = completedCount,
            nowMillis = now,
            celebratingUntilMillis = local.celebratingUntilMillis,
            celebrationSpeech = local.celebrationSpeech,
            lastEarnedXp = local.lastEarnedXp,
            bonusPetTouches = local.petInteractionCombo,
            bonusHappinessFromPlay = (local.bonusHappinessFromPlay + local.weatherState.condition.moodHappinessBonus).coerceAtMost(28),
            activePlayGesture = local.activePlayGesture
        )
        val resolvedPetState = machineOutput.petState
        val resolvedMoodState = machineOutput.moodState.copy(
            streakDays = consistencyStreak.currentStreakDays
        )

        val liveParsed = NookiBreathParser.parse(
            rawInput = local.breathInputText,
            nowMillis = now,
            existingItems = allReminders
        )

        val combinedXp = dbXp + local.bonusPetXp
        val level = (combinedXp / 50) + 1
        val xpInLevel = combinedXp % 50

        val syncedProfile = profile.copy(
            currentStreakDays = consistencyStreak.currentStreakDays,
            longestStreakDays = consistencyStreak.longestStreakDays
        )
        val resolvedTheme = NookiAppTheme.fromId(syncedProfile.selectedThemeId)

        DeskUiState(
            petState = resolvedPetState,
            petMood = resolvedPetState.mood,
            moodState = resolvedMoodState,
            consistencyStreak = consistencyStreak,
            userProfile = syncedProfile,
            selectedTheme = resolvedTheme,
            weatherState = local.weatherState,
            journalEntries = journalEntries,
            selectedJournalFilter = local.selectedJournalFilter,
            isSoundEnabled = syncedProfile.soundEnabled,
            isHapticsEnabled = syncedProfile.hapticsEnabled,
            speechBubbleText = resolvedPetState.speechText,
            stateTransitionSummary = "${resolvedPetState.mood.stateCode} • ${resolvedMoodState.happinessTier.label} (${resolvedMoodState.happinessScore}%) • 🔥 ${consistencyStreak.currentStreakDays}d Streak",
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
            todoTasks = todoTasks,
            doneTasks = doneKnots,
            selectedTaskFilter = local.selectedTaskFilter,
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
            val prevTier = lastLoggedHappinessTier
            val tierChanged = prevTier != state.moodState.happinessTier
            if (moodChanged || tierChanged) {
                val prevMood = lastLoggedMood?.stateCode ?: "INIT"
                lastLoggedMood = state.petMood
                lastLoggedHappinessTier = state.moodState.happinessTier
                NookiSchedulerTelemetry.record(
                    source = "MoodStateMachine",
                    message = "State: $prevMood ➔ ${state.petMood.stateCode} | Happiness: ${state.moodState.happinessScore}% | Streak: ${state.consistencyStreak.currentStreakDays}d"
                )

                // Record a Pet Personality Shift in the Milestone Journal when Nooki transitions tiers after initialization
                if (hasInitializedPersonalitySnapshot && tierChanged && prevTier != null) {
                    val newTier = state.moodState.happinessTier
                    viewModelScope.launch {
                        repository.recordJournalMilestone(
                            milestoneType = PetMilestoneType.PERSONALITY_SHIFT,
                            title = "Pet Personality Shift: ${newTier.label}",
                            narrative = "Nooki shifted from ${prevTier.label} to ${newTier.label} (${state.moodState.happinessScore}% happiness) as your thread updated.",
                            badgeEmoji = newTier.badgeEmoji,
                            happinessSnapshot = state.moodState.happinessScore,
                            streakSnapshot = state.consistencyStreak.currentStreakDays
                        )
                    }
                }
                hasInitializedPersonalitySnapshot = true
            }
            val currentStreak = state.consistencyStreak.currentStreakDays
            val longestStreak = state.consistencyStreak.longestStreakDays
            if (currentStreak != lastSyncedStreakDays || longestStreak != lastSyncedLongestStreak) {
                val prevStreak = lastSyncedStreakDays
                lastSyncedStreakDays = currentStreak
                lastSyncedLongestStreak = longestStreak
                val lastDayEpoch = state.doneTasks.firstOrNull()?.completedDayEpoch ?: 0L
                viewModelScope.launch {
                    repository.syncConsistencyStreakInProfile(
                        currentStreakDays = currentStreak,
                        longestStreakDays = longestStreak,
                        lastCompletedDayEpoch = lastDayEpoch
                    )
                    if (prevStreak >= 0 && currentStreak >= 3 && currentStreak > prevStreak && currentStreak !in recordedStreakMilestones) {
                        recordedStreakMilestones.add(currentStreak)
                        repository.recordJournalMilestone(
                            milestoneType = PetMilestoneType.FIRST_HIGH_STREAK,
                            title = "High-Streak Achievement: ${currentStreak}-Day Thread!",
                            narrative = "You maintained a ${currentStreak}-day consistency streak! Nooki's 3D aura and orbital particles are glowing at full strength.",
                            badgeEmoji = "🔥",
                            happinessSnapshot = state.moodState.happinessScore,
                            streakSnapshot = currentStreak
                        )
                    }
                }
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
                handlePetGesture(PetGestureType.HEAD_PAT)
            }

            is DeskIntent.TriggerPetGesture -> {
                handlePetGesture(intent.gesture)
            }

            DeskIntent.ToggleAutoRotate3D -> {
                ephemeralState.update { it.copy(isAutoRotate3DEnabled = !it.isAutoRotate3DEnabled) }
            }

            is DeskIntent.SelectAppTheme -> {
                viewModelScope.launch {
                    val currentTheme = uiState.value.selectedTheme
                    if (currentTheme == intent.theme) return@launch
                    repository.selectAppTheme(intent.theme)
                    val currentMood = uiState.value.moodState
                    val currentStreak = uiState.value.consistencyStreak.currentStreakDays
                    repository.recordJournalMilestone(
                        milestoneType = PetMilestoneType.THEME_ATMOSPHERE,
                        title = "Atmosphere Shift: ${intent.theme.displayName}",
                        narrative = "Switched desk sanctuary to ${intent.theme.displayName} (${intent.theme.subtitle}). 3D particle colors attuned to the new palette!",
                        badgeEmoji = intent.theme.badgeEmoji,
                        happinessSnapshot = currentMood.happinessScore,
                        streakSnapshot = currentStreak
                    )
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = System.currentTimeMillis() + 3_000L,
                            celebrationSpeech = "${intent.theme.badgeEmoji} Switched to ${intent.theme.displayName}! Look at my matching 3D particles!"
                        )
                    }
                    emitSensoryCue(SensoryCue.KNOT_TIED)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Applied '${intent.theme.displayName}' theme & particle colors")
                    )
                }
            }

            is DeskIntent.ApplyLocationWeatherUpdate -> {
                val incoming = intent.weatherState
                val now = System.currentTimeMillis()
                ephemeralState.update { state ->
                    state.copy(
                        weatherState = incoming,
                        lastLiveLocationWeather = incoming,
                        celebratingUntilMillis = now + 3_200L,
                        celebrationSpeech = "${incoming.condition.badgeEmoji} ${incoming.condition.petReactionLine} (${incoming.localityName}, ${incoming.temperatureCelsius}°C)"
                    )
                }
                viewModelScope.launch {
                    if (incoming.isFromLiveLocationApi) {
                        _sideEffects.send(
                            DeskSideEffect.ShowMessage(
                                "Synced local weather: ${incoming.condition.displayName} (${incoming.temperatureCelsius}°C) in ${incoming.localityName}"
                            )
                        )
                    }
                }
            }

            is DeskIntent.SelectWeatherOverride -> {
                val now = System.currentTimeMillis()
                val overrideCondition = intent.condition
                if (overrideCondition == null) {
                    val restored = ephemeralState.value.lastLiveLocationWeather
                        ?: LocalWeatherCalculator.computeFromLocation(
                            latitude = ephemeralState.value.weatherState.latitude,
                            longitude = ephemeralState.value.weatherState.longitude,
                            nowMillis = now,
                            isFromLiveLocationApi = ephemeralState.value.weatherState.isFromLiveLocationApi,
                            hasLocationPermission = ephemeralState.value.weatherState.hasLocationPermission
                        )
                    ephemeralState.update {
                        it.copy(
                            weatherState = restored.copy(isManualOverride = false),
                            celebratingUntilMillis = now + 2_600L,
                            celebrationSpeech = "${restored.condition.badgeEmoji} Returned to Auto GPS weather (${restored.condition.displayName} • ${restored.temperatureCelsius}°C)!"
                        )
                    }
                } else {
                    val current = ephemeralState.value.weatherState
                    val adjustedTemp = when (overrideCondition) {
                        WeatherCondition.MISTY_MOUNTAIN_SNOW -> 4
                        WeatherCondition.MONSOON_RAIN -> 24
                        WeatherCondition.STARRY_NIGHT_DEW -> 19
                        WeatherCondition.GOLDEN_TWILIGHT -> 25
                        WeatherCondition.SUNLIT_BREEZE -> 28
                    }
                    val updatedWeather = current.copy(
                        condition = overrideCondition,
                        temperatureCelsius = adjustedTemp,
                        isManualOverride = true,
                        lastUpdatedMillis = now
                    )
                    ephemeralState.update {
                        it.copy(
                            weatherState = updatedWeather,
                            celebratingUntilMillis = now + 3_000L,
                            celebrationSpeech = "${overrideCondition.badgeEmoji} ${overrideCondition.petReactionLine}"
                        )
                    }
                    viewModelScope.launch {
                        emitSensoryCue(SensoryCue.KNOT_TIED)
                        _sideEffects.send(
                            DeskSideEffect.ShowMessage(
                                "3D Environment set to ${overrideCondition.displayName} (+${overrideCondition.moodHappinessBonus}% Mood)"
                            )
                        )
                    }
                }
            }

            is DeskIntent.SelectJournalFilter -> {
                ephemeralState.update { it.copy(selectedJournalFilter = intent.milestoneType) }
            }

            is DeskIntent.AddCustomJournalNote -> {
                val cleanTitle = intent.title.trim()
                val cleanNarrative = intent.narrative.trim()
                if (cleanTitle.isEmpty()) return
                viewModelScope.launch {
                    val state = uiState.value
                    repository.recordJournalMilestone(
                        milestoneType = intent.milestoneType,
                        title = cleanTitle,
                        narrative = cleanNarrative.ifEmpty {
                            "Recorded with Nooki at ${state.moodState.happinessScore}% happiness and a ${state.consistencyStreak.currentStreakDays}-day streak."
                        },
                        badgeEmoji = intent.milestoneType.defaultBadgeEmoji,
                        happinessSnapshot = state.moodState.happinessScore,
                        streakSnapshot = state.consistencyStreak.currentStreakDays
                    )
                    emitSensoryCue(SensoryCue.KNOT_TIED)
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage("Saved milestone to Nooki's Journal!")
                    )
                }
            }

            is DeskIntent.DeleteJournalEntry -> {
                viewModelScope.launch {
                    repository.deleteJournalEntry(intent.id)
                }
            }

            DeskIntent.ToggleSoundEnabled -> {
                viewModelScope.launch {
                    val currentProfile = uiState.value.userProfile
                    val nextSound = !currentProfile.soundEnabled
                    repository.updateSensoryPreferences(
                        soundEnabled = nextSound,
                        hapticsEnabled = currentProfile.hapticsEnabled
                    )
                    if (nextSound) {
                        _sideEffects.send(
                            DeskSideEffect.PlaySensoryCue(
                                cue = SensoryCue.KNOT_TIED,
                                soundEnabled = true,
                                hapticsEnabled = currentProfile.hapticsEnabled
                            )
                        )
                    }
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage(
                            if (nextSound) "Cozy sound effects enabled" else "Sound effects muted"
                        )
                    )
                }
            }

            DeskIntent.ToggleHapticsEnabled -> {
                viewModelScope.launch {
                    val currentProfile = uiState.value.userProfile
                    val nextHaptics = !currentProfile.hapticsEnabled
                    repository.updateSensoryPreferences(
                        soundEnabled = currentProfile.soundEnabled,
                        hapticsEnabled = nextHaptics
                    )
                    if (nextHaptics) {
                        _sideEffects.send(
                            DeskSideEffect.PlaySensoryCue(
                                cue = SensoryCue.PET_HEAD_PAT,
                                soundEnabled = false,
                                hapticsEnabled = true
                            )
                        )
                    }
                    _sideEffects.send(
                        DeskSideEffect.ShowMessage(
                            if (nextHaptics) "Haptic waveform feedback enabled" else "Haptic feedback muted"
                        )
                    )
                }
            }

            is DeskIntent.PreviewSensoryCue -> {
                viewModelScope.launch {
                    val current = uiState.value
                    _sideEffects.send(
                        DeskSideEffect.PlaySensoryCue(
                            cue = intent.cue,
                            soundEnabled = current.isSoundEnabled,
                            hapticsEnabled = current.isHapticsEnabled
                        )
                    )
                }
            }

            is DeskIntent.UpdateBreathInput -> {
                ephemeralState.update { it.copy(breathInputText = intent.text) }
            }

            is DeskIntent.SelectTaskBoardFilter -> {
                ephemeralState.update { it.copy(selectedTaskFilter = intent.filter) }
            }

            is DeskIntent.QuickAddTodoTask -> {
                val cleanTitle = intent.title.trim()
                if (cleanTitle.isEmpty()) return
                viewModelScope.launch {
                    val now = System.currentTimeMillis()
                    val parsed = NookiBreathParser.parse(
                        rawInput = cleanTitle,
                        nowMillis = now,
                        existingItems = uiState.value.allReminders
                    )
                    val (entity, _) = if (cleanTitle.contains(" ") && parsed.timeExplanation != "Today (in 1 hour)") {
                        repository.saveParsedBreathCapture(parsed)
                    } else {
                        repository.createAndScheduleReminder(
                            title = cleanTitle,
                            notes = "KIND:TODO_TASK | Added from To-Do Board",
                            rawInput = cleanTitle,
                            triggerAtMillis = now + 60 * 60_000L,
                            priority = intent.priority,
                            category = intent.category
                        )
                    }
                    ephemeralState.update {
                        it.copy(
                            selectedTaskFilter = TaskBoardFilter.TODO,
                            celebratingUntilMillis = now + 2_800L,
                            celebrationSpeech = "Added '${entity.title}' to your To-Do tasks!"
                        )
                    }
                    emitSensoryCue(SensoryCue.KNOT_TIED)
                    _sideEffects.send(DeskSideEffect.ShowMessage("Added To-Do task: ${entity.title}"))
                }
            }

            is DeskIntent.ReopenDoneTask -> {
                viewModelScope.launch {
                    val reopened = repository.reopenReminderToTodo(intent.reminder.id)
                    if (reopened != null) {
                        ephemeralState.update {
                            it.copy(
                                celebratingUntilMillis = System.currentTimeMillis() + 2_400L,
                                celebrationSpeech = "Moved '${reopened.title}' back to your To-Do tasks!"
                            )
                        }
                        _sideEffects.send(DeskSideEffect.ShowMessage("Moved '${reopened.title}' back to To-Do"))
                    }
                }
            }

            is DeskIntent.SaveUserProfile -> {
                viewModelScope.launch {
                    val updated = repository.updateUserProfile(
                        displayName = intent.displayName,
                        companionTitle = intent.companionTitle,
                        avatarBadge = intent.avatarBadge,
                        bioMotto = intent.bioMotto,
                        dailyReminderGoal = intent.dailyReminderGoal
                    )
                    ephemeralState.update {
                        it.copy(
                            celebratingUntilMillis = System.currentTimeMillis() + 3_000L,
                            celebrationSpeech = "Namaste ${updated.displayName}! Daily goal set to ${updated.dailyReminderGoal} task(s)/day!"
                        )
                    }
                    emitSensoryCue(SensoryCue.KNOT_TIED)
                    _sideEffects.send(DeskSideEffect.ShowMessage("Saved profile for ${updated.displayName}"))
                }
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
                    emitSensoryCue(SensoryCue.KNOT_TIED)
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
                    emitSensoryCue(SensoryCue.KNOT_TIED)
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
                            celebratingUntilMillis = now + 3_800L,
                            celebrationSpeech = "Knot untied! '${intent.reminder.title}' is done! +${intent.reminder.petMoodImpact} XP & +8% Happiness!",
                            lastEarnedXp = intent.reminder.petMoodImpact,
                            activePlayGesture = PetGestureType.PLAY_BOOP
                        )
                    }
                    val currentState = uiState.value
                    repository.recordJournalMilestone(
                        milestoneType = PetMilestoneType.TASK_CHAMPION,
                        title = "Knot Untied: ${intent.reminder.title}",
                        narrative = "Completed '${intent.reminder.title}' (+${intent.reminder.petMoodImpact} XP). Nooki celebrated with a 3D hop!",
                        badgeEmoji = "🪡",
                        happinessSnapshot = (currentState.moodState.happinessScore + 8).coerceAtMost(100),
                        streakSnapshot = maxOf(1, currentState.consistencyStreak.currentStreakDays),
                        timestampMillis = now
                    )
                    emitSensoryCue(SensoryCue.TASK_COMPLETED)
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

    private fun handlePetGesture(gesture: PetGestureType) {
        val now = System.currentTimeMillis()
        val speech = when (gesture) {
            PetGestureType.HEAD_PAT -> {
                val lines = listOf(
                    "Purr! That head pat gave me +${gesture.happinessBoost}% Happiness & +${gesture.xpReward} XP!",
                    "Hehe! My ears perked right up! Double-tap me for a 3D play hop!",
                    "Tap any knot on my thread to untie it when you're done!"
                )
                lines[(ephemeralState.value.petInteractionCombo) % lines.size]
            }
            PetGestureType.PLAY_BOOP ->
                "Wheee! 3D Play Spin! +${gesture.happinessBoost}% Happiness & +${gesture.xpReward} XP!"
            PetGestureType.BELLY_SCRITCH ->
                "Cozy belly scritches! My tail can't stop wagging! +${gesture.happinessBoost}% Happiness!"
        }

        val nextCombo = ephemeralState.value.petInteractionCombo + 1
        ephemeralState.update { state ->
            state.copy(
                nowMillis = now,
                celebratingUntilMillis = now + 3_500L,
                celebrationSpeech = speech,
                lastEarnedXp = gesture.xpReward,
                petInteractionCombo = nextCombo,
                bonusPetXp = state.bonusPetXp + gesture.xpReward,
                bonusHappinessFromPlay = (state.bonusHappinessFromPlay + gesture.happinessBoost).coerceAtMost(24),
                activePlayGesture = gesture
            )
        }

        val sensoryCue = SensoryCue.fromPetGesture(gesture)
        NookiSchedulerTelemetry.record(
            source = "3DGestureEngine",
            message = "Triggered ${gesture.name} (${gesture.displayLabel}) -> +${gesture.happinessBoost}% MoodState Happiness, +${gesture.xpReward} XP, Cue=${sensoryCue.name}"
        )

        viewModelScope.launch {
            emitSensoryCue(sensoryCue)
            if (nextCombo == 5 || (gesture == PetGestureType.PLAY_BOOP && nextCombo % 4 == 0)) {
                val current = uiState.value
                repository.recordJournalMilestone(
                    milestoneType = PetMilestoneType.BONDING_MOMENT,
                    title = "3D Bonding Combo x$nextCombo (${gesture.displayLabel})",
                    narrative = "Shared a joyful ${gesture.displayLabel} session! Nooki's happiness climbed to ${current.moodState.happinessScore}%.",
                    badgeEmoji = "🦊",
                    happinessSnapshot = current.moodState.happinessScore,
                    streakSnapshot = current.consistencyStreak.currentStreakDays,
                    timestampMillis = now
                )
            }
        }
    }

    private suspend fun emitSensoryCue(cue: SensoryCue) {
        val current = uiState.value
        _sideEffects.send(
            DeskSideEffect.PlaySensoryCue(
                cue = cue,
                soundEnabled = current.isSoundEnabled,
                hapticsEnabled = current.isHapticsEnabled
            )
        )
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
