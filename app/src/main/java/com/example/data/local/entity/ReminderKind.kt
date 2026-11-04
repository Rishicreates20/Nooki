package com.example.data.local.entity

/**
 * What a reminder is about in the user's wider workflow. Unlike [ReminderCategory] (which picks
 * Nooki's desk badge), the kind decides the heads-up schedule before the moment and how much the
 * reminder weighs on Nooki's mood.
 *
 * @property defaultLeadMinutes heads-ups sent before the reminder itself, largest first.
 * @property overdueWeight how much a missed one counts toward Overwhelmed. 0 means it never stresses
 *   Nooki (an AI limit coming back is good news, not a chore).
 * @property awakeWindowMillis how far ahead an upcoming one keeps Nooki awake instead of napping.
 * @property alertWindowMillis how far ahead an upcoming one makes Nooki Alert.
 */
enum class ReminderKind(
    val label: String,
    val badgeText: String,
    val defaultLeadMinutes: List<Int>,
    val overdueWeight: Int,
    val awakeWindowMillis: Long,
    val alertWindowMillis: Long
) {
    TASK("Task", "TASK", emptyList(), 1, 60 * MINUTE, 5 * MINUTE),
    EXAM("Exam", "EXAM", listOf(7 * DAY_MIN, DAY_MIN, 2 * 60), 2, 24 * HOUR, 3 * HOUR),
    DEADLINE("Deadline", "DUE", listOf(3 * DAY_MIN, DAY_MIN, 3 * 60), 2, 24 * HOUR, 3 * HOUR),
    AI_LIMIT("AI limit reset", "AI", emptyList(), 0, 60 * MINUTE, 0L),
    BILL("Bill or renewal", "BILL", listOf(3 * DAY_MIN, DAY_MIN), 2, 24 * HOUR, 60 * MINUTE),
    HABIT("Habit", "HABIT", emptyList(), 1, 60 * MINUTE, 5 * MINUTE);

    companion object {
        fun fromName(value: String?): ReminderKind =
            entries.firstOrNull { it.name == value } ?: TASK
    }
}

/** Calendar-anchored repeats. The next one keeps the same clock time and date pattern. */
enum class RecurrenceRule(val label: String) {
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year");

    companion object {
        fun fromName(value: String?): RecurrenceRule? = entries.firstOrNull { it.name == value }
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY_MIN = 24 * 60
