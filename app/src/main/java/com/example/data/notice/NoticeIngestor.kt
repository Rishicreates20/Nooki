package com.example.data.notice

import com.example.core.notice.NoticeExtractor
import com.example.core.notice.NoticeSettings
import com.example.core.notice.Noticed
import com.example.core.notice.RawNotification
import com.example.data.local.dao.NoticedDao
import com.example.data.local.entity.NoticedItemEntity
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import java.security.MessageDigest

/**
 * The only path from a notification to the database. [NoticeExtractor] drops OTPs, passwords, PINs
 * and balances first and keeps no message content, so nothing sensitive can be written here.
 * Each saved item is a suggestion until the person taps it.
 */
class NoticeIngestor(private val dao: NoticedDao) {

    /** Returns the new row id, or null when nothing was kept. */
    suspend fun ingest(raw: RawNotification, settings: NoticeSettings, notificationKey: String?): Long? {
        val noticed = NoticeExtractor.extract(raw, settings) ?: return null
        val keyHash = notificationKey?.let(::hash)

        // Chat apps update the same notification as messages arrive: keep one item.
        if (keyHash != null && dao.findOpenByKey(keyHash) != null) return null

        val entity = when (noticed) {
            is Noticed.MissedCall -> {
                if (dao.countSince(NoticedType.MISSED_CALL.name, noticed.who, noticed.atMillis - COLLAPSE_MILLIS) > 0) return null
                NoticedItemEntity(type = NoticedType.MISSED_CALL, who = noticed.who, noticedAtMillis = noticed.atMillis, sourceKeyHash = keyHash)
            }
            is Noticed.UnreadMessage -> {
                if (dao.countSince(NoticedType.UNREAD_MESSAGE.name, noticed.who, noticed.atMillis - COLLAPSE_MILLIS) > 0) return null
                NoticedItemEntity(type = NoticedType.UNREAD_MESSAGE, who = noticed.who, noticedAtMillis = noticed.atMillis, sourceKeyHash = keyHash)
            }
            is Noticed.Bill -> NoticedItemEntity(
                type = NoticedType.BILL,
                who = noticed.name,
                amountPaise = noticed.amountPaise,
                dueDay = noticed.due?.day,
                dueMonth = noticed.due?.month,
                dueYear = noticed.due?.year,
                noticedAtMillis = noticed.atMillis,
                sourceKeyHash = keyHash,
            )
        }
        return dao.insert(entity)
    }

    /**
     * The person opened the notification (or the app read the message), so an unread message or
     * missed call no longer needs Nooki. Swiping it away is not opening it, so that keeps the item.
     */
    suspend fun onOpened(notificationKey: String) {
        val item = dao.findOpenByKey(hash(notificationKey)) ?: return
        // Reading a bill SMS does not pay the bill, so bills stay until the person acts.
        if (item.type == NoticedType.BILL) return
        dao.setStatus(item.id, NoticedStatus.ACCEPTED.name)
    }

    companion object {
        const val COLLAPSE_MILLIS = 30 * 60_000L

        fun hash(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }
}
