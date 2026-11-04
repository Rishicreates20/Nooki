package com.example.rhythm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.core.today.KnotRef
import com.example.core.today.RhythmText
import com.example.data.local.database.AppDatabase
import com.example.data.repository.ReminderRepositoryImpl
import com.example.data.today.loadTodayBoard
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NotificationHelper
import com.example.widget.NookiWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * A daily rhythm, not a stream of pings: a morning brief at 8:00 and an evening wrap at 20:30.
 * Inexact repeating alarms, so the phone batches them and no special permission is needed.
 */
object DailyRhythm {
    const val ACTION_MORNING = "com.example.nooki.ACTION_MORNING_BRIEF"
    const val ACTION_EVENING = "com.example.nooki.ACTION_EVENING_WRAP"
    const val ACTION_MOVE_TO_TOMORROW = "com.example.nooki.ACTION_MOVE_TO_TOMORROW"

    private const val MORNING_HOUR = 8
    private const val EVENING_HOUR = 20
    private const val EVENING_MINUTE = 30

    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextAt(MORNING_HOUR, 0), AlarmManager.INTERVAL_DAY, pi(context, ACTION_MORNING))
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextAt(EVENING_HOUR, EVENING_MINUTE), AlarmManager.INTERVAL_DAY, pi(context, ACTION_EVENING))
        }
    }

    private fun nextAt(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun pi(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, DailyRhythmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun moveToTomorrowIntent(context: Context): PendingIntent = pi(context, ACTION_MOVE_TO_TOMORROW)
}

class DailyRhythmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(app)
                val board = loadTodayBoard(db)
                when (action) {
                    DailyRhythm.ACTION_MORNING -> RhythmText.morning(board)?.let {
                        NotificationHelper.showDailyNote(app, it.title, it.body, moveAction = null)
                    }
                    DailyRhythm.ACTION_EVENING -> {
                        val battery = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
                        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
                        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                        RhythmText.evening(board, percent, charging)?.let {
                            NotificationHelper.showDailyNote(
                                app, it.title, it.body,
                                moveAction = if (it.offerMoveToTomorrow) DailyRhythm.moveToTomorrowIntent(app) else null,
                            )
                        }
                    }
                    DailyRhythm.ACTION_MOVE_TO_TOMORROW -> {
                        val ids = (board.missed + board.todo + board.forgot).mapNotNull { (it.ref as? KnotRef.Reminder)?.id }.distinct()
                        ReminderRepositoryImpl(db.reminderDao(), AndroidExactAlarmScheduler(app)).moveToTomorrow(ids)
                        NotificationHelper.cancelDailyNote(app)
                    }
                }
                NookiWidget.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
