package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `correct password verifies and wrong one does not`() {
        val stored = PasswordHasher.hash("chai-at-4pm", iterations = 1_000)
        assertTrue(PasswordHasher.verify("chai-at-4pm", stored))
        assertFalse(PasswordHasher.verify("chai-at-5pm", stored))
        assertFalse(PasswordHasher.verify("", stored))
    }

    @Test
    fun `same password hashes differently each time because of the salt`() {
        val a = PasswordHasher.hash("secret1", iterations = 1_000)
        val b = PasswordHasher.hash("secret1", iterations = 1_000)
        assertNotEquals(a, b)
        assertTrue(PasswordHasher.verify("secret1", a))
        assertTrue(PasswordHasher.verify("secret1", b))
    }

    @Test
    fun `stored hash never contains the password`() {
        val stored = PasswordHasher.hash("plaintext-password", iterations = 1_000)
        assertFalse(stored.contains("plaintext-password"))
        assertEquals(4, stored.split('$').size)
    }

    @Test
    fun `malformed stored values are rejected instead of crashing`() {
        listOf("", "nonsense", "pbkdf2_sha1\$abc\$00\$00", "md5\$1\$00\$00", "pbkdf2_sha1\$10\$zz\$00")
            .forEach { assertFalse(it, PasswordHasher.verify("x", it)) }
    }
}

class AuthValidatorTest {

    @Test
    fun `sign up accepts good input`() {
        assertNull(AuthValidator.validateSignUp("Rishi", " Rishi@Example.com ", "abc123", "abc123"))
    }

    @Test
    fun `sign up explains the first problem`() {
        assertEquals("Enter your name", AuthValidator.validateSignUp(" ", "a@b.co", "abc123", "abc123"))
        assertEquals("That email doesn't look right", AuthValidator.validateSignUp("A", "not-an-email", "abc123", "abc123"))
        assertEquals("Password needs at least 6 characters", AuthValidator.validateSignUp("A", "a@b.co", "abc", "abc"))
        assertEquals("Passwords don't match", AuthValidator.validateSignUp("A", "a@b.co", "abc123", "abc124"))
    }

    @Test
    fun `emails are compared case-insensitively`() {
        assertEquals("rishi@example.com", AuthValidator.normalizeEmail("  Rishi@Example.COM "))
    }

    @Test
    fun `login needs email and password`() {
        assertEquals("Enter your email", AuthValidator.validateLogin("", "x"))
        assertEquals("Enter your password", AuthValidator.validateLogin("a@b.co", ""))
        assertNull(AuthValidator.validateLogin("a@b.co", "x"))
    }
}
