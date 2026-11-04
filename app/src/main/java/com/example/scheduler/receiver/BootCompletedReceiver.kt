package com.example.scheduler.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.database.NookiDatabase
import com.example.data.repository.ReminderRepositoryImpl
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that restores all pending exact alarms when the device finishes booting,
 * when the app package is updated, when system time/timezone is changed, or when
 * [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED] is broadcast.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = NookiDatabase.getInstance(appContext)
                val scheduler = AndroidExactAlarmScheduler(appContext)
                val repository = ReminderRepositoryImpl(database.reminderDao(), scheduler)

                val report = repository.reconcileAndRescheduleAlarms()
                com.example.rhythm.DailyRhythm.schedule(appContext)
                com.example.widget.NookiWidget.refresh(appContext)
                NookiSchedulerTelemetry.record(
                    source = "BootCompletedReceiver",
                    message = "Processed '$action': rescheduled=${report.rescheduledCount}, markedMissed=${report.markedMissedCount}, exactPermitted=${report.exactAlarmsPermitted}"
                )
            } catch (t: Throwable) {
                NookiSchedulerTelemetry.record(
                    source = "BootCompletedReceiver",
                    message = "Boot recovery error on '$action': ${t.message}",
                    isWarning = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
