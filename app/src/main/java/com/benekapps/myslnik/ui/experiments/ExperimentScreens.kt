package com.benekapps.myslnik.ui.experiments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.BehavioralExperiment
import com.benekapps.myslnik.data.db.ExperimentDao
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.HintText
import com.benekapps.myslnik.ui.components.NoteField
import com.benekapps.myslnik.ui.components.ScaleSlider
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.formatDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExperimentsViewModel(dao: ExperimentDao) : ViewModel() {
    val experiments: StateFlow<List<BehavioralExperiment>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ExperimentsScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: ExperimentsViewModel = appViewModel { c -> ExperimentsViewModel(c.db.experiments()) },
) {
    val list by viewModel.experiments.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.experiments_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.experiments_intro))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.experiments_new), modifier = Modifier.padding(start = 8.dp))
            }
            if (list?.isEmpty() == true) HintText(stringResource(R.string.experiments_empty), Modifier.padding(top = 16.dp))
            list.orEmpty().forEach { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(e.id) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(e.belief, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    val status = stringResource(if (e.outcome.isBlank()) R.string.experiments_planned else R.string.experiments_done)
                    val belief = if (e.beliefBefore != null && e.beliefAfter != null) {
                        " · " + stringResource(R.string.experiments_belief_change, e.beliefBefore, e.beliefAfter)
                    } else {
                        ""
                    }
                    Text(
                        "$status · ${formatDateTime(e.createdAt)}$belief",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

data class ExperimentFormState(
    val loading: Boolean = true,
    val existing: BehavioralExperiment? = null,
    val form: BehavioralExperiment = BehavioralExperiment(createdAt = 0, updatedAt = 0, belief = "", beliefBefore = 70),
    val closed: Boolean = false,
)

class ExperimentViewModel(
    private val dao: ExperimentDao,
    savedState: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(ExperimentFormState())
    val state: StateFlow<ExperimentFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (id > 0) dao.get(id) else null
            _state.value = ExperimentFormState(
                loading = false,
                existing = existing,
                form = existing ?: BehavioralExperiment(createdAt = now(), updatedAt = now(), belief = "", beliefBefore = 70),
            )
        }
    }

    fun edit(transform: (BehavioralExperiment) -> BehavioralExperiment) = _state.update { it.copy(form = transform(it.form)) }

    fun save() {
        val f = _state.value.form
        if (f.belief.isBlank()) return
        viewModelScope.launch {
            dao.upsert(
                f.copy(
                    updatedAt = now(),
                    belief = f.belief.trim(),
                    plan = f.plan.trim(),
                    prediction = f.prediction.trim(),
                    obstacles = f.obstacles.trim(),
                    outcome = f.outcome.trim(),
                    learned = f.learned.trim(),
                ),
            )
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val e = _state.value.existing ?: return
        viewModelScope.launch {
            dao.delete(e.id)
            _state.update { it.copy(closed = true) }
        }
    }
}

@Composable
fun ExperimentScreen(
    onDone: () -> Unit,
    onSos: () -> Unit,
    viewModel: ExperimentViewModel = appViewModel { c -> ExperimentViewModel(c.db.experiments(), createSavedStateHandle()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onDone() }
    ScreenScaffold(
        title = stringResource(R.string.experiments_title),
        onSos = onSos,
        onBack = onDone,
        actions = {
            if (state.existing != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) { padding ->
        if (state.loading) return@ScreenScaffold
        val f = state.form
        ScrollingContent(padding) {
            SectionTitle(stringResource(R.string.experiment_part_plan))
            HintText(stringResource(R.string.experiment_belief_hint))
            NoteField(f.belief, { v -> viewModel.edit { it.copy(belief = v) } }, stringResource(R.string.experiment_belief), minLines = 2)
            ScaleSlider(
                stringResource(R.string.experiment_belief_before),
                f.beliefBefore ?: 0,
                { v -> viewModel.edit { it.copy(beliefBefore = v) } },
                max = 100,
                step = 5,
                valueText = "${f.beliefBefore ?: 0}%",
                modifier = Modifier.padding(top = 8.dp),
            )
            HintText(stringResource(R.string.experiment_plan_hint), Modifier.padding(top = 12.dp))
            NoteField(f.plan, { v -> viewModel.edit { it.copy(plan = v) } }, stringResource(R.string.experiment_plan))
            HintText(stringResource(R.string.experiment_prediction_hint), Modifier.padding(top = 12.dp))
            NoteField(f.prediction, { v -> viewModel.edit { it.copy(prediction = v) } }, stringResource(R.string.experiment_prediction))
            HintText(stringResource(R.string.experiment_obstacles_hint), Modifier.padding(top = 12.dp))
            NoteField(f.obstacles, { v -> viewModel.edit { it.copy(obstacles = v) } }, stringResource(R.string.experiment_obstacles), minLines = 2)

            SectionTitle(stringResource(R.string.experiment_part_result))
            HintText(stringResource(R.string.experiment_result_hint))
            NoteField(f.outcome, { v -> viewModel.edit { it.copy(outcome = v) } }, stringResource(R.string.experiment_outcome))
            NoteField(f.learned, { v -> viewModel.edit { it.copy(learned = v) } }, stringResource(R.string.experiment_learned), Modifier.padding(top = 8.dp))
            val rateAfter = f.beliefAfter != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .toggleable(
                        value = rateAfter,
                        role = Role.Checkbox,
                        onValueChange = { on -> viewModel.edit { it.copy(beliefAfter = if (on) (it.beliefBefore ?: 50) else null) } },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rateAfter, onCheckedChange = null)
                Text(stringResource(R.string.experiment_rate_after), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
            f.beliefAfter?.let { after ->
                ScaleSlider(
                    stringResource(R.string.experiment_belief_after),
                    after,
                    { v -> viewModel.edit { it.copy(beliefAfter = v) } },
                    max = 100,
                    step = 5,
                    valueText = "$after%",
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = f.belief.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.experiments_delete_title),
            text = stringResource(R.string.delete_cannot_undo),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
