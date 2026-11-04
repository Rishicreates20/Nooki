package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.core.model.RecurrenceRule
import com.example.core.model.ReminderKind
import com.example.data.local.entity.MoneyDirectionColumn
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderStatus

/**
 * Room TypeConverters for persisting enum types cleanly as Strings in SQLite.
 */
class ReminderTypeConverters {

    @TypeConverter
    fun fromPriority(priority: ReminderPriority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): ReminderPriority =
        runCatching { ReminderPriority.valueOf(value) }.getOrDefault(ReminderPriority.MEDIUM)

    @TypeConverter
    fun fromCategory(category: ReminderCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): ReminderCategory =
        runCatching { ReminderCategory.valueOf(value) }.getOrDefault(ReminderCategory.DESK)

    @TypeConverter
    fun fromStatus(status: ReminderStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ReminderStatus =
        runCatching { ReminderStatus.valueOf(value) }.getOrDefault(ReminderStatus.SCHEDULED)

    @TypeConverter
    fun fromKind(kind: ReminderKind): String = kind.name

    @TypeConverter
    fun toKind(value: String): ReminderKind = ReminderKind.fromName(value)

    @TypeConverter
    fun fromRecurrence(rule: RecurrenceRule?): String? = rule?.name

    @TypeConverter
    fun toRecurrence(value: String?): RecurrenceRule? = RecurrenceRule.fromName(value)

    @TypeConverter
    fun fromMoneyDirection(direction: MoneyDirectionColumn): String = direction.name

    @TypeConverter
    fun toMoneyDirection(value: String): MoneyDirectionColumn =
        runCatching { MoneyDirectionColumn.valueOf(value) }.getOrDefault(MoneyDirectionColumn.THEY_OWE_ME)

    @TypeConverter
    fun fromNoticedType(type: NoticedType): String = type.name

    @TypeConverter
    fun toNoticedType(value: String): NoticedType =
        runCatching { NoticedType.valueOf(value) }.getOrDefault(NoticedType.UNREAD_MESSAGE)

    @TypeConverter
    fun fromNoticedStatus(status: NoticedStatus): String = status.name

    @TypeConverter
    fun toNoticedStatus(value: String): NoticedStatus =
        runCatching { NoticedStatus.valueOf(value) }.getOrDefault(NoticedStatus.SUGGESTED)
}
