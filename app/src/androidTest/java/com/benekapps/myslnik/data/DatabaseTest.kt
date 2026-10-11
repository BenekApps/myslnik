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
import com.benekapps.myslnik.data.db.ExposureAttempt
import com.benekapps.myslnik.data.db.ExposureLadder
import com.benekapps.myslnik.data.db.ExposureStep
import com.benekapps.myslnik.data.db.PlannedActivity
import com.benekapps.myslnik.data.db.Worry
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
 * For each new version: add the migration to AppDatabase and a test here
 * that creates the previous version with data, migrates and checks the rows.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsEntries() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO thought_records (created_at, updated_at, is_draft, draft_step, situation, emotions, " +
                    "automatic_thoughts, hot_thought_index, distortions, evidence_for, evidence_against, alternative_thought) " +
                    "VALUES (1, 1, 0, 0, 'Sytuacja z 1.0', '[{\"emotion\":\"anxiety\",\"before\":70}]', '[\"myśl\"]', 0, '[]', '', '', '')",
            )
            execSQL("INSERT INTO mood_entries (created_at, updated_at, rating, emotions, note) VALUES (2, 2, 6, '[\"calm\"]', 'notatka')")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true).apply {
            query("SELECT situation, emotions FROM thought_records").use { c ->
                check(c.moveToFirst())
                assertEquals("Sytuacja z 1.0", c.getString(0))
                assertEquals("[{\"emotion\":\"anxiety\",\"before\":70}]", c.getString(1))
            }
            query("SELECT rating, note FROM mood_entries").use { c ->
                check(c.moveToFirst())
                assertEquals(6, c.getInt(0))
                assertEquals("notatka", c.getString(1))
            }
            // New tables exist and accept rows.
            execSQL("INSERT INTO sleep_entries (created_at, updated_at, night, in_bed, tried_to_sleep, minutes_to_fall_asleep, awakenings, minutes_awake, final_wake, out_of_bed, quality, note) VALUES (1,1,20000,660,675,15,1,10,1110,1125,3,'')")
            close()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()

    @After
    fun close() = db.close()

    @Test
    fun deletingALadderRemovesStepsAndAttempts() = runBlocking {
        val exposure = db.exposure()
        val ladder = exposure.upsertLadder(ExposureLadder(createdAt = 1, updatedAt = 1, title = "Autobus"))
        val easy = exposure.upsertStep(ExposureStep(ladderId = ladder, createdAt = 1, updatedAt = 1, description = "Przystanek", expectedAnxiety = 20))
        val hard = exposure.upsertStep(ExposureStep(ladderId = ladder, createdAt = 1, updatedAt = 1, description = "Cała trasa", expectedAnxiety = 80))
        exposure.insertAttempt(ExposureAttempt(stepId = easy, createdAt = 1, updatedAt = 1, anxietyBefore = 50, anxietyAfter = 20))
        exposure.insertAttempt(ExposureAttempt(stepId = easy, createdAt = 2, updatedAt = 2, anxietyBefore = 40, anxietyAfter = 10))

        val steps = exposure.observeSteps(ladder).first()
        assertEquals(listOf(easy, hard), steps.map { it.id })
        assertEquals(2, steps.first().attempts)
        assertEquals(40, steps.first().lastBefore)
        assertEquals(10, steps.first().lastAfter)

        exposure.deleteLadder(ladder)
        assertTrue(exposure.getAllSteps().isEmpty())
        assertTrue(exposure.getAllAttempts().isEmpty())
    }

    @Test
    fun activitiesByDayAndUpsert() = runBlocking {
        val dao = db.activities()
        val id = dao.upsert(PlannedActivity(createdAt = 1, updatedAt = 1, day = 100, timeMinutes = 600, title = "Spacer"))
        dao.upsert(PlannedActivity(createdAt = 1, updatedAt = 1, day = 100, timeMinutes = null, title = "Telefon"))
        dao.upsert(PlannedActivity(createdAt = 1, updatedAt = 1, day = 101, title = "Jutro"))
        assertEquals(listOf("Spacer", "Telefon"), dao.observeDay(100).first().map { it.title })
        dao.upsert(dao.get(id)!!.copy(done = true, pleasure = 7, mastery = 5))
        assertEquals(7, dao.get(id)!!.pleasure)
        assertEquals(1, dao.observeDoneBetween(100, 101).first().size)
    }

    @Test
    fun restoreWithExposureInsertsParentsFirst() = runBlocking {
        val repo = RoomEntriesRepository(db)
        val backup = BackupData(
            createdAt = 0,
            thoughtRecords = emptyList(),
            moodEntries = emptyList(),
            exposureLadders = listOf(ExposureLadder(id = 10, createdAt = 1, updatedAt = 1, title = "L")),
            exposureSteps = listOf(ExposureStep(id = 20, ladderId = 10, createdAt = 1, updatedAt = 1, description = "S", expectedAnxiety = 30)),
            exposureAttempts = listOf(ExposureAttempt(id = 30, stepId = 20, createdAt = 1, updatedAt = 1, anxietyBefore = 50, anxietyAfter = 30)),
            worries = listOf(Worry(id = 40, createdAt = 1, updatedAt = 1, text = "W")),
        )
        repo.replaceAll(backup)
        val exported = repo.exportAll(0)
        assertEquals(backup.exposureAttempts, exported.exposureAttempts)
        assertEquals(backup.worries, exported.worries)
    }
}
