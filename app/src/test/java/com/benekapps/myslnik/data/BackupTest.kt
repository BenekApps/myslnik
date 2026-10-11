package com.benekapps.myslnik.data

import com.benekapps.myslnik.data.backup.BackupData
import com.benekapps.myslnik.data.backup.BackupSerializer
import com.benekapps.myslnik.data.db.EmotionRating
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.security.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    // Few iterations keep the tests fast; the format stores the count.
    private val iterations = 1_000
    private val plain = "Zażółć gęślą jaźń — dane testowe".toByteArray()

    @Test
    fun `encrypt and decrypt round trip`() {
        val encrypted = BackupCrypto.encrypt(plain, "hasło-testowe".toCharArray(), iterations)
        assertTrue(BackupCrypto.isBackup(encrypted))
        assertArrayEquals(plain, BackupCrypto.decrypt(encrypted, "hasło-testowe".toCharArray()))
    }

    @Test
    fun `ciphertext does not contain the plain text`() {
        val encrypted = BackupCrypto.encrypt(plain, "haslo1234".toCharArray(), iterations)
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("testowe"))
    }

    @Test
    fun `same input encrypts differently every time`() {
        val a = BackupCrypto.encrypt(plain, "haslo1234".toCharArray(), iterations)
        val b = BackupCrypto.encrypt(plain, "haslo1234".toCharArray(), iterations)
        assertFalse(a.contentEquals(b))
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun `wrong password is rejected`() {
        val encrypted = BackupCrypto.encrypt(plain, "dobre-haslo".toCharArray(), iterations)
        BackupCrypto.decrypt(encrypted, "zle-haslo".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun `changed ciphertext is rejected`() {
        val encrypted = BackupCrypto.encrypt(plain, "haslo1234".toCharArray(), iterations)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()
        BackupCrypto.decrypt(encrypted, "haslo1234".toCharArray())
    }

    @Test
    fun `changed header is rejected`() {
        val encrypted = BackupCrypto.encrypt(plain, "haslo1234".toCharArray(), iterations)
        encrypted[20] = (encrypted[20] + 1).toByte() // inside the salt
        try {
            BackupCrypto.decrypt(encrypted, "haslo1234".toCharArray())
            fail("Expected an exception")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // expected
        }
    }

    @Test(expected = BackupCrypto.NotABackupException::class)
    fun `other files are not backups`() {
        BackupCrypto.decrypt("%PDF-1.7 not a backup at all, just some bytes".toByteArray(), "x".toCharArray())
    }
}

class BackupSerializerTest {
    private val data = BackupData(
        createdAt = 1_760_000_000_000,
        thoughtRecords = listOf(
            ThoughtRecord(
                id = 7,
                createdAt = 1_759_000_000_000,
                updatedAt = 1_759_000_100_000,
                isDraft = false,
                draftStep = 6,
                situation = "Spotkanie w pracy, „cytat” i \"cudzysłów\"\nnowa linia",
                emotions = listOf(EmotionRating("anxiety", 80, 40), EmotionRating("custom:żal", 50, null)),
                automaticThoughts = listOf("Na pewno się pomylę", "Wszyscy to zauważą"),
                hotThoughtIndex = 1,
                distortions = listOf("fortune_telling", "mind_reading"),
                evidenceFor = "za",
                evidenceAgainst = "przeciw",
                alternativeThought = "myśl alternatywna",
            ),
            ThoughtRecord(id = 8, createdAt = 1, updatedAt = 2, isDraft = true, draftStep = 2, situation = "szkic"),
        ),
        moodEntries = listOf(MoodEntry(id = 3, createdAt = 5, updatedAt = 6, rating = 0, emotions = listOf("sadness"), note = "")),
    )

    @Test
    fun `json round trip keeps every field`() {
        assertEquals(data, BackupSerializer.fromJson(BackupSerializer.toJson(data)))
    }

    @Test(expected = BackupSerializer.UnsupportedBackupException::class)
    fun `newer backup versions are refused`() {
        val json = BackupSerializer.toJson(data).replace("\"version\":1", "\"version\":99")
        BackupSerializer.fromJson(json)
    }

    @Test(expected = BackupSerializer.UnsupportedBackupException::class)
    fun `unknown formats are refused`() {
        BackupSerializer.fromJson("{\"format\":\"something-else\",\"version\":1}")
    }
}
