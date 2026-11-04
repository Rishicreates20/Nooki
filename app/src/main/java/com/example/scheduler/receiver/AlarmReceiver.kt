package com.example.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.database.AppDatabase
import com.example.data.repository.ReminderRepositoryImpl
import com.example.scheduler.AndroidAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Primary [BroadcastReceiver] triggered when an exact [android.app.AlarmManager] alarm fires.
 * Uses [goAsync] with an IO [CoroutineScope] to safely transition the reminder to `RINGING`
 * in [AppDatabase] and post the reminder notification, then refresh the home screen widget.
 */
open class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra(AndroidAlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId <= 0L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(appContext)
                val scheduler = AndroidAlarmScheduler(appContext)
                val repository = ReminderRepositoryImpl(database.reminderDao(), scheduler)

                val updatedReminder = repository.markRinging(reminderId)
                if (updatedReminder != null) {
                    NookiSchedulerTelemetry.record(
                        source = "AlarmReceiver",
                        message = "Alarm fired for #$reminderId ('${updatedReminder.title}') -> status = RINGING"
                    )
                    NotificationHelper.showReminderAlarmNotification(appContext, updatedReminder)
                } else {
                    NookiSchedulerTelemetry.record(
                        source = "AlarmReceiver",
                        message = "Alarm fired for #$reminderId, but entity was no longer in Room DB.",
                        isWarning = true
                    )
                }
                com.example.widget.NookiWidget.refresh(appContext)
            } catch (t: Throwable) {
                NookiSchedulerTelemetry.record(
                    source = "AlarmReceiver",
                    message = "Error handling alarm #$reminderId: ${t.message}",
                    isWarning = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
