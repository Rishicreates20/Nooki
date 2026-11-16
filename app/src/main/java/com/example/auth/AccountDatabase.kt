package com.example.auth

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * One account on this phone. Each account owns a separate Room database file ([databaseName]) so
 * reminders, health logs, profile and journal never mix between people sharing a device.
 * Passwords are stored only as a salted PBKDF2 hash ([PasswordHasher]). Nothing leaves the device.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["email"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    @ColumnInfo(name = "database_name")
    val databaseName: String,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_login_at_millis")
    val lastLoginAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY last_login_at_millis DESC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Query("UPDATE accounts SET last_login_at_millis = :atMillis WHERE id = :id")
    suspend fun touchLogin(id: Long, atMillis: Long)
}

/**
 * Small, separate database that only holds the list of accounts on this phone.
 */
@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
abstract class AccountDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        const val DATABASE_NAME = "nooki_accounts.db"

        @Volatile
        private var INSTANCE: AccountDatabase? = null

        fun getInstance(context: Context): AccountDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AccountDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
    }
}
