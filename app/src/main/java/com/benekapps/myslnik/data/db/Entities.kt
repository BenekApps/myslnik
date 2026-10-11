package com.benekapps.myslnik.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
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
