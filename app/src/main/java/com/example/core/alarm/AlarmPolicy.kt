package com.example.core.alarm

/**
 * How precisely the phone should fire a reminder.
 *
 * Plain Kotlin with no Android imports so the rule can be unit tested.
 */
enum class AlarmPrecision {
    /** Fire at the exact minute, waking the phone from Doze. Only when lateness causes harm. */
    EXACT,

    /** Let the phone batch it with other wake-ups. Fine for almost everything. */
    INEXACT,

    /**
     * The reminder deserves an exact alarm but the person has not allowed exact alarms (denied by
     * default for most new installs since Android 14). Schedule it inexactly so it is never lost,
     * and offer the permission at this moment, when it clearly helps.
     */
    INEXACT_UNTIL_ALLOWED,
}

/**
 * Nooki's reliability rule from the product brief: exact alarms are used only when a late alarm
 * causes harm, like medicine or something marked critical. Everything else uses inexact alarms,
 * which also means no scary permission on day one.
 */
object AlarmPolicy {

    fun wantsExact(isCritical: Boolean, isMedicine: Boolean): Boolean = isCritical || isMedicine

    fun precisionFor(isCritical: Boolean, isMedicine: Boolean, exactAllowed: Boolean): AlarmPrecision =
        when {
            !wantsExact(isCritical, isMedicine) -> AlarmPrecision.INEXACT
            exactAllowed -> AlarmPrecision.EXACT
            else -> AlarmPrecision.INEXACT_UNTIL_ALLOWED
        }
}
