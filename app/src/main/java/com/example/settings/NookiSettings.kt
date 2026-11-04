package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Who the person is. It only decides which examples Nooki offers, never what the app can do. */
enum class Persona(val label: String, val blurb: String, val starters: List<String>) {
    STUDENT(
        label = "I study",
        blurb = "Exams, assignments, fees",
        starters = listOf(
            "Exam on 15 december",
            "Assignment due monday",
            "College fee last date on the 10th",
            "Call home every sunday at 7pm"
        )
    ),
    WORKING(
        label = "I work",
        blurb = "Meetings, bills, follow-ups",
        starters = listOf(
            "Pay rent on the 1st every month",
            "Car insurance expires on 12 january",
            "Credit card bill on the 5th every month",
            "Drink water every 2 hours"
        )
    ),
    HOME(
        label = "I run a home",
        blurb = "Family, medicines, chores",
        starters = listOf(
            "School fees on the 5th every month",
            "Book gas cylinder every month on the 20th",
            "Take BP tablet every day at 9pm",
            "Maa's birthday on 12 march"
        )
    ),
    EVERYONE(
        label = "A bit of everything",
        blurb = "Show me a mix",
        starters = listOf(
            "Pay electricity bill on the 15th every month",
            "Take vitamins every day at 9am",
            "Call mom every 7 days",
            "Passport expires on 3 march"
        )
    );

    companion object {
        fun fromName(value: String?): Persona = entries.firstOrNull { it.name == value } ?: EVERYONE
    }
}

data class NookiPrefs(
    val onboarded: Boolean = false,
    val name: String = "",
    val persona: Persona = Persona.EVERYONE,
    val morningMinutes: Int = 8 * 60,
    val eveningMinutes: Int = 21 * 60,
    val briefsOn: Boolean = true,
    val watchCalls: Boolean = true,
    val watchMessages: Boolean = true,
    val watchBills: Boolean = true
)

private val Context.nookiDataStore: DataStore<Preferences> by preferencesDataStore(name = "nooki_settings")

class NookiSettings(context: Context) {

    private val store = context.applicationContext.nookiDataStore

    val prefs: Flow<NookiPrefs> = store.data.map { p ->
        NookiPrefs(
            onboarded = p[ONBOARDED] ?: false,
            name = p[NAME].orEmpty(),
            persona = Persona.fromName(p[PERSONA]),
            morningMinutes = p[MORNING] ?: 8 * 60,
            eveningMinutes = p[EVENING] ?: 21 * 60,
            briefsOn = p[BRIEFS] ?: true,
            watchCalls = p[WATCH_CALLS] ?: true,
            watchMessages = p[WATCH_MESSAGES] ?: true,
            watchBills = p[WATCH_BILLS] ?: true
        )
    }

    suspend fun current(): NookiPrefs = prefs.first()

    suspend fun update(transform: (NookiPrefs) -> NookiPrefs) {
        val next = transform(current())
        store.edit { p ->
            p[ONBOARDED] = next.onboarded
            p[NAME] = next.name
            p[PERSONA] = next.persona.name
            p[MORNING] = next.morningMinutes
            p[EVENING] = next.eveningMinutes
            p[BRIEFS] = next.briefsOn
            p[WATCH_CALLS] = next.watchCalls
            p[WATCH_MESSAGES] = next.watchMessages
            p[WATCH_BILLS] = next.watchBills
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NAME = stringPreferencesKey("name")
        val PERSONA = stringPreferencesKey("persona")
        val MORNING = intPreferencesKey("morning_minutes")
        val EVENING = intPreferencesKey("evening_minutes")
        val BRIEFS = booleanPreferencesKey("briefs_on")
        val WATCH_CALLS = booleanPreferencesKey("watch_calls")
        val WATCH_MESSAGES = booleanPreferencesKey("watch_messages")
        val WATCH_BILLS = booleanPreferencesKey("watch_bills")
    }
}
