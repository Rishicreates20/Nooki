package com.example.domain.health

/**
 * Kinds of workout Nooki can track. [met] is the standard metabolic-equivalent value used to
 * estimate calories (Compendium of Physical Activities, moderate effort).
 */
enum class WorkoutType(val label: String, val emoji: String, val met: Double) {
    WALK("Walk", "🚶", 3.5),
    RUN("Run", "🏃", 9.8),
    CYCLING("Cycling", "🚴", 7.5),
    GYM("Gym / weights", "🏋️", 5.0),
    YOGA("Yoga", "🧘", 2.5),
    SWIM("Swim", "🏊", 7.0),
    SPORTS("Sports", "🏸", 6.5),
    DANCE("Dance", "💃", 5.5),
    HIIT("HIIT", "🔥", 8.0),
    OTHER("Other", "💪", 4.0);

    companion object {
        fun fromName(name: String): WorkoutType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

object FitnessGoals {
    /** WHO recommends at least 150 active minutes a week, about 30 a day on 5 days. */
    const val ACTIVE_MINUTES_PER_DAY = 30
    const val ACTIVE_MINUTES_PER_WEEK = 150
    const val DEFAULT_BODY_WEIGHT_KG = 65.0
    const val MAX_WORKOUT_MINUTES = 6 * 60
}

object WorkoutMath {
    /** Calories ≈ MET × body weight (kg) × hours. */
    fun estimateCalories(
        type: WorkoutType,
        durationMinutes: Int,
        weightKg: Double = FitnessGoals.DEFAULT_BODY_WEIGHT_KG
    ): Int {
        if (durationMinutes <= 0) return 0
        return Math.round(type.met * weightKg * (durationMinutes / 60.0)).toInt()
    }

    /** Whole minutes between two instants, rounded to the nearest minute, at least 1. */
    fun elapsedMinutes(startMillis: Long, endMillis: Long): Int {
        val minutes = Math.round((endMillis - startMillis).coerceAtLeast(0L) / 60_000.0).toInt()
        return minutes.coerceIn(1, FitnessGoals.MAX_WORKOUT_MINUTES)
    }

    /** "mm:ss" or "h:mm:ss" for the live workout timer. */
    fun formatTimer(elapsedMillis: Long): String {
        val total = (elapsedMillis / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
