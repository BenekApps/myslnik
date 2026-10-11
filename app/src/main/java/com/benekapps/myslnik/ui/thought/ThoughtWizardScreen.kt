package com.benekapps.myslnik.ui.thought

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.hotThought
import com.benekapps.myslnik.domain.Distortions
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.ContentMaxWidth
import com.benekapps.myslnik.ui.components.CrisisSupportCard
import com.benekapps.myslnik.ui.components.EmotionPicker
import com.benekapps.myslnik.ui.components.IntensitySlider
import com.benekapps.myslnik.ui.components.ScreenScaffold

private val stepTitles = intArrayOf(
    R.string.tr_step_situation_title, R.string.tr_step_emotions_title, R.string.tr_step_thoughts_title,
    R.string.tr_step_distortions_title, R.string.tr_step_evidence_for_title, R.string.tr_step_evidence_against_title,
    R.string.tr_step_alternative_title,
)
private val stepHints = intArrayOf(
    R.string.tr_step_situation_hint, R.string.tr_step_emotions_hint, R.string.tr_step_thoughts_hint,
    R.string.tr_step_distortions_hint, R.string.tr_step_evidence_for_hint, R.string.tr_step_evidence_against_hint,
    R.string.tr_step_alternative_hint,
)
private val stepExamples = intArrayOf(
    R.string.tr_step_situation_example, R.string.tr_step_emotions_example, R.string.tr_step_thoughts_example,
    R.string.tr_step_distortions_example, R.string.tr_step_evidence_for_example, R.string.tr_step_evidence_against_example,
    R.string.tr_step_alternative_example,
)

@Composable
fun ThoughtWizardScreen(
    onClose: (savedId: Long?) -> Unit,
    onSos: () -> Unit,
    viewModel: ThoughtWizardViewModel = appViewModel { c ->
        ThoughtWizardViewModel(c.entries, createSavedStateHandle(), c.appScope)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.closed) {
        if (state.closed) onClose(state.closedWithId)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveDraft() }
    BackHandler(enabled = !state.closed) {
        if (!viewModel.back()) onClose(null)
    }

    ScreenScaffold(
        title = stringResource(R.string.tr_title),
        onSos = onSos,
        onBack = { if (!viewModel.back()) onClose(null) },
        actions = {
            if (state.record.id != 0L || !state.record.isEmptyForUi()) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tr_delete))
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val step = state.step
            LinearProgressIndicator(
                progress = { (step + 1) / ThoughtWizardViewModel.STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (state.crisisVisible) {
                        CrisisSupportCard(onOpenSos = onSos, onDismiss = viewModel::dismissCrisis)
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        stringResource(R.string.tr_step_counter, step + 1, ThoughtWizardViewModel.STEP_COUNT),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(stepTitles[step]),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                    Text(stringResource(stepHints[step]), style = MaterialTheme.typography.bodyLarge)
                    ExampleToggle(step, stringResource(stepExamples[step]))

                    if (step >= 3) {
                        state.record.hotThought()?.let { HotThoughtCard(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (step) {
                        0 -> MultilineField(
                            value = state.record.situation,
                            onValueChange = viewModel::setSituation,
                            label = stringResource(R.string.tr_field_situation),
                        )
                        1 -> EmotionsStep(state.record, viewModel)
                        2 -> ThoughtsStep(state, viewModel)
                        3 -> DistortionsStep(state.record, viewModel::toggleDistortion)
                        4 -> MultilineField(
                            value = state.record.evidenceFor,
                            onValueChange = viewModel::setEvidenceFor,
                            label = stringResource(R.string.tr_field_evidence_for),
                        )
                        5 -> MultilineField(
                            value = state.record.evidenceAgainst,
                            onValueChange = viewModel::setEvidenceAgainst,
                            label = stringResource(R.string.tr_field_evidence_against),
                        )
                        else -> AlternativeStep(state.record, viewModel)
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { viewModel.back() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tr_back))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (step == ThoughtWizardViewModel.LAST_STEP) R.string.tr_finish else R.string.tr_next))
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

private fun ThoughtRecord.isEmptyForUi() =
    situation.isBlank() && emotions.isEmpty() && automaticThoughts.isEmpty()

@Composable
private fun ExampleToggle(step: Int, example: String) {
    var visible by rememberSaveable(step) { mutableStateOf(false) }
    TextButton(onClick = { visible = !visible }, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(if (visible) R.string.tr_hide_example else R.string.tr_show_example))
    }
    if (visible) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                example,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun HotThoughtCard(thought: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.tr_hot_thought), style = MaterialTheme.typography.labelLarge)
            Text("„$thought”", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MultilineField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmotionsStep(record: ThoughtRecord, viewModel: ThoughtWizardViewModel) {
    val resources = LocalResources.current
    EmotionPicker(selected = record.emotions.map { it.emotion }, onToggle = viewModel::toggleEmotion)
    if (record.emotions.isEmpty()) {
        Text(
            stringResource(R.string.tr_emotions_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    } else {
        Text(
            stringResource(R.string.tr_emotions_rate),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        record.emotions.forEach { e ->
            IntensitySlider(
                label = Emotions.label(resources, e.emotion),
                value = e.intensityBefore,
                onValueChange = { viewModel.setIntensityBefore(e.emotion, it) },
            )
        }
    }
}

@Composable
private fun ThoughtsStep(state: WizardUiState, viewModel: ThoughtWizardViewModel) {
    val record = state.record
    OutlinedTextField(
        value = state.pendingThought,
        onValueChange = viewModel::setPendingThought,
        label = { Text(stringResource(R.string.tr_thought_label)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.addPendingThought() }),
        modifier = Modifier.fillMaxWidth(),
    )
    FilledTonalButton(
        onClick = viewModel::addPendingThought,
        enabled = state.pendingThought.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.tr_add_thought), modifier = Modifier.padding(start = 6.dp))
    }
    if (record.automaticThoughts.size > 1) {
        Text(
            stringResource(R.string.tr_choose_hot_thought),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    record.automaticThoughts.forEachIndexed { index, thought ->
        val selected = record.hotThoughtIndex == index
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.setHotThought(index) }),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(thought, style = MaterialTheme.typography.bodyLarge)
                if (selected) {
                    Text(
                        stringResource(R.string.tr_hot_thought),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = { viewModel.removeThought(index) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tr_remove_thought, thought))
            }
        }
    }
}

@Composable
private fun DistortionsStep(record: ThoughtRecord, onToggle: (String) -> Unit) {
    Distortions.all.forEach { d ->
        val checked = d.key in record.distortions
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (checked) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                Modifier
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(d.key) })
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(stringResource(d.name), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(d.description), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(d.example),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlternativeStep(record: ThoughtRecord, viewModel: ThoughtWizardViewModel) {
    val resources = LocalResources.current
    MultilineField(
        value = record.alternativeThought,
        onValueChange = viewModel::setAlternativeThought,
        label = stringResource(R.string.tr_field_alternative),
    )
    if (record.emotions.isNotEmpty()) {
        Text(
            stringResource(R.string.tr_rerate_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        record.emotions.forEach { e ->
            IntensitySlider(
                label = stringResource(
                    R.string.tr_rerate_label,
                    Emotions.label(resources, e.emotion),
                    e.intensityBefore,
                ),
                value = e.intensityAfter ?: e.intensityBefore,
                onValueChange = { viewModel.setIntensityAfter(e.emotion, it) },
            )
        }
    }
}
