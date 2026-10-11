package com.benekapps.myslnik.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("DELETE FROM activities")
    suspend fun deleteAll()

    @Query("SELECT * FROM activities WHERE day = :day ORDER BY time_minutes IS NULL, time_minutes, id")
    fun observeDay(day: Long): Flow<List<PlannedActivity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): PlannedActivity?

    @Query("SELECT * FROM activities WHERE day BETWEEN :fromDay AND :toDay ORDER BY day, time_minutes IS NULL, time_minutes, id")
    suspend fun between(fromDay: Long, toDay: Long): List<PlannedActivity>

    @Query("SELECT * FROM activities WHERE day BETWEEN :fromDay AND :toDay AND done = 1")
    fun observeDoneBetween(fromDay: Long, toDay: Long): Flow<List<PlannedActivity>>

    @Query("SELECT * FROM activities ORDER BY id")
    suspend fun getAll(): List<PlannedActivity>

    @Upsert
    suspend fun upsert(activity: PlannedActivity): Long

    @Insert
    suspend fun insertAll(activities: List<PlannedActivity>)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SleepDao {
    @Query("DELETE FROM sleep_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM sleep_entries ORDER BY night DESC, id DESC")
    fun observeAll(): Flow<List<SleepEntry>>

    @Query("SELECT * FROM sleep_entries WHERE id = :id")
    suspend fun get(id: Long): SleepEntry?

    @Query("SELECT * FROM sleep_entries WHERE night BETWEEN :fromNight AND :toNight ORDER BY night")
    suspend fun between(fromNight: Long, toNight: Long): List<SleepEntry>

    @Query("SELECT * FROM sleep_entries ORDER BY id")
    suspend fun getAll(): List<SleepEntry>

    @Upsert
    suspend fun upsert(entry: SleepEntry): Long

    @Insert
    suspend fun insertAll(entries: List<SleepEntry>)

    @Query("DELETE FROM sleep_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

data class StepWithStats(
    val id: Long,
    val description: String,
    val expectedAnxiety: Int,
    val attempts: Int,
    val lastBefore: Int?,
    val lastAfter: Int?,
)

data class LadderWithCount(val id: Long, val title: String, val steps: Int, val updatedAt: Long)

@Dao
interface ExposureDao {
    /** Children first. */
    @Transaction
    suspend fun deleteAll() {
        deleteAllAttempts()
        deleteAllSteps()
        deleteAllLadders()
    }

    @Query("DELETE FROM exposure_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM exposure_steps")
    suspend fun deleteAllSteps()

    @Query("DELETE FROM exposure_ladders")
    suspend fun deleteAllLadders()

    @Query(
        "SELECT l.id, l.title, l.updated_at AS updatedAt, (SELECT COUNT(*) FROM exposure_steps s WHERE s.ladder_id = l.id) AS steps " +
            "FROM exposure_ladders l ORDER BY l.updated_at DESC",
    )
    fun observeLadders(): Flow<List<LadderWithCount>>

    @Query("SELECT * FROM exposure_ladders WHERE id = :id")
    fun observeLadder(id: Long): Flow<ExposureLadder?>

    @Query(
        "SELECT s.id, s.description, s.expected_anxiety AS expectedAnxiety, " +
            "(SELECT COUNT(*) FROM exposure_attempts a WHERE a.step_id = s.id) AS attempts, " +
            "(SELECT a.anxiety_before FROM exposure_attempts a WHERE a.step_id = s.id ORDER BY a.created_at DESC LIMIT 1) AS lastBefore, " +
            "(SELECT a.anxiety_after FROM exposure_attempts a WHERE a.step_id = s.id ORDER BY a.created_at DESC LIMIT 1) AS lastAfter " +
            "FROM exposure_steps s WHERE s.ladder_id = :ladderId ORDER BY s.expected_anxiety, s.id",
    )
    fun observeSteps(ladderId: Long): Flow<List<StepWithStats>>

    @Query("SELECT * FROM exposure_steps WHERE id = :id")
    fun observeStep(id: Long): Flow<ExposureStep?>

    @Query("SELECT * FROM exposure_attempts WHERE step_id = :stepId ORDER BY created_at DESC")
    fun observeAttempts(stepId: Long): Flow<List<ExposureAttempt>>

    @Query("SELECT * FROM exposure_attempts WHERE created_at BETWEEN :from AND :to ORDER BY created_at")
    suspend fun attemptsBetween(from: Long, to: Long): List<ExposureAttempt>

    @Query("SELECT * FROM exposure_ladders ORDER BY id")
    suspend fun getAllLadders(): List<ExposureLadder>

    @Query("SELECT * FROM exposure_steps ORDER BY id")
    suspend fun getAllSteps(): List<ExposureStep>

    @Query("SELECT * FROM exposure_attempts ORDER BY id")
    suspend fun getAllAttempts(): List<ExposureAttempt>

    @Upsert
    suspend fun upsertLadder(ladder: ExposureLadder): Long

    @Upsert
    suspend fun upsertStep(step: ExposureStep): Long

    @Insert
    suspend fun insertAttempt(attempt: ExposureAttempt): Long

    @Insert
    suspend fun insertLadders(ladders: List<ExposureLadder>)

    @Insert
    suspend fun insertSteps(steps: List<ExposureStep>)

    @Insert
    suspend fun insertAttempts(attempts: List<ExposureAttempt>)

    @Query("SELECT * FROM exposure_ladders WHERE id = :id")
    suspend fun getLadder(id: Long): ExposureLadder?

    @Query("SELECT * FROM exposure_steps WHERE id = :id")
    suspend fun getStep(id: Long): ExposureStep?

    @Query("UPDATE exposure_ladders SET updated_at = :time WHERE id = :id")
    suspend fun touchLadder(id: Long, time: Long)

    @Query("DELETE FROM exposure_attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)

    /** Children are deleted explicitly, so this works with or without foreign key enforcement. */
    @Transaction
    suspend fun deleteStep(id: Long) {
        deleteAttemptsOfStep(id)
        deleteStepRow(id)
    }

    @Transaction
    suspend fun deleteLadder(id: Long) {
        deleteAttemptsOfLadder(id)
        deleteStepsOfLadder(id)
        deleteLadderRow(id)
    }

    @Query("DELETE FROM exposure_attempts WHERE step_id = :stepId")
    suspend fun deleteAttemptsOfStep(stepId: Long)

    @Query("DELETE FROM exposure_steps WHERE id = :id")
    suspend fun deleteStepRow(id: Long)

    @Query("DELETE FROM exposure_attempts WHERE step_id IN (SELECT id FROM exposure_steps WHERE ladder_id = :ladderId)")
    suspend fun deleteAttemptsOfLadder(ladderId: Long)

    @Query("DELETE FROM exposure_steps WHERE ladder_id = :ladderId")
    suspend fun deleteStepsOfLadder(ladderId: Long)

    @Query("DELETE FROM exposure_ladders WHERE id = :id")
    suspend fun deleteLadderRow(id: Long)
}

@Dao
interface ExperimentDao {
    @Query("DELETE FROM experiments")
    suspend fun deleteAll()

    @Query("SELECT * FROM experiments ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BehavioralExperiment>>

    @Query("SELECT * FROM experiments WHERE id = :id")
    suspend fun get(id: Long): BehavioralExperiment?

    @Query("SELECT * FROM experiments WHERE created_at BETWEEN :from AND :to ORDER BY created_at")
    suspend fun between(from: Long, to: Long): List<BehavioralExperiment>

    @Query("SELECT * FROM experiments ORDER BY id")
    suspend fun getAll(): List<BehavioralExperiment>

    @Upsert
    suspend fun upsert(experiment: BehavioralExperiment): Long

    @Insert
    suspend fun insertAll(experiments: List<BehavioralExperiment>)

    @Query("DELETE FROM experiments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WorryDao {
    @Query("DELETE FROM worries")
    suspend fun deleteAll()

    @Query("SELECT * FROM worries WHERE status = 'OPEN' ORDER BY created_at")
    fun observeOpen(): Flow<List<Worry>>

    @Query("SELECT COUNT(*) FROM worries WHERE status != 'OPEN'")
    fun observeClosedCount(): Flow<Int>

    @Query("SELECT * FROM worries ORDER BY id")
    suspend fun getAll(): List<Worry>

    @Insert
    suspend fun insert(worry: Worry): Long

    @Insert
    suspend fun insertAll(worries: List<Worry>)

    @Update
    suspend fun update(worry: Worry)

    @Query("DELETE FROM worries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM worries WHERE status != 'OPEN'")
    suspend fun deleteClosed()
}

@Dao
interface ProblemDao {
    @Query("DELETE FROM problems")
    suspend fun deleteAll()

    @Query("SELECT * FROM problems ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ProblemSolving>>

    @Query("SELECT * FROM problems WHERE id = :id")
    fun observe(id: Long): Flow<ProblemSolving?>

    @Query("SELECT * FROM problems WHERE id = :id")
    suspend fun get(id: Long): ProblemSolving?

    @Query("SELECT * FROM problems WHERE is_draft = 0 AND created_at BETWEEN :from AND :to ORDER BY created_at")
    suspend fun completedBetween(from: Long, to: Long): List<ProblemSolving>

    @Query("SELECT * FROM problems ORDER BY id")
    suspend fun getAll(): List<ProblemSolving>

    @Upsert
    suspend fun upsert(problem: ProblemSolving): Long

    @Insert
    suspend fun insertAll(problems: List<ProblemSolving>)

    @Query("DELETE FROM problems WHERE id = :id")
    suspend fun delete(id: Long)
}
