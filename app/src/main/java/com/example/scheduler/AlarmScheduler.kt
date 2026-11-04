package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.example.MainActivity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.toEntity
import com.example.domain.model.Reminder
import com.example.scheduler.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean Architecture contract for scheduling and cancelling exact OS-level alarms
 * via [AlarmManager] for Nooki reminders.
 */
interface AlarmScheduler : ExactAlarmScheduler {
    override fun canScheduleExactAlarms(): Boolean
    fun schedule(reminder: Reminder): ScheduleResult
    override fun scheduleExact(reminder: ReminderEntity): ScheduleResult
    override fun cancel(reminderId: Long)
    fun cancel(reminder: Reminder) = cancel(reminder.id)
    override fun buildExactAlarmPermissionIntent(): Intent?
}

/**
 * Production Android [AlarmManager] implementation of [AlarmScheduler].
 *
 * - Verifies `AlarmManager.canScheduleExactAlarms()` on Android 12+ (API 31+).
 * - Uses [AlarmManager.setAlarmClock] / [AlarmManager.setExactAndAllowWhileIdle] for
 *   Doze-piercing exact delivery when permitted.
 * - Gracefully falls back to [AlarmManager.setAndAllowWhileIdle] if the user revokes
 *   `SCHEDULE_EXACT_ALARM` at runtime.
 */
class AndroidAlarmScheduler(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun schedule(reminder: Reminder): ScheduleResult =
        scheduleExact(reminder.toEntity())

    override fun scheduleExact(reminder: ReminderEntity): ScheduleResult {
        val manager = alarmManager ?: return ScheduleResult.Failed(
            reminderId = reminder.id,
            errorMessage = "AlarmManager service unavailable on this device."
        )

        val pendingIntent = createTriggerPendingIntent(reminder)
        val safeTriggerMillis =
            reminder.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)

        return try {
            if (canScheduleExactAlarms()) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    reminder.id.toInt(),
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_REMINDER_ID, reminder.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(safeTriggerMillis, showIntent)
                manager.setAlarmClock(clockInfo, pendingIntent)

                NookiSchedulerTelemetry.record(
                    source = "AlarmScheduler",
                    message = "Scheduled EXACT alarm #${reminder.id} ('${reminder.title}') for ${formatTime(safeTriggerMillis)}"
                )
                ScheduleResult.ExactScheduled(
                    reminderId = reminder.id,
                    triggerAtMillis = safeTriggerMillis
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    safeTriggerMillis,
                    pendingIntent
                )
                NookiSchedulerTelemetry.record(
                    source = "AlarmScheduler",
                    message = "Scheduled INEXACT fallback #${reminder.id} ('${reminder.title}') - SCHEDULE_EXACT_ALARM not granted",
                    isWarning = true
                )
                ScheduleResult.InexactFallbackScheduled(
                    reminderId = reminder.id,
                    triggerAtMillis = safeTriggerMillis
                )
            }
        } catch (se: SecurityException) {
            runCatching {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    safeTriggerMillis,
                    pendingIntent
                )
            }
            NookiSchedulerTelemetry.record(
                source = "AlarmScheduler",
                message = "SecurityException on exact alarm #${reminder.id}; fell back to setAndAllowWhileIdle (${se.message})",
                isWarning = true
            )
            ScheduleResult.InexactFallbackScheduled(
                reminderId = reminder.id,
                triggerAtMillis = safeTriggerMillis,
                reason = se.message ?: "SecurityException"
            )
        } catch (t: Throwable) {
            NookiSchedulerTelemetry.record(
                source = "AlarmScheduler",
                message = "Failed to schedule alarm #${reminder.id}: ${t.message}",
                isWarning = true
            )
            ScheduleResult.Failed(
                reminderId = reminder.id,
                errorMessage = t.message ?: "Unknown AlarmManager error"
            )
        }
    }

    override fun cancel(reminderId: Long) {
        val manager = alarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER_ALARM
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        NookiSchedulerTelemetry.record(
            source = "AlarmScheduler",
            message = "Cancelled PendingIntent for reminder #$reminderId"
        )
    }

    override fun buildExactAlarmPermissionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            null
        }
    }

    private fun createTriggerPendingIntent(reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER_ALARM
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_CATEGORY, reminder.category.name)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(millis))

    companion object {
        const val ACTION_TRIGGER_REMINDER_ALARM = "com.example.nooki.ACTION_TRIGGER_REMINDER_ALARM"
        const val ACTION_COMPLETE_REMINDER = "com.example.nooki.ACTION_COMPLETE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.example.nooki.ACTION_SNOOZE_REMINDER"
        const val ACTION_DISMISS_REMINDER = "com.example.nooki.ACTION_DISMISS_REMINDER"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_CATEGORY = "extra_reminder_category"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }
}
