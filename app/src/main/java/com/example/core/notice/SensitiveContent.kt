package com.example.core.notice

/**
 * Recognises text Nooki must never keep: one-time passwords, verification codes, passwords, PINs,
 * card security codes and bank balances. Anything that matches is dropped before parsing, so it can
 * never reach the database.
 *
 * Plain Kotlin with no Android imports so the rule is unit tested. It errs on the side of dropping:
 * losing a bill suggestion is fine, keeping an OTP is not.
 */
object SensitiveContent {

    private val keywordPatterns: List<Regex> = listOf(
        // One-time passwords and verification codes
        """\botp\b""",
        """\bo\.t\.p\b""",
        """one[\s-]?time[\s-]?(password|passcode|pin|code)""",
        """verification\s+code""",
        """security\s+code""",
        """auth(entication)?\s+code""",
        """login\s+code""",
        """\b(your|the)\s+code\s+(is|:)""",
        """\bis\s+your\s+.{0,20}code\b""",
        """\bpass\s?code\b""",
        """\bdo\s+not\s+share\b""",
        """\bdon'?t\s+share\b""",
        """\bnever\s+share\b""",
        // Passwords, PINs, card codes
        """\bpass\s?word\b""",
        """\bm?pin\b""",
        """\bupi\s+pin\b""",
        """\bcvv\b""",
        """\bcvc\b""",
        // Bank balances
        """\bavl\.?\s*bal""",
        """\bavail(able)?\.?\s+bal(ance)?""",
        """\ba/?c\.?\s+bal""",
        """\bbal(ance)?\s*(is|:|-)?\s*(rs\.?|inr|₹)""",
        """\bclosing\s+balance""",
        """\bcurrent\s+balance""",
        // Hindi and Hinglish
        """ओटीपी""",
        """पासवर्ड""",
        """पिन""",
        """सत्यापन\s*कोड""",
        """बैलेंस""",
        """शेष\s*राशि""",
        """\bkisi\s+(ke\s+)?saath\s+share\s+(na|mat)""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    fun isSensitive(vararg parts: String?): Boolean {
        val text = parts.filterNotNull().joinToString(" ")
        if (text.isBlank()) return false
        return keywordPatterns.any { it.containsMatchIn(text) }
    }
}
