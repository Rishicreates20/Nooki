package com.example.core.today

/** A daily note from Nooki: one calm voice, twice a day, instead of a stream of pings. */
data class RhythmNote(val title: String, val body: String, val offerMoveToTomorrow: Boolean)

/**
 * Morning brief and evening wrap, written from the Today board. Returns null when there is
 * nothing worth saying, because Nooki never nags about itself.
 */
object RhythmText {

    fun morning(board: TodayBoard): RhythmNote? {
        val lines = mutableListOf<String>()
        if (board.todo.isNotEmpty()) {
            val first = board.todo.first()
            val more = board.todo.size - 1
            lines += "On today: ${first.line} at ${first.detail}" + if (more > 0) ", and $more more." else "."
        }
        val close = board.forgot.filter { it.ref !is KnotRef.Money && it.detail.startsWith("Due") }
        if (close.isNotEmpty()) lines += "Getting close: " + close.take(2).joinToString(", ") { it.line } + "."
        if (board.missed.isNotEmpty()) lines += "Still open from before: ${count(board.missed.size, "thing")}."
        val money = board.forgot.count { it.ref is KnotRef.Money }
        if (money > 0) lines += "Money out with people: ${count(money, "entry", "entries")}."
        if (lines.isEmpty()) return null
        val title = if (board.todo.isEmpty()) "Good morning" else "Good morning. ${count(board.todo.size, "thing")} today"
        return RhythmNote(title, lines.joinToString(" "), offerMoveToTomorrow = false)
    }

    fun evening(board: TodayBoard, batteryPercent: Int?, charging: Boolean): RhythmNote? {
        val left = board.todo.size + board.missed.count { it.ref is KnotRef.Reminder } + board.noTimeCount
        val lines = mutableListOf<String>()
        if (left > 0) lines += "${count(left, "thing")} didn't get done. Move ${if (left == 1) "it" else "them"} to tomorrow in one tap."
        if (board.noTimeCount > 0) lines += "${count(board.noTimeCount, "note")} still need${if (board.noTimeCount == 1) "s" else ""} a time."
        val chargeNudge = batteryPercent != null && batteryPercent < 40 && !charging
        if (chargeNudge) lines += "Your phone is at $batteryPercent%. Plug it in tonight so Nooki can ring on time."
        if (lines.isEmpty()) return null
        val title = if (left > 0) "Evening wrap" else "Before you sleep"
        return RhythmNote(title, lines.joinToString(" "), offerMoveToTomorrow = left > 0)
    }

    private fun count(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"
}
