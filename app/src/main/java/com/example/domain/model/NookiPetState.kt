package com.example.domain.model

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.ui.theme.NookiAmberLight
import com.example.ui.theme.NookiCoralAccent
import com.example.ui.theme.NookiMintSuccess
import com.example.ui.theme.NookiMutedLavender
import com.example.ui.theme.NookiSkyInfo

/**
 * Visual and animation parameters for each mood in Nooki's state machine.
 */
enum class NookiPetMood(
    val stateCode: String,
    val displayTitle: String,
    val defaultSpeech: String,
    val primaryAuraColor: Color,
    val breathingSpeedMs: Int
) {
    IDLE_COZY(
        stateCode = "STATE_IDLE_COZY",
        displayTitle = "Cozy & Relaxed",
        defaultSpeech = "Tap to pat my head, double-tap to play, or rub my belly in 3D!",
        primaryAuraColor = NookiMintSuccess,
        breathingSpeedMs = 1800
    ),
    ATTENTIVE(
        stateCode = "STATE_ATTENTIVE",
        displayTitle = "Focused & Watching Thread",
        defaultSpeech = "I'm keeping an eye on your upcoming reminders!",
        primaryAuraColor = NookiSkyInfo,
        breathingSpeedMs = 1350
    ),
    EXCITED_RINGING(
        stateCode = "STATE_RINGING_ALERT",
        displayTitle = "RINGING RIGHT NOW!",
        defaultSpeech = "Ding-ding! Your reminder is ringing right now!",
        primaryAuraColor = NookiCoralAccent,
        breathingSpeedMs = 320
    ),
    WORRIED_OVERDUE(
        stateCode = "STATE_FLUSTERED_MISSED",
        displayTitle = "Flustered (You Missed Items)",
        defaultSpeech = "A few things piled up in 'You missed' — let's untie a knot together!",
        primaryAuraColor = NookiAmberLight,
        breathingSpeedMs = 860
    ),
    CELEBRATING_XP(
        stateCode = "STATE_CELEBRATING_XP",
        displayTitle = "Happy & Glowing!",
        defaultSpeech = "Yay! Great job! +XP & happy sparkles!",
        primaryAuraColor = NookiMintSuccess,
        breathingSpeedMs = 460
    ),
    SLEEPING(
        stateCode = "STATE_SLEEPING",
        displayTitle = "Peaceful Nap",
        defaultSpeech = "Zzz... Everything is calm and taken care of.",
        primaryAuraColor = NookiMutedLavender,
        breathingSpeedMs = 2400
    )
}

/**
 * Strongly-typed sealed state hierarchy representing Nooki's reactive state machine nodes.
 */
sealed interface NookiPetState {
    val mood: NookiPetMood
    val speechText: String
    val dbTriggerSummary: String

    data class IdleCozy(
        val happinessScore: Int = 75,
        override val speechText: String = NookiPetMood.IDLE_COZY.defaultSpeech,
        override val dbTriggerSummary: String = "Calm • Happiness $happinessScore%"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.IDLE_COZY
    }

    data class Sleeping(
        val completedTotal: Int,
        val happinessScore: Int,
        override val speechText: String = "Zzz... All $completedTotal knot(s) untied! Happiness at $happinessScore%.",
        override val dbTriggerSummary: String = "0 active rows, $completedTotal COMPLETED • Happiness $happinessScore%"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.SLEEPING
    }

    data class Attentive(
        val nextReminder: ReminderEntity,
        val activeCount: Int,
        val millisRemaining: Long,
        val happinessScore: Int,
        override val speechText: String,
        override val dbTriggerSummary: String = "$activeCount active in Room (Next: #${nextReminder.id}) • Happiness $happinessScore%"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.ATTENTIVE
    }

    data class RingingAlert(
        val ringingReminder: ReminderEntity,
        val totalRingingCount: Int,
        override val speechText: String = "🔔 '${ringingReminder.title}' is ringing! Untie the knot for +${ringingReminder.petMoodImpact} XP!",
        override val dbTriggerSummary: String = "$totalRingingCount row(s) RINGING in Room (#${ringingReminder.id})"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.EXCITED_RINGING
    }

    data class WorriedOverdue(
        val overdueReminder: ReminderEntity,
        val overdueCount: Int,
        val happinessScore: Int,
        override val speechText: String = "🐾 '${overdueReminder.title}' is in 'You missed' ($overdueCount open). Tap its knot to untie it!",
        override val dbTriggerSummary: String = "$overdueCount row(s) in 'You missed' • Happiness $happinessScore%"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.WORRIED_OVERDUE
    }

    data class CelebratingXp(
        val earnedXp: Int,
        val happinessScore: Int,
        override val speechText: String,
        override val dbTriggerSummary: String = "Happy state (+$earnedXp XP) • Happiness $happinessScore%"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.CELEBRATING_XP
    }
}

/**
 * Combined output of [NookiStateMachine.reduceWithMood], pairing the discrete [NookiPetState]
 * node with the continuous completion-frequency & gesture-driven [MoodState].
 */
