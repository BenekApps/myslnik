package com.benekapps.myslnik.ui.problems

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.ProblemDao
import com.benekapps.myslnik.data.db.ProblemSolving
import com.benekapps.myslnik.data.db.SolutionOption
import com.benekapps.myslnik.data.db.chosenSolution
import com.benekapps.myslnik.data.db.isEmpty
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.ContentMaxWidth
import com.benekapps.myslnik.ui.components.HintText
import com.benekapps.myslnik.ui.components.LabeledText
import com.benekapps.myslnik.ui.components.NoteField
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.formatDateTime
import com.benekapps.myslnik.ui.thought.DetailState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProblemsViewModel(dao: ProblemDao) : ViewModel() {
    val problems: StateFlow<List<ProblemSolving>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ProblemsScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (ProblemSolving) -> Unit,
    onSos: () -> Unit,
    viewModel: ProblemsViewModel = appViewModel { c -> ProblemsViewModel(c.db.problems()) },
) {
    val problems by viewModel.problems.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.problems_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.problems_intro))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.problems_new), modifier = Modifier.padding(start = 8.dp))
            }
            val list = problems.orEmpty()
            if (problems != null && list.isEmpty()) HintText(stringResource(R.string.problems_empty), Modifier.padding(top = 16.dp))
            list.forEach { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(p) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(p.problem.ifBlank { stringResource(R.string.problems_untitled) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    val label = stringResource(if (p.isDraft) R.string.problems_draft else R.string.problems_title_single)
                    Text(
                        "$label · ${formatDateTime(p.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.isDraft) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

// ---- Wizard ----

data class ProblemWizardState(
    val loading: Boolean = true,
    val problem: ProblemSolving = ProblemSolving(createdAt = 0, updatedAt = 0),
    val step: Int = 0,
    val pendingSolution: String = "",
    val closed: Boolean = false,
    val closedWithId: Long? = null,
)

/** Same draft rules as the thought record: saved after every step and when leaving. */
class ProblemWizardViewModel(
    private val dao: ProblemDao,
    savedState: SavedStateHandle,
    private val persistScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val requestedId: Long = savedState.get<Long>("id") ?: 0L
    private val initialText: String = savedState.get<String>("text").orEmpty()
    private val _state = MutableStateFlow(ProblemWizardState())
    val state: StateFlow<ProblemWizardState> = _state.asStateFlow()
    private val mutex = Mutex()

    init {
        viewModelScope.launch {
            val existing = if (requestedId > 0) dao.get(requestedId) else null
            val time = now()
            _state.value = if (existing != null) {
                ProblemWizardState(loading = false, problem = existing, step = if (existing.isDraft) existing.draftStep.coerceIn(0, LAST_STEP) else 0)
            } else {
                ProblemWizardState(loading = false, problem = ProblemSolving(createdAt = time, updatedAt = time, problem = initialText))
            }
        }
    }

    private fun edit(transform: (ProblemSolving) -> ProblemSolving) = _state.update { it.copy(problem = transform(it.problem)) }

    fun setProblem(v: String) = edit { it.copy(problem = v) }
    fun setPendingSolution(v: String) = _state.update { it.copy(pendingSolution = v) }

    fun addPendingSolution() {
        val text = _state.value.pendingSolution.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(pendingSolution = "", problem = it.problem.copy(solutions = it.problem.solutions + SolutionOption(text))) }
    }

    fun removeSolution(index: Int) = edit { p ->
        val chosen = p.chosenIndex
        p.copy(
            solutions = p.solutions.filterIndexed { i, _ -> i != index },
            chosenIndex = when {
                chosen == null || chosen == index -> null
                chosen > index -> chosen - 1
                else -> chosen
            },
        )
    }

    fun setPros(index: Int, v: String) = edit { p -> p.copy(solutions = p.solutions.mapIndexed { i, s -> if (i == index) s.copy(pros = v) else s }) }
    fun setCons(index: Int, v: String) = edit { p -> p.copy(solutions = p.solutions.mapIndexed { i, s -> if (i == index) s.copy(cons = v) else s }) }
    fun choose(index: Int) = edit { it.copy(chosenIndex = index) }
    fun setPlan(v: String) = edit { it.copy(plan = v) }
    fun setReview(v: String) = edit { it.copy(review = v) }

    fun next() {
        addPendingSolution()
        val step = _state.value.step
        if (step >= LAST_STEP) return finish()
        _state.update { it.copy(step = step + 1) }
        saveDraft()
    }

    fun back(): Boolean {
        val step = _state.value.step
        if (step == 0) return false
        _state.update { it.copy(step = step - 1) }
        saveDraft()
        return true
    }

    fun saveDraft() = persistScope.launch { persist(complete = false) }

    fun finish() {
        addPendingSolution()
        persistScope.launch {
            if (_state.value.problem.isEmpty()) {
                _state.update { it.copy(closed = true) }
                return@launch
            }
            val id = persist(complete = true)
            _state.update { it.copy(closed = true, closedWithId = id) }
        }
    }

    fun delete() {
        persistScope.launch {
            mutex.withLock {
                val id = _state.value.problem.id
                if (id != 0L) dao.delete(id)
                _state.update { it.copy(closed = true) }
            }
        }
    }

    override fun onCleared() {
        if (!_state.value.closed) saveDraft()
    }

    private suspend fun persist(complete: Boolean): Long? = mutex.withLock {
        val s = _state.value
        if (s.loading || (s.closed && !complete)) return null
        val p = s.problem
        if (p.id == 0L && p.isEmpty()) return null
        val toSave = p.copy(updatedAt = now(), draftStep = s.step, isDraft = p.isDraft && !complete)
        val id = dao.upsert(toSave).let { if (it == -1L) p.id else it }
        _state.update { it.copy(problem = it.problem.copy(id = id, isDraft = toSave.isDraft)) }
        id
    }

    companion object {
        const val STEP_COUNT = 6
        const val LAST_STEP = STEP_COUNT - 1
    }
}

private val stepTitles = intArrayOf(
    R.string.problem_step_define_title, R.string.problem_step_solutions_title, R.string.problem_step_pros_cons_title,
    R.string.problem_step_choose_title, R.string.problem_step_plan_title, R.string.problem_step_review_title,
)
private val stepHints = intArrayOf(
    R.string.problem_step_define_hint, R.string.problem_step_solutions_hint, R.string.problem_step_pros_cons_hint,
    R.string.problem_step_choose_hint, R.string.problem_step_plan_hint, R.string.problem_step_review_hint,
)

@Composable
fun ProblemWizardScreen(
    onClose: (savedId: Long?) -> Unit,
    onSos: () -> Unit,
    viewModel: ProblemWizardViewModel = appViewModel { c -> ProblemWizardViewModel(c.db.problems(), createSavedStateHandle(), c.appScope) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose(state.closedWithId) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveDraft() }
    BackHandler(enabled = !state.closed) { if (!viewModel.back()) onClose(null) }

    ScreenScaffold(
        title = stringResource(R.string.problems_title_single),
        onSos = onSos,
        onBack = { if (!viewModel.back()) onClose(null) },
        actions = {
            if (state.problem.id != 0L || !state.problem.isEmpty()) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.problems_delete))
                }
            }
        },
    ) { padding ->
        if (state.loading) return@ScreenScaffold
        val step = state.step
        val p = state.problem
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(progress = { (step + 1) / ProblemWizardViewModel.STEP_COUNT.toFloat() }, modifier = Modifier.fillMaxWidth())
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.tr_step_counter, step + 1, ProblemWizardViewModel.STEP_COUNT),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(stepTitles[step]),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { heading() },
                    )
                    Text(stringResource(stepHints[step]), style = MaterialTheme.typography.bodyLarge)
                    if (step > 0 && p.problem.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.problem_field_problem), style = MaterialTheme.typography.labelLarge)
                                Text(p.problem, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Spacer(Modifier.padding(top = 12.dp))
                    when (step) {
                        0 -> NoteField(p.problem, viewModel::setProblem, stringResource(R.string.problem_field_problem), minLines = 3)
                        1 -> SolutionsStep(state, viewModel)
                        2 -> ProsConsStep(p, viewModel)
                        3 -> ChooseStep(p, viewModel)
                        4 -> {
                            p.chosenSolution()?.let { LabeledText(stringResource(R.string.problem_field_chosen), it.text) }
                            NoteField(p.plan, viewModel::setPlan, stringResource(R.string.problem_field_plan), Modifier.padding(top = 8.dp), minLines = 4)
                        }
                        else -> NoteField(p.review, viewModel::setReview, stringResource(R.string.problem_field_review), minLines = 4)
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { viewModel.back() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tr_back)) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (step == ProblemWizardViewModel.LAST_STEP) R.string.tr_finish else R.string.tr_next))
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.problems_delete_title),
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

