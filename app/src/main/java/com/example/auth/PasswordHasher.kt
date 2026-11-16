package com.example.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 password hashing for Nooki's on-device accounts. Plain Kotlin/JVM (no Android
 * imports) so it is unit-tested directly.
 *
 * Stored format: `pbkdf2_sha1$<iterations>$<saltHex>$<hashHex>`.
 * PBKDF2WithHmacSHA1 is used because it is available on every Android version Nooki supports
 * (minSdk 24); the SHA-256 variant needs API 26.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val PREFIX = "pbkdf2_sha1"
    private const val SEPARATOR = '$'
    const val DEFAULT_ITERATIONS = 40_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(password, salt, iterations)
        return listOf(PREFIX, iterations.toString(), salt.toHex(), derived.toHex())
            .joinToString(SEPARATOR.toString())
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = parts[2].hexToBytesOrNull() ?: return false
        val expected = parts[3].hexToBytesOrNull() ?: return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (isEmpty() || length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
