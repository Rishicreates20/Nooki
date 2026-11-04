package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.data.local.entity.LooseEndDirection
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.data.local.entity.RecurrenceRule
import com.example.data.local.entity.ReminderCategory
import com.example.data.local.entity.ReminderKind
import com.example.data.local.entity.ReminderPriority
import com.example.data.local.entity.ReminderSource
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
    fun fromRecurrenceRule(rule: RecurrenceRule?): String? = rule?.name

    @TypeConverter
    fun toRecurrenceRule(value: String?): RecurrenceRule? = RecurrenceRule.fromName(value)

    @TypeConverter
    fun fromSource(source: ReminderSource): String = source.name

    @TypeConverter
    fun toSource(value: String): ReminderSource = ReminderSource.fromName(value)

    @TypeConverter
    fun fromDirection(direction: LooseEndDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): LooseEndDirection =
        runCatching { LooseEndDirection.valueOf(value) }.getOrDefault(LooseEndDirection.THEY_HAVE)

    @TypeConverter
    fun fromNoticedType(type: NoticedType): String = type.name

    @TypeConverter
    fun toNoticedType(value: String): NoticedType =
        runCatching { NoticedType.valueOf(value) }.getOrDefault(NoticedType.EVENT)

    @TypeConverter
    fun fromNoticedStatus(status: NoticedStatus): String = status.name

    @TypeConverter
    fun toNoticedStatus(value: String): NoticedStatus =
        runCatching { NoticedStatus.valueOf(value) }.getOrDefault(NoticedStatus.NEW)
}
