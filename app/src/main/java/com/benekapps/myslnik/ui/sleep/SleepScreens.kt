package com.benekapps.myslnik.ui.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.SleepDao
import com.benekapps.myslnik.data.db.SleepEntry
import com.benekapps.myslnik.domain.SleepMath
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.HintText
import com.benekapps.myslnik.ui.components.NoteField
import com.benekapps.myslnik.ui.components.NumberStepper
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.TimeField
import com.benekapps.myslnik.ui.components.formatDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun durationText(minutes: Int): String = stringResource(R.string.duration_hm, minutes / 60, minutes % 60)

class SleepViewModel(dao: SleepDao) : ViewModel() {
    val entries: StateFlow<List<SleepEntry>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SleepScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: SleepViewModel = appViewModel { c -> SleepViewModel(c.db.sleep()) },
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.sleep_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.sleep_intro))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.sleep_add), modifier = Modifier.padding(start = 8.dp))
            }
            val list = entries.orEmpty()
            SleepMath.summary(list.take(7))?.let { s ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.sleep_summary_title, s.nights), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.sleep_summary_sleep, durationText(s.averageSleepMinutes)), style = MaterialTheme.typography.bodyLarge)
                        s.averageEfficiency?.let {
                            Text(stringResource(R.string.sleep_summary_efficiency, it), style = MaterialTheme.typography.bodyLarge)
                        }
                        HintText(stringResource(R.string.sleep_efficiency_explained), Modifier.padding(top = 8.dp))
                    }
                }
            }
            SectionTitle(stringResource(R.string.sleep_nights))
            if (entries != null && list.isEmpty()) HintText(stringResource(R.string.sleep_empty))
            list.forEach { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(e.id) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.sleep_night_of, formatDay(LocalDate.ofEpochDay(e.night))), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.sleep_row, durationText(SleepMath.totalSleep(e)), SleepMath.efficiency(e) ?: 0, e.quality),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

// ---- One night ----

data class SleepFormState(
    val loading: Boolean = true,
    val existing: SleepEntry? = null,
    val entry: SleepEntry = defaultEntry(LocalDate.now().minusDays(1).toEpochDay(), 0),
    val closed: Boolean = false,
)

/** A typical night as a starting point; the user moves only what differs. */
fun defaultEntry(night: Long, now: Long) = SleepEntry(
    createdAt = now,
    updatedAt = now,
    night = night,
    inBed = SleepMath.fromClock(23, 0),
    triedToSleep = SleepMath.fromClock(23, 15),
    minutesToFallAsleep = 15,
    awakenings = 1,
    minutesAwake = 10,
    finalWake = SleepMath.fromClock(6, 30),
    outOfBed = SleepMath.fromClock(6, 45),
    quality = 3,
)

class SleepEntryViewModel(
    private val dao: SleepDao,
    savedState: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(SleepFormState())
    val state: StateFlow<SleepFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (id > 0) dao.get(id) else null
            _state.value = SleepFormState(
                loading = false,
                existing = existing,
                entry = existing ?: defaultEntry(LocalDate.now().minusDays(1).toEpochDay(), now()),
            )
        }
    }

    fun edit(transform: (SleepEntry) -> SleepEntry) = _state.update { it.copy(entry = transform(it.entry)) }

    fun save() {
        val e = _state.value.entry
        if (SleepMath.validate(e) != null) return
        viewModelScope.launch {
            dao.upsert(e.copy(updatedAt = now(), note = e.note.trim()))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepEntryScreen(
    onDone: () -> Unit,
    onSos: () -> Unit,
    viewModel: SleepEntryViewModel = appViewModel { c -> SleepEntryViewModel(c.db.sleep(), createSavedStateHandle()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onDone() }

    ScreenScaffold(
        title = stringResource(R.string.sleep_entry_title),
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
        val e = state.entry
        ScrollingContent(padding) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.edit { it.copy(night = it.night - 1) } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.day_previous))
                }
                Text(
                    stringResource(R.string.sleep_night_of, formatDay(LocalDate.ofEpochDay(e.night))),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { viewModel.edit { it.copy(night = it.night + 1) } },
                    enabled = e.night < LocalDate.now().toEpochDay(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.day_next))
                }
            }
            HintText(stringResource(R.string.sleep_entry_hint))

            SectionTitle(stringResource(R.string.sleep_evening))
            SleepTimeField(stringResource(R.string.sleep_in_bed), e.inBed) { v -> viewModel.edit { it.copy(inBed = v) } }
            SleepTimeField(stringResource(R.string.sleep_tried), e.triedToSleep) { v -> viewModel.edit { it.copy(triedToSleep = v) } }
            NumberStepper(
                stringResource(R.string.sleep_latency),
                e.minutesToFallAsleep,
                { v -> viewModel.edit { it.copy(minutesToFallAsleep = v) } },
                range = 0..600,
                step = 5,
                valueText = stringResource(R.string.minutes_value, e.minutesToFallAsleep),
            )

            SectionTitle(stringResource(R.string.sleep_night))
            NumberStepper(
                stringResource(R.string.sleep_awakenings),
                e.awakenings,
                { v -> viewModel.edit { it.copy(awakenings = v) } },
                range = 0..30,
            )
            NumberStepper(
                stringResource(R.string.sleep_awake),
                e.minutesAwake,
                { v -> viewModel.edit { it.copy(minutesAwake = v) } },
                range = 0..720,
                step = 5,
                valueText = stringResource(R.string.minutes_value, e.minutesAwake),
            )

            SectionTitle(stringResource(R.string.sleep_morning))
            SleepTimeField(stringResource(R.string.sleep_final_wake), e.finalWake) { v -> viewModel.edit { it.copy(finalWake = v) } }
            SleepTimeField(stringResource(R.string.sleep_out_of_bed), e.outOfBed) { v -> viewModel.edit { it.copy(outOfBed = v) } }

            SectionTitle(stringResource(R.string.sleep_quality))
            val labels = stringArrayResource(R.array.sleep_quality_labels)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..5).forEach { q ->
                    val description = labels[q - 1]
                    SegmentedButton(
                        selected = e.quality == q,
                        onClick = { viewModel.edit { it.copy(quality = q) } },
                        shape = SegmentedButtonDefaults.itemShape(q - 1, 5),
                        icon = {},
                        modifier = Modifier.semantics { contentDescription = description },
                    ) { Text(q.toString()) }
                }
            }
            Text(labels[e.quality - 1], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))

            NoteField(e.note, { v -> viewModel.edit { it.copy(note = v) } }, stringResource(R.string.sleep_note), Modifier.padding(top = 16.dp), minLines = 2)

            val problem = SleepMath.validate(e)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (problem == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(Modifier.padding(16.dp)) {
                    when (problem) {
                        SleepMath.Problem.ORDER -> Text(stringResource(R.string.sleep_error_order))
                        SleepMath.Problem.TOO_MUCH_AWAKE -> Text(stringResource(R.string.sleep_error_awake))
                        null -> {
                            Text(stringResource(R.string.sleep_time_in_bed, durationText(SleepMath.timeInBed(e))))
                            Text(stringResource(R.string.sleep_total, durationText(SleepMath.totalSleep(e))))
                            Text(
                                stringResource(R.string.sleep_efficiency, SleepMath.efficiency(e) ?: 0),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = viewModel::save,
                enabled = problem == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.sleep_delete_title),
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
private fun SleepTimeField(label: String, minutesFromNoon: Int, onChange: (Int) -> Unit) {
    TimeField(label, SleepMath.toMinuteOfDay(minutesFromNoon), { m -> onChange(SleepMath.fromClock(m / 60, m % 60)) })
}
