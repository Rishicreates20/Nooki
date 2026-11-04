package com.example.domain.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NookiAmberLight
import com.example.ui.theme.NookiCoralAccent
import com.example.ui.theme.NookiMintSuccess
import com.example.ui.theme.NookiMutedLavender
import com.example.ui.theme.NookiSkyInfo

/**
 * Reactive state machine states for Nooki's 3D desk-pet model.
 */
enum class NookiPetMood(
    val displayTitle: String,
    val defaultSpeech: String,
    val primaryAuraColor: Color,
    val breathingSpeedMs: Int
) {
    IDLE_COZY(
        displayTitle = "Cozy & Relaxed",
        defaultSpeech = "Drag to rotate me in 3D, or tap my head for a desk check-in!",
        primaryAuraColor = NookiMintSuccess,
        breathingSpeedMs = 1800
    ),
    ATTENTIVE(
        displayTitle = "Focused & Watching Clock",
        defaultSpeech = "I'm keeping an eye on your upcoming desk reminders!",
        primaryAuraColor = NookiSkyInfo,
        breathingSpeedMs = 1350
    ),
    EXCITED_RINGING(
        displayTitle = "RINGING DESK BELL!",
        defaultSpeech = "Ding-ding! Your desk reminder is ringing right now!",
        primaryAuraColor = NookiCoralAccent,
        breathingSpeedMs = 340
    ),
    WORRIED_OVERDUE(
        displayTitle = "Worried (Overdue Task)",
        defaultSpeech = "Uh oh... we have an overdue task waiting on the desk!",
        primaryAuraColor = NookiAmberLight,
        breathingSpeedMs = 900
    ),
    CELEBRATING_XP(
        displayTitle = "Happy & Energized!",
        defaultSpeech = "Yay! Great job finishing your task! +XP power-up!",
        primaryAuraColor = NookiMintSuccess,
        breathingSpeedMs = 480
    ),
    SLEEPING(
        displayTitle = "Dozy Desk Nap",
        defaultSpeech = "Zzz... All tasks complete. Tap me to wake me up!",
        primaryAuraColor = NookiMutedLavender,
        breathingSpeedMs = 2400
    )
}
