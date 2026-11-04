package com.example.domain.model

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * High-level happiness tier derived from real reminder completion frequency and active load.
 * Follows Nooki's rule: "Mood comes from real data, never from random animation.
 * Levels and XP reward care for your own life. They never guilt anyone."
 */
enum class HappinessTier(
    val label: String,
    val badgeEmoji: String,
    val tailWagMultiplier: Float,
    val earPerkDegrees: Float
) {
    RADIANT(
        label = "Radiant & Thriving",
        badgeEmoji = "✨",
        tailWagMultiplier = 1.6f,
        earPerkDegrees = 8f
    ),
    CONTENT(
        label = "Warm & Content",
        badgeEmoji = "🦊",
        tailWagMultiplier = 1.15f,
        earPerkDegrees = 4f
    ),
    CALM(
        label = "Calm & Steady",
        badgeEmoji = "🌿",
        tailWagMultiplier = 0.85f,
        earPerkDegrees = 0f
    ),
    SLEEPY_PEACEFUL(
        label = "Peacefully Resting",
        badgeEmoji = "💤",
        tailWagMultiplier = 0.5f,
        earPerkDegrees = -4f
    ),
    FLUSTERED(
        label = "A Little Flustered",
        badgeEmoji = "🐾",
        tailWagMultiplier = 1.35f,
        earPerkDegrees = -8f
    )
}

/**
 * Immutable data class representing Nooki's real-time happiness and emotional state,
 * computed deterministically from Room reminder completion frequency and queue state.
 */
data class MoodState(
    val happinessScore: Int = 75,
    val happinessTier: HappinessTier = HappinessTier.CONTENT,
    val petMood: NookiPetMood = NookiPetMood.IDLE_COZY,
    val completedTodayCount: Int = 0,
    val completedLast24Hours: Int = 0,
    val totalCompletedCount: Int = 0,
    val missedCount: Int = 0,
    val upcomingCloseCount: Int = 0,
    val completionFrequencyPerDay: Float = 0f,
    val onTimeCompletionRatio: Float = 1f,
    val streakDays: Int = 0,
    val careMessage: String = "Nooki is calm and watching your thread."
) {
    val happinessFraction: Float
        get() = (happinessScore.coerceIn(0, 100)) / 100f
}

/**
 * Pure Kotlin calculator that derives [MoodState] from Room [ReminderEntity] records.
 * Zero Android framework imports so it can be unit-tested on the JVM.
 */
object MoodStateCalculator {

    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

    // Gentle floor: Nooki never drops below 35% happiness so it never guilts the user
    private const val MIN_GENTLE_HAPPINESS = 35
    private const val BASE_HAPPINESS = 68

    fun compute(
        allReminders: List<ReminderEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        bonusPetTouches: Int = 0,
        resolvedPetMood: NookiPetMood = NookiPetMood.IDLE_COZY
    ): MoodState {
        val completedItems = allReminders.filter { it.status == ReminderStatus.COMPLETED }
        val missedItems = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                item.status != ReminderStatus.RINGING &&
                !isHeldOrMoneyNote(item) &&
                (item.status == ReminderStatus.MISSED || item.triggerAtMillis < nowMillis)
        }
        val upcomingCloseItems = allReminders.filter { item ->
            item.status != ReminderStatus.COMPLETED &&
                item.status != ReminderStatus.CANCELLED &&
                !isHeldOrMoneyNote(item) &&
                item.triggerAtMillis in nowMillis..(nowMillis + 2 * MILLIS_PER_HOUR)
        }

        val startOfTodayMillis = getStartOfDayMillis(nowMillis)
        val cutoff24hMillis = nowMillis - MILLIS_PER_DAY
        val cutoff7dMillis = nowMillis - 7 * MILLIS_PER_DAY

        val completedToday = completedItems.count {
            val ts = it.completedAtMillis ?: it.createdAtMillis
            ts >= startOfTodayMillis
        }

        val completedLast24h = completedItems.count {
            val ts = it.completedAtMillis ?: it.createdAtMillis
            ts >= cutoff24hMillis
        }

        val completedLast7d = completedItems.count {
            val ts = it.completedAtMillis ?: it.createdAtMillis
            ts >= cutoff7dMillis
        }

