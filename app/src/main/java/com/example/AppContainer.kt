package com.example

import android.content.Context
import com.example.attention.AttentionEngine
import com.example.attention.CalendarEvent
import com.example.attention.CalendarReader
import com.example.attention.TimeWords
import com.example.attention.TodayDigest
import com.example.data.local.database.NookiDatabase
import com.example.data.repository.AttentionRepository
import com.example.data.repository.ReminderRepository
import com.example.data.repository.ReminderRepositoryImpl
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.DailyRhythm
import com.example.settings.NookiSettings
import kotlinx.coroutines.flow.first

/** The app's objects, built once and shared by the screens, receivers and services. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = NookiDatabase.getInstance(appContext)

    val scheduler = AndroidExactAlarmScheduler(appContext)
    val reminders: ReminderRepository = ReminderRepositoryImpl(database.reminderDao(), scheduler)
    val attention = AttentionRepository(database.attentionDao())
    val settings = NookiSettings(appContext)
    val calendar = CalendarReader(appContext)
    val rhythm = DailyRhythm(appContext)

    fun upcomingEvents(nowMillis: Long): List<CalendarEvent> = calendar.upcoming(
        fromMillis = TimeWords.startOfDay(nowMillis),
        toMillis = TimeWords.startOfDay(nowMillis, offsetDays = AttentionEngine.COMING_UP_DAYS + 1)
    )

    /** One look at everything, for the briefs and the widget, which have no screen to observe it. */
    suspend fun digestNow(nowMillis: Long = System.currentTimeMillis()): TodayDigest = AttentionEngine.build(
        reminders = reminders.allReminders.first(),
        noticed = attention.noticed.first(),
        looseEnds = attention.looseEnds.first(),
        events = upcomingEvents(nowMillis),
        nowMillis = nowMillis
    )
}

object NookiGraph {
    @Volatile
    private var instance: AppContainer? = null

    fun get(context: Context): AppContainer = instance ?: synchronized(this) {
        instance ?: AppContainer(context).also { instance = it }
    }
}
