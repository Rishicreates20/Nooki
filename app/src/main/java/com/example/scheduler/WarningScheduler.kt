package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.core.schedule.WorkflowSchedule
import com.example.data.local.entity.ReminderEntity
import com.example.scheduler.receiver.WarningReceiver

/**
 * Warnings ahead of the day (30, 7 and 1 days before an expiry; 7 and 1 before a birthday; 3 and 1
 * before a bill). Always inexact: a warning a few minutes late harms nobody, and the phone can
 * batch it. Only the next warning is scheduled; when it fires it schedules the one after.
 */
object WarningScheduler {

    const val ACTION_WARNING = "com.example.nooki.ACTION_REMINDER_WARNING"
    const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"

    fun scheduleNext(context: Context, reminder: ReminderEntity, nowMillis: Long = System.currentTimeMillis()) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(context, reminder.id)
        if (!reminder.hasTime) return
        val next = WorkflowSchedule.nextLeadAlert(reminder.triggerAtMillis, reminder.leadMinuteList, nowMillis) ?: return
        val pi = pendingIntent(context, reminder.id, next.leadMinutes) ?: return
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pi) }
    }

    fun cancel(context: Context, reminderId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val existing = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, WarningReceiver::class.java).setAction(ACTION_WARNING),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        manager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(context: Context, reminderId: Long, leadMinutes: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, WarningReceiver::class.java)
                .setAction(ACTION_WARNING)
                .putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_LEAD_MINUTES, leadMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
