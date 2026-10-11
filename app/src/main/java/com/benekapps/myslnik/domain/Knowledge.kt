package com.benekapps.myslnik.domain

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.benekapps.myslnik.R

/** Where a card's "try it" button leads. */
enum class Exercise { THOUGHT_RECORD, MOOD, ACTIVITIES, SLEEP, EXPOSURE, EXPERIMENTS, WORRY_TIME, PROBLEM_SOLVING, SOS }

/**
 * A psychoeducation card. Texts live in res/values/strings_cards.xml
 * (and values-uk), so the reviewer reads them in one place.
 */
data class KnowledgeCard(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:ArrayRes val paragraphs: Int,
    val exercise: Exercise? = null,
)

object KnowledgeCards {
    val all: List<KnowledgeCard> = listOf(
        KnowledgeCard("cbt", R.string.card_cbt_title, R.string.card_cbt_summary, R.array.card_cbt_paragraphs),
        KnowledgeCard("automatic_thoughts", R.string.card_thoughts_title, R.string.card_thoughts_summary, R.array.card_thoughts_paragraphs, Exercise.THOUGHT_RECORD),
        KnowledgeCard("emotions", R.string.card_emotions_title, R.string.card_emotions_summary, R.array.card_emotions_paragraphs, Exercise.MOOD),
        KnowledgeCard("anxiety", R.string.card_anxiety_title, R.string.card_anxiety_summary, R.array.card_anxiety_paragraphs),
        KnowledgeCard("avoidance", R.string.card_avoidance_title, R.string.card_avoidance_summary, R.array.card_avoidance_paragraphs, Exercise.EXPOSURE),
        KnowledgeCard("activation", R.string.card_activation_title, R.string.card_activation_summary, R.array.card_activation_paragraphs, Exercise.ACTIVITIES),
        KnowledgeCard("experiments", R.string.card_experiments_title, R.string.card_experiments_summary, R.array.card_experiments_paragraphs, Exercise.EXPERIMENTS),
        KnowledgeCard("worry", R.string.card_worry_title, R.string.card_worry_summary, R.array.card_worry_paragraphs, Exercise.WORRY_TIME),
        KnowledgeCard("problem_solving", R.string.card_problems_title, R.string.card_problems_summary, R.array.card_problems_paragraphs, Exercise.PROBLEM_SOLVING),
        KnowledgeCard("sleep", R.string.card_sleep_title, R.string.card_sleep_summary, R.array.card_sleep_paragraphs, Exercise.SLEEP),
        KnowledgeCard("self_kindness", R.string.card_kindness_title, R.string.card_kindness_summary, R.array.card_kindness_paragraphs),
        KnowledgeCard("getting_help", R.string.card_help_title, R.string.card_help_summary, R.array.card_help_paragraphs, Exercise.SOS),
    )

    fun byKey(key: String): KnowledgeCard? = all.firstOrNull { it.key == key }
}
