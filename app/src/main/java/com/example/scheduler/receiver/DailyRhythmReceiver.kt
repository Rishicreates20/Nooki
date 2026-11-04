package com.example.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.NookiGraph
import com.example.attention.BriefComposer
import com.example.scheduler.DailyRhythm
import com.example.scheduler.NookiSchedulerTelemetry
import com.example.scheduler.NotificationHelper
import com.example.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Posts the morning brief or evening wrap, if there is anything worth saying, and books the next. */
class DailyRhythmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != DailyRhythm.ACTION_MORNING && action != DailyRhythm.ACTION_EVENING) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = NookiGraph.get(appContext)
                val prefs = graph.settings.current()
                val now = System.currentTimeMillis()
                graph.rhythm.arm(prefs)
                if (!prefs.briefsOn) return@launch

                val digest = graph.digestNow(now)
                val brief = if (action == DailyRhythm.ACTION_MORNING) {
                    BriefComposer.morning(digest, prefs.name, now)
                } else {
                    val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
                    BriefComposer.evening(digest, percent, charging, now)
                }
                if (brief != null) NotificationHelper.showBrief(appContext, brief)

                graph.attention.purgeOld(now - RESOLVED_KEEP_MILLIS)
                WidgetRefresher.refresh(appContext)
            } catch (t: Throwable) {
                NookiSchedulerTelemetry.record(
                    source = "DailyRhythmReceiver",
                    message = "Brief failed: ${t.message}",
                    isWarning = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val RESOLVED_KEEP_MILLIS = 30L * 24 * 60 * 60_000L
    }
}
