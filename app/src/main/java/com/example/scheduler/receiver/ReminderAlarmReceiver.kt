package com.example.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NookiGraph
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import com.example.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered when an exact or fallback [android.app.AlarmManager] alarm fires.
 * Uses [goAsync] to safely update Room and post the notification.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId <= 0L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = NookiGraph.get(appContext).reminders
                val now = System.currentTimeMillis()

                if (intent?.action == AndroidExactAlarmScheduler.ACTION_LEAD_ALERT) {
                    val reminder = repository.getReminderById(reminderId)
                    if (reminder != null && reminder.isActive && reminder.triggerAtMillis > now) {
                        NookiSchedulerTelemetry.record(
                            source = "ReminderAlarmReceiver",
                            message = "Heads-up for #$reminderId ('${reminder.title}')"
                        )
                        NotificationHelper.showLeadAlertNotification(appContext, reminder, now)
                        repository.scheduleNextLeadAlert(reminder, now + 1_000L)
                    }
                    return@launch
                }

                val ringing = repository.onAlarmFired(reminderId, now)
                if (ringing != null) {
                    NookiSchedulerTelemetry.record(
                        source = "ReminderAlarmReceiver",
                        message = "Alarm fired for #$reminderId ('${ringing.title}') -> status = RINGING, ask ${ringing.askCount}"
                    )
                    NotificationHelper.showReminderAlarmNotification(appContext, ringing)
                } else {
                    NookiSchedulerTelemetry.record(
                        source = "ReminderAlarmReceiver",
                        message = "Alarm fired for #$reminderId, but it no longer needs to ring."
                    )
                }
                WidgetRefresher.refresh(appContext)
            } catch (t: Throwable) {
                NookiSchedulerTelemetry.record(
                    source = "ReminderAlarmReceiver",
                    message = "Error handling alarm #$reminderId: ${t.message}",
                    isWarning = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
