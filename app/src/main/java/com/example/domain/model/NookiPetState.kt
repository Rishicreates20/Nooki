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
        defaultSpeech = "Drag to rotate me in 3D, or tap my head for a desk check-in!",
        primaryAuraColor = NookiMintSuccess,
        breathingSpeedMs = 1800
    ),
    ATTENTIVE(
        stateCode = "STATE_ATTENTIVE",
        displayTitle = "Focused & Watching Clock",
        defaultSpeech = "I'm keeping an eye on your upcoming desk reminders!",
        primaryAuraColor = NookiSkyInfo,
        breathingSpeedMs = 1350
    ),
    EXCITED_RINGING(
        stateCode = "STATE_RINGING_ALERT",
        displayTitle = "RINGING DESK BELL!",
        defaultSpeech = "Ding-ding! Your desk reminder is ringing right now!",
        primaryAuraColor = NookiCoralAccent,
        breathingSpeedMs = 320
    ),
    WORRIED_OVERDUE(
        stateCode = "STATE_GRUMPY_OVERDUE",
        displayTitle = "Worried (Overdue Task)",
        defaultSpeech = "Uh oh... we have an overdue task waiting on the desk!",
        primaryAuraColor = NookiAmberLight,
        breathingSpeedMs = 860
    ),
    CELEBRATING_XP(
        stateCode = "STATE_CELEBRATING_XP",
        displayTitle = "Happy & Energized!",
        defaultSpeech = "Yay! Great job finishing your task! +XP power-up!",
        primaryAuraColor = NookiMintSuccess,
        breathingSpeedMs = 460
    ),
    SLEEPING(
        stateCode = "STATE_SLEEPING",
        displayTitle = "Dozy Desk Nap",
        defaultSpeech = "Zzz... All tasks complete. Tap me to wake me up!",
        primaryAuraColor = NookiMutedLavender,
        breathingSpeedMs = 2400
    )
}

/**
 * Strongly-typed sealed state hierarchy representing Nooki's reactive state machine nodes.
 * Every emission from Room's `Flow<List<ReminderEntity>>` deterministically resolves to one
 * of these states.
 */
sealed interface NookiPetState {
    val mood: NookiPetMood
    val speechText: String
    val dbTriggerSummary: String

    data class IdleCozy(
        override val speechText: String = NookiPetMood.IDLE_COZY.defaultSpeech,
        override val dbTriggerSummary: String = "0 active rows, 0 completed rows in Room"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.IDLE_COZY
    }

    data class Sleeping(
        val completedTotal: Int,
        override val speechText: String = "Zzz... All $completedTotal desk task(s) finished! Tap me or add a reminder!",
        override val dbTriggerSummary: String = "0 active rows, $completedTotal COMPLETED in Room"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.SLEEPING
    }

    data class Attentive(
        val nextReminder: ReminderEntity,
        val activeCount: Int,
        val millisRemaining: Long,
        override val speechText: String,
        override val dbTriggerSummary: String = "$activeCount SCHEDULED/SNOOZED in Room (Next: #${nextReminder.id})"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.ATTENTIVE
    }

    data class RingingAlert(
        val ringingReminder: ReminderEntity,
        val totalRingingCount: Int,
        override val speechText: String = "🔔 '${ringingReminder.title}' is ringing! Complete it for +${ringingReminder.petMoodImpact} XP!",
        override val dbTriggerSummary: String = "$totalRingingCount row(s) with status=RINGING in Room (#${ringingReminder.id})"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.EXCITED_RINGING
    }

    data class WorriedOverdue(
        val overdueReminder: ReminderEntity,
        val overdueCount: Int,
        override val speechText: String = "🐾 '${overdueReminder.title}' is overdue! Let's complete or snooze it!",
        override val dbTriggerSummary: String = "$overdueCount overdue/MISSED row(s) in Room (#${overdueReminder.id})"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.WORRIED_OVERDUE
    }

    data class CelebratingXp(
        val earnedXp: Int,
        override val speechText: String,
        override val dbTriggerSummary: String = "Room status updated -> +$earnedXp XP celebration window"
    ) : NookiPetState {
        override val mood: NookiPetMood = NookiPetMood.CELEBRATING_XP
    }
}

/**
 * Pure, deterministic state machine reducer that evaluates Room database snapshots
 * and real-time clock ticks into a [NookiPetState].
 */
object NookiStateMachine {

    fun reduce(
        allReminders: List<ReminderEntity>,
        activeReminders: List<ReminderEntity>,
        completedCount: Int,
        nowMillis: Long,
        celebratingUntilMillis: Long,
        celebrationSpeech: String?,
        lastEarnedXp: Int
    ): NookiPetState {
        val ringingList = allReminders.filter { it.status == ReminderStatus.RINGING }
        val overdueList = allReminders.filter {
            it.isOverdue(nowMillis) &&
                it.status != ReminderStatus.COMPLETED &&
                it.status != ReminderStatus.CANCELLED
        }

        // Priority 1: Active ringing alarm in Room always takes highest priority
        if (ringingList.isNotEmpty()) {
            return NookiPetState.RingingAlert(
                ringingReminder = ringingList.first(),
                totalRingingCount = ringingList.size
            )
        }

        // Priority 2: Active XP celebration window (e.g. right after Room marks a task COMPLETED)
        if (nowMillis < celebratingUntilMillis && !celebrationSpeech.isNullOrBlank()) {
            return NookiPetState.CelebratingXp(
                earnedXp = lastEarnedXp,
                speechText = celebrationSpeech
            )
        }

        // Priority 3: Overdue or MISSED reminders in Room
        if (overdueList.isNotEmpty()) {
            return NookiPetState.WorriedOverdue(
                overdueReminder = overdueList.first(),
                overdueCount = overdueList.size
            )
        }

        // Priority 4: Scheduled / Snoozed reminders in Room
        val nextReminder = activeReminders.firstOrNull()
        if (nextReminder != null) {
            val remaining = (nextReminder.triggerAtMillis - nowMillis).coerceAtLeast(0L)
            val mins = remaining / 60_000L
            val secs = (remaining % 60_000L) / 1_000L
            val speech = when {
                mins == 0L -> "Almost time! '${nextReminder.title}' rings in ${secs}s!"
                mins < 60L -> "Next reminder in ${mins}m ${secs}s: '${nextReminder.title}'"
                else -> "Watching ${activeReminders.size} alarm(s). Next: '${nextReminder.title}'"
            }
            return NookiPetState.Attentive(
                nextReminder = nextReminder,
                activeCount = activeReminders.size,
                millisRemaining = remaining,
                speechText = speech
            )
        }

        // Priority 5: All tasks completed -> Nooki naps peacefully
        if (completedCount > 0) {
            return NookiPetState.Sleeping(completedTotal = completedCount)
        }

        // Default: Idle Cozy
        return NookiPetState.IdleCozy()
    }
}
