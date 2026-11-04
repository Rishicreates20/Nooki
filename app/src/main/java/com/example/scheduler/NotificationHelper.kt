package com.example.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.entity.ReminderEntity
import com.example.scheduler.receiver.ReminderActionReceiver

/**
 * Helper responsible for creating Nooki's notification channel, posting high-priority
 * heads-up alarm notifications with Complete / Snooze actions, and triggering tactile haptics.
 */
object NotificationHelper {

    const val CHANNEL_ID_ALARMS = "nooki_desk_pet_alarms"
    /** Warnings ahead of the day and the daily notes. Quiet: no sound, no vibration. */
    const val CHANNEL_ID_QUIET = "nooki_quiet"
    private const val DAILY_NOTE_ID = 1_000_001

    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID_ALARMS)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_ALARMS,
                    context.getString(R.string.channel_alarms_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_alarms_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 180L, 90L, 260L)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(CHANNEL_ID_QUIET) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID_QUIET, "Heads-ups and daily notes", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Warnings before bills, birthdays and expiries, the morning brief and the evening wrap"
                    }
                )
            }
        }
    }

    fun showWarning(context: Context, reminder: ReminderEntity, whenText: String) {
        ensureChannelCreated(context)
        val open = PendingIntent.getActivity(
            context,
            reminder.id.toInt() + 500_000,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID_QUIET)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(whenText)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(reminder.id.toInt() + 500_000, n) }
    }

    /** Morning brief or evening wrap. One calm note; replaces the previous one. */
    fun showDailyNote(context: Context, title: String, body: String, moveAction: PendingIntent?) {
        ensureChannelCreated(context)
        val open = PendingIntent.getActivity(
            context,
            DAILY_NOTE_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_QUIET)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (moveAction != null) builder.addAction(0, "Move to tomorrow", moveAction)
        runCatching { NotificationManagerCompat.from(context).notify(DAILY_NOTE_ID, builder.build()) }
    }

    fun cancelDailyNote(context: Context) {
        NotificationManagerCompat.from(context).cancel(DAILY_NOTE_ID)
    }

    fun showReminderAlarmNotification(context: Context, reminder: ReminderEntity) {
        ensureChannelCreated(context)
        triggerDeskPetPulseVibration(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                NookiSchedulerTelemetry.record(
                    source = "NotificationHelper",
                    message = "Skipped OS notification for #${reminder.id} (POST_NOTIFICATIONS not granted); in-app Nooki alert active.",
                    isWarning = true
                )
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completePendingIntent = buildActionPendingIntent(
            context = context,
            reminderId = reminder.id,
            action = AndroidExactAlarmScheduler.ACTION_COMPLETE_REMINDER,
            requestCodeOffset = 100_000
        )

        val snoozePendingIntent = buildActionPendingIntent(
            context = context,
            reminderId = reminder.id,
            action = AndroidExactAlarmScheduler.ACTION_SNOOZE_REMINDER,
            requestCodeOffset = 200_000
        )

        val dismissPendingIntent = buildActionPendingIntent(
            context = context,
            reminderId = reminder.id,
            action = AndroidExactAlarmScheduler.ACTION_DISMISS_REMINDER,
            requestCodeOffset = 300_000
        )

        val bodyText = reminder.notes.ifBlank {
            if (reminder.kind.needsConfirmation) {
                "Tap Done once you have taken it. Nooki will ask again if not."
            } else {
                "Tap Done when it's done. Swiping this away keeps it in You missed."
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                context.getString(R.string.action_complete),
                completePendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.action_snooze),
                snoozePendingIntent
            )
            .setDeleteIntent(dismissPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
        }
    }

    fun cancelNotification(context: Context, reminderId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(reminderId.toInt())
        }
    }

    fun triggerDeskPetPulseVibration(context: Context) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0L, 90L, 60L, 140L)
                    val amplitudes = intArrayOf(0, 180, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200L)
                }
            }
        }
    }

    private fun buildActionPendingIntent(
        context: Context,
        reminderId: Long,
        action: String,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AndroidExactAlarmScheduler.EXTRA_SNOOZE_MINUTES, 5)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeOffset + reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
