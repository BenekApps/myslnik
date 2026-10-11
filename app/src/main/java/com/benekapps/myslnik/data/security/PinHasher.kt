package com.benekapps.myslnik.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the app lock PIN as a salted PBKDF2 hash ("v1:iterations:salt:hash").
 * The PIN itself is never stored.
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, iterations)
        val b64 = Base64.getEncoder()
        return "v1:$iterations:${b64.encodeToString(salt)}:${b64.encodeToString(hash)}"
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != "v1") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { b64.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(pbkdf2(pin, salt, iterations), expected)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