@Composable
private fun SolutionsStep(state: ProblemWizardState, viewModel: ProblemWizardViewModel) {
    OutlinedTextField(
        value = state.pendingSolution,
        onValueChange = viewModel::setPendingSolution,
        label = { Text(stringResource(R.string.problem_solution_label)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.addPendingSolution() }),
        modifier = Modifier.fillMaxWidth(),
    )
    FilledTonalButton(onClick = viewModel::addPendingSolution, enabled = state.pendingSolution.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.problem_add_solution), modifier = Modifier.padding(start = 6.dp))
    }
    state.problem.solutions.forEachIndexed { i, s ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}. ${s.text}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.removeSolution(i) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.problem_remove_solution, s.text))
            }
        }
    }
}

@Composable
private fun ProsConsStep(p: ProblemSolving, viewModel: ProblemWizardViewModel) {
    if (p.solutions.isEmpty()) HintText(stringResource(R.string.problem_no_solutions))
    p.solutions.forEachIndexed { i, s ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("${i + 1}. ${s.text}", style = MaterialTheme.typography.titleSmall)
                NoteField(s.pros, { viewModel.setPros(i, it) }, stringResource(R.string.problem_pros), Modifier.padding(top = 8.dp), minLines = 2)
                NoteField(s.cons, { viewModel.setCons(i, it) }, stringResource(R.string.problem_cons), Modifier.padding(top = 8.dp), minLines = 2)
            }
        }
    }
}

