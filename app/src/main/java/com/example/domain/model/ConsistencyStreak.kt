package com.example.domain.model

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single day node in the 7-day Consistency Streak visual timeline.
 */
data class DayStreakNode(
    val dayEpoch: Long,
    val dayShortLabel: String,
    val dateLabel: String,
    val completedCount: Int,
    val isToday: Boolean,
    val isStreakDay: Boolean
)

/**
 * Domain model representing the user's Consistency Streak based on consecutive days
 * of completing scheduled reminders.
 */
data class ConsistencyStreakState(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val totalActiveDays: Int = 0,
    val completedTodayCount: Int = 0,
    val dailyGoalCount: Int = 3,
    val isTodayStreakSecured: Boolean = false,
    val isStreakAtRiskToday: Boolean = false,
    val last7DaysTimeline: List<DayStreakNode> = emptyList(),
    val streakTitle: String = "Start your streak today",
    val streakSubtitle: String = "Untie 1 scheduled reminder today to light up Day 1!"
) {
    val dailyGoalProgressFraction: Float
        get() = if (dailyGoalCount <= 0) 1f else (completedTodayCount.toFloat() / dailyGoalCount.toFloat()).coerceIn(0f, 1f)
}

/**
 * Pure Kotlin calculator that derives [ConsistencyStreakState] from Room [ReminderEntity]
 * completion timestamps (`completed_day_epoch` / `completed_at_millis`) and persisted profile stats.
 */
object ConsistencyStreakCalculator {

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Converts a epoch timestamp in milliseconds to a sequential local calendar day number
     * (days since Jan 1, 1970 in local time zone) so consecutive days always differ by exactly 1L.
     */
    fun toLocalDayEpoch(timestampMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val offsetMillis = cal.timeZone.getOffset(cal.timeInMillis).toLong()
        return (cal.timeInMillis + offsetMillis) / MILLIS_PER_DAY
    }

    fun startOfDayMillisForOffset(nowMillis: Long, daysOffset: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, daysOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun compute(
        allReminders: List<ReminderEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        persistedLongestStreak: Int = 0,
        dailyGoalCount: Int = 3
    ): ConsistencyStreakState {
        val completedReminders = allReminders.filter { it.status == ReminderStatus.COMPLETED }
        val todayDayEpoch = toLocalDayEpoch(nowMillis)

        // Group completed reminders by their local day epoch
        val countsByDayEpoch = mutableMapOf<Long, Int>()
        for (item in completedReminders) {
            val dayEpoch = if (item.completedDayEpoch > 0L) {
                item.completedDayEpoch
            } else {
                toLocalDayEpoch(item.completedAtMillis ?: item.createdAtMillis)
            }
            countsByDayEpoch[dayEpoch] = (countsByDayEpoch[dayEpoch] ?: 0) + 1
        }

        val sortedCompletedDays = countsByDayEpoch.keys.sorted()
        val completedTodayCount = countsByDayEpoch[todayDayEpoch] ?: 0
        val isTodaySecured = completedTodayCount > 0

        // Calculate current consecutive day streak:
        // If today already has >= 1 completion, count backwards from today.
        // If today has 0 completions so far, check if yesterday was completed (active streak waiting on today).
        val currentStreak = when {
            todayDayEpoch in countsByDayEpoch -> {
                var streak = 0
                var cursor = todayDayEpoch
                while (cursor in countsByDayEpoch) {
                    streak++
                    cursor -= 1L
                }
                streak
            }
            (todayDayEpoch - 1L) in countsByDayEpoch -> {
                var streak = 0
                var cursor = todayDayEpoch - 1L
                while (cursor in countsByDayEpoch) {
                    streak++
                    cursor -= 1L
                }
                streak
            }
            else -> 0
        }

        // Calculate all-time longest consecutive streak across all sorted day epochs
        var calculatedLongest = 0
        var runningRun = 0
        var prevDay: Long? = null
        for (day in sortedCompletedDays) {
            if (prevDay == null || day == prevDay + 1L) {
                runningRun++
            } else if (day > prevDay + 1L) {
                runningRun = 1
            }
            if (runningRun > calculatedLongest) {
                calculatedLongest = runningRun
            }
            prevDay = day
        }

        val longestStreak = maxOf(calculatedLongest, currentStreak, persistedLongestStreak)
        val isStreakAtRisk = !isTodaySecured && currentStreak > 0

        // Build 7-day timeline ending today (Today - 6 days .. Today)
        val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateNumFormat = SimpleDateFormat("d", Locale.getDefault())

        val last7Days = (-6..0).map { offset ->
            val dayStartMillis = startOfDayMillisForOffset(nowMillis, offset)
            val dayEpoch = toLocalDayEpoch(dayStartMillis)
            val count = countsByDayEpoch[dayEpoch] ?: 0
            val date = Date(dayStartMillis)
            DayStreakNode(
                dayEpoch = dayEpoch,
                dayShortLabel = dayNameFormat.format(date).take(3),
                dateLabel = dateNumFormat.format(date),
                completedCount = count,
                isToday = offset == 0,
                isStreakDay = count > 0
            )
        }

        val title = when {
            currentStreak >= 7 -> "🔥 $currentStreak-Day Consistency Streak!"
            currentStreak >= 2 -> "🔥 $currentStreak-Day Consistency Streak"
            currentStreak == 1 && isTodaySecured -> "🔥 1-Day Streak Started!"
            currentStreak == 1 && !isTodaySecured -> "⏳ 1-Day Streak — Keep it going today!"
            else -> "✨ Start Your Consistency Streak"
        }

        val subtitle = when {
            isStreakAtRisk ->
                "You have a $currentStreak-day streak from yesterday! Complete 1 reminder today to extend it to ${currentStreak + 1} days."
            isTodaySecured && completedTodayCount >= dailyGoalCount ->
                "Daily goal reached ($completedTodayCount/$dailyGoalCount done today) • Best streak: $longestStreak day(s)"
            isTodaySecured ->
                "Today's streak is locked in ($completedTodayCount/$dailyGoalCount daily goal) • Best: $longestStreak day(s)"
            else ->
                "Complete 1 scheduled task today to light up today's knot • Best: $longestStreak day(s)"
        }

        return ConsistencyStreakState(
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            totalActiveDays = countsByDayEpoch.size,
            completedTodayCount = completedTodayCount,
            dailyGoalCount = dailyGoalCount.coerceAtLeast(1),
            isTodayStreakSecured = isTodaySecured,
            isStreakAtRiskToday = isStreakAtRisk,
            last7DaysTimeline = last7Days,
            streakTitle = title,
            streakSubtitle = subtitle
        )
    }
}
