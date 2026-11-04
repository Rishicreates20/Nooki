package com.example.parser

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class SmartReminderParserTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val now = 1_800_000_000_000L

    private class FakeExtractor(
        private val available: Boolean = true,
        private val delayMillis: Long = 0,
        private val result: () -> ParsedReminder?
    ) : TaskExtractor {
        override suspend fun isAvailable() = available
        override suspend fun extract(input: String, nowMillis: Long, timeZone: TimeZone): ParsedReminder? {
            if (delayMillis > 0) delay(delayMillis)
            return result()
        }
    }

    private val regexAnswer get() = ReminderTextParser.parse("stretch in 5 min", now, zone)

    @Test
    fun `uses the model when it answers`() = runBlocking {
        val fromModel = ParsedReminder("Model title", now + 1)
        val parser = SmartReminderParser(FakeExtractor { fromModel })
        assertEquals(fromModel, parser.parse("stretch in 5 min", now, zone))
    }

    @Test
    fun `falls back when the model throws`() = runBlocking {
        val parser = SmartReminderParser(FakeExtractor { throw IllegalStateException("AICore not ready") })
        assertEquals(regexAnswer, parser.parse("stretch in 5 min", now, zone))
    }

    @Test
    fun `falls back when the model is unavailable, slow or empty`() = runBlocking {
        val unavailable = SmartReminderParser(FakeExtractor(available = false) { ParsedReminder("x", now) })
        assertEquals(regexAnswer, unavailable.parse("stretch in 5 min", now, zone))

        val slow = SmartReminderParser(FakeExtractor(delayMillis = 1_000) { ParsedReminder("x", now) }, 50)
        assertEquals(regexAnswer, slow.parse("stretch in 5 min", now, zone))

        val blank = SmartReminderParser(FakeExtractor { ParsedReminder(" ", now) })
        assertEquals(regexAnswer, blank.parse("stretch in 5 min", now, zone))
    }

    @Test
    fun `works with no model at all`() = runBlocking {
        assertEquals(regexAnswer, SmartReminderParser().parse("stretch in 5 min", now, zone))
    }
}
