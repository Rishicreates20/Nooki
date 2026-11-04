package com.example.presentation.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.parse.TellNooki
import com.example.core.parse.Understood
import com.example.core.parse.UnderstoodText
import com.example.core.parse.MoneyDirection
import com.example.core.today.KnotRef
import com.example.core.today.TodayBuilder
import com.example.data.local.database.AppDatabase
import com.example.data.today.toToday
import com.example.data.local.entity.MoneyDirectionColumn
import com.example.data.local.entity.MoneyEntryEntity
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.ReminderStatus
import com.example.data.local.entity.SavedThingEntity
import com.example.data.repository.ReminderRepositoryImpl
import com.example.data.settings.NookiSettings
import com.example.scheduler.AndroidExactAlarmScheduler
import com.example.scheduler.NotificationHelper
import com.example.scheduler.ScheduleResult
import com.example.widget.NookiWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val scheduler = AndroidExactAlarmScheduler(application)
    private val repository = ReminderRepositoryImpl(db.reminderDao(), scheduler)
    private val settings = NookiSettings(application)

    private val draft = MutableStateFlow("")

    /** The widget refreshes only when what it shows changes, not every minute. */
    private val widgetSummary = MutableStateFlow<List<Any?>>(emptyList())
    private val answer = MutableStateFlow<Answer?>(null)
    private val effects = Channel<TodayEffect>(Channel.BUFFERED)
    val effect: Flow<TodayEffect> = effects.receiveAsFlow()

    /** Re-evaluates "missed" and "close" every minute without any input. */
    private val minuteTick: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    private val board = combine(
        db.reminderDao().observeAllReminders(),
        db.moneyDao().observeOpen(),
        db.noticedDao().observeSuggested(),
        minuteTick,
    ) { reminders, money, notices, now ->
        TodayBuilder.build(
            reminders = reminders.filter { it.status != ReminderStatus.CANCELLED }.map { it.toToday() },
            money = money.map { it.toToday() },
            notices = notices.map { it.toToday() },
            nowMillis = now,
        ) to now
    }.flowOn(Dispatchers.Default)
        .onEach { (b, _) -> widgetSummary.value = listOf(b.mood, b.missed.size, b.forgot.size, b.todo.size, b.ringing?.line, b.todo.firstOrNull()?.line) }

    init {
        viewModelScope.launch {
            widgetSummary.drop(1).distinctUntilChanged().collect { NookiWidget.refresh(getApplication()) }
        }
    }

    val state: StateFlow<TodayState> = combine(
        board, draft, answer, settings.noticeSettings, settings.noticingOfferHidden,
    ) { (b, now), text, ans, noticeSettings, offerHidden ->
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val leftovers = b.missed.any { it.ref is KnotRef.Reminder } || b.todo.isNotEmpty() || b.noTimeCount > 0
        TodayState(
            board = b,
            draft = text,
            preview = UnderstoodText.describe(TellNooki.understand(text, now), now),
            answer = ans,
            showEveningWrap = hour >= 18 && leftovers,
            noticeSettings = noticeSettings,
            noticingOfferHidden = offerHidden,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun onIntent(intent: TodayIntent) {
        when (intent) {
            is TodayIntent.EditDraft -> draft.value = intent.text
            TodayIntent.SubmitDraft -> submit(draft.value)
            is TodayIntent.Untie -> untie(intent.knot.ref)
            is TodayIntent.Later -> later(intent.ref)
            TodayIntent.MoveLeftoversToTomorrow -> moveLeftovers()
            TodayIntent.CloseAnswer -> answer.value = null
            is TodayIntent.SetNoticeSettings -> viewModelScope.launch { settings.setNoticeSettings(intent.settings) }
            TodayIntent.HideNoticingOffer -> viewModelScope.launch { settings.hideNoticingOffer() }
        }
    }

    /** Text arriving from the share sheet or text selection in another app. */
    fun prefill(text: String) {
        draft.value = text.trim().take(500)
    }

    private fun submit(text: String) {
        val raw = text.trim()
        if (raw.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            when (val u = TellNooki.understand(raw)) {
                Understood.Empty -> return@launch
                is Understood.Question -> {
                    answer.value = answerFor(raw, u)
                }
                is Understood.SavedThing -> {
                    db.savedThingDao().insert(SavedThingEntity(thing = u.thing, place = u.place, rawInput = raw))
                    effects.send(TodayEffect.Say("Remembered: ${u.thing}"))
                }
                is Understood.Money -> {
                    db.moneyDao().insert(
                        MoneyEntryEntity(
                            person = u.person,
                            direction = if (u.direction == MoneyDirection.THEY_OWE_ME) MoneyDirectionColumn.THEY_OWE_ME else MoneyDirectionColumn.I_OWE_THEM,
                            amountPaise = u.amountPaise,
                            thing = u.thing,
                            rawInput = raw,
                        )
                    )
                    effects.send(TodayEffect.Say("Noted. It stays in You forgot until it is settled."))
                }
                is Understood.Reminder -> {
                    val (saved, result) = repository.saveParsed(u.reminder, raw)
                    effects.send(TodayEffect.Say(if (saved.hasTime) "Saved" else "Saved. Nooki will ask for a time this evening."))
                    if (saved.hasTime) effects.send(TodayEffect.AskNotificationPermission)
                    if (result is ScheduleResult.InexactFallbackScheduled) effects.send(TodayEffect.OfferExactAlarms)
                }
            }
            draft.update { if (it.trim() == raw) "" else it }
        }
    }

    private suspend fun answerFor(raw: String, q: Understood.Question): Answer {
        if (q.aboutMoney) {
            val open = db.moneyDao().getOpen()
            val lines = open.map { m ->
                val what = m.amountPaise?.let(TodayBuilder::rupees) ?: m.thing ?: "something"
                if (m.direction == MoneyDirectionColumn.I_OWE_THEM) "You owe ${m.person} $what" else "${m.person} has your $what"
            }
            return Answer(raw, lines.ifEmpty { listOf("Nobody owes anything right now.") })
        }
        val words = q.query.split(' ').filter { it.length > 2 }.ifEmpty { listOf(q.query) }
        val found = words.flatMap { db.savedThingDao().search(it) }.distinctBy { it.id }
        val lines = found.map { "${it.thing}: ${it.place}" }
        // Honesty: never guess. If nothing was saved, say so.
        return Answer(raw, lines.ifEmpty { listOf("Nooki doesn't know yet. Tell Nooki where it is next time, like “${q.query} is in the blue bag”.") })
    }

    private fun untie(ref: KnotRef) {
        viewModelScope.launch(Dispatchers.IO) {
            when (ref) {
                is KnotRef.Reminder -> {
                    repository.completeReminder(ref.id)
                    NotificationHelper.cancelNotification(getApplication(), ref.id)
                }
                is KnotRef.Money -> db.moneyDao().settle(ref.id, System.currentTimeMillis())
                is KnotRef.Notice -> db.noticedDao().setStatus(ref.id, NoticedStatus.ACCEPTED.name)
            }
        }
    }

    private fun later(ref: KnotRef) {
        viewModelScope.launch(Dispatchers.IO) {
            when (ref) {
                is KnotRef.Reminder -> repository.moveToTomorrow(listOf(ref.id))
                is KnotRef.Notice -> db.noticedDao().setStatus(ref.id, NoticedStatus.DISMISSED.name)
                is KnotRef.Money -> Unit
            }
        }
    }

    private fun moveLeftovers() {
        val b = state.value.board ?: return
        val ids = (b.missed + b.todo + b.forgot).mapNotNull { (it.ref as? KnotRef.Reminder)?.id }.distinct()
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveToTomorrow(ids)
            effects.send(TodayEffect.Say("Moved ${ids.size} to tomorrow"))
        }
    }
}

