package com.example.data

import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderSource
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.toDomain
import com.example.data.local.entity.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMapperTest {

    @Test
    fun `a reminder survives the trip to the domain model and back`() {
        val entity = ReminderEntity(
            id = 7,
            title = "Take BP tablet",
            notes = "After dinner",
            rawInput = "take bp tablet every day at 9pm",
            triggerAtMillis = 2_000L,
            createdAtMillis = 1_000L,
            status = ReminderStatus.SNOOZED,
            snoozeCount = 2,
            kind = ReminderKind.MEDICINE,
            leadTimes = "1440",
            recurrenceRule = RecurrenceRule.DAILY,
            undated = true,
            keepAsking = true,
            askCount = 1,
            source = ReminderSource.VOICE,
            nextSpawned = true
        )
        assertEquals(entity, entity.toDomain().toEntity())
    }
}
