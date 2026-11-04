package com.example.notice

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.NookiGraph
import com.example.data.local.entity.NoticedType
import com.example.settings.NookiPrefs
import com.example.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Looks at notifications as they arrive and keeps only what [NotificationClassifier] allows. Message
 * text stays in memory for the length of that one check and is never written anywhere. The app has
 * no internet permission, so none of this can leave the phone.
 */
class NookiNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notice = sbn.toNotice(this)
        scope.launch {
            runCatching {
                val graph = NookiGraph.get(applicationContext)
                val prefs = graph.settings.current()
                val now = System.currentTimeMillis()
                val candidate = NotificationClassifier.classify(notice, now, ownPackage = packageName) ?: return@runCatching
                if (!prefs.allows(candidate.type)) return@runCatching
                graph.attention.ingest(candidate)
                WidgetRefresher.refresh(applicationContext)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Gone from the shade means the person opened or cleared it, so it needs no reminder.
        val key = NotificationClassifier.messageKey(sbn.toNotice(this)) ?: return
        scope.launch {
            runCatching { NookiGraph.get(applicationContext).attention.messageOpened(key) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun NookiPrefs.allows(type: NoticedType): Boolean = when (type) {
        NoticedType.MISSED_CALL -> watchCalls
        NoticedType.UNREAD_MESSAGE -> watchMessages
        NoticedType.BILL, NoticedType.DELIVERY, NoticedType.EVENT -> watchBills
    }

    private fun StatusBarNotification.toNotice(context: Context): IncomingNotice {
        val n = notification
        val extras = n.extras
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
        return IncomingNotice(
            packageName = packageName,
            appLabel = label,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            category = n.category,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = isOngoing,
            isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        )
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val me = ComponentName(context, NookiNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
