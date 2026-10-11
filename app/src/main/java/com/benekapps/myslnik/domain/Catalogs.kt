package com.benekapps.myslnik.domain

import android.content.res.Resources
import androidx.annotation.StringRes
import com.benekapps.myslnik.R

data class EmotionDef(val key: String, @param:StringRes val label: Int)

/**
 * Emotions offered in the thought record and mood check-in.
 * Entries store the key, so labels can be reworded or translated later.
 */
object Emotions {
    const val CUSTOM_PREFIX = "custom:"
    const val CUSTOM_MAX_LENGTH = 40

    val all: List<EmotionDef> = listOf(
        EmotionDef("anxiety", R.string.emotion_anxiety),
        EmotionDef("worry", R.string.emotion_worry),
        EmotionDef("sadness", R.string.emotion_sadness),
        EmotionDef("anger", R.string.emotion_anger),
        EmotionDef("irritation", R.string.emotion_irritation),
        EmotionDef("shame", R.string.emotion_shame),
        EmotionDef("guilt", R.string.emotion_guilt),
        EmotionDef("frustration", R.string.emotion_frustration),
        EmotionDef("disappointment", R.string.emotion_disappointment),
        EmotionDef("loneliness", R.string.emotion_loneliness),
        EmotionDef("helplessness", R.string.emotion_helplessness),
        EmotionDef("hurt", R.string.emotion_hurt),
        EmotionDef("jealousy", R.string.emotion_jealousy),
        EmotionDef("tension", R.string.emotion_tension),
        EmotionDef("calm", R.string.emotion_calm),
        EmotionDef("joy", R.string.emotion_joy),
        EmotionDef("relief", R.string.emotion_relief),
        EmotionDef("hope", R.string.emotion_hope),
        EmotionDef("gratitude", R.string.emotion_gratitude),
        EmotionDef("pride", R.string.emotion_pride),
    )

    private val byKey = all.associateBy { it.key }

    fun custom(text: String): String = CUSTOM_PREFIX + text.trim().take(CUSTOM_MAX_LENGTH)

    fun isCustom(key: String) = key.startsWith(CUSTOM_PREFIX)

    fun label(resources: Resources, key: String): String = when {
        isCustom(key) -> key.removePrefix(CUSTOM_PREFIX)
        else -> byKey[key]?.let { resources.getString(it.label) } ?: key
    }
}

data class DistortionDef(
    val key: String,
    @param:StringRes val name: Int,
    @param:StringRes val description: Int,
    @param:StringRes val example: Int,
)

/** Common cognitive distortions (after Beck and Burns). Texts need CBT review. */
object Distortions {
    val all: List<DistortionDef> = listOf(
        DistortionDef("all_or_nothing", R.string.distortion_all_or_nothing, R.string.distortion_all_or_nothing_desc, R.string.distortion_all_or_nothing_example),
        DistortionDef("overgeneralization", R.string.distortion_overgeneralization, R.string.distortion_overgeneralization_desc, R.string.distortion_overgeneralization_example),
        DistortionDef("mental_filter", R.string.distortion_mental_filter, R.string.distortion_mental_filter_desc, R.string.distortion_mental_filter_example),
        DistortionDef("disqualifying_positive", R.string.distortion_disqualifying_positive, R.string.distortion_disqualifying_positive_desc, R.string.distortion_disqualifying_positive_example),
        DistortionDef("mind_reading", R.string.distortion_mind_reading, R.string.distortion_mind_reading_desc, R.string.distortion_mind_reading_example),
        DistortionDef("fortune_telling", R.string.distortion_fortune_telling, R.string.distortion_fortune_telling_desc, R.string.distortion_fortune_telling_example),
        DistortionDef("catastrophizing", R.string.distortion_catastrophizing, R.string.distortion_catastrophizing_desc, R.string.distortion_catastrophizing_example),
        DistortionDef("emotional_reasoning", R.string.distortion_emotional_reasoning, R.string.distortion_emotional_reasoning_desc, R.string.distortion_emotional_reasoning_example),
        DistortionDef("should_statements", R.string.distortion_should_statements, R.string.distortion_should_statements_desc, R.string.distortion_should_statements_example),
        DistortionDef("labeling", R.string.distortion_labeling, R.string.distortion_labeling_desc, R.string.distortion_labeling_example),
        DistortionDef("personalization", R.string.distortion_personalization, R.string.distortion_personalization_desc, R.string.distortion_personalization_example),
        DistortionDef("blaming", R.string.distortion_blaming, R.string.distortion_blaming_desc, R.string.distortion_blaming_example),
    )

    private val byKey = all.associateBy { it.key }

    fun name(resources: Resources, key: String): String =
        byKey[key]?.let { resources.getString(it.name) } ?: key
}

/**
 * When to gently show help numbers. Only the numeric ratings are used:
 * the app never reads the text of entries (privacy first).
 */
object CrisisCheck {
    const val EMOTION_THRESHOLD = 90
    const val MOOD_THRESHOLD = 1

    fun isEmotionCritical(intensity: Int) = intensity >= EMOTION_THRESHOLD
    fun isMoodCritical(rating: Int) = rating <= MOOD_THRESHOLD
}

data class Helpline(val number: String, val name: String, val description: String, val hours: String) {
    /** Number for the dialer, without spaces. */
    val dialNumber: String get() = number.filter { it.isDigit() || it == '+' }
}

/** Help numbers live in res/values/helplines.xml with their verification date. */
object Helplines {
    fun load(resources: Resources): List<Helpline> {
        val numbers = resources.getStringArray(R.array.helpline_numbers)
        val names = resources.getStringArray(R.array.helpline_names)
        val descriptions = resources.getStringArray(R.array.helpline_descriptions)
        val hours = resources.getStringArray(R.array.helpline_hours)
        check(numbers.size == names.size && names.size == descriptions.size && descriptions.size == hours.size) {
            "helplines.xml arrays must have the same length"
        }
        return numbers.indices.map { Helpline(numbers[it], names[it], descriptions[it], hours[it]) }
    }

    /** The 3 numbers shown in short help cards: crisis, adult support, emergency. */
    fun short(resources: Resources): List<Helpline> = load(resources).take(3)
}
