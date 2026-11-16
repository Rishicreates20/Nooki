package com.example.auth

import android.content.Context

/**
 * Remembers who is signed in on this phone. SharedPreferences are read synchronously, so alarm and
 * boot receivers can find the right database without opening the accounts database.
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val activeAccountId: Long
        get() = prefs.getLong(KEY_ACCOUNT_ID, NO_ACCOUNT)

    val activeDatabaseName: String?
        get() = prefs.getString(KEY_DB_NAME, null)

    /** True once any account has been created. From then on, logged out means "no database". */
    val hasAnyAccount: Boolean
        get() = prefs.getBoolean(KEY_HAS_ACCOUNT, false)

    val isSignedIn: Boolean
        get() = activeAccountId != NO_ACCOUNT && activeDatabaseName != null

    fun signIn(account: AccountEntity) {
        prefs.edit()
            .putLong(KEY_ACCOUNT_ID, account.id)
            .putString(KEY_DB_NAME, account.databaseName)
            .putBoolean(KEY_HAS_ACCOUNT, true)
            .commit()
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_DB_NAME)
            .commit()
    }

    companion object {
        const val NO_ACCOUNT = -1L
        private const val PREFS = "nooki_session"
        private const val KEY_ACCOUNT_ID = "active_account_id"
        private const val KEY_DB_NAME = "active_database_name"
        private const val KEY_HAS_ACCOUNT = "has_any_account"
    }
}
