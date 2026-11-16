package com.example.auth

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.ReminderRepositoryImpl
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NookiSchedulerTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface AuthResult {
    data class Success(val account: AccountEntity) : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * On-device login and sign-up. Every account gets its own Room database file, and only the
 * signed-in account's alarms are scheduled: they are cancelled at logout and restored at login.
 */
class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val accountDao = AccountDatabase.getInstance(appContext).accountDao()
    private val session = SessionStore(appContext)

    val isSignedIn: Boolean get() = session.isSignedIn

    suspend fun currentAccount(): AccountEntity? = withContext(Dispatchers.IO) {
        if (!session.isSignedIn) return@withContext null
        val account = accountDao.findById(session.activeAccountId)
        if (account == null) session.signOut()
        account
    }

    suspend fun accountsOnThisPhone(): List<AccountEntity> = withContext(Dispatchers.IO) {
        accountDao.getAll()
    }

    suspend fun signUp(name: String, email: String, password: String, confirmPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            AuthValidator.validateSignUp(name, email, password, confirmPassword)?.let {
                return@withContext AuthResult.Error(it)
            }
            val normalizedEmail = AuthValidator.normalizeEmail(email)
            if (accountDao.findByEmail(normalizedEmail) != null) {
                return@withContext AuthResult.Error("An account with this email already exists on this phone. Log in instead.")
            }

            // The first account on the phone adopts the data that was already here, so nothing is lost
            // when an existing install upgrades. Everyone after that starts with a fresh database.
            val databaseName = if (accountDao.count() == 0) {
                AppDatabase.DATABASE_NAME
            } else {
                "nooki_user_${UUID.randomUUID()}.db"
            }

            val now = System.currentTimeMillis()
            val id = try {
                accountDao.insert(
                    AccountEntity(
                        email = normalizedEmail,
                        displayName = name.trim(),
                        passwordHash = PasswordHasher.hash(password),
                        databaseName = databaseName,
                        createdAtMillis = now,
                        lastLoginAtMillis = now
                    )
                )
            } catch (_: SQLiteConstraintException) {
                return@withContext AuthResult.Error("An account with this email already exists on this phone. Log in instead.")
            }
            val account = checkNotNull(accountDao.findById(id))

            writeProfileName(account)
            activate(account)
            AuthResult.Success(account)
        }

    suspend fun logIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        AuthValidator.validateLogin(email, password)?.let { return@withContext AuthResult.Error(it) }
        val account = accountDao.findByEmail(AuthValidator.normalizeEmail(email))
        if (account == null || !PasswordHasher.verify(password, account.passwordHash)) {
            return@withContext AuthResult.Error("Email or password is wrong")
        }
        accountDao.touchLogin(account.id, System.currentTimeMillis())
        activate(account)
        AuthResult.Success(account)
    }

    /** Cancels this user's alarms and notifications, then forgets the session. */
    suspend fun logOut() = withContext(Dispatchers.IO) {
        if (session.isSignedIn) {
            runCatching {
                AppDatabase.getActiveOrNull(appContext)?.let { db ->
                    val cancelled = ReminderRepositoryImpl(db.reminderDao(), AndroidExactAlarmScheduler(appContext))
                        .cancelAllScheduledAlarms()
                    NookiSchedulerTelemetry.record("Auth", "Logged out: paused $cancelled alarm(s)")
                }
            }
            NotificationManagerCompat.from(appContext).cancelAll()
        }
        session.signOut()
        AppDatabase.closeActive()
    }

    private suspend fun activate(account: AccountEntity) {
        AppDatabase.closeActive()
        session.signIn(account)
        try {
            val db = AppDatabase.getInstance(appContext)
            val report = ReminderRepositoryImpl(db.reminderDao(), AndroidExactAlarmScheduler(appContext))
                .reconcileAndRescheduleAlarms()
            NookiSchedulerTelemetry.record("Auth", "Signed in: restored ${report.rescheduledCount} alarm(s)")
        } catch (t: Throwable) {
            NookiSchedulerTelemetry.record("Auth", "Could not restore alarms: ${t.message}", isWarning = true)
        }
    }

    /** Makes the new account's profile show the name they signed up with. */
    private suspend fun writeProfileName(account: AccountEntity) {
        AppDatabase.closeActive()
        val db = AppDatabase.openStandalone(appContext, account.databaseName)
        try {
            val dao = db.reminderDao()
            val existing = dao.getUserProfile()
            dao.upsertUserProfile(
                existing?.copy(displayName = account.displayName, updatedAtMillis = System.currentTimeMillis())
                    ?: UserProfileEntity(
                        displayName = account.displayName,
                        companionTitle = "Keeper of the Thread",
                        currentStreakDays = 0,
                        longestStreakDays = 0,
                        lastCompletedDayEpoch = 0L
                    )
            )
        } finally {
            db.close()
        }
    }
}
