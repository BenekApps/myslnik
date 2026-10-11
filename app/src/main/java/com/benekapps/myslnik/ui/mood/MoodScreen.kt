package com.benekapps.myslnik.ui.mood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.domain.dailyAverages
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.LabeledText
import com.benekapps.myslnik.ui.components.PolishLocale
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.formatDateTime
import com.benekapps.myslnik.ui.components.formatShortDate
import com.benekapps.myslnik.ui.thought.DetailState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MoodViewModel(repo: EntriesRepository) : ViewModel() {
    val entries: StateFlow<List<MoodEntry>?> = repo.observeMoodEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun MoodScreen(
    onNewEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: MoodViewModel = appViewModel { c -> MoodViewModel(c.entries) },
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(7) }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(entries) { System.currentTimeMillis() }
    val range = TimeRange.lastDays(days, now, zone)
    val inRange = entries.orEmpty().filter { it.createdAt in range }

    ScreenScaffold(title = stringResource(R.string.nav_mood), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            Button(onClick = onNewEntry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.mood_add), modifier = Modifier.padding(start = 8.dp))
            }
            SectionTitle(stringResource(R.string.mood_chart_title))
            PeriodSwitch(days = days, onChange = { days = it })
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    val points = dailyAverages(inRange.map { it.createdAt to it.rating }, days, now, zone)
                    if (inRange.isEmpty()) {
                        Text(stringResource(R.string.mood_chart_empty), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            stringResource(
                                R.string.mood_chart_summary,
                                String.format(PolishLocale, "%.1f", inRange.map { it.rating }.average()),
                                inRange.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MoodChart(points, Modifier.padding(top = 12.dp))
                    }
                }
            }
            SectionTitle(stringResource(R.string.mood_entries_title))
            if (inRange.isEmpty()) {
                Text(
                    stringResource(R.string.mood_entries_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inRange.forEach { entry ->
                MoodRow(entry, onClick = { onOpenEntry(entry.id) })
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSwitch(days: Int, onChange: (Int) -> Unit) {
    val options = listOf(7 to R.string.period_7_days, 30 to R.string.period_30_days)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = days == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
fun MoodRow(entry: MoodEntry, onClick: () -> Unit) {
    val res = LocalResources.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(entry.createdAt), style = MaterialTheme.typography.bodyMedium)
            if (entry.emotions.isNotEmpty()) {
                Text(
                    entry.emotions.joinToString(", ") { Emotions.label(res, it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.mood_rating_value, entry.rating),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Daily average mood, 0–10. Days without entries break the line. */
@Composable
fun MoodChart(points: List<Pair<LocalDate, Double?>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val values = points.mapNotNull { it.second }
    val description = if (values.isEmpty()) {
        ""
    } else {
        stringResource(
            R.string.mood_chart_description,
            points.size,
            String.format(PolishLocale, "%.1f", values.min()),
            String.format(PolishLocale, "%.1f", values.max()),
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gutter = 28.dp
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val inset = 6.dp.toPx()
            val plotLeft = gutter.toPx() + inset
            val plotRight = size.width - inset
            val top = inset
            val bottom = size.height - inset
            val stepX = if (points.size > 1) (plotRight - plotLeft) / (points.size - 1) else 0f
            fun y(v: Double) = (bottom - (v / 10.0) * (bottom - top)).toFloat()
            listOf(0, 5, 10).forEach { v ->
                val lineY = y(v.toDouble())
                drawLine(gridColor, Offset(gutter.toPx(), lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
                val label = textMeasurer.measure(v.toString(), axisLabelStyle)
                drawText(label, topLeft = Offset(gutter.toPx() - label.size.width - 6.dp.toPx(), lineY - label.size.height / 2f))
            }
            var previous: Offset? = null
            points.forEachIndexed { i, (_, v) ->
                if (v == null) {
                    previous = null
                    return@forEachIndexed
                }
                val p = Offset(plotLeft + i * stepX, y(v))
                previous?.let {
                    drawLine(lineColor, it, p, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
                drawCircle(lineColor, radius = 4.dp.toPx(), center = p)
                previous = p
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = gutter, top = 4.dp),
        ) {
            Text(formatShortDate(points.first().first), style = axisLabelStyle, modifier = Modifier.weight(1f))
            Text(formatShortDate(points.last().first), style = axisLabelStyle)
        }
    }
}

class MoodDetailViewModel(private val repo: EntriesRepository, private val id: Long) : ViewModel() {
    val state: StateFlow<DetailState<MoodEntry>> = repo.observeMoodEntry(id)
        .map { if (it == null) DetailState.Missing else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun delete() {
        viewModelScope.launch { repo.deleteMoodEntry(id) }
    }
}

@Composable
fun MoodDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSos: () -> Unit,
    viewModel: MoodDetailViewModel = appViewModel { c ->
        MoodDetailViewModel(c.entries, createSavedStateHandle().get<Long>(MoodEntryViewModel.ARG_ID) ?: 0L)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val entry = (state as? DetailState.Loaded)?.value
    if (state == DetailState.Missing) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val res = LocalResources.current
    ScreenScaffold(
        title = stringResource(R.string.mood_detail_title),
        onSos = onSos,
        onBack = onBack,
        actions = {
            if (entry != null) {
                IconButton(onClick = { onEdit(entry.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) { padding ->
        if (entry == null) return@ScreenScaffold
        ScrollingContent(padding) {
            Text(formatDateTime(entry.createdAt), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.mood_rating_value, entry.rating),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            LabeledText(stringResource(R.string.mood_emotions_title), entry.emotions.joinToString(", ") { Emotions.label(res, it) })
            LabeledText(stringResource(R.string.mood_note_title), entry.note)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.mood_delete_title),
            text = stringResource(R.string.mood_delete_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
