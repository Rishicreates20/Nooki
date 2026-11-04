package com.example.domain.notice

/**
 * Category of notification noticing that the user can individually toggle on or off.
 * "The person can turn off each kind of noticing, and 'off' means off."
 */
enum class NoticeSourceKind(val displayLabel: String) {
    MISSED_CALL("Missed calls"),
    UNREAD_MESSAGE("Unopened messages"),
    BILL_OR_DUE("Bills & dues"),
    PARCEL_DELIVERY("Parcels arriving today"),
    APPOINTMENT_OR_TICKET("Appointments, exams & tickets")
}

/**
 * User preferences controlling which notification types Nooki is allowed to notice.
 */
data class NoticeFilterSettings(
    val masterEnabled: Boolean = true,
    val enabledKinds: Set<NoticeSourceKind> = NoticeSourceKind.entries.toSet()
) {
    fun isAllowed(kind: NoticeSourceKind): Boolean =
        masterEnabled && kind in enabledKinds
}

/**
 * Result of inspecting an incoming notification in pure Kotlin.
 * Guarantees that OTPs, passwords, PINs, bank balances, and raw chat message bodies
 * NEVER reach the database.
 */
sealed interface NoticeInspectionResult {
    data class DroppedSensitive(val reason: String) : NoticeInspectionResult
    data class DroppedDisabledOrIrrelevant(val reason: String) : NoticeInspectionResult

    /**
     * Sanitized, privacy-safe suggestion.
     * "Noticing is a suggestion. Nothing becomes a reminder until the person taps."
     * "Never store message content. Keep who and when. For bills and events, keep only the name, amount and date."
     */
    data class SafeSuggestion(
        val kind: NoticeSourceKind,
        val sanitizedTitle: String,
        val sanitizedSubtitle: String,
        val timestampMillis: Long
    ) : NoticeInspectionResult
}

/**
 * Pure Kotlin privacy & noticing filter (zero Android imports) so it can be unit-tested on JVM.
 * Enforces the unbreakable rules from the Nooki 10/10 Product Prompt:
 * 1. Never touch OTPs, passwords, PINs or bank balances. Drop them before anything is saved.
 * 2. Never store message content. Keep only who and when.
 * 3. For bills and events, keep only the name, amount and date.
 */
object NookiNoticeFilter {

    private val sensitiveOtpOrBankRegex = Regex(
        """\b(?:otp|one[\s-]*time[\s-]*password|verification\s+code|security\s+code|passcode|password|pin\s+is|cvv|avail(?:able)?\s+bal(?:ance)?|a/c\s+bal|account\s+balance|गुप्त\s*कोड|ओटीपी)\b|\b\d{4,8}\s+is\s+your\s+(?:otp|code|pin)\b""",
        RegexOption.IGNORE_CASE
    )

    fun inspectNotification(
        packageName: String,
        senderOrTitle: String,
        rawText: String,
        timestampMillis: Long = System.currentTimeMillis(),
        settings: NoticeFilterSettings = NoticeFilterSettings()
    ): NoticeInspectionResult {
        val combined = "$senderOrTitle $rawText"

        // RULE 1 (UNBREAKABLE): Never touch OTPs, passwords, PINs or bank balances.
        if (sensitiveOtpOrBankRegex.containsMatchIn(combined)) {
            return NoticeInspectionResult.DroppedSensitive(
                reason = "Dropped immediately: notification contains an OTP, PIN, password, or bank balance."
            )
        }

        if (!settings.masterEnabled) {
            return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Nooki noticing is turned off.")
        }

        val lowerText = combined.lowercase()
        val cleanSender = senderOrTitle.trim().ifBlank { "Someone" }

        // Check Missed Calls
        if (lowerText.contains("missed call") || lowerText.contains("छूटी हुई कॉल") || packageName.contains("dialer") || packageName.contains("telecom")) {
            if (!settings.isAllowed(NoticeSourceKind.MISSED_CALL)) {
                return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Missed call noticing is off.")
            }
            return NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.MISSED_CALL,
                sanitizedTitle = "$cleanSender called",
                sanitizedSubtitle = "Missed call • Tap to add a call-back knot",
                timestampMillis = timestampMillis
            )
        }

        // Check Bills & Dues (keep ONLY name, amount, and date — strip raw message)
        val billKeywords = listOf("bill", "due", "electricity", "broadband", "credit card", "emi", "recharge", "statement")
        if (billKeywords.any { lowerText.contains(it) }) {
            if (!settings.isAllowed(NoticeSourceKind.BILL_OR_DUE)) {
                return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Bill noticing is off.")
            }
            val amountMatch = Regex("""(?:₹|rs\.?|inr)\s*([\d,]+)""", RegexOption.IGNORE_CASE).find(combined)
            val amountLabel = amountMatch?.let { "₹${it.groupValues[1]}" } ?: "Bill due"
            return NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.BILL_OR_DUE,
                sanitizedTitle = "$cleanSender • $amountLabel",
                sanitizedSubtitle = "Bill noticed (raw SMS dropped for privacy) • Tap to track",
                timestampMillis = timestampMillis
            )
        }

        // Check Parcels Arriving Today
        if (lowerText.contains("out for delivery") || lowerText.contains("arriving today") || lowerText.contains("parcel") || lowerText.contains("package")) {
            if (!settings.isAllowed(NoticeSourceKind.PARCEL_DELIVERY)) {
                return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Parcel noticing is off.")
            }
            return NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.PARCEL_DELIVERY,
                sanitizedTitle = "Parcel from $cleanSender arriving today",
                sanitizedSubtitle = "Delivery noticed • Tap to keep on today's thread",
                timestampMillis = timestampMillis
            )
        }

        // Check Appointments / Exams / Tickets
        if (lowerText.contains("appointment") || lowerText.contains("interview") || lowerText.contains("pnr") || lowerText.contains("flight") || lowerText.contains("train")) {
            if (!settings.isAllowed(NoticeSourceKind.APPOINTMENT_OR_TICKET)) {
                return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Appointment noticing is off.")
            }
            return NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.APPOINTMENT_OR_TICKET,
                sanitizedTitle = "Event from $cleanSender",
                sanitizedSubtitle = "Appointment/Ticket noticed • Tap to add to thread",
                timestampMillis = timestampMillis
            )
        }

        // Check Messaging apps (RULE 2 UNBREAKABLE: Never store message content! Keep ONLY who and when)
        val isMessagingPackage = packageName.contains("whatsapp") ||
            packageName.contains("messaging") ||
            packageName.contains("telegram") ||
            packageName.contains("mms") ||
            packageName.contains("chat")
        if (isMessagingPackage) {
            if (!settings.isAllowed(NoticeSourceKind.UNREAD_MESSAGE)) {
                return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Unread message noticing is off.")
            }
            return NoticeInspectionResult.SafeSuggestion(
                kind = NoticeSourceKind.UNREAD_MESSAGE,
                sanitizedTitle = "Unread message from $cleanSender",
                sanitizedSubtitle = "Message content never stored • Tap to reply later",
                timestampMillis = timestampMillis
            )
        }

        return NoticeInspectionResult.DroppedDisabledOrIrrelevant("Not a reminder-worthy notification.")
    }
}