        // Daily completion frequency over the last 7 days (weighted toward last 24h)
        val sevenDayAvg = completedLast7d / 7f
        val completionFrequencyPerDay = maxOf(completedLast24h.toFloat(), sevenDayAvg)

        // Ratio of completed vs. missed items
        val totalActionable = completedItems.size + missedItems.size
        val onTimeRatio = if (totalActionable > 0) {
            completedItems.size.toFloat() / totalActionable.toFloat()
        } else {
            1f
        }

        // Consecutive day streak calculation
        val streakDays = computeConsecutiveStreakDays(completedItems, nowMillis)

        // Happiness score formula driven by completion frequency:
        // - Base: 68
        // - +8 per reminder completed in the last 24h (up to +28)
        // - +3 per streak day (up to +12)
        // - +2 per head-pat interaction (up to +8)
        // - Gentle dip (-6 per missed item, capped at -24) when things pile up ("gets flustered when things pile up")
        val frequencyBoost = (completedLast24h * 8).coerceAtMost(28)
        val streakBoost = (streakDays * 3).coerceAtMost(12)
        val touchBoost = (bonusPetTouches * 2).coerceAtMost(8)
        val pileUpLoad = (missedItems.size * 6).coerceAtMost(24)

        val rawScore = BASE_HAPPINESS + frequencyBoost + streakBoost + touchBoost - pileUpLoad
        val happinessScore = rawScore.coerceIn(MIN_GENTLE_HAPPINESS, 100)

        val tier = when {
            missedItems.size >= 3 || resolvedPetMood == NookiPetMood.WORRIED_OVERDUE && missedItems.size >= 2 ->
                HappinessTier.FLUSTERED
            resolvedPetMood == NookiPetMood.SLEEPING ->
                HappinessTier.SLEEPY_PEACEFUL
            happinessScore >= 85 || completedLast24h >= 3 ->
                HappinessTier.RADIANT
            happinessScore >= 70 || completedLast24h >= 1 ->
                HappinessTier.CONTENT
            else ->
                HappinessTier.CALM
        }

        val careMessage = when (tier) {
            HappinessTier.RADIANT ->
                "You've untied $completedLast24h knot(s) in the last 24h! Nooki is glowing with energy."
            HappinessTier.CONTENT ->
                if (completedToday > 0) {
                    "$completedToday knot(s) untied today • Nooki feels warm and steady."
                } else {
                    "Nooki is content and keeping watch over your thread."
                }
            HappinessTier.SLEEPY_PEACEFUL ->
                "All tasks are done ($completedToday today). Nooki is resting peacefully."
            HappinessTier.FLUSTERED ->
                "${missedItems.size} items piled up in 'You missed' — untie one knot to help Nooki breathe easy."
            HappinessTier.CALM ->
                "Nooki is calm and ready whenever you need to remember something."
        }

        return MoodState(
            happinessScore = happinessScore,
            happinessTier = tier,
            petMood = resolvedPetMood,
            completedTodayCount = completedToday,
            completedLast24Hours = completedLast24h,
            totalCompletedCount = completedItems.size,
            missedCount = missedItems.size,
            upcomingCloseCount = upcomingCloseItems.size,
            completionFrequencyPerDay = (completionFrequencyPerDay * 10f).roundToInt() / 10f,
            onTimeCompletionRatio = onTimeRatio,
            streakDays = streakDays,
            careMessage = careMessage
        )
    }

    private fun computeConsecutiveStreakDays(
        completedItems: List<ReminderEntity>,
        nowMillis: Long
    ): Int {
        if (completedItems.isEmpty()) return 0
        val completedDayBuckets = completedItems
            .map { getStartOfDayMillis(it.completedAtMillis ?: it.createdAtMillis) }
            .toSet()

        var streak = 0
        var cursorDay = getStartOfDayMillis(nowMillis)
        while (cursorDay in completedDayBuckets) {
            streak++
            cursorDay -= MILLIS_PER_DAY
        }
        return streak
    }

    private fun getStartOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun isHeldOrMoneyNote(item: ReminderEntity): Boolean {
        return item.notes.contains("KIND:HELD_MEMORY") ||
            item.notes.contains("KIND:MONEY_OWED_TO_ME") ||
            item.notes.contains("KIND:MONEY_I_OWE") ||
            item.notes.contains("KIND:ITEM_LENT")
    }
}
