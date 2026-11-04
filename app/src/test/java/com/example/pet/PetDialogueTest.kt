package com.example.pet

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetDialogueTest {

    @Test
    fun `parses actions and strips tags`() {
        val r = PetActionParser.parse("Time to stretch! [ACTION:MARK_DONE:Done] [ACTION:SNOOZE_10M:Snooze 10m]")
        assertEquals("Time to stretch!", r.cleanText)
        assertEquals(
            listOf(PetAction("MARK_DONE", "Done"), PetAction("SNOOZE_10M", "Snooze 10m")),
            r.actions
        )
    }

    @Test
    fun `hides a half streamed tag`() {
        val r = PetActionParser.parse("Time to stretch! [ACTION:MARK_D")
        assertEquals("Time to stretch!", r.cleanText)
        assertTrue(r.actions.isEmpty())
    }

    @Test
    fun `stream reassembles into the full line with whole tags`() = runBlocking {
        val voice = LocalPetVoice(tokenDelayMillis = 0) { it.first() }
        val topic = PetTopic.Focus(PetMood.ALERT, "Drink water", minutesUntil = 0)
        val tokens = voice.stream(topic).toList()
        assertEquals(voice.compose(topic), tokens.joinToString(""))
        assertTrue(tokens.filter { it.contains("[") }.all { it.trim().startsWith("[ACTION:") && it.trim().endsWith("]") })

        val parsed = PetActionParser.parse(tokens.joinToString(""))
        assertEquals("It's time! “Drink water”, right now.", parsed.cleanText)
        assertEquals(listOf(PetAction.MARK_DONE, PetAction.SNOOZE_10M), parsed.actions.map { it.id })
    }

    @Test
    fun `non focus lines carry no actions`() {
        val voice = LocalPetVoice(tokenDelayMillis = 0) { it.first() }
        val added = voice.compose(PetTopic.Added(PetMood.HAPPY, "Call mom", "tomorrow at 6:00 PM"))
        assertEquals("Got it! I'll nudge you about “Call mom” tomorrow at 6:00 PM.", added)
        assertTrue(PetActionParser.parse(added).actions.isEmpty())
    }
}
