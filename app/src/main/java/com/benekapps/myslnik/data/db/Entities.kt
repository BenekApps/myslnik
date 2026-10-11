package com.benekapps.myslnik.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One emotion in a thought record, rated 0–100 before and (optionally)
 * after the alternative thought.
 *
 * [emotion] is a key from [com.benekapps.myslnik.domain.Emotions] or a
 * custom label prefixed with [com.benekapps.myslnik.domain.Emotions.CUSTOM_PREFIX].
 */
data class EmotionRating(
    val emotion: String,
    val intensityBefore: Int,
    val intensityAfter: Int? = null,
)

/**
 * A thought record (Beck's dysfunctional thought record, 7 columns).
 * Drafts are stored in the same table so the wizard can resume after
 * every step.
 */
@Entity(tableName = "thought_records", indices = [Index("created_at"), Index("is_draft")])
data class ThoughtRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_draft") val isDraft: Boolean = true,
    /** Wizard step to resume a draft at (0-based). */
    @ColumnInfo(name = "draft_step") val draftStep: Int = 0,
    val situation: String = "",
    val emotions: List<EmotionRating> = emptyList(),
    @ColumnInfo(name = "automatic_thoughts") val automaticThoughts: List<String> = emptyList(),
    @ColumnInfo(name = "hot_thought_index") val hotThoughtIndex: Int? = null,
    /** Keys from [com.benekapps.myslnik.domain.Distortions]. */
    val distortions: List<String> = emptyList(),
    @ColumnInfo(name = "evidence_for") val evidenceFor: String = "",
    @ColumnInfo(name = "evidence_against") val evidenceAgainst: String = "",
    @ColumnInfo(name = "alternative_thought") val alternativeThought: String = "",
)

/** The thought the user marked as "hot", or the only one there is. */
fun ThoughtRecord.hotThought(): String? =
    hotThoughtIndex?.let { automaticThoughts.getOrNull(it) }
        ?: automaticThoughts.singleOrNull()

/** True when nothing has been entered yet (such drafts are not saved). */
fun ThoughtRecord.isEmpty(): Boolean =
    situation.isBlank() && emotions.isEmpty() && automaticThoughts.isEmpty() &&
        distortions.isEmpty() && evidenceFor.isBlank() && evidenceAgainst.isBlank() &&
        alternativeThought.isBlank()

/** A mood check-in: rating 0–10, emotions and a short note. */
@Entity(tableName = "mood_entries", indices = [Index("created_at")])
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val rating: Int,
    val emotions: List<String> = emptyList(),
    val note: String = "",
)

// ---- 1.1: behavioural activation and sleep diary ----

/** A planned activity (behavioural activation), rated after it is done. */
@Entity(tableName = "activities", indices = [Index("day")])
data class PlannedActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** [java.time.LocalDate.toEpochDay]. */
    val day: Long,
    /** Minutes from midnight, or null for "any time of the day". */
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int? = null,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    /** 0–10, rated after doing it. */
    val pleasure: Int? = null,
    /** 0–10, sense of achievement ("poczucie sprawstwa"). */
    val mastery: Int? = null,
)

/**
 * One night in the sleep diary. Times are minutes from noon of [night]
 * (0 = 12:00, 720 = midnight, 1439 = 11:59 the next day), so a night never
 * crosses the end of the scale and daylight saving does not matter.
 */
@Entity(tableName = "sleep_entries", indices = [Index("night")])
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Epoch day of the evening the night started. */
    val night: Long,
    @ColumnInfo(name = "in_bed") val inBed: Int,
    @ColumnInfo(name = "tried_to_sleep") val triedToSleep: Int,
    @ColumnInfo(name = "minutes_to_fall_asleep") val minutesToFallAsleep: Int,
    val awakenings: Int,
    @ColumnInfo(name = "minutes_awake") val minutesAwake: Int,
    @ColumnInfo(name = "final_wake") val finalWake: Int,
    @ColumnInfo(name = "out_of_bed") val outOfBed: Int,
    /** 1 (very poor) – 5 (very good). */
    val quality: Int,
    val note: String = "",
)

// ---- 1.2: exposure, experiments, worry time, problem solving ----

@Entity(tableName = "exposure_ladders")
data class ExposureLadder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** What the ladder is about, e.g. "Jazda autobusem". */
    val title: String,
    val goal: String = "",
)

@Entity(
    tableName = "exposure_steps",
    foreignKeys = [
        ForeignKey(
            entity = ExposureLadder::class,
            parentColumns = ["id"],
            childColumns = ["ladder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ladder_id")],
)
data class ExposureStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ladder_id") val ladderId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val description: String,
    /** Expected anxiety 0–100; the ladder is sorted by it. */
    @ColumnInfo(name = "expected_anxiety") val expectedAnxiety: Int,
)

@Entity(
    tableName = "exposure_attempts",
    foreignKeys = [
        ForeignKey(
            entity = ExposureStep::class,
            parentColumns = ["id"],
            childColumns = ["step_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("step_id"), Index("created_at")],
)
data class ExposureAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "step_id") val stepId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Anxiety 0–100 just before and after the exposure. */
    @ColumnInfo(name = "anxiety_before") val anxietyBefore: Int,
    @ColumnInfo(name = "anxiety_after") val anxietyAfter: Int,
    val note: String = "",
)

/** A behavioural experiment (Bennett-Levy et al.): plan, then outcome. */
@Entity(tableName = "experiments", indices = [Index("created_at")])
data class BehavioralExperiment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** The thought being tested. */
    val belief: String,
    /** How strongly it is believed, 0–100 %. */
    @ColumnInfo(name = "belief_before") val beliefBefore: Int? = null,
    val plan: String = "",
    val prediction: String = "",
    val obstacles: String = "",
    val outcome: String = "",
    val learned: String = "",
    @ColumnInfo(name = "belief_after") val beliefAfter: Int? = null,
)

enum class WorryStatus { OPEN, LET_GO, PROBLEM_SOLVING }

/** A worry noted during the day and postponed to the worry time. */
@Entity(tableName = "worries", indices = [Index("created_at")])
data class Worry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val text: String,
    val status: WorryStatus = WorryStatus.OPEN,
)

data class SolutionOption(val text: String, val pros: String = "", val cons: String = "")

/** Structured problem solving. Drafts work like thought records. */
@Entity(tableName = "problems", indices = [Index("created_at")])
data class ProblemSolving(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_draft") val isDraft: Boolean = true,
    @ColumnInfo(name = "draft_step") val draftStep: Int = 0,
    val problem: String = "",
    val solutions: List<SolutionOption> = emptyList(),
    @ColumnInfo(name = "chosen_index") val chosenIndex: Int? = null,
    val plan: String = "",
    val review: String = "",
)

fun ProblemSolving.chosenSolution(): SolutionOption? = chosenIndex?.let { solutions.getOrNull(it) }

fun ProblemSolving.isEmpty(): Boolean =
    problem.isBlank() && solutions.isEmpty() && plan.isBlank() && review.isBlank()
