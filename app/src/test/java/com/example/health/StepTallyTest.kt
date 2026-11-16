package com.example.health

import com.example.domain.health.HealthFormat
import com.example.domain.health.StepTally
import org.junit.Assert.assertEquals
import org.junit.Test

class StepTallyTest {

    @Test
    fun `first reading starts counting without inventing steps`() {
        val r = StepTally.apply(todaySteps = 0, lastCounterValue = -1, reading = 52_000)
        assertEquals(0, r.steps)
        assertEquals(52_000L, r.lastCounterValue)
    }

    @Test
    fun `later readings add the difference`() {
        val r = StepTally.apply(todaySteps = 1_200, lastCounterValue = 52_000, reading = 52_850)
        assertEquals(2_050, r.steps)
        assertEquals(52_850L, r.lastCounterValue)
    }

    @Test
    fun `reboot resets the counter and the new count is added`() {
        val r = StepTally.apply(todaySteps = 3_000, lastCounterValue = 52_850, reading = 400)
        assertEquals(3_400, r.steps)
        assertEquals(400L, r.lastCounterValue)
    }

    @Test
    fun `manual steps survive sensor readings`() {
        // 500 added by hand, then the sensor moves 100 steps.
        val r = StepTally.apply(todaySteps = 500, lastCounterValue = 1_000, reading = 1_100)
        assertEquals(600, r.steps)
    }

    @Test
    fun `negative readings are ignored`() {
        val r = StepTally.apply(todaySteps = 10, lastCounterValue = 5, reading = -1)
        assertEquals(10, r.steps)
        assertEquals(5L, r.lastCounterValue)
    }

    @Test
    fun `formatting helpers`() {
        assertEquals(0.5f, HealthFormat.progress(3_000, 6_000), 0.0001f)
        assertEquals(1f, HealthFormat.progress(9_000, 6_000), 0.0001f)
        assertEquals("7h 30m", HealthFormat.sleep(450))
        assertEquals("8h", HealthFormat.sleep(480))
        assertEquals("Not logged", HealthFormat.sleep(0))
    }
}

class WorkoutMathTest {

    @Test
    fun `calories use MET times weight times hours`() {
        // Run: 9.8 MET x 65 kg x 0.5 h = 318.5 -> 319 (rounded half up)
        org.junit.Assert.assertEquals(
            319,
            com.example.domain.health.WorkoutMath.estimateCalories(com.example.domain.health.WorkoutType.RUN, 30)
        )
        org.junit.Assert.assertEquals(
            0,
            com.example.domain.health.WorkoutMath.estimateCalories(com.example.domain.health.WorkoutType.WALK, 0)
        )
    }

    @Test
    fun `elapsed minutes round and stay in range`() {
        val m = com.example.domain.health.WorkoutMath
        org.junit.Assert.assertEquals(1, m.elapsedMinutes(0L, 10_000L))
        org.junit.Assert.assertEquals(25, m.elapsedMinutes(0L, 25 * 60_000L + 20_000L))
        org.junit.Assert.assertEquals(360, m.elapsedMinutes(0L, 10 * 3_600_000L))
    }

    @Test
    fun `timer text`() {
        val m = com.example.domain.health.WorkoutMath
        org.junit.Assert.assertEquals("00:05", m.formatTimer(5_000L))
        org.junit.Assert.assertEquals("12:34", m.formatTimer((12 * 60 + 34) * 1000L))
        org.junit.Assert.assertEquals("1:02:03", m.formatTimer((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun `unknown workout names fall back to Other`() {
        org.junit.Assert.assertEquals(
            com.example.domain.health.WorkoutType.OTHER,
            com.example.domain.health.WorkoutType.fromName("PARKOUR")
        )
    }
}
