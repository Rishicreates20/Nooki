package com.example.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NookiGraph
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import com.example.widget.WidgetRefresher
import com.example.workflow.WorkflowSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver handling notification actions without opening the app. Swiping a notification
 * away arrives here as dismiss, and that moves the reminder to "You missed" instead of losing it.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val reminderId = intent.getLongExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, -1L)
        val moveAll = action == AndroidExactAlarmScheduler.ACTION_MOVE_TO_TOMORROW
        if (reminderId <= 0L && !moveAll) return

        val snoozeMinutes = intent.getIntExtra(AndroidExactAlarmScheduler.EXTRA_SNOOZE_MINUTES, 60)
        val snoozeTomorrow = intent.getBooleanExtra(AndroidExactAlarmScheduler.EXTRA_SNOOZE_TOMORROW, false)
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = NookiGraph.get(appContext).reminders

                when (action) {
                    AndroidExactAlarmScheduler.ACTION_COMPLETE_REMINDER -> {
                        val completed = repository.completeReminder(reminderId)
                        NotificationHelper.cancelNotification(appContext, reminderId)
                        NookiSchedulerTelemetry.record(
                            source = "ReminderActionReceiver",
                            message = "Completed reminder #$reminderId ('${completed?.title ?: ""}') via action receiver (+${completed?.petMoodImpact ?: 10} XP)"
                        )
                    }

                    AndroidExactAlarmScheduler.ACTION_SNOOZE_REMINDER -> {
                        val minutes = if (snoozeTomorrow) {
                            val now = System.currentTimeMillis()
                            ((WorkflowSchedule.nextDaily(9 * 60, now) - now) / 60_000L).toInt() + 1
                        } else {
                            snoozeMinutes
                        }
                        val snoozed = repository.snoozeReminder(reminderId, minutes)
                        NotificationHelper.cancelNotification(appContext, reminderId)
                        NookiSchedulerTelemetry.record(
                            source = "ReminderActionReceiver",
                            message = "Snoozed reminder #$reminderId for $minutes mins (snoozeCount=${snoozed?.first?.snoozeCount ?: 1})"
                        )
                    }

                    AndroidExactAlarmScheduler.ACTION_DISMISS_REMINDER -> {
                        repository.dismissReminder(reminderId)
                        NotificationHelper.cancelNotification(appContext, reminderId)
                        NookiSchedulerTelemetry.record(
                            source = "ReminderActionReceiver",
                            message = "Notification for #$reminderId swiped away; moved to You missed"
                        )
                    }

                    AndroidExactAlarmScheduler.ACTION_MOVE_TO_TOMORROW -> {
                        val moved = repository.moveOpenToTomorrow()
                        NotificationHelper.cancelBrief(appContext)
                        NookiSchedulerTelemetry.record(
                            source = "ReminderActionReceiver",
                            message = "Moved $moved open reminders to tomorrow"
                        )
                    }
                }
                WidgetRefresher.refresh(appContext)
            } catch (t: Throwable) {
                NookiSchedulerTelemetry.record(
                    source = "ReminderActionReceiver",
                    message = "Failed processing action $action for #$reminderId: ${t.message}",
                    isWarning = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
