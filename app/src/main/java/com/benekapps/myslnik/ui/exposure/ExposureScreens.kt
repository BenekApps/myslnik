package com.benekapps.myslnik.ui.exposure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.ExposureAttempt
import com.benekapps.myslnik.data.db.ExposureDao
import com.benekapps.myslnik.data.db.ExposureLadder
import com.benekapps.myslnik.data.db.ExposureStep
import com.benekapps.myslnik.data.db.LadderWithCount
import com.benekapps.myslnik.data.db.StepWithStats
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.HintText
import com.benekapps.myslnik.ui.components.IntensitySlider
import com.benekapps.myslnik.ui.components.NoteField
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.formatDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Anxiety during exposure is expected to be high; the crisis card is not shown
// here on purpose (see docs/DECISIONS.md). SOS stays in the top bar.

class LaddersViewModel(private val dao: ExposureDao, private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    val ladders: StateFlow<List<LadderWithCount>?> = dao.observeLadders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(title: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val time = now()
        onCreated(dao.upsertLadder(ExposureLadder(createdAt = time, updatedAt = time, title = title.trim())))
    }
}

@Composable
fun ExposureLaddersScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: LaddersViewModel = appViewModel { c -> LaddersViewModel(c.db.exposure()) },
) {
    val ladders by viewModel.ladders.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.exposure_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.exposure_intro))
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.exposure_new_ladder), modifier = Modifier.padding(start = 8.dp))
            }
            val list = ladders.orEmpty()
            if (ladders != null && list.isEmpty()) HintText(stringResource(R.string.exposure_empty), Modifier.padding(top = 16.dp))
            list.forEach { l ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(l.id) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(l.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.exposure_steps_count, l.steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
    if (creating) {
        TextDialog(
            title = stringResource(R.string.exposure_new_ladder),
            label = stringResource(R.string.exposure_ladder_label),
            hint = stringResource(R.string.exposure_ladder_hint),
            onConfirm = {
                creating = false
                viewModel.create(it, onOpen)
            },
            onDismiss = { creating = false },
        )
    }
}

class LadderViewModel(private val dao: ExposureDao, private val id: Long, private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    val ladder: StateFlow<ExposureLadder?> = dao.observeLadder(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val steps: StateFlow<List<StepWithStats>?> = dao.observeSteps(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(title: String) = viewModelScope.launch {
        dao.getLadder(id)?.let { dao.upsertLadder(it.copy(title = title.trim(), updatedAt = now())) }
    }

    fun addStep(description: String, expectedAnxiety: Int) = viewModelScope.launch {
        val time = now()
        dao.upsertStep(ExposureStep(ladderId = id, createdAt = time, updatedAt = time, description = description.trim(), expectedAnxiety = expectedAnxiety))
        dao.touchLadder(id, time)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        dao.deleteLadder(id)
        onDone()
    }
}

@Composable
fun ExposureLadderScreen(
    onBack: () -> Unit,
    onOpenStep: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: LadderViewModel = appViewModel { c -> LadderViewModel(c.db.exposure(), createSavedStateHandle().get<Long>("id") ?: 0L) },
) {
    val ladder by viewModel.ladder.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(
        title = ladder?.title ?: stringResource(R.string.exposure_title),
        onSos = onSos,
        onBack = onBack,
        actions = {
            IconButton(onClick = { dialog = DIALOG_RENAME }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.exposure_rename))
            }
            IconButton(onClick = { dialog = DIALOG_DELETE }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        },
    ) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.exposure_ladder_intro))
            OutlinedButton(onClick = { dialog = DIALOG_STEP }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.exposure_add_step), modifier = Modifier.padding(start = 8.dp))
            }
            val list = steps.orEmpty()
            if (steps != null && list.isEmpty()) HintText(stringResource(R.string.exposure_no_steps), Modifier.padding(top = 16.dp))
            if (list.isNotEmpty()) SectionTitle(stringResource(R.string.exposure_steps_title))
            // Lowest expected anxiety first: start at the bottom of the ladder.
            list.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStep(s.id) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.expectedAnxiety.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(56.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(s.description, style = MaterialTheme.typography.bodyLarge)
                        val last = if (s.lastBefore != null && s.lastAfter != null) {
                            " · " + stringResource(R.string.exposure_last_attempt, s.lastBefore, s.lastAfter)
                        } else {
                            ""
                        }
                        Text(
                            stringResource(R.string.exposure_attempts_count, s.attempts) + last,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            if (list.isNotEmpty()) HintText(stringResource(R.string.exposure_expected_legend), Modifier.padding(top = 8.dp))
        }
    }
    when (dialog) {
        DIALOG_STEP -> StepDialog(
            onConfirm = { text, anxiety ->
                dialog = 0
                viewModel.addStep(text, anxiety)
            },
            onDismiss = { dialog = 0 },
        )
        DIALOG_RENAME -> TextDialog(
            title = stringResource(R.string.exposure_rename),
            label = stringResource(R.string.exposure_ladder_label),
            initial = ladder?.title.orEmpty(),
            onConfirm = {
                dialog = 0
                viewModel.rename(it)
            },
            onDismiss = { dialog = 0 },
        )
        DIALOG_DELETE -> ConfirmDialog(
            title = stringResource(R.string.exposure_delete_ladder),
            text = stringResource(R.string.exposure_delete_ladder_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                dialog = 0
                viewModel.delete(onBack)
            },
            onDismiss = { dialog = 0 },
        )
    }
}

