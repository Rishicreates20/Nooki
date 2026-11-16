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
