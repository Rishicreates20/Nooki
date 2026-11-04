package com.example.core.model

/**
 * What a reminder is about. The kind decides the warnings Nooki sets up ahead of the day (so people
 * never do this maths), whether it repeats by itself, and how much a missed one weighs on Nooki.
 *
 * Plain Kotlin with no Android imports.
 *
 * @property defaultLeadMinutes warnings before the day itself, largest first.
 * @property repeatsYearly yearly things repeat without being asked (birthdays).
 * @property needsConfirmation keeps asking until the person confirms it, up to a limit (medicine).
 * @property exactAlarm a late alarm causes harm, so it may use an exact alarm.
 * @property overdueWeight how much a missed one counts toward Nooki getting flustered. 0 = never.
 */
enum class ReminderKind(
    val label: String,
    val defaultLeadMinutes: List<Int>,
    val repeatsYearly: Boolean = false,
    val needsConfirmation: Boolean = false,
    val exactAlarm: Boolean = false,
    val overdueWeight: Int = 1,
) {
    TASK("Task", emptyList()),
    MEDICINE("Medicine", emptyList(), needsConfirmation = true, exactAlarm = true, overdueWeight = 3),
    BILL("Bill", listOf(3 * DAY_MIN, DAY_MIN), overdueWeight = 2),
    BIRTHDAY("Birthday", listOf(7 * DAY_MIN, DAY_MIN), repeatsYearly = true),
    EXPIRY("Expiry", listOf(30 * DAY_MIN, 7 * DAY_MIN, DAY_MIN), overdueWeight = 2),
    EXAM("Exam", listOf(7 * DAY_MIN, DAY_MIN, 2 * 60), overdueWeight = 2),
    DEADLINE("Deadline", listOf(3 * DAY_MIN, DAY_MIN, 3 * 60), overdueWeight = 2),
    AI_LIMIT("AI limit reset", emptyList(), overdueWeight = 0),
    HABIT("Habit", emptyList());

    companion object {
        fun fromName(value: String?): ReminderKind = entries.firstOrNull { it.name == value } ?: TASK
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

private const val DAY_MIN = 24 * 60