@Composable
private fun ChooseStep(p: ProblemSolving, viewModel: ProblemWizardViewModel) {
    if (p.solutions.isEmpty()) HintText(stringResource(R.string.problem_no_solutions))
    p.solutions.forEachIndexed { i, s ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .selectable(selected = p.chosenIndex == i, role = Role.RadioButton, onClick = { viewModel.choose(i) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = p.chosenIndex == i, onClick = null)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(s.text, style = MaterialTheme.typography.bodyLarge)
                if (s.pros.isNotBlank()) Text(stringResource(R.string.problem_pros_value, s.pros), style = MaterialTheme.typography.bodySmall)
                if (s.cons.isNotBlank()) Text(stringResource(R.string.problem_cons_value, s.cons), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---- Detail ----

class ProblemDetailViewModel(private val dao: ProblemDao, private val id: Long) : ViewModel() {
    val state: StateFlow<DetailState<ProblemSolving>> = dao.observe(id)
        .map { if (it == null) DetailState.Missing else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun delete() {
        viewModelScope.launch { dao.delete(id) }
    }
}

@Composable
fun ProblemDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: ProblemDetailViewModel = appViewModel { c -> ProblemDetailViewModel(c.db.problems(), createSavedStateHandle().get<Long>("id") ?: 0L) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (state == DetailState.Missing) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val p = (state as? DetailState.Loaded)?.value
    ScreenScaffold(
        title = stringResource(R.string.problems_title_single),
        onSos = onSos,
        onBack = onBack,
        actions = {
            if (p != null) {
                IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
        },
    ) { padding ->
        if (p == null) return@ScreenScaffold
        val res = LocalResources.current
        ScrollingContent(padding) {
            Text(formatDateTime(p.createdAt), style = MaterialTheme.typography.titleMedium)
            LabeledText(stringResource(R.string.problem_field_problem), p.problem)
            if (p.solutions.isNotEmpty()) {
                LabeledText(
                    stringResource(R.string.problem_field_solutions),
                    p.solutions.joinToString("\n\n") { s ->
                        buildString {
                            append("• ").append(s.text)
                            if (s.pros.isNotBlank()) append("\n").append(res.getString(R.string.problem_pros_value, s.pros))
                            if (s.cons.isNotBlank()) append("\n").append(res.getString(R.string.problem_cons_value, s.cons))
                        }
                    },
                )
            }
            p.chosenSolution()?.let { LabeledText(stringResource(R.string.problem_field_chosen), it.text) }
            LabeledText(stringResource(R.string.problem_field_plan), p.plan)
            LabeledText(stringResource(R.string.problem_field_review), p.review)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.problems_delete_title),
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
