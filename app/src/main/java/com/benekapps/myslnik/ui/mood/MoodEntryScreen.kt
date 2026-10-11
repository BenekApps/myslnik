package com.benekapps.myslnik.ui.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.domain.CrisisCheck
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.CrisisSupportCard
import com.benekapps.myslnik.ui.components.EmotionPicker
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoodEntryUiState(
    val loading: Boolean = true,
    val entry: MoodEntry? = null,
    val rating: Int? = null,
    val emotions: List<String> = emptyList(),
    val note: String = "",
    val crisisVisible: Boolean = false,
    val saved: Boolean = false,
)

class MoodEntryViewModel(
    private val repo: EntriesRepository,
    savedStateHandle: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>(ARG_ID) ?: 0L
    private val _state = MutableStateFlow(MoodEntryUiState())
    val state: StateFlow<MoodEntryUiState> = _state.asStateFlow()
    private var crisisShown = false

    init {
        viewModelScope.launch {
            val existing = if (id > 0) repo.getMoodEntry(id) else null
            _state.value = MoodEntryUiState(
                loading = false,
                entry = existing,
                rating = existing?.rating,
                emotions = existing?.emotions.orEmpty(),
                note = existing?.note.orEmpty(),
            )
        }
    }

    fun setRating(rating: Int) {
        _state.update { it.copy(rating = rating) }
        if (!crisisShown && CrisisCheck.isMoodCritical(rating)) {
            crisisShown = true
            _state.update { it.copy(crisisVisible = true) }
        }
    }

    fun toggleEmotion(key: String) = _state.update {
        it.copy(emotions = if (key in it.emotions) it.emotions - key else it.emotions + key)
    }

    fun setNote(text: String) = _state.update { it.copy(note = text.take(NOTE_MAX_LENGTH)) }
    fun dismissCrisis() = _state.update { it.copy(crisisVisible = false) }

    fun save() {
        val s = _state.value
        val rating = s.rating ?: return
        viewModelScope.launch {
            val time = now()
            val entry = s.entry?.copy(updatedAt = time, rating = rating, emotions = s.emotions, note = s.note.trim())
                ?: MoodEntry(createdAt = time, updatedAt = time, rating = rating, emotions = s.emotions, note = s.note.trim())
            repo.saveMoodEntry(entry)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val NOTE_MAX_LENGTH = 1000
    }
}

@Composable
fun MoodEntryScreen(
    onDone: () -> Unit,
    onSos: () -> Unit,
    viewModel: MoodEntryViewModel = appViewModel { c -> MoodEntryViewModel(c.entries, createSavedStateHandle()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    ScreenScaffold(
        title = stringResource(if (state.entry == null) R.string.mood_new_title else R.string.mood_edit_title),
        onSos = onSos,
        onBack = onDone,
    ) { padding ->
        if (state.loading) return@ScreenScaffold
        ScrollingContent(padding) {
            Text(stringResource(R.string.mood_question), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.mood_scale_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            RatingSelector(value = state.rating, onSelect = viewModel::setRating)
            if (state.crisisVisible) {
                Spacer(Modifier.height(16.dp))
                CrisisSupportCard(onOpenSos = onSos, onDismiss = viewModel::dismissCrisis)
            }
            SectionTitle(stringResource(R.string.mood_emotions_title))
            EmotionPicker(selected = state.emotions, onToggle = viewModel::toggleEmotion)
            SectionTitle(stringResource(R.string.mood_note_title))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.mood_note_label)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::save,
                enabled = state.rating != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.save))
            }
            if (state.rating == null) {
                Text(
                    stringResource(R.string.mood_choose_rating_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 0–10 as large buttons; easier than a slider for many people. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSelector(value: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..10).forEach { n ->
            val description = stringResource(R.string.mood_rating_description, n)
            val buttonModifier = Modifier
                .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
                .semantics {
                    contentDescription = description
                    selected = value == n
                    role = Role.RadioButton
                }
            if (value == n) {
                Button(onClick = { onSelect(n) }, modifier = buttonModifier, contentPadding = PaddingValues(4.dp)) { Text(n.toString()) }
            } else {
                OutlinedButton(onClick = { onSelect(n) }, modifier = buttonModifier, contentPadding = PaddingValues(4.dp)) { Text(n.toString()) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            stringResource(R.string.mood_scale_low),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.mood_scale_high), style = MaterialTheme.typography.bodySmall)
    }
}
