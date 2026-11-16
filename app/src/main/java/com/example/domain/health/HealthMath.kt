package com.example.domain.health

/**
 * Daily goals for the Health section.
 */
object HealthGoals {
    const val STEPS = 6_000
    const val WATER_GLASSES = 8
    const val SLEEP_MINUTES = 8 * 60
    const val MAX_WATER_GLASSES = 30
    const val MAX_SLEEP_MINUTES = 16 * 60
}

/**
 * Turns readings from the phone's cumulative step counter (steps since the phone booted) into steps
 * for today. Plain Kotlin so it can be unit-tested.
 */
object StepTally {

    data class Result(val steps: Int, val lastCounterValue: Long)

    /**
     * @param todaySteps steps already recorded for today.
     * @param lastCounterValue the counter value Nooki saw last time, or -1 if it has never seen one.
     * @param reading the new cumulative counter value.
     */
    fun apply(todaySteps: Int, lastCounterValue: Long, reading: Long): Result {
        if (reading < 0) return Result(todaySteps, lastCounterValue)
        return when {
            // First reading ever: start counting from here, we can't know what happened before.
            lastCounterValue < 0 -> Result(todaySteps, reading)
            // Normal case: the counter only goes up between reboots.
            reading >= lastCounterValue -> Result(addCapped(todaySteps, reading - lastCounterValue), reading)
            // The phone rebooted and the counter restarted from zero.
            else -> Result(addCapped(todaySteps, reading), reading)
        }
    }

    private fun addCapped(steps: Int, delta: Long): Int =
        (steps.toLong() + delta).coerceIn(0L, 200_000L).toInt()
}

/**
 * Small formatting helpers shared by the Health and Home screens.
 */
object HealthFormat {
    fun progress(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else (value.toFloat() / goal).coerceIn(0f, 1f)

    fun sleep(minutes: Int): String {
        if (minutes <= 0) return "Not logged"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    fun steps(steps: Int): String =
        if (steps >= 10_000) "%.1fk".format(steps / 1000f) else "%,d".format(steps)
}
