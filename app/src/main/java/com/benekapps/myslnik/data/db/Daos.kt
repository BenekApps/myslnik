package com.benekapps.myslnik.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ThoughtRecordDao {
    @Query("SELECT * FROM thought_records ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ThoughtRecord>>

    @Query("SELECT * FROM thought_records WHERE id = :id")
    fun observe(id: Long): Flow<ThoughtRecord?>

    @Query("SELECT * FROM thought_records WHERE is_draft = 1 ORDER BY updated_at DESC LIMIT 1")
    fun observeLatestDraft(): Flow<ThoughtRecord?>

    @Query("SELECT * FROM thought_records WHERE id = :id")
    suspend fun get(id: Long): ThoughtRecord?

    @Query(
        "SELECT * FROM thought_records WHERE is_draft = 0 AND created_at BETWEEN :from AND :to " +
            "ORDER BY created_at ASC",
    )
    suspend fun completedBetween(from: Long, to: Long): List<ThoughtRecord>

    @Query("SELECT * FROM thought_records ORDER BY id")
    suspend fun getAll(): List<ThoughtRecord>

    @Query("SELECT COUNT(*) FROM thought_records WHERE is_draft = 0")
    fun observeCompletedCount(): Flow<Int>

    @Insert
    suspend fun insert(record: ThoughtRecord): Long

    @Insert
    suspend fun insertAll(records: List<ThoughtRecord>)

    @Update
    suspend fun update(record: ThoughtRecord)

    @Query("DELETE FROM thought_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM thought_records")
    suspend fun deleteAll()
}

@Dao
interface MoodEntryDao {
    @Query("SELECT * FROM mood_entries ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries WHERE id = :id")
    fun observe(id: Long): Flow<MoodEntry?>

    @Query("SELECT * FROM mood_entries WHERE id = :id")
    suspend fun get(id: Long): MoodEntry?

    @Query("SELECT * FROM mood_entries WHERE created_at BETWEEN :from AND :to ORDER BY created_at ASC")
    suspend fun between(from: Long, to: Long): List<MoodEntry>

    @Query("SELECT * FROM mood_entries ORDER BY id")
    suspend fun getAll(): List<MoodEntry>

    @Query("SELECT COUNT(*) FROM mood_entries")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(entry: MoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<MoodEntry>)

    @Update
    suspend fun update(entry: MoodEntry)

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM mood_entries")
    suspend fun deleteAll()
}
