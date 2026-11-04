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
import com.example.data.local.entity.ReminderKind
import com.example.workflow.WorkflowSchedule
import com.example.scheduler.receiver.ReminderActionReceiver

/**
 * Helper responsible for creating Nooki's notification channel, posting high-priority
 * heads-up alarm notifications with Complete / Snooze actions, and triggering tactile haptics.
 */
object NotificationHelper {

    const val CHANNEL_ID_ALARMS = "nooki_desk_pet_alarms"
    const val CHANNEL_ID_HEADS_UP = "nooki_heads_up"

    /** Heads-ups use their own notification id so the real alarm later doesn't replace one silently. */
    private const val HEADS_UP_ID_OFFSET = 400_000

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
            if (manager.getNotificationChannel(CHANNEL_ID_HEADS_UP) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID_HEADS_UP,
                        context.getString(R.string.channel_heads_up_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.channel_heads_up_description)
                        setShowBadge(true)
                    }
                )
            }
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * A heads-up before an exam, deadline or bill ("Physics exam: 1 day left"). It is quieter than
     * the alarm and carries no Done action, since the thing itself hasn't happened yet.
     */
    fun showLeadAlertNotification(context: Context, reminder: ReminderEntity, nowMillis: Long) {
        ensureChannelCreated(context)
        if (!canPost(context)) return
        val left = WorkflowSchedule.countdown(reminder.triggerAtMillis, nowMillis)
        val body = when (reminder.kind) {
            ReminderKind.EXAM -> "Nooki packed your pencils. Time for a revision block?"
            ReminderKind.DEADLINE -> "Nooki is guarding the deadline. A small push today keeps it calm."
            ReminderKind.BILL -> "Pay it early and Nooki won't have to worry."
            else -> "Coming up soon. Nooki is keeping an eye on it."
        }
        val openIntent = PendingIntent.getActivity(
            context,
            HEADS_UP_ID_OFFSET + reminder.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEADS_UP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${reminder.kind.label}: ${reminder.title} · $left")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(HEADS_UP_ID_OFFSET + reminder.id.toInt(), notification)
        }
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

        val bodyText = buildString {
            append(
                when (reminder.kind) {
                    ReminderKind.AI_LIMIT -> "Good news: it's back! Nooki is ready to build with you. "
                    ReminderKind.EXAM -> "Exam time. Nooki believes in you! "
                    ReminderKind.DEADLINE -> "Deadline now! "
                    ReminderKind.BILL -> "Due today. "
                    else -> "Nooki is tapping your desk! "
                }
            )
            if (reminder.notes.isNotBlank()) {
                append(reminder.notes)
            } else {
                append("Complete this ${reminder.category.label.lowercase()} for +${reminder.petMoodImpact} XP.")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🐾 ${reminder.title}")
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
            NotificationManagerCompat.from(context).cancel(HEADS_UP_ID_OFFSET + reminderId.toInt())
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
