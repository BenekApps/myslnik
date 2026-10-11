package com.benekapps.myslnik.data.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backup files.
 *
 * Layout: "MYSLNIK1" | iterations (int32) | salt (16) | iv (12) | AES-256-GCM ciphertext + tag.
 * The key is derived from the user's password with PBKDF2-HMAC-SHA256.
 * The header is authenticated as associated data, so it cannot be altered.
 */
object BackupCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8

    private val MAGIC = "MYSLNIK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_ITERATIONS = 10_000_000
    private val HEADER_SIZE = MAGIC.size + Int.SIZE_BYTES + SALT_BYTES + IV_BYTES

    class NotABackupException : Exception("Not a Myslnik backup file")
    class WrongPasswordException : Exception("Wrong password or damaged file")

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC).putInt(iterations).put(salt).put(iv)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun isBackup(data: ByteArray): Boolean =
        data.size > HEADER_SIZE && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (!isBackup(data)) throw NotABackupException()
        val buffer = ByteBuffer.wrap(data, MAGIC.size, HEADER_SIZE - MAGIC.size)
        val iterations = buffer.int
        if (iterations !in 1..MAX_ITERATIONS) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(data, 0, HEADER_SIZE)
        try {
            return cipher.doFinal(data, HEADER_SIZE, data.size - HEADER_SIZE)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
