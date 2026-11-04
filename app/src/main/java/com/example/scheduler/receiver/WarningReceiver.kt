package com.example.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.schedule.WorkflowSchedule
import com.example.data.local.database.AppDatabase
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NotificationHelper
import com.example.scheduler.WarningScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shows "Rent: in 3 days" and lines up the next warning. */
class WarningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getLongExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (id <= 0L) return
        val lead = intent?.getIntExtra(WarningScheduler.EXTRA_LEAD_MINUTES, 0) ?: 0
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminder = AppDatabase.getInstance(app).reminderDao().getReminderById(id) ?: return@launch
                if (!reminder.isActive) return@launch
                NotificationHelper.showWarning(app, reminder, "In ${WorkflowSchedule.describeLead(lead)}")
                WarningScheduler.scheduleNext(app, reminder)
            } finally {
                pending.finish()
            }
        }
    }
}