class StepViewModel(private val dao: ExposureDao, private val id: Long, private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    val step: StateFlow<ExposureStep?> = dao.observeStep(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val attempts: StateFlow<List<ExposureAttempt>?> = dao.observeAttempts(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addAttempt(before: Int, after: Int, note: String) = viewModelScope.launch {
        val time = now()
        dao.insertAttempt(ExposureAttempt(stepId = id, createdAt = time, updatedAt = time, anxietyBefore = before, anxietyAfter = after, note = note.trim()))
        dao.getStep(id)?.let { dao.touchLadder(it.ladderId, time) }
    }

    fun update(description: String, expected: Int) = viewModelScope.launch {
        dao.getStep(id)?.let { dao.upsertStep(it.copy(description = description.trim(), expectedAnxiety = expected, updatedAt = now())) }
    }

    fun deleteAttempt(attemptId: Long) = viewModelScope.launch { dao.deleteAttempt(attemptId) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        dao.deleteStep(id)
        onDone()
    }
}

@Composable
fun ExposureStepScreen(
    onBack: () -> Unit,
    onSos: () -> Unit,
    viewModel: StepViewModel = appViewModel { c -> StepViewModel(c.db.exposure(), createSavedStateHandle().get<Long>("id") ?: 0L) },
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val attempts by viewModel.attempts.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(
        title = stringResource(R.string.exposure_step_title),
        onSos = onSos,
        onBack = onBack,
        actions = {
            IconButton(onClick = { dialog = DIALOG_RENAME }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = { dialog = DIALOG_DELETE }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        },
    ) { padding ->
        val s = step ?: return@ScreenScaffold
        ScrollingContent(padding) {
            Text(s.description, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.exposure_expected_value, s.expectedAnxiety),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            HintText(stringResource(R.string.exposure_step_hint), Modifier.padding(top = 12.dp))
            Button(onClick = { dialog = DIALOG_STEP }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.exposure_add_attempt))
            }
            val list = attempts.orEmpty()
            if (list.isNotEmpty()) SectionTitle(stringResource(R.string.exposure_attempts_title))
            list.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDateTime(a.createdAt), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.exposure_attempt_values, a.anxietyBefore, a.anxietyAfter), style = MaterialTheme.typography.titleMedium)
                        if (a.note.isNotBlank()) Text(a.note, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { viewModel.deleteAttempt(a.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.exposure_delete_attempt))
                    }
                }
                HorizontalDivider()
            }
        }
    }
    when (dialog) {
        DIALOG_STEP -> AttemptDialog(
            onConfirm = { before, after, note ->
                dialog = 0
                viewModel.addAttempt(before, after, note)
            },
            onDismiss = { dialog = 0 },
        )
        DIALOG_RENAME -> step?.let { s ->
            StepDialog(
                initialText = s.description,
                initialAnxiety = s.expectedAnxiety,
                onConfirm = { text, anxiety ->
                    dialog = 0
                    viewModel.update(text, anxiety)
                },
                onDismiss = { dialog = 0 },
            )
        }
        DIALOG_DELETE -> ConfirmDialog(
            title = stringResource(R.string.exposure_delete_step),
            text = stringResource(R.string.delete_cannot_undo),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                dialog = 0
                viewModel.delete(onBack)
            },
            onDismiss = { dialog = 0 },
        )
    }
}

private const val DIALOG_STEP = 1
private const val DIALOG_RENAME = 2
private const val DIALOG_DELETE = 3

@Composable
fun TextDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    hint: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                hint?.let { HintText(it) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StepDialog(
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
    initialAnxiety: Int = 50,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var anxiety by rememberSaveable { mutableIntStateOf(initialAnxiety) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exposure_step_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.exposure_step_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                IntensitySlider(stringResource(R.string.exposure_expected), anxiety, { anxiety = it }, Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text, anxiety) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AttemptDialog(onConfirm: (Int, Int, String) -> Unit, onDismiss: () -> Unit) {
    var before by rememberSaveable { mutableIntStateOf(50) }
    var after by rememberSaveable { mutableIntStateOf(50) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exposure_add_attempt)) },
        text = {
            Column {
                IntensitySlider(stringResource(R.string.exposure_before), before, { before = it })
                IntensitySlider(stringResource(R.string.exposure_after), after, { after = it })
                NoteField(note, { note = it }, stringResource(R.string.exposure_attempt_note), Modifier.padding(top = 8.dp), minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(before, after, note) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
