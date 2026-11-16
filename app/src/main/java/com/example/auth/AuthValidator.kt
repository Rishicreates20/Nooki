package com.example.auth

/**
 * Plain-Kotlin validation for the login and sign-up forms. Returns a friendly message for the first
 * problem found, or null when the input is fine.
 */
object AuthValidator {
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_NAME_LENGTH = 40

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateEmail(email: String): String? {
        val e = normalizeEmail(email)
        return when {
            e.isEmpty() -> "Enter your email"
            !EMAIL_REGEX.matches(e) -> "That email doesn't look right"
            else -> null
        }
    }

    fun validateLogin(email: String, password: String): String? =
        validateEmail(email) ?: if (password.isEmpty()) "Enter your password" else null

    fun validateSignUp(name: String, email: String, password: String, confirmPassword: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Enter your name"
            n.length > MAX_NAME_LENGTH -> "Name is too long (max $MAX_NAME_LENGTH characters)"
            else -> validateEmail(email)
                ?: when {
                    password.length < MIN_PASSWORD_LENGTH -> "Password needs at least $MIN_PASSWORD_LENGTH characters"
                    password != confirmPassword -> "Passwords don't match"
                    else -> null
                }
        }
    }
}
