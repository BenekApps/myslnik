package com.benekapps.myslnik.ui.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.benekapps.myslnik.R
import com.benekapps.myslnik.domain.Exercise
import com.benekapps.myslnik.domain.KnowledgeCards
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle

private data class ExerciseItem(val exercise: Exercise, val title: Int, val description: Int, val icon: ImageVector)

private val groups = listOf(
    R.string.exercises_group_thoughts to listOf(
        ExerciseItem(Exercise.THOUGHT_RECORD, R.string.tr_title, R.string.exercise_thought_desc, Icons.Filled.Edit),
        ExerciseItem(Exercise.MOOD, R.string.nav_mood, R.string.exercise_mood_desc, Icons.Filled.Face),
        ExerciseItem(Exercise.EXPERIMENTS, R.string.experiments_title, R.string.exercise_experiments_desc, Icons.Filled.Search),
    ),
    R.string.exercises_group_action to listOf(
        ExerciseItem(Exercise.ACTIVITIES, R.string.activities_title, R.string.exercise_activities_desc, Icons.Filled.CheckCircle),
        ExerciseItem(Exercise.EXPOSURE, R.string.exposure_title, R.string.exercise_exposure_desc, Icons.Filled.KeyboardArrowUp),
        ExerciseItem(Exercise.PROBLEM_SOLVING, R.string.problems_title, R.string.exercise_problems_desc, Icons.Filled.Build),
    ),
    R.string.exercises_group_daily to listOf(
        ExerciseItem(Exercise.WORRY_TIME, R.string.worry_title, R.string.exercise_worry_desc, Icons.Filled.DateRange),
        ExerciseItem(Exercise.SLEEP, R.string.sleep_title, R.string.exercise_sleep_desc, Icons.Filled.Star),
    ),
)

/** Every exercise one tap away (two from the start screen). */
@Composable
fun ExercisesScreen(onOpen: (Exercise) -> Unit, onSos: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.nav_exercises), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            groups.forEach { (groupTitle, items) ->
                SectionTitle(stringResource(groupTitle))
                items.forEach { item -> ExerciseCard(item, onClick = { onOpen(item.exercise) }) }
            }
        }
    }
}

@Composable
private fun ExerciseCard(item: ExerciseItem, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(item.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** The psychoeducation library. */
@Composable
fun KnowledgeScreen(onOpenCard: (String) -> Unit, onOpenModel: () -> Unit, onSos: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.nav_knowledge), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.knowledge_intro), style = MaterialTheme.typography.bodyLarge)
            KnowledgeRow(stringResource(R.string.model_title), stringResource(R.string.model_card_text), onOpenModel)
            KnowledgeCards.all.forEach { card ->
                KnowledgeRow(stringResource(card.title), stringResource(card.summary)) { onOpenCard(card.key) }
            }
        }
    }
}

@Composable
private fun KnowledgeRow(title: String, summary: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun KnowledgeCardScreen(key: String, onBack: () -> Unit, onOpenExercise: (Exercise) -> Unit, onSos: () -> Unit) {
    val card = KnowledgeCards.byKey(key)
    ScreenScaffold(title = card?.let { stringResource(it.title) } ?: "", onSos = onSos, onBack = onBack) { padding ->
        if (card == null) return@ScreenScaffold
        ScrollingContent(padding) {
            stringArrayResource(card.paragraphs).forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
            }
            card.exercise?.let { ex ->
                Button(onClick = { onOpenExercise(ex) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(if (ex == Exercise.SOS) R.string.knowledge_open_sos else R.string.knowledge_try))
                }
            }
            Text(
                stringResource(R.string.knowledge_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
