package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.converter.ReminderTypeConverters
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.ReminderEntity

/**
 * Room Database for Nooki: The Desk-Pet Reminder.
 */
@Database(
    entities = [ReminderEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ReminderTypeConverters::class)
abstract class NookiDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "nooki_desk_pet.db"

        @Volatile
        private var INSTANCE: NookiDatabase? = null

        fun getInstance(context: Context): NookiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NookiDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
