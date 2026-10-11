package com.benekapps.myslnik.data

import androidx.room.withTransaction
import com.benekapps.myslnik.data.backup.BackupData
import com.benekapps.myslnik.data.db.AppDatabase
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** All user entries. The only way the UI touches the database. */
interface EntriesRepository {
    fun observeThoughtRecords(): Flow<List<ThoughtRecord>>
    fun observeThoughtRecord(id: Long): Flow<ThoughtRecord?>
    fun observeLatestDraft(): Flow<ThoughtRecord?>
    suspend fun getThoughtRecord(id: Long): ThoughtRecord?

    /** Inserts when [ThoughtRecord.id] is 0, updates otherwise. Returns the id. */
    suspend fun saveThoughtRecord(record: ThoughtRecord): Long
    suspend fun deleteThoughtRecord(id: Long)
    suspend fun completedThoughtRecordsBetween(from: Long, to: Long): List<ThoughtRecord>

    fun observeMoodEntries(): Flow<List<MoodEntry>>
    fun observeMoodEntry(id: Long): Flow<MoodEntry?>
    suspend fun getMoodEntry(id: Long): MoodEntry?

    /** Inserts when [MoodEntry.id] is 0, updates otherwise. Returns the id. */
    suspend fun saveMoodEntry(entry: MoodEntry): Long
    suspend fun deleteMoodEntry(id: Long)
    suspend fun moodEntriesBetween(from: Long, to: Long): List<MoodEntry>

    fun observeEntryCount(): Flow<Int>

    suspend fun exportAll(now: Long): BackupData

    /** Replaces every entry with the backup's, in one transaction. */
    suspend fun replaceAll(data: BackupData)
    suspend fun deleteAll()
}

class RoomEntriesRepository(private val db: AppDatabase) : EntriesRepository {
    private val thoughts = db.thoughtRecords()
    private val moods = db.moodEntries()

    override fun observeThoughtRecords() = thoughts.observeAll()
    override fun observeThoughtRecord(id: Long) = thoughts.observe(id)
    override fun observeLatestDraft() = thoughts.observeLatestDraft()
    override suspend fun getThoughtRecord(id: Long) = thoughts.get(id)

    override suspend fun saveThoughtRecord(record: ThoughtRecord): Long =
        if (record.id == 0L) thoughts.insert(record) else record.id.also { thoughts.update(record) }

    override suspend fun deleteThoughtRecord(id: Long) = thoughts.delete(id)
    override suspend fun completedThoughtRecordsBetween(from: Long, to: Long) = thoughts.completedBetween(from, to)

    override fun observeMoodEntries() = moods.observeAll()
    override fun observeMoodEntry(id: Long) = moods.observe(id)
    override suspend fun getMoodEntry(id: Long) = moods.get(id)

    override suspend fun saveMoodEntry(entry: MoodEntry): Long =
        if (entry.id == 0L) moods.insert(entry) else entry.id.also { moods.update(entry) }

    override suspend fun deleteMoodEntry(id: Long) = moods.delete(id)
    override suspend fun moodEntriesBetween(from: Long, to: Long) = moods.between(from, to)

    override fun observeEntryCount(): Flow<Int> =
        kotlinx.coroutines.flow.combine(thoughts.observeCompletedCount(), moods.observeCount()) { a, b -> a + b }

    override suspend fun exportAll(now: Long) = db.withTransaction {
        BackupData(
            createdAt = now,
            thoughtRecords = thoughts.getAll(),
            moodEntries = moods.getAll(),
            activities = db.activities().getAll(),
            sleepEntries = db.sleep().getAll(),
            exposureLadders = db.exposure().getAllLadders(),
            exposureSteps = db.exposure().getAllSteps(),
            exposureAttempts = db.exposure().getAllAttempts(),
            experiments = db.experiments().getAll(),
            worries = db.worries().getAll(),
            problems = db.problems().getAll(),
        )
    }

    /** One transaction: a failed restore leaves the old entries untouched. */
    override suspend fun replaceAll(data: BackupData) {
        db.withTransaction {
            thoughts.deleteAll()
            moods.deleteAll()
            db.activities().deleteAll()
            db.sleep().deleteAll()
            db.exposure().deleteAll()
            db.experiments().deleteAll()
            db.worries().deleteAll()
            db.problems().deleteAll()
            // Parents before children (exposure).
            thoughts.insertAll(data.thoughtRecords)
            moods.insertAll(data.moodEntries)
            db.activities().insertAll(data.activities)
            db.sleep().insertAll(data.sleepEntries)
            db.exposure().insertLadders(data.exposureLadders)
            db.exposure().insertSteps(data.exposureSteps)
            db.exposure().insertAttempts(data.exposureAttempts)
            db.experiments().insertAll(data.experiments)
            db.worries().insertAll(data.worries)
            db.problems().insertAll(data.problems)
        }
    }

    /** Also runs VACUUM, so deleted text does not linger in free pages of the file. */
    override suspend fun deleteAll() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
