package com.benekapps.myslnik.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.ActivityDao
import com.benekapps.myslnik.data.db.PlannedActivity
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.HintText
import com.benekapps.myslnik.ui.components.NoteField
import com.benekapps.myslnik.ui.components.ScaleSlider
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.TimeField
import com.benekapps.myslnik.ui.components.formatClock
import com.benekapps.myslnik.ui.components.formatDay
import com.benekapps.myslnik.ui.components.formatDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

const val ARG_DAY = "day"

data class WeekSummary(val done: Int, val pleasure: Double?, val mastery: Double?)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModel(
    private val dao: ActivityDao,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _day = MutableStateFlow(today())
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    val activities: StateFlow<List<PlannedActivity>?> = _day
        .flatMapLatest { dao.observeDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val week: StateFlow<WeekSummary?> = dao
        .observeDoneBetween(today().minusDays(6).toEpochDay(), today().toEpochDay())
        .map { done ->
            if (done.isEmpty()) {
                null
            } else {
                WeekSummary(
                    done = done.size,
                    pleasure = done.mapNotNull { it.pleasure }.takeIf { it.isNotEmpty() }?.average(),
                    mastery = done.mapNotNull { it.mastery }.takeIf { it.isNotEmpty() }?.average(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousDay() = _day.update { it.minusDays(1) }
    fun nextDay() = _day.update { it.plusDays(1) }
    fun goToToday() = _day.update { today() }
    fun isToday(date: LocalDate) = date == today()

    fun complete(activity: PlannedActivity, pleasure: Int, mastery: Int) = viewModelScope.launch {
        dao.upsert(activity.copy(done = true, pleasure = pleasure, mastery = mastery, updatedAt = now()))
    }

    fun undo(activity: PlannedActivity) = viewModelScope.launch {
        dao.upsert(activity.copy(done = false, pleasure = null, mastery = null, updatedAt = now()))
    }
}

@Composable
fun ActivitiesScreen(
    onBack: () -> Unit,
    onAdd: (day: Long) -> Unit,
    onEdit: (id: Long) -> Unit,
    onSos: () -> Unit,
    viewModel: ActivitiesViewModel = appViewModel { c -> ActivitiesViewModel(c.db.activities()) },
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    var rating by remember { mutableStateOf<PlannedActivity?>(null) }

    ScreenScaffold(title = stringResource(R.string.activities_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.activities_intro))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = viewModel::previousDay) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.day_previous))
                }
                Text(
                    dayLabel(day, viewModel.isToday(day)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = viewModel::nextDay) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.day_next))
                }
            }
            if (!viewModel.isToday(day)) {
                TextButton(onClick = viewModel::goToToday) { Text(stringResource(R.string.day_go_today)) }
            }
            Button(onClick = { onAdd(day.toEpochDay()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.activities_add), modifier = Modifier.padding(start = 8.dp))
            }
            val list = activities
            if (list != null && list.isEmpty()) {
                HintText(stringResource(R.string.activities_empty), Modifier.padding(top = 16.dp))
            }
            list?.forEach { a ->
                ActivityRow(
                    activity = a,
                    onToggle = { if (a.done) viewModel.undo(a) else rating = a },
                    onClick = { onEdit(a.id) },
                )
                HorizontalDivider()
            }
            week?.let { w ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.activities_week_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.activities_week_done, w.done), style = MaterialTheme.typography.bodyMedium)
                        if (w.pleasure != null && w.mastery != null) {
                            Text(
                                stringResource(R.string.activities_average, formatDecimal(w.pleasure), formatDecimal(w.mastery)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    rating?.let { a ->
        RateActivityDialog(
            title = a.title,
            onConfirm = { p, m ->
                rating = null
                viewModel.complete(a, p, m)
            },
            onDismiss = { rating = null },
        )
    }
}

@Composable
private fun dayLabel(day: LocalDate, isToday: Boolean): String =
    if (isToday) stringResource(R.string.day_today_value, formatDay(day)) else formatDay(day)

@Composable
private fun ActivityRow(activity: PlannedActivity, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val doneLabel = stringResource(R.string.activities_mark_done, activity.title)
        Checkbox(
            checked = activity.done,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = doneLabel },
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(activity.title, style = MaterialTheme.typography.bodyLarge)
            val details = buildList {
                activity.timeMinutes?.let { add(formatClock(it)) }
                activity.pleasure?.let { add(stringResource(R.string.activities_pleasure_value, it)) }
                activity.mastery?.let { add(stringResource(R.string.activities_mastery_value, it)) }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RateActivityDialog(title: String, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit, initialPleasure: Int = 5, initialMastery: Int = 5) {
    var pleasure by rememberSaveable { mutableIntStateOf(initialPleasure) }
    var mastery by rememberSaveable { mutableIntStateOf(initialMastery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.activities_rate_title)) },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                HintText(stringResource(R.string.activities_rate_hint), Modifier.padding(top = 8.dp))
                ScaleSlider(stringResource(R.string.activities_pleasure), pleasure, { pleasure = it }, max = 10)
                ScaleSlider(stringResource(R.string.activities_mastery), mastery, { mastery = it }, max = 10)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pleasure, mastery) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---- Add / edit ----

data class ActivityEditState(
    val loading: Boolean = true,
    val activity: PlannedActivity? = null,
    val title: String = "",
    val timeMinutes: Int? = null,
    val note: String = "",
    val done: Boolean = false,
    val pleasure: Int = 5,
    val mastery: Int = 5,
    val day: Long = 0,
    val closed: Boolean = false,
)

class ActivityEditViewModel(
    private val dao: ActivityDao,
    savedState: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val initialDay: Long = savedState.get<Long>(ARG_DAY)?.takeIf { it != NO_DAY } ?: LocalDate.now().toEpochDay()
    private val _state = MutableStateFlow(ActivityEditState())
    val state: StateFlow<ActivityEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val a = if (id > 0) dao.get(id) else null
            _state.value = if (a != null) {
                ActivityEditState(
                    loading = false, activity = a, title = a.title, timeMinutes = a.timeMinutes, note = a.note,
                    done = a.done, pleasure = a.pleasure ?: 5, mastery = a.mastery ?: 5, day = a.day,
                )
            } else {
                ActivityEditState(loading = false, day = initialDay)
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setTime(v: Int?) = _state.update { it.copy(timeMinutes = v) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun setDone(v: Boolean) = _state.update { it.copy(done = v) }
    fun setPleasure(v: Int) = _state.update { it.copy(pleasure = v) }
    fun setMastery(v: Int) = _state.update { it.copy(mastery = v) }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) return
        viewModelScope.launch {
            val time = now()
            dao.upsert(
                PlannedActivity(
                    id = s.activity?.id ?: 0,
                    createdAt = s.activity?.createdAt ?: time,
                    updatedAt = time,
                    day = s.day,
                    timeMinutes = s.timeMinutes,
                    title = s.title.trim(),
                    note = s.note.trim(),
                    done = s.done,
                    pleasure = if (s.done) s.pleasure else null,
                    mastery = if (s.done) s.mastery else null,
                ),
            )
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val a = _state.value.activity ?: return
        viewModelScope.launch {
            dao.delete(a.id)
            _state.update { it.copy(closed = true) }
        }
    }

    companion object {
        const val NO_DAY = Long.MIN_VALUE
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityEditScreen(
    onDone: () -> Unit,
    onSos: () -> Unit,
    viewModel: ActivityEditViewModel = appViewModel { c -> ActivityEditViewModel(c.db.activities(), createSavedStateHandle()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onDone() }

    ScreenScaffold(
        title = stringResource(if (state.activity == null) R.string.activities_new else R.string.activities_edit),
        onSos = onSos,
        onBack = onDone,
        actions = {
            if (state.activity != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) { padding ->
        if (state.loading) return@ScreenScaffold
        ScrollingContent(padding) {
            Text(formatDay(LocalDate.ofEpochDay(state.day)), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.activities_what)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            SectionTitle(stringResource(R.string.activities_ideas))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.activity_ideas).forEach { idea ->
                    SuggestionChip(onClick = { viewModel.setTitle(idea) }, label = { Text(idea) })
                }
            }
            SectionTitle(stringResource(R.string.activities_when))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = state.timeMinutes != null,
                        role = Role.Switch,
                        onValueChange = { viewModel.setTime(if (it) 18 * 60 else null) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.activities_at_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.timeMinutes != null, onCheckedChange = null)
            }
            state.timeMinutes?.let { t ->
                TimeField(stringResource(R.string.activities_time), t, { viewModel.setTime(it) })
            }
            NoteField(
                state.note,
                viewModel::setNote,
                stringResource(R.string.activities_note),
                Modifier.padding(top = 12.dp),
                minLines = 2,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(top = 8.dp)
                    .toggleable(value = state.done, role = Role.Checkbox, onValueChange = viewModel::setDone),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.done, onCheckedChange = null)
                Text(stringResource(R.string.activities_done), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
            if (state.done) {
                HintText(stringResource(R.string.activities_rate_hint))
                ScaleSlider(stringResource(R.string.activities_pleasure), state.pleasure, viewModel::setPleasure, max = 10)
                ScaleSlider(stringResource(R.string.activities_mastery), state.mastery, viewModel::setMastery, max = 10)
            }
            Button(
                onClick = viewModel::save,
                enabled = state.title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.activities_delete_title),
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
