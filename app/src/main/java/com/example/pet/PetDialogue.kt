package com.example.pet

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A tappable chip inside Nooki's speech, e.g. `[ACTION:MARK_DONE:Done]`. */
data class PetAction(val id: String, val label: String) {
    companion object {
        const val MARK_DONE = "MARK_DONE"
        const val SNOOZE_10M = "SNOOZE_10M"
    }
}

/**
 * Splits Nooki's (possibly half-streamed) speech into display text and action chips (PRD FR-2.4).
 * An unfinished tag at the end of a partial stream ("...[ACTION:MARK_D") is hidden instead of
 * flashing on screen.
 */
object PetActionParser {
    private val ACTION = Regex("\\[ACTION:([A-Z_0-9]+):([^\\]]+)]")

    data class Result(val cleanText: String, val actions: List<PetAction>)

    fun parse(raw: String): Result {
        val actions = ACTION.findAll(raw).map { PetAction(it.groupValues[1], it.groupValues[2].trim()) }.toList()
        var text = ACTION.replace(raw, "")
        val bracket = text.lastIndexOf('[')
        if (bracket != -1 && !text.substring(bracket).contains(']')) text = text.substring(0, bracket)
        return Result(text.replace(Regex("\\s{2,}"), " ").trim(), actions)
    }
}

/** What Nooki is reacting to right now. */
sealed interface PetTopic {
    val mood: PetMood

    /** Something ringing, overdue or due soon that Nooki wants handled. */
    data class Focus(override val mood: PetMood, val title: String, val minutesUntil: Long) : PetTopic

    /** The user just added a reminder. */
    data class Added(override val mood: PetMood, val title: String, val whenText: String) : PetTopic

    /** The user just finished something. */
    data class Completed(override val mood: PetMood, val title: String) : PetTopic

    /** Nothing specific: just a line that fits the mood. */
    data class Idle(override val mood: PetMood, val activeCount: Int) : PetTopic
}

/**
 * Produces Nooki's lines as a token stream (PRD FR-2.3). The local implementation is phrase-based so
 * it works on every phone with no network; an on-device LLM can implement the same interface later.
 */
interface PetVoice {
    fun stream(topic: PetTopic): Flow<String>
}

class LocalPetVoice(
    private val tokenDelayMillis: Long = 45L,
    private val pick: (List<String>) -> String = { it.random() }
) : PetVoice {

    override fun stream(topic: PetTopic): Flow<String> = flow {
        for (token in tokenize(compose(topic))) {
            emit(token)
            delay(tokenDelayMillis)
        }
    }

    /** The full line, including action tags. Exposed for tests. */
    fun compose(topic: PetTopic): String = when (topic) {
        is PetTopic.Focus -> {
            val t = quote(topic.title)
            val line = when {
                topic.minutesUntil < -1 -> pick(
                    listOf(
                        "Psst... $t slipped past ${-topic.minutesUntil} min ago. Shall we sort it out?",
                        "$t is waiting on us. One tap and it's off my desk!",
                        "I've been guarding $t for a while now. Done, or later?"
                    )
                )
                topic.minutesUntil <= 1 -> pick(
                    listOf(
                        "It's time! $t, right now.",
                        "Ding ding! $t is up.",
                        "Hey hey, $t is happening now!"
                    )
                )
                else -> pick(
                    listOf(
                        "Heads up: $t in ${topic.minutesUntil} min.",
                        "$t is coming up in ${topic.minutesUntil} min. I'm watching the clock!"
                    )
                )
            }
            "$line [ACTION:${PetAction.MARK_DONE}:Done] [ACTION:${PetAction.SNOOZE_10M}:Snooze 10m]"
        }
        is PetTopic.Added -> pick(
            listOf(
                "Got it! I'll nudge you about ${quote(topic.title)} ${topic.whenText}.",
                "Noted on my desk: ${quote(topic.title)}, ${topic.whenText}.",
                "Leave it to me. ${quote(topic.title)} ${topic.whenText}!"
            )
        )
        is PetTopic.Completed -> pick(
            listOf(
                "Yay! ${quote(topic.title)} is done. You're on a roll!",
                "Sparkly! One less thing: ${quote(topic.title)}.",
                "Nice work! I'm doing a little happy dance."
            )
        )
        is PetTopic.Idle -> when (topic.mood) {
            PetMood.SLEEPING -> pick(
                listOf(
                    "Zzz... nothing due soon. Type a reminder and I'll wake up for it.",
                    "*yawn* All quiet. Tell me what to remember."
                )
            )
            PetMood.HAPPY -> pick(
                listOf(
                    "Everything's on track. I'm keeping an eye on ${topic.activeCount} reminder${plural(topic.activeCount)}.",
                    "Feeling good! ${topic.activeCount} thing${plural(topic.activeCount)} on my desk, all under control."
                )
            )
            PetMood.ALERT -> "Something needs you soon. Let's check the list!"
            PetMood.OVERWHELMED -> "Eep, things are piling up! Let's clear one together."
        }
    }

    private fun quote(title: String) = "“${title.trim()}”"

    private fun plural(n: Int) = if (n == 1) "" else "s"

    companion object {
        /** Words keep their trailing space; an action tag is one token so it never streams half-way. */
        fun tokenize(line: String): List<String> =
            Regex("\\[ACTION:[^\\]]*]\\s*|\\S+\\s*").findAll(line).map { it.value }.toList()
    }
}
