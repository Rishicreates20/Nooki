package com.example.presentation.today

import com.example.core.notice.NoticeSettings
import com.example.core.today.Knot
import com.example.core.today.KnotRef
import com.example.core.today.TodayBoard

/** What the Today screen shows. One immutable state, rebuilt from Room (MVI). */
data class TodayState(
    val board: TodayBoard? = null,
    val draft: String = "",
    /** What Nooki understood from [draft], shown before saving. */
    val preview: String = "",
    /** Answer to the last question ("where is my passport"). Null when none is open. */
    val answer: Answer? = null,
    /** After 6 pm: offer to move what is left to tomorrow in one tap. */
    val showEveningWrap: Boolean = false,
    val noticeSettings: NoticeSettings = NoticeSettings(),
    /** The person said "Not now" to noticing; Nooki doesn't offer it again on its own. */
    val noticingOfferHidden: Boolean = true,
)

data class Answer(val question: String, val lines: List<String>)

sealed interface TodayIntent {
    data class EditDraft(val text: String) : TodayIntent
    data object SubmitDraft : TodayIntent
    data class Untie(val knot: Knot) : TodayIntent
    data class Later(val ref: KnotRef) : TodayIntent
    data object MoveLeftoversToTomorrow : TodayIntent
    data object CloseAnswer : TodayIntent
    /** One switch changed; applied to the stored settings, not to a possibly stale snapshot. */
    data class ChangeNoticeSettings(val change: (NoticeSettings) -> NoticeSettings) : TodayIntent
    data object HideNoticingOffer : TodayIntent
}

/** One-off things the Activity does: messages and asking for a permission at the moment it helps. */
sealed interface TodayEffect {
    data class Say(val message: String) : TodayEffect
    data object AskNotificationPermission : TodayEffect
    data object OfferExactAlarms : TodayEffect
}
