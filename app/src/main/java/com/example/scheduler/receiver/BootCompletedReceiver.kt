package com.example.scheduler.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NookiGraph
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores every pending alarm and the daily briefs when the device finishes booting, when the app
 * is updated, when the time or time zone changes, or when exact alarm permission changes.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = NookiGraph.get(appContext)
                val report = graph.reminders.reconcileAndRescheduleAlarms()
                graph.rhythm.arm(graph.settings.current())
                WidgetRefresher.refresh(appContext)
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
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
