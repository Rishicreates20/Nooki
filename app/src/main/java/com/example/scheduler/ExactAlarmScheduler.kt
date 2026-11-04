package com.example.scheduler

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result returned when scheduling a reminder with [AlarmScheduler].
 */
sealed class ScheduleResult {
    data class ExactScheduled(
        val reminderId: Long,
        val triggerAtMillis: Long,
        val mode: String = "setAlarmClock / setExactAndAllowWhileIdle"
    ) : ScheduleResult()

    /** Normal reminders: inexact by design, so the phone can batch wake-ups. */
    data class InexactScheduled(
        val reminderId: Long,
        val triggerAtMillis: Long
    ) : ScheduleResult()

    /** A critical reminder that had to be scheduled inexactly because exact alarms are not allowed. */
    data class InexactFallbackScheduled(
        val reminderId: Long,
        val triggerAtMillis: Long,
        val reason: String = "Exact alarm permission not granted; used setAndAllowWhileIdle"
    ) : ScheduleResult()

    data class Failed(
        val reminderId: Long,
        val errorMessage: String
    ) : ScheduleResult()
}

/**
 * Diagnostic log entry emitted by [AlarmScheduler] and BroadcastReceivers
 * so developers/users can inspect real-time AlarmManager & BootReceiver activity.
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

typealias ExactAlarmScheduler = AlarmScheduler
typealias AndroidExactAlarmScheduler = AndroidAlarmScheduler
