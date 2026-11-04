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
import com.example.attention.Brief
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderKind
import com.example.scheduler.receiver.ReminderActionReceiver
import com.example.workflow.WorkflowSchedule

/**
 * Nooki's voice on the phone: reminders that ring, quieter heads-ups, and the two daily briefs.
 */
object NotificationHelper {

    const val CHANNEL_ID_ALARMS = "nooki_desk_pet_alarms"
    const val CHANNEL_ID_HEADS_UP = "nooki_heads_up"
    const val CHANNEL_ID_BRIEFS = "nooki_briefs"

    private const val HEADS_UP_ID_OFFSET = 400_000
    private const val BRIEF_NOTIFICATION_ID = 900_001
    private const val SMALL_ICON = R.drawable.ic_launcher_monochrome

    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID_ALARMS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ALARMS,
                    context.getString(R.string.channel_alarms_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_alarms_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 180L, 90L, 260L)
                    setShowBadge(true)
                }
            )
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
        if (manager.getNotificationChannel(CHANNEL_ID_BRIEFS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_BRIEFS,
                    context.getString(R.string.channel_briefs_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_briefs_description)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** A heads-up before a date that matters. Quieter than the alarm, and nothing to tick off yet. */
    fun showLeadAlertNotification(context: Context, reminder: ReminderEntity, nowMillis: Long) {
        ensureChannelCreated(context)
        if (!canPost(context)) return
        val left = WorkflowSchedule.countdown(reminder.triggerAtMillis, nowMillis)
        val body = when (reminder.kind) {
            ReminderKind.EXAM -> "A revision block today will help."
            ReminderKind.DEADLINE -> "A small push today keeps this calm."
            ReminderKind.BILL -> "Pay early and it's off your mind."
            ReminderKind.BIRTHDAY -> "Time to plan something."
            ReminderKind.EXPIRY -> "Renew it before it runs out."
            else -> "Coming up soon."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEADS_UP)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle("${reminder.title}, $left")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, HEADS_UP_ID_OFFSET + reminder.id.toInt()))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(HEADS_UP_ID_OFFSET + reminder.id.toInt(), notification)
        }
    }

    fun showReminderAlarmNotification(context: Context, reminder: ReminderEntity) {
        ensureChannelCreated(context)
        triggerDeskPetPulseVibration(context)

        if (!canPost(context)) {
            NookiSchedulerTelemetry.record(
                source = "NotificationHelper",
                message = "Skipped OS notification for #${reminder.id} (POST_NOTIFICATIONS not granted); in-app Nooki alert active.",
                isWarning = true
            )
            return
        }

        val body = when {
            reminder.askCount > 0 -> "Asking again. Tap Done once it's handled."
            reminder.notes.isNotBlank() -> reminder.notes
            reminder.kind == ReminderKind.MEDICINE -> "Nooki will ask again until you confirm."
            reminder.kind == ReminderKind.AI_LIMIT -> "It's back."
            reminder.kind == ReminderKind.EXAM -> "All the best."
            reminder.kind == ReminderKind.DEADLINE -> "This is due now."
            reminder.kind == ReminderKind.BILL -> "Due today."
            reminder.kind == ReminderKind.BIRTHDAY -> "Today's the day."
            reminder.kind == ReminderKind.EXPIRY -> "This expires today."
            else -> "Nooki is reminding you."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, reminder.id.toInt()))
            .addAction(
                0,
                context.getString(R.string.action_complete),
                actionIntent(context, reminder.id, AndroidExactAlarmScheduler.ACTION_COMPLETE_REMINDER, 100_000)
            )
            .setDeleteIntent(
                actionIntent(context, reminder.id, AndroidExactAlarmScheduler.ACTION_DISMISS_REMINDER, 300_000)
            )

        if (reminder.keepAsking) {
            builder.addAction(
                0,
                context.getString(R.string.action_snooze_10m),
                actionIntent(context, reminder.id, AndroidExactAlarmScheduler.ACTION_SNOOZE_REMINDER, 200_000, snoozeMinutes = 10)
            )
        } else {
            builder.addAction(
                0,
                context.getString(R.string.action_snooze_1h),
                actionIntent(context, reminder.id, AndroidExactAlarmScheduler.ACTION_SNOOZE_REMINDER, 200_000, snoozeMinutes = 60)
            )
            builder.addAction(
                0,
                context.getString(R.string.action_tomorrow),
                actionIntent(context, reminder.id, AndroidExactAlarmScheduler.ACTION_SNOOZE_REMINDER, 500_000, tomorrow = true)
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(reminder.id.toInt(), builder.build())
        }
    }

    fun showBrief(context: Context, brief: Brief) {
        ensureChannelCreated(context)
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_BRIEFS)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle(brief.title)
            .setContentText(brief.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(brief.body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, BRIEF_NOTIFICATION_ID))
        if (brief.offerMoveToTomorrow) {
            val move = PendingIntent.getBroadcast(
                context,
                BRIEF_NOTIFICATION_ID,
                Intent(context, ReminderActionReceiver::class.java)
                    .setAction(AndroidExactAlarmScheduler.ACTION_MOVE_TO_TOMORROW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.action_move_tomorrow), move)
        }
        runCatching { NotificationManagerCompat.from(context).notify(BRIEF_NOTIFICATION_ID, builder.build()) }
    }

    fun cancelBrief(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(BRIEF_NOTIFICATION_ID) }
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

    private fun actionIntent(
        context: Context,
        reminderId: Long,
        action: String,
        requestCodeOffset: Int,
        snoozeMinutes: Int = 60,
        tomorrow: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(AndroidExactAlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AndroidExactAlarmScheduler.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(AndroidExactAlarmScheduler.EXTRA_SNOOZE_TOMORROW, tomorrow)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeOffset + reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
