package com.example.pet

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PetMoodResolverTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun reminder(
        offsetMinutes: Long,
        status: ReminderStatus = ReminderStatus.SCHEDULED,
        priority: ReminderPriority = ReminderPriority.MEDIUM,
        completedAt: Long? = null
    ) = ReminderEntity(
        title = "t",
        triggerAtMillis = now + offsetMinutes * minute,
        status = status,
        priority = priority,
        completedAtMillis = completedAt
    )

    @Test
    fun `empty desk sleeps`() {
        assertEquals(PetMood.SLEEPING, PetMoodResolver.resolve(emptyList(), now))
    }

    @Test
    fun `far away reminder still sleeps`() {
        assertEquals(PetMood.SLEEPING, PetMoodResolver.resolve(listOf(reminder(180)), now))
    }

    @Test
    fun `reminder within the hour is happy`() {
        assertEquals(PetMood.HAPPY, PetMoodResolver.resolve(listOf(reminder(30)), now))
    }

    @Test
    fun `recent completion is happy`() {
        val done = reminder(-20, ReminderStatus.COMPLETED, completedAt = now - 3 * minute)
        assertEquals(PetMood.HAPPY, PetMoodResolver.resolve(listOf(done), now))
    }

    @Test
    fun `ringing or due within five minutes is alert`() {
        assertEquals(PetMood.ALERT, PetMoodResolver.resolve(listOf(reminder(0, ReminderStatus.RINGING)), now))
        assertEquals(PetMood.ALERT, PetMoodResolver.resolve(listOf(reminder(3)), now))
    }

    @Test
    fun `single overdue is alert`() {
        assertEquals(PetMood.ALERT, PetMoodResolver.resolve(listOf(reminder(-10)), now))
    }

    @Test
    fun `pile of overdue tasks is overwhelmed`() {
        val pile = listOf(reminder(-30), reminder(-20), reminder(-10))
        assertEquals(PetMood.OVERWHELMED, PetMoodResolver.resolve(pile, now))
        val urgentPair = listOf(reminder(-30, priority = ReminderPriority.URGENT), reminder(-10))
        assertEquals(PetMood.OVERWHELMED, PetMoodResolver.resolve(urgentPair, now))
    }
}
