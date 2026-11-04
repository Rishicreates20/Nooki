package com.example.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPolicyTest {

    @Test
    fun `everyday reminders are inexact even when exact alarms are allowed`() {
        assertEquals(
            AlarmPrecision.INEXACT,
            AlarmPolicy.precisionFor(isCritical = false, isMedicine = false, exactAllowed = true),
        )
        assertEquals(
            AlarmPrecision.INEXACT,
            AlarmPolicy.precisionFor(isCritical = false, isMedicine = false, exactAllowed = false),
        )
    }

    @Test
    fun `critical and medicine reminders are exact when allowed`() {
        assertEquals(AlarmPrecision.EXACT, AlarmPolicy.precisionFor(true, false, exactAllowed = true))
        assertEquals(AlarmPrecision.EXACT, AlarmPolicy.precisionFor(false, true, exactAllowed = true))
    }

    @Test
    fun `critical reminders still get scheduled when exact alarms are denied`() {
        assertEquals(
            AlarmPrecision.INEXACT_UNTIL_ALLOWED,
            AlarmPolicy.precisionFor(isCritical = true, isMedicine = false, exactAllowed = false),
        )
    }

    @Test
    fun `wantsExact only for harm-if-late reminders`() {
        assertTrue(AlarmPolicy.wantsExact(isCritical = true, isMedicine = false))
        assertTrue(AlarmPolicy.wantsExact(isCritical = false, isMedicine = true))
        assertFalse(AlarmPolicy.wantsExact(isCritical = false, isMedicine = false))
    }
}
