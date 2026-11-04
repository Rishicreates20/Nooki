package com.example

import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import com.example.domain.model.HappinessTier
import com.example.domain.model.MoodStateCalculator
import com.example.domain.model.NookiPetMood
import com.example.domain.notice.NookiNoticeFilter
import com.example.domain.notice.NoticeInspectionResult
import com.example.domain.notice.NoticeSourceKind
import com.example.domain.parser.NookiBreathParser
import com.example.domain.parser.NookiCaptureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {

    @Test
    fun hinglish_kalSubah9BajeDoctor_parsesCorrectly() {
        val now = 1762240000000L
        val parsed = NookiBreathParser.parse("kal subah 9 baje doctor", nowMillis = now)

        assertEquals("Doctor", parsed.cleanTitle)
        assertEquals(NookiCaptureKind.REMINDER, parsed.kind)

        val cal = Calendar.getInstance().apply { timeInMillis = parsed.triggerAtMillis }
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(parsed.timeExplanation.contains("Tomorrow at 9:00 AM"))
    }

    @Test
    fun hindi_kalShaam6BajeDawai_parsesAsCriticalMedicine() {
        val now = 1762240000000L
        val parsed = NookiBreathParser.parse("कल शाम 6 बजे दवाई", nowMillis = now)

        assertEquals(NookiCaptureKind.MEDICINE, parsed.kind)
        assertTrue(parsed.isCriticalExactAlarm)
        assertEquals(ReminderPriority.URGENT, parsed.mappedPriority)

        val cal = Calendar.getInstance().apply { timeInMillis = parsed.triggerAtMillis }
        assertEquals(18, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun moneyAndLending_rahulOwesMe500_extractsPersonAndAmount() {
        val parsed = NookiBreathParser.parse("Rahul owes me 500")
        assertEquals(NookiCaptureKind.MONEY_OWED_TO_ME, parsed.kind)
        assertEquals("Rahul", parsed.personName)
        assertEquals(500, parsed.amountInr)
    }

    @Test
    fun heldMemory_andQuestionLookup_findsPassportInBlueBag() {
        val savedPassport = ReminderEntity(
            id = 1L,
            title = "Passport is in the blue bag",
            notes = "KIND:HELD_MEMORY",
            rawInput = "passport is in the blue bag",
            triggerAtMillis = System.currentTimeMillis() + 86_400_000L,
            category = ReminderCategory.CUSTOM
        )
        val queryResult = NookiBreathParser.parse(
            rawInput = "where is my passport",
            existingItems = listOf(savedPassport)
        )
        assertEquals(NookiCaptureKind.QUESTION, queryResult.kind)
        assertNotNull(queryResult.instantAnswerFromMemory)
        assertTrue(queryResult.instantAnswerFromMemory!!.contains("Passport is in the blue bag"))
    }

    @Test
    fun otpAndBankBalance_neverReachesDatabase_provenByTest() {
        val otpSms = NookiNoticeFilter.inspectNotification(
            packageName = "com.google.android.apps.messaging",
            senderOrTitle = "AX-HDFCBK",
            rawText = "482910 is your OTP for netbanking login. Do not share with anyone."
        )
        assertTrue(otpSms is NoticeInspectionResult.DroppedSensitive)

        val bankBalanceSms = NookiNoticeFilter.inspectNotification(
            packageName = "com.google.android.apps.messaging",
            senderOrTitle = "SBIINB",
            rawText = "Your A/c XX1234 is debited for Rs.200. Avail Bal Rs. 14,500.00"
        )
        assertTrue(bankBalanceSms is NoticeInspectionResult.DroppedSensitive)
    }

    @Test
    fun chatNotification_neverStoresMessageContent() {
        val secretMessageBody = "Hey, meet me at the secret cafe at 5pm and bring the documents"
        val result = NookiNoticeFilter.inspectNotification(
            packageName = "com.whatsapp",
            senderOrTitle = "Aman",
            rawText = secretMessageBody
        )
        assertTrue(result is NoticeInspectionResult.SafeSuggestion)
        val suggestion = result as NoticeInspectionResult.SafeSuggestion
        assertEquals(NoticeSourceKind.UNREAD_MESSAGE, suggestion.kind)
        assertEquals("Unread message from Aman", suggestion.sanitizedTitle)
        assertTrue(!suggestion.sanitizedTitle.contains("secret cafe"))
        assertTrue(!suggestion.sanitizedSubtitle.contains("secret cafe"))
    }

    @Test
    fun moodState_happinessIncreasesWithCompletionFrequency() {
        val now = 1762260000000L
        val emptyMood = MoodStateCalculator.compute(
            allReminders = emptyList(),
            nowMillis = now
        )

        val frequentCompletions = listOf(
            ReminderEntity(
                id = 1L,
                title = "Morning medicine",
                triggerAtMillis = now - 3_600_000L,
                completedAtMillis = now - 3_500_000L,
                status = ReminderStatus.COMPLETED
            ),
            ReminderEntity(
                id = 2L,
                title = "Pay electricity bill",
                triggerAtMillis = now - 2_400_000L,
                completedAtMillis = now - 2_300_000L,
                status = ReminderStatus.COMPLETED
            ),
            ReminderEntity(
                id = 3L,
                title = "Call Mom back",
                triggerAtMillis = now - 1_200_000L,
                completedAtMillis = now - 1_100_000L,
                status = ReminderStatus.COMPLETED
            )
        )

        val radiantMood = MoodStateCalculator.compute(
            allReminders = frequentCompletions,
            nowMillis = now,
            resolvedPetMood = NookiPetMood.ATTENTIVE
        )

        assertTrue(radiantMood.happinessScore > emptyMood.happinessScore)
        assertEquals(3, radiantMood.completedLast24Hours)
        assertEquals(HappinessTier.RADIANT, radiantMood.happinessTier)
    }

    @Test
    fun moodState_getsFlusteredWhenMissedItemsPileUp_butNeverDropsBelowGentleFloor() {
        val now = 1762260000000L
        val piledUpMissed = (1L..8L).map { id ->
            ReminderEntity(
                id = id,
                title = "Missed item $id",
                triggerAtMillis = now - id * 3_600_000L,
                status = ReminderStatus.MISSED
            )
        }

        val flusteredMood = MoodStateCalculator.compute(
            allReminders = piledUpMissed,
            nowMillis = now,
            resolvedPetMood = NookiPetMood.WORRIED_OVERDUE
        )

        assertEquals(HappinessTier.FLUSTERED, flusteredMood.happinessTier)
        assertTrue(flusteredMood.happinessScore >= 35)
    }

    @Test
    fun petGesture_updatesMoodStateAndActivatesHappyParticles() {
        val now = 1762260000000L
        val baseline = MoodStateCalculator.compute(
            allReminders = emptyList(),
            nowMillis = now
        )

        val afterPlayBoop = MoodStateCalculator.compute(
            allReminders = emptyList(),
            nowMillis = now,
            bonusPetTouches = 2,
            bonusHappinessFromPlay = com.example.domain.model.PetGestureType.PLAY_BOOP.happinessBoost,
            activePlayGesture = com.example.domain.model.PetGestureType.PLAY_BOOP,
            resolvedPetMood = NookiPetMood.CELEBRATING_XP
        )

        assertTrue(afterPlayBoop.happinessScore > baseline.happinessScore)
        assertTrue(afterPlayBoop.isHappyState)
        assertEquals(com.example.domain.model.PetGestureType.PLAY_BOOP, afterPlayBoop.activePlayGesture)
    }

    @Test
    fun consistencyStreak_tracksConsecutiveDaysAndLongestStreak() {
        val now = 1762260000000L
        val oneDayMs = 86_400_000L

        val completedThreeConsecutiveDays = listOf(
            ReminderEntity(
                id = 10L,
                title = "Today task",
                triggerAtMillis = now - 3_600_000L,
                completedAtMillis = now - 1_800_000L,
                completedDayEpoch = com.example.domain.model.ConsistencyStreakCalculator.toLocalDayEpoch(now),
                status = ReminderStatus.COMPLETED
            ),
            ReminderEntity(
                id = 11L,
                title = "Yesterday task",
                triggerAtMillis = now - oneDayMs - 3_600_000L,
                completedAtMillis = now - oneDayMs,
                completedDayEpoch = com.example.domain.model.ConsistencyStreakCalculator.toLocalDayEpoch(now - oneDayMs),
                status = ReminderStatus.COMPLETED
            ),
            ReminderEntity(
                id = 12L,
                title = "Two days ago task",
                triggerAtMillis = now - 2 * oneDayMs - 3_600_000L,
                completedAtMillis = now - 2 * oneDayMs,
                completedDayEpoch = com.example.domain.model.ConsistencyStreakCalculator.toLocalDayEpoch(now - 2 * oneDayMs),
                status = ReminderStatus.COMPLETED
            )
        )

        val streakState = com.example.domain.model.ConsistencyStreakCalculator.compute(
            allReminders = completedThreeConsecutiveDays,
            nowMillis = now,
            persistedLongestStreak = 2,
            dailyGoalCount = 2
        )

        assertEquals(3, streakState.currentStreakDays)
        assertEquals(3, streakState.longestStreakDays)
        assertEquals(1, streakState.completedTodayCount)
        assertTrue(streakState.isTodayStreakSecured)
        assertEquals(7, streakState.last7DaysTimeline.size)
    }

    @Test
    fun sensoryCueAndCozyWaveSynthesizer_generateValidWaveformsAndRiffWavAudio() {
        for (cue in com.example.domain.model.SensoryCue.entries) {
            assertEquals(cue.waveformTimings.size, cue.waveformAmplitudes.size)
            assertTrue(cue.waveformTimings.isNotEmpty())
            assertTrue(cue.waveformAmplitudes.all { it in 0..255 })

            val wavBytes = com.example.scheduler.CozyWaveSynthesizer.synthesizeWavBytes(cue)
            assertTrue(wavBytes.size > 44)
            assertEquals("RIFF", String(wavBytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(wavBytes, 8, 4, Charsets.US_ASCII))
        }
    }

    @Test
    fun nookiAppTheme_providesDistinctColorPalettesAndMatching3DParticleColors() {
        val midnight = com.example.domain.model.NookiAppTheme.fromId("MIDNIGHT_COZY")
        val forest = com.example.domain.model.NookiAppTheme.fromId("FOREST_MEADOW")
        val hearth = com.example.domain.model.NookiAppTheme.fromId("WARM_HEARTH")

        assertEquals(com.example.domain.model.NookiAppTheme.MIDNIGHT_COZY, midnight)
        assertEquals(com.example.domain.model.NookiAppTheme.FOREST_MEADOW, forest)
        assertEquals(com.example.domain.model.NookiAppTheme.WARM_HEARTH, hearth)

        assertTrue(forest.backgroundDeepHex != midnight.backgroundDeepHex)
        assertTrue(hearth.backgroundDeepHex != forest.backgroundDeepHex)

        for (theme in com.example.domain.model.NookiAppTheme.entries) {
            assertEquals(5, theme.particleColorsHex.size)
            assertTrue(theme.displayName.isNotBlank())
        }
        assertTrue(forest.particleColorsHex != midnight.particleColorsHex)
        assertTrue(hearth.particleColorsHex != forest.particleColorsHex)
    }

    @Test
    fun petJournalEntry_recordsHighStreakAndPersonalityShiftMilestones() {
        val streakEntry = com.example.data.local.entity.PetJournalEntryEntity(
            id = 1L,
            milestoneType = com.example.data.local.entity.PetMilestoneType.FIRST_HIGH_STREAK,
            title = "First High-Streak Achievement (5-Day Thread)",
            narrative = "Completed scheduled reminders 5 days in a row!",
            happinessSnapshot = 95,
            streakSnapshot = 5
        )
        val personalityEntry = com.example.data.local.entity.PetJournalEntryEntity(
            id = 2L,
            milestoneType = com.example.data.local.entity.PetMilestoneType.PERSONALITY_SHIFT,
            title = "Pet Personality Shift: Radiant & Joyful",
            narrative = "Nooki shifted to Radiant & Joyful with orbital fireflies!",
            happinessSnapshot = 92,
            streakSnapshot = 5
        )

        assertEquals("🔥", streakEntry.badgeEmoji)
        assertEquals("High-Streak Achievement", streakEntry.milestoneType.categoryLabel)
        assertEquals("✨", personalityEntry.badgeEmoji)
        assertEquals("Pet Personality Shift", personalityEntry.milestoneType.categoryLabel)
    }
}
