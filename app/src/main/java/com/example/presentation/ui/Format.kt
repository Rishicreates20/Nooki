package com.example.presentation.ui

import com.example.attention.ForgotReason
import com.example.attention.TimeWords
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.parser.ParsedReminder
import com.example.workflow.WorkflowSchedule

/** Words shown on screen, kept in one place so an action never changes its name between screens. */
object Words {

    fun repeatLabel(rule: RecurrenceRule?, intervalMinutes: Int?): String? = when {
        rule != null -> when (rule) {
            RecurrenceRule.DAILY -> "Every day"
            RecurrenceRule.WEEKDAYS -> "Every weekday"
            RecurrenceRule.WEEKLY -> "Every week"
            RecurrenceRule.MONTHLY -> "Every month"
            RecurrenceRule.YEARLY -> "Every year"
        }
        intervalMinutes != null && intervalMinutes > 0 -> "Every ${WorkflowSchedule.describeLead(intervalMinutes)}"
        else -> null
    }

    fun repeatLabel(reminder: ReminderEntity): String? = repeatLabel(reminder.recurrenceRule, reminder.recurrenceMinutes)

    fun kindWord(kind: ReminderKind): String? = when (kind) {
        ReminderKind.TASK, ReminderKind.HABIT -> null
        ReminderKind.AI_LIMIT -> "AI limit"
        ReminderKind.BILL -> "Bill"
        else -> kind.label
    }

    fun missedMeta(reminder: ReminderEntity, nowMillis: Long): String =
        listOfNotNull(kindWord(reminder.kind), "was due ${TimeWords.ago(reminder.triggerAtMillis, nowMillis)}")
            .joinToString(", ")
            .replaceFirstChar { it.uppercase() }

    fun forgotMeta(reminder: ReminderEntity, reason: ForgotReason?, nowMillis: Long): String = when (reason) {
        ForgotReason.KEEPS_SLIPPING -> "Put off ${reminder.snoozeCount} times"
        ForgotReason.NO_TIME_SET -> "No time set, added ${TimeWords.ago(reminder.createdAtMillis, nowMillis)}"
        null -> TimeWords.ago(reminder.createdAtMillis, nowMillis)
    }

    fun toDoMeta(reminder: ReminderEntity): String? =
        if (reminder.undated) "Any time today" else listOfNotNull(kindWord(reminder.kind), repeatLabel(reminder)).joinToString(", ").ifBlank { null }

    fun comingUpMeta(reminder: ReminderEntity, nowMillis: Long): String =
        listOfNotNull(kindWord(reminder.kind), TimeWords.dayGap(reminder.triggerAtMillis, nowMillis))
            .joinToString(", ")
            .replaceFirstChar { it.uppercase() }

    fun noticedTitle(item: NoticedEntity): String =
        if (item.type == NoticedType.MISSED_CALL && item.times > 1) {
            "${item.person ?: "Someone"} called ${item.times} times"
        } else {
            item.title
        }

    fun noticedMeta(item: NoticedEntity, nowMillis: Long): String {
        val age = TimeWords.ago(item.noticedAtMillis, nowMillis)
        return when (item.type) {
            NoticedType.MISSED_CALL -> "Missed, $age"
            NoticedType.UNREAD_MESSAGE -> "${item.appLabel}, $age"
            NoticedType.BILL, NoticedType.EVENT -> listOf(item.detail, "from ${item.appLabel}").filter { it.isNotBlank() }.joinToString(", ")
                .replaceFirstChar { it.uppercase() }
            NoticedType.DELIVERY -> "From ${item.appLabel}"
        }
    }

    /** What Nooki understood, as sentences for the capture sheet. */
    fun understood(parsed: ParsedReminder, nowMillis: Long): List<String> = buildList {
        parsed.triggerAtMillis?.let { at ->
            add(
                if (at - nowMillis in 0 until 60 * 60_000L) {
                    "In ${(at - nowMillis + 59_999) / 60_000} min"
                } else {
                    TimeWords.moment(at, nowMillis).let { moment ->
                        if (moment.first().isDigit()) "Today at $moment" else moment
                    }
                }
            )
        }
        repeatLabel(parsed.recurrenceRule, parsed.recurrenceMinutes)?.let { add(it) }
        val leads = parsed.triggerAtMillis?.let { WorkflowSchedule.upcomingLeads(it, parsed.leadMinutes, nowMillis) }
            ?: parsed.leadMinutes
        if (leads.isNotEmpty()) {
            add("Warns you ${leads.joinToString(", ") { WorkflowSchedule.describeLead(it) }} before")
        }
        if (parsed.keepAsking) add("Keeps asking until you confirm")
    }
}
