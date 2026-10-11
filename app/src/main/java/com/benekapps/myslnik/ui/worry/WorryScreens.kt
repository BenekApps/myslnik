package com.benekapps.myslnik.ui.worry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.db.Worry
import com.benekapps.myslnik.data.db.WorryDao
import com.benekapps.myslnik.data.db.WorryStatus
import com.benekapps.myslnik.reminders.ReminderScheduler
import com.benekapps.myslnik.reminders.ReminderType
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.HintText
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.TimeField
import com.benekapps.myslnik.ui.components.formatDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorriesViewModel(
    private val dao: WorryDao,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val open: StateFlow<List<Worry>?> = dao.observeOpen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val closedCount: StateFlow<Int> = dao.observeClosedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val worryTime: StateFlow<Int?> = settings.settings.map { it.worryTime }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val time = now()
            dao.insert(Worry(createdAt = time, updatedAt = time, text = text.trim()))
        }
    }

    fun letGo(worry: Worry) = setStatus(worry, WorryStatus.LET_GO)
    fun toProblemSolving(worry: Worry) = setStatus(worry, WorryStatus.PROBLEM_SOLVING)

    private fun setStatus(worry: Worry, status: WorryStatus) = viewModelScope.launch {
        dao.update(worry.copy(status = status, updatedAt = now()))
    }

    fun delete(worry: Worry) = viewModelScope.launch { dao.delete(worry.id) }
    fun clearClosed() = viewModelScope.launch { dao.deleteClosed() }

    fun setWorryTime(minuteOfDay: Int) = viewModelScope.launch {
        settings.setReminderTime(ReminderType.WORRY_TIME, minuteOfDay)
        if (settings.settings.first().reminderWorry) reminders.schedule(ReminderType.WORRY_TIME, minuteOfDay)
    }
}

@Composable
fun WorriesScreen(
    onBack: () -> Unit,
    onStartWorryTime: () -> Unit,
    onSos: () -> Unit,
    viewModel: WorriesViewModel = appViewModel { c -> WorriesViewModel(c.db.worries(), c.settings, c.reminders) },
) {
    val open by viewModel.open.collectAsStateWithLifecycle()
    val closed by viewModel.closedCount.collectAsStateWithLifecycle()
    val worryTime by viewModel.worryTime.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        viewModel.add(text)
        text = ""
    }
    ScreenScaffold(title = stringResource(R.string.worry_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            HintText(stringResource(R.string.worry_intro))
            worryTime?.let { t ->
                TimeField(stringResource(R.string.worry_time_label), t, viewModel::setWorryTime)
                HintText(stringResource(R.string.worry_time_hint), Modifier.padding(top = 8.dp))
            }
            SectionTitle(stringResource(R.string.worry_postpone_title))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.worry_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(onClick = add, enabled = text.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.worry_postpone))
            }
            val list = open.orEmpty()
            SectionTitle(stringResource(R.string.worry_list_title, list.size))
            if (open != null && list.isEmpty()) HintText(stringResource(R.string.worry_empty))
            list.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.text, style = MaterialTheme.typography.bodyLarge)
                        Text(formatDateTime(w.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.delete(w) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.worry_delete, w.text))
                    }
                }
                HorizontalDivider()
            }
            Button(
                onClick = onStartWorryTime,
                enabled = list.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) { Text(stringResource(R.string.worry_start)) }
            if (closed > 0) {
                TextButton(onClick = viewModel::clearClosed, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.worry_clear_closed, closed))
                }
            }
        }
    }
}

/** The worry time itself: a 15-minute timer and a decision for every worry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorryTimeScreen(
    onBack: () -> Unit,
    onSolve: (text: String) -> Unit,
    onSos: () -> Unit,
    viewModel: WorriesViewModel = appViewModel { c -> WorriesViewModel(c.db.worries(), c.settings, c.reminders) },
) {
    val open by viewModel.open.collectAsStateWithLifecycle()
    // Whole minutes only: a ticking seconds counter can add to the tension.
    var minutesLeft by rememberSaveable { mutableIntStateOf(WORRY_TIME_MINUTES) }
    LaunchedEffect(Unit) {
        while (minutesLeft > 0) {
            delay(60_000)
            minutesLeft--
        }
    }
    ScreenScaffold(title = stringResource(R.string.worry_time_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (minutesLeft > 0) {
                            stringResource(R.string.worry_timer, minutesLeft)
                        } else {
                            stringResource(R.string.worry_timer_done)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { if (minutesLeft == 0) liveRegion = LiveRegionMode.Polite },
                    )
                    Text(stringResource(R.string.worry_time_hint_session), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            val list = open.orEmpty()
            if (open != null && list.isEmpty()) {
                Text(
                    stringResource(R.string.worry_all_done),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            list.forEach { w ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(w.text, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.worry_question), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedButton(onClick = { viewModel.letGo(w) }) { Text(stringResource(R.string.worry_let_go)) }
                            FilledTonalButton(onClick = {
                                viewModel.toProblemSolving(w)
                                onSolve(w.text)
                            }) { Text(stringResource(R.string.worry_solve)) }
                        }
                    }
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text(stringResource(R.string.worry_finish))
            }
        }
    }
}

private const val WORRY_TIME_MINUTES = 15
