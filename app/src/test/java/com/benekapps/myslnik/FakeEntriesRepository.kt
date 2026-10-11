package com.benekapps.myslnik

import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.backup.BackupData
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory repository for ViewModel tests. */
class FakeEntriesRepository : EntriesRepository {
    val thoughts = MutableStateFlow<List<ThoughtRecord>>(emptyList())
    val moods = MutableStateFlow<List<MoodEntry>>(emptyList())
    var saveCount = 0
        private set
    private var nextId = 1L

    override fun observeThoughtRecords(): Flow<List<ThoughtRecord>> = thoughts
    override fun observeThoughtRecord(id: Long) = thoughts.map { list -> list.firstOrNull { it.id == id } }
    override fun observeLatestDraft() = thoughts.map { list -> list.filter { it.isDraft }.maxByOrNull { it.updatedAt } }
    override suspend fun getThoughtRecord(id: Long) = thoughts.value.firstOrNull { it.id == id }

    override suspend fun saveThoughtRecord(record: ThoughtRecord): Long {
        saveCount++
        val saved = if (record.id == 0L) record.copy(id = nextId++) else record
        thoughts.value = thoughts.value.filterNot { it.id == saved.id } + saved
        return saved.id
    }

    override suspend fun deleteThoughtRecord(id: Long) {
        thoughts.value = thoughts.value.filterNot { it.id == id }
    }

    override suspend fun completedThoughtRecordsBetween(from: Long, to: Long) =
        thoughts.value.filter { !it.isDraft && it.createdAt in from..to }

    override fun observeMoodEntries(): Flow<List<MoodEntry>> = moods
    override fun observeMoodEntry(id: Long) = moods.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getMoodEntry(id: Long) = moods.value.firstOrNull { it.id == id }

    override suspend fun saveMoodEntry(entry: MoodEntry): Long {
        val saved = if (entry.id == 0L) entry.copy(id = nextId++) else entry
        moods.value = moods.value.filterNot { it.id == saved.id } + saved
        return saved.id
    }

    override suspend fun deleteMoodEntry(id: Long) {
        moods.value = moods.value.filterNot { it.id == id }
    }

    override suspend fun moodEntriesBetween(from: Long, to: Long) = moods.value.filter { it.createdAt in from..to }

    override fun observeEntryCount() = combine(thoughts, moods) { t, m -> t.count { !it.isDraft } + m.size }

    override suspend fun exportAll(now: Long) = BackupData(now, thoughts.value, moods.value)

    override suspend fun replaceAll(data: BackupData) {
        thoughts.value = data.thoughtRecords
        moods.value = data.moodEntries
    }

    override suspend fun deleteAll() {
        thoughts.value = emptyList()
        moods.value = emptyList()
    }
}
