package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.notice.NoticeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nookiSettings: DataStore<Preferences> by preferencesDataStore(name = "nooki_settings")

/** Settings that live in DataStore. Room stays the source of truth for the person's data. */
class NookiSettings(private val context: Context) {

    private object Keys {
        val missedCalls = booleanPreferencesKey("notice_missed_calls")
        val messages = booleanPreferencesKey("notice_unread_messages")
        val bills = booleanPreferencesKey("notice_bills")
        val noticingOfferHidden = booleanPreferencesKey("noticing_offer_hidden")
    }

    val noticeSettings: Flow<NoticeSettings> = context.nookiSettings.data.map { readNotice(it) }

    private fun readNotice(p: Preferences): NoticeSettings =
        NoticeSettings(
            missedCalls = p[Keys.missedCalls] ?: true,
            unreadMessages = p[Keys.messages] ?: true,
            bills = p[Keys.bills] ?: true,
        )

    /** The person said "Not now" to noticing. Nooki never asks again on its own. */
    val noticingOfferHidden: Flow<Boolean> = context.nookiSettings.data.map { it[Keys.noticingOfferHidden] ?: false }

    suspend fun currentNoticeSettings(): NoticeSettings = noticeSettings.first()

    /**
     * Changes the switches from what is stored now, inside one DataStore transaction, so quick
     * taps on different switches never overwrite each other with a stale snapshot.
     */
    suspend fun updateNoticeSettings(change: (NoticeSettings) -> NoticeSettings) {
        context.nookiSettings.edit {
            val settings = change(readNotice(it))
            it[Keys.missedCalls] = settings.missedCalls
            it[Keys.messages] = settings.unreadMessages
            it[Keys.bills] = settings.bills
        }
    }

    suspend fun hideNoticingOffer() {
        context.nookiSettings.edit { it[Keys.noticingOfferHidden] = true }
    }
}
