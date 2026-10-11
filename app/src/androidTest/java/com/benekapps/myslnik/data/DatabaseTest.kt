package com.benekapps.myslnik.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.benekapps.myslnik.data.backup.BackupData
import com.benekapps.myslnik.data.db.AppDatabase
import com.benekapps.myslnik.data.db.EmotionRating
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "encryption-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun databaseFileIsNotReadableWithoutTheKey() = runBlocking {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val db = AppDatabase.openEncrypted(context, key, name)
        RoomEntriesRepository(db).saveThoughtRecord(
            ThoughtRecord(createdAt = 1, updatedAt = 1, situation = "TAJNA-SYTUACJA-123"),
        )
        db.close()

        val bytes = context.getDatabasePath(name).readBytes()
        val header = String(bytes.copyOfRange(0, 16), Charsets.US_ASCII)
        assertFalse("Database must not be plain SQLite", header.startsWith("SQLite format 3"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("TAJNA-SYTUACJA-123"))

        val reopened = AppDatabase.openEncrypted(context, key, name)
        assertEquals("TAJNA-SYTUACJA-123", RoomEntriesRepository(reopened).observeThoughtRecords().first().single().situation)
        reopened.close()
    }

    @Test
    fun wrongKeyCannotOpenTheDatabase() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        AppDatabase.openEncrypted(context, key, name).apply {
            openHelper.writableDatabase
            close()
        }
        val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            AppDatabase.openEncrypted(context, wrong, name).openHelper.writableDatabase
            fail("Opened with a wrong key")
        } catch (e: Exception) {
            // expected: "file is not a database"
        }
    }
}

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    private val repo = RoomEntriesRepository(db)

    @After
    fun close() = db.close()

    @Test
    fun saveUpdateAndQuery() = runBlocking {
        val id = repo.saveThoughtRecord(
            ThoughtRecord(
                createdAt = 100,
                updatedAt = 100,
                isDraft = false,
                emotions = listOf(EmotionRating("anxiety", 70, 30)),
                automaticThoughts = listOf("a", "b"),
                hotThoughtIndex = 1,
                distortions = listOf("catastrophizing"),
            ),
        )
        repo.saveThoughtRecord(ThoughtRecord(createdAt = 150, updatedAt = 150, isDraft = true, situation = "szkic"))
        val loaded = repo.getThoughtRecord(id)!!
        assertEquals(listOf(EmotionRating("anxiety", 70, 30)), loaded.emotions)
        assertEquals(1, loaded.hotThoughtIndex)

        repo.saveThoughtRecord(loaded.copy(situation = "zmienione"))
        assertEquals("zmienione", repo.getThoughtRecord(id)!!.situation)

        // Drafts never go into the PDF.
        assertEquals(listOf(id), repo.completedThoughtRecordsBetween(0, 1_000).map { it.id })
        assertEquals("szkic", repo.observeLatestDraft().first()!!.situation)
    }

    @Test
    fun replaceAllAndDeleteAll() = runBlocking {
        repo.saveMoodEntry(MoodEntry(createdAt = 1, updatedAt = 1, rating = 5))
        val backup = BackupData(
            createdAt = 0,
            thoughtRecords = listOf(ThoughtRecord(id = 42, createdAt = 2, updatedAt = 2, isDraft = false, situation = "z kopii")),
            moodEntries = listOf(MoodEntry(id = 7, createdAt = 3, updatedAt = 3, rating = 8, emotions = listOf("joy"))),
        )
        repo.replaceAll(backup)
        assertEquals(backup.thoughtRecords, repo.observeThoughtRecords().first())
        assertEquals(backup.moodEntries, repo.observeMoodEntries().first())

        repo.deleteAll()
        assertTrue(repo.observeThoughtRecords().first().isEmpty())
        assertTrue(repo.observeMoodEntries().first().isEmpty())
    }
}

/**
 * Every schema version must open, and every migration must keep the data.
 * When adding version 2: add MIGRATION_1_2 to AppDatabase and a test here that
 * creates version 1 with data, runs the migration and checks the rows.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1SchemaIsValid() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO thought_records (created_at, updated_at, is_draft, draft_step, situation, emotions, " +
                    "automatic_thoughts, hot_thought_index, distortions, evidence_for, evidence_against, alternative_thought) " +
                    "VALUES (1, 1, 0, 0, 'x', '[]', '[]', NULL, '[]', '', '', '')",
            )
            close()
        }
    }
}
