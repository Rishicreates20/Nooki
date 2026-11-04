package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.scheduler.receiver.DailyRhythmReceiver
import com.example.settings.NookiPrefs
import com.example.workflow.WorkflowSchedule

/**
 * Arms the morning brief and evening wrap. These never need exact timing, so they use inexact
 * alarms the phone can batch, and each one books the next when it fires.
 */
class DailyRhythm(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun arm(prefs: NookiPrefs) {
        val manager = alarmManager ?: return
        val morning = pendingIntent(ACTION_MORNING)
        val evening = pendingIntent(ACTION_EVENING)
        if (!prefs.briefsOn) {
            runCatching {
                manager.cancel(morning)
                manager.cancel(evening)
            }
            return
        }
        val now = System.currentTimeMillis()
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, WorkflowSchedule.nextDaily(prefs.morningMinutes, now), morning)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, WorkflowSchedule.nextDaily(prefs.eveningMinutes, now), evening)
        }
    }

    private fun pendingIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, DailyRhythmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_MORNING = "com.example.nooki.ACTION_MORNING_BRIEF"
        const val ACTION_EVENING = "com.example.nooki.ACTION_EVENING_WRAP"
    }
}
