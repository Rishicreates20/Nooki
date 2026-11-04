package com.example.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.database.NookiDatabase
import com.example.data.repository.ReminderRepositoryImpl
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver handling notification actions (Complete, Snooze 5m, Dismiss)
 * without requiring the user to open the full activity first.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val reminderId = intent.getLongExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val snoozeMinutes = intent.getIntExtra(AndroidExactAlarmScheduler.EXTRA_SNOOZE_MINUTES, 5)
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = NookiDatabase.getInstance(appContext)
                val scheduler = AndroidExactAlarmScheduler(appContext)
                val repository = ReminderRepositoryImpl(database.reminderDao(), scheduler)

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
                        val snoozed = repository.snoozeReminder(reminderId, snoozeMinutes)
                        NotificationHelper.cancelNotification(appContext, reminderId)
                        NookiSchedulerTelemetry.record(
                            source = "ReminderActionReceiver",
                            message = "Snoozed reminder #$reminderId for $snoozeMinutes mins (snoozeCount=${snoozed?.first?.snoozeCount ?: 1})"
                        )
                    }

                    AndroidExactAlarmScheduler.ACTION_DISMISS_REMINDER -> {
                        // Swiped away is not done: it moves to "You missed" (medicine asks again).
                        repository.markMissed(reminderId)
                        NotificationHelper.cancelNotification(appContext, reminderId)
                        NookiSchedulerTelemetry.record(
                            source = "ReminderActionReceiver",
                            message = "Reminder #$reminderId swiped away; moved to You missed"
                        )
                    }
                }
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