data class NookiStateMachineOutput(
    val petState: NookiPetState,
    val moodState: MoodState
)

/**
 * Pure, deterministic state machine reducer that evaluates Room database snapshots,
 * reminder completion frequency, and petting/play gestures into a [NookiStateMachineOutput].
 */
object NookiStateMachine {

    fun reduceWithMood(
        allReminders: List<ReminderEntity>,
        activeReminders: List<ReminderEntity>,
        completedCount: Int,
        nowMillis: Long,
        celebratingUntilMillis: Long,
        celebrationSpeech: String?,
        lastEarnedXp: Int,
        bonusPetTouches: Int = 0,
        bonusHappinessFromPlay: Int = 0,
        activePlayGesture: PetGestureType? = null
    ): NookiStateMachineOutput {
        val ringingList = allReminders.filter { it.status == ReminderStatus.RINGING }
        val overdueList = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                !isHeldOrMoneyNote(item) &&
                (item.status == ReminderStatus.MISSED || item.triggerAtMillis < nowMillis)
        }

        val isCelebratingActive = nowMillis < celebratingUntilMillis && !celebrationSpeech.isNullOrBlank()

        val preliminaryMood = when {
            ringingList.isNotEmpty() -> NookiPetMood.EXCITED_RINGING
            isCelebratingActive -> NookiPetMood.CELEBRATING_XP
            overdueList.isNotEmpty() -> NookiPetMood.WORRIED_OVERDUE
            activeReminders.isNotEmpty() -> NookiPetMood.ATTENTIVE
            completedCount > 0 -> NookiPetMood.SLEEPING
            else -> NookiPetMood.IDLE_COZY
        }

        val moodState = MoodStateCalculator.compute(
            allReminders = allReminders,
            nowMillis = nowMillis,
            bonusPetTouches = bonusPetTouches,
            bonusHappinessFromPlay = bonusHappinessFromPlay,
            activePlayGesture = if (isCelebratingActive) activePlayGesture else null,
            resolvedPetMood = preliminaryMood
        )

        val petState: NookiPetState = when {
            ringingList.isNotEmpty() -> {
                NookiPetState.RingingAlert(
                    ringingReminder = ringingList.first(),
                    totalRingingCount = ringingList.size
                )
            }

            isCelebratingActive -> {
                NookiPetState.CelebratingXp(
                    earnedXp = lastEarnedXp,
                    happinessScore = moodState.happinessScore,
                    speechText = celebrationSpeech!!
                )
            }

            overdueList.isNotEmpty() -> {
                NookiPetState.WorriedOverdue(
                    overdueReminder = overdueList.first(),
                    overdueCount = overdueList.size,
                    happinessScore = moodState.happinessScore
                )
            }

            activeReminders.isNotEmpty() -> {
                val nextReminder = activeReminders.first()
                val remaining = (nextReminder.triggerAtMillis - nowMillis).coerceAtLeast(0L)
                val mins = remaining / 60_000L
                val secs = (remaining % 60_000L) / 1_000L
                val speech = when {
                    mins == 0L -> "Almost time! '${nextReminder.title}' rings in ${secs}s!"
                    mins < 60L -> "Looking up — '${nextReminder.title}' is in ${mins}m ${secs}s."
                    else -> "${moodState.happinessTier.badgeEmoji} ${moodState.careMessage} Next: '${nextReminder.title}'"
                }
                NookiPetState.Attentive(
                    nextReminder = nextReminder,
                    activeCount = activeReminders.size,
                    millisRemaining = remaining,
                    happinessScore = moodState.happinessScore,
                    speechText = speech
                )
            }

            completedCount > 0 -> {
                NookiPetState.Sleeping(
                    completedTotal = completedCount,
                    happinessScore = moodState.happinessScore
                )
            }

            else -> {
                NookiPetState.IdleCozy(
                    happinessScore = moodState.happinessScore,
                    speechText = moodState.careMessage
                )
            }
        }

        return NookiStateMachineOutput(
            petState = petState,
            moodState = moodState.copy(petMood = petState.mood)
        )
    }

    fun reduce(
        allReminders: List<ReminderEntity>,
        activeReminders: List<ReminderEntity>,
        completedCount: Int,
        nowMillis: Long,
        celebratingUntilMillis: Long,
        celebrationSpeech: String?,
        lastEarnedXp: Int
    ): NookiPetState = reduceWithMood(
        allReminders = allReminders,
        activeReminders = activeReminders,
        completedCount = completedCount,
        nowMillis = nowMillis,
        celebratingUntilMillis = celebratingUntilMillis,
        celebrationSpeech = celebrationSpeech,
        lastEarnedXp = lastEarnedXp
    ).petState

    private fun isHeldOrMoneyNote(item: ReminderEntity): Boolean {
        return item.notes.contains("KIND:HELD_MEMORY") ||
            item.notes.contains("KIND:MONEY_OWED_TO_ME") ||
            item.notes.contains("KIND:MONEY_I_OWE") ||
            item.notes.contains("KIND:ITEM_LENT")
    }
}
