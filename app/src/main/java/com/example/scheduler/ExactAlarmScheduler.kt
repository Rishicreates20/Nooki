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
import com.example.data.local.entity.ReminderPriority
import com.example.scheduler.receiver.ReminderAlarmReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result returned when scheduling a reminder with [ExactAlarmScheduler].
 */
sealed class ScheduleResult {
    data class ExactScheduled(
        val reminderId: Long,
        val triggerAtMillis: Long,
        val mode: String = "Exact alarm (Medicine / Critical)"
    ) : ScheduleResult()

    data class InexactFallbackScheduled(
        val reminderId: Long,
        val triggerAtMillis: Long,
        val reason: String = "Batch-friendly alarm (setAndAllowWhileIdle)"
    ) : ScheduleResult()

    data class Failed(
        val reminderId: Long,
        val errorMessage: String
    ) : ScheduleResult()
}

/**
 * Diagnostic log entry emitted by [ExactAlarmScheduler] and BroadcastReceivers.
 */
data class SchedulerDiagnosticEntry(
    val id: Long = System.nanoTime(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: String,
    val message: String,
    val isWarning: Boolean = false
) {
    fun formattedTime(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMillis))
}

/**
 * Shared in-memory diagnostic bus for Scheduler & BroadcastReceiver telemetry.
 */
object NookiSchedulerTelemetry {
    private val _logs = MutableStateFlow<List<SchedulerDiagnosticEntry>>(emptyList())
    val logs: StateFlow<List<SchedulerDiagnosticEntry>> = _logs.asStateFlow()

    fun record(source: String, message: String, isWarning: Boolean = false) {
        val entry = SchedulerDiagnosticEntry(
            source = source,
            message = message,
            isWarning = isWarning
        )
        _logs.update { current -> (listOf(entry) + current).take(60) }
    }
}

/**
 * Contract for scheduling and cancelling OS-level alarms for Nooki reminders.
 */
interface ExactAlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun scheduleExact(reminder: ReminderEntity): ScheduleResult
    fun cancel(reminderId: Long)
    fun buildExactAlarmPermissionIntent(): Intent?
}

/**
 * Production Android [AlarmManager] implementation following the Nooki 10/10 Reliability Rule:
 * "Exact alarms are used only when a late alarm causes harm, like medicine or something marked critical.
 * Everything else uses inexact alarms that the phone can batch. This also means no scary permission on day one."
 */
class AndroidExactAlarmScheduler(
    private val context: Context
) : ExactAlarmScheduler {

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

    override fun scheduleExact(reminder: ReminderEntity): ScheduleResult {
        val manager = alarmManager ?: return ScheduleResult.Failed(
            reminderId = reminder.id,
            errorMessage = "AlarmManager service unavailable on this device."
        )

        val pendingIntent = createTriggerPendingIntent(reminder)
        val safeTriggerMillis = reminder.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)

        // Check if this reminder is Medicine or marked Critical (URGENT)
        val requiresExactPrecision = reminder.priority == ReminderPriority.URGENT ||
            reminder.title.contains("medicine", ignoreCase = true) ||
            reminder.title.contains("dawai", ignoreCase = true) ||
            reminder.title.contains("दवाई")

        return try {
            if (requiresExactPrecision && canScheduleExactAlarms()) {
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
                    message = "Scheduled CRITICAL EXACT alarm #${reminder.id} ('${reminder.title}') for ${formatTime(safeTriggerMillis)}"
                )
                ScheduleResult.ExactScheduled(
                    reminderId = reminder.id,
                    triggerAtMillis = safeTriggerMillis
                )
            } else {
                // Standard reminder or exact alarm permission not yet granted: use Doze-aware batch alarm
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    safeTriggerMillis,
                    pendingIntent
                )
                NookiSchedulerTelemetry.record(
                    source = "AlarmScheduler",
                    message = "Scheduled batch-friendly alarm #${reminder.id} ('${reminder.title}') for ${formatTime(safeTriggerMillis)}"
                )
                ScheduleResult.InexactFallbackScheduled(
                    reminderId = reminder.id,
                    triggerAtMillis = safeTriggerMillis,
                    reason = if (requiresExactPrecision) {
                        "Exact alarm permission not granted; used Doze-safe setAndAllowWhileIdle"
                    } else {
                        "Standard reminder scheduled with battery-friendly setAndAllowWhileIdle"
                    }
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
                message = "Fell back to setAndAllowWhileIdle for #${reminder.id}: ${se.message}",
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
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
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
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
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
