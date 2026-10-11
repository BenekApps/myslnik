package com.benekapps.myslnik.ui.thought

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.hotThought
import com.benekapps.myslnik.domain.Distortions
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.LabeledText
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.formatDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailState<out T> {
    data object Loading : DetailState<Nothing>
    data object Missing : DetailState<Nothing>
    data class Loaded<T>(val value: T) : DetailState<T>
}

class ThoughtDetailViewModel(private val repo: EntriesRepository, private val id: Long) : ViewModel() {
    val state: StateFlow<DetailState<ThoughtRecord>> = repo.observeThoughtRecord(id)
        .map { if (it == null) DetailState.Missing else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    /** The screen closes itself when the record disappears. */
    fun delete() {
        viewModelScope.launch { repo.deleteThoughtRecord(id) }
    }
}

@Composable
fun ThoughtDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: ThoughtDetailViewModel = appViewModel { c ->
        ThoughtDetailViewModel(c.entries, createSavedStateHandle().get<Long>(ThoughtWizardViewModel.ARG_ID) ?: 0L)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val record = (state as? DetailState.Loaded)?.value
    if (state == DetailState.Missing) {
        // Deleted (here or elsewhere): leave the screen once.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    ScreenScaffold(
        title = stringResource(R.string.tr_title),
        onSos = onSos,
        onBack = onBack,
        actions = {
            if (record != null) {
                IconButton(onClick = { onEdit(record.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) { padding ->
        if (record == null) return@ScreenScaffold
        ScrollingContent(padding) {
            ThoughtRecordContent(record)
            if (record.isDraft) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = { onEdit(record.id) }) { Text(stringResource(R.string.continue_draft)) }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tr_delete_title),
            text = stringResource(R.string.tr_delete_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ThoughtRecordContent(record: ThoughtRecord) {
    val res = LocalResources.current
    Text(
        formatDateTime(record.createdAt),
        style = MaterialTheme.typography.titleMedium,
    )
    if (record.isDraft) {
        Text(
            stringResource(R.string.draft),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
    LabeledText(stringResource(R.string.tr_field_situation), record.situation)
    LabeledText(
        stringResource(R.string.tr_field_emotions),
        record.emotions.joinToString("\n") { e ->
            val after = e.intensityAfter?.let { " → $it" } ?: ""
            "${Emotions.label(res, e.emotion)}: ${e.intensityBefore}$after"
        },
    )
    val hot = record.hotThought()
    LabeledText(
        stringResource(R.string.tr_field_thoughts),
        record.automaticThoughts.joinToString("\n") { t ->
            if (t == hot) "• $t (${res.getString(R.string.tr_hot_thought).lowercase()})" else "• $t"
        },
    )
    LabeledText(
        stringResource(R.string.tr_field_distortions),
        record.distortions.joinToString(", ") { Distortions.name(res, it) },
    )
    LabeledText(stringResource(R.string.tr_field_evidence_for), record.evidenceFor)
    LabeledText(stringResource(R.string.tr_field_evidence_against), record.evidenceAgainst)
    LabeledText(stringResource(R.string.tr_field_alternative), record.alternativeThought)
}
