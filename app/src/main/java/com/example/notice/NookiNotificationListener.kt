package com.example.notice

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.notice.RawNotification
import com.example.data.local.database.AppDatabase
import com.example.data.notice.NoticeIngestor
import com.example.data.settings.NookiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Nooki notices. Only runs after the person reads the in-app disclosure and turns on notification
 * access. Reads the title and short text of each notification in memory, hands them to
 * [NoticeIngestor] (which keeps only who/when, or a bill's name, amount and date) and forgets the
 * rest. Nothing can leave the phone: the app has no internet permission.
 */
class NookiNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ingestor by lazy { NoticeIngestor(AppDatabase.getInstance(applicationContext).noticedDao()) }
    private val settings by lazy { NookiSettings(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 && n.category != Notification.CATEGORY_MISSED_CALL) return

        val extras = n.extras
        val raw = RawNotification(
            appPackage = sbn.packageName,
            category = n.category,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            postedAtMillis = sbn.postTime,
        )
        val key = sbn.key
        scope.launch {
            runCatching { ingestor.ingest(raw, settings.currentNoticeSettings(), key) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // Opened by tapping, or cleared by the app because it was read. Swipes keep the item.
        if (reason == REASON_CLICK || reason == REASON_APP_CANCEL) {
            val key = sbn.key
            scope.launch { runCatching { ingestor.onOpened(key) } }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
