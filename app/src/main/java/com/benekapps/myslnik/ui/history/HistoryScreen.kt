package com.benekapps.myslnik.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.db.AppDatabase
import com.benekapps.myslnik.data.db.BehavioralExperiment
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ProblemSolving
import com.benekapps.myslnik.data.db.SleepEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.hotThought
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.domain.Period
import com.benekapps.myslnik.domain.SleepMath
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.formatDate
import com.benekapps.myslnik.ui.components.formatDateTime
import com.benekapps.myslnik.ui.components.formatDay
import com.benekapps.myslnik.ui.sleep.durationText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

sealed interface HistoryItem {
    val id: Long
    val time: Long

    data class Thought(val record: ThoughtRecord) : HistoryItem {
        override val id get() = record.id
        override val time get() = record.createdAt
    }

    data class Mood(val entry: MoodEntry) : HistoryItem {
        override val id get() = entry.id
        override val time get() = entry.createdAt
    }

    /** Sorted by the night itself (22:00 that evening), not by when it was written. */
    data class Sleep(val entry: SleepEntry) : HistoryItem {
        override val id get() = entry.id
        override val time get() = LocalDate.ofEpochDay(entry.night).atTime(22, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    data class Experiment(val experiment: BehavioralExperiment) : HistoryItem {
        override val id get() = experiment.id
        override val time get() = experiment.createdAt
    }

    data class Problem(val problem: ProblemSolving) : HistoryItem {
        override val id get() = problem.id
        override val time get() = problem.createdAt
    }
}

enum class EntryFilter(val label: Int) {
    ALL(R.string.filter_all),
    THOUGHTS(R.string.filter_thoughts),
    MOOD(R.string.filter_mood),
    SLEEP(R.string.filter_sleep),
    EXPERIMENTS(R.string.filter_experiments),
    PROBLEMS(R.string.filter_problems),
    ;

    fun matches(item: HistoryItem) = when (this) {
        ALL -> true
        THOUGHTS -> item is HistoryItem.Thought
        MOOD -> item is HistoryItem.Mood
        SLEEP -> item is HistoryItem.Sleep
        EXPERIMENTS -> item is HistoryItem.Experiment
        PROBLEMS -> item is HistoryItem.Problem
    }
}

fun combineHistory(
    thoughts: List<ThoughtRecord>,
    moods: List<MoodEntry>,
    sleep: List<SleepEntry> = emptyList(),
    experiments: List<BehavioralExperiment> = emptyList(),
    problems: List<ProblemSolving> = emptyList(),
): List<HistoryItem> =
    (
        thoughts.map { HistoryItem.Thought(it) } + moods.map { HistoryItem.Mood(it) } +
            sleep.map { HistoryItem.Sleep(it) } + experiments.map { HistoryItem.Experiment(it) } +
            problems.map { HistoryItem.Problem(it) }
        ).sortedByDescending { it.time }

class HistoryViewModel(repo: EntriesRepository, db: AppDatabase) : ViewModel() {
    val items: StateFlow<List<HistoryItem>?> =
        combine(
            repo.observeThoughtRecords(),
            repo.observeMoodEntries(),
            db.sleep().observeAll(),
            db.experiments().observeAll(),
            db.problems().observeAll(),
            ::combineHistory,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenItem: (HistoryItem) -> Unit,
    onExport: () -> Unit,
    onSos: () -> Unit,
    viewModel: HistoryViewModel = appViewModel { c -> HistoryViewModel(c.entries, c.db) },
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(EntryFilter.ALL) }
    var period by rememberSaveable { mutableStateOf(Period.ALL) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(items) { System.currentTimeMillis() }

    val customRange = if (customFrom != null && customTo != null) TimeRange(customFrom!!, customTo!!) else null
    val range = period.range(now, zone, customRange)
    val visible = items.orEmpty().filter { item ->
        item.time in range && filter.matches(item)
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_history),
        onSos = onSos,
        actions = {
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export_title))
            }
        },
    ) { padding ->
        ScrollingContent(padding) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryFilter.entries.forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(stringResource(value.label)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Period.WEEK to R.string.period_7_days,
                    Period.MONTH to R.string.period_30_days,
                    Period.ALL to R.string.period_all,
                ).forEach { (value, label) ->
                    FilterChip(selected = period == value, onClick = { period = value }, label = { Text(stringResource(label)) })
                }
                FilterChip(
                    selected = period == Period.CUSTOM,
                    onClick = { pickingRange = true },
                    label = {
                        Text(
                            if (period == Period.CUSTOM && customRange != null) {
                                stringResource(R.string.period_custom_value, formatDate(customRange.from), formatDate(customRange.to))
                            } else {
                                stringResource(R.string.period_custom)
                            },
                        )
                    },
                )
            }
            if (items != null && visible.isEmpty()) {
                Text(
                    stringResource(if (items!!.isEmpty()) R.string.history_empty else R.string.history_empty_filtered),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            visible.forEach { item ->
                HistoryRow(item, onClick = { onOpenItem(item) })
                HorizontalDivider()
            }
        }
    }

    if (pickingRange) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingRange = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis ?: start
                        if (start != null && end != null) {
                            val r = TimeRange.ofDays(utcDate(start), utcDate(end), zone)
                            customFrom = r.from
                            customTo = r.to
                            period = Period.CUSTOM
                        }
                        pickingRange = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingRange = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

/** Date pickers return UTC midnight millis. */
fun utcDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    val res = LocalResources.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (item) {
                is HistoryItem.Thought -> Icons.Filled.Edit
                is HistoryItem.Mood -> Icons.Filled.Face
                is HistoryItem.Sleep -> Icons.Filled.Star
                is HistoryItem.Experiment -> Icons.Filled.Search
                is HistoryItem.Problem -> Icons.Filled.Build
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            when (item) {
                is HistoryItem.Thought -> {
                    val r = item.record
                    val title = r.situation.lineSequence().firstOrNull { it.isNotBlank() }
                        ?: r.hotThought()
                        ?: stringResource(R.string.tr_untitled)
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    val type = stringResource(if (r.isDraft) R.string.tr_draft_label else R.string.tr_title)
                    Text(
                        "$type · ${formatDateTime(r.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.isDraft) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is HistoryItem.Sleep -> {
                    val e = item.entry
                    Text(
                        stringResource(R.string.sleep_night_of, formatDay(LocalDate.ofEpochDay(e.night))),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.sleep_row, durationText(SleepMath.totalSleep(e)), SleepMath.efficiency(e) ?: 0, e.quality),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is HistoryItem.Experiment -> {
                    val e = item.experiment
                    Text(e.belief, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "${stringResource(R.string.experiments_title_single)} · ${formatDateTime(e.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is HistoryItem.Problem -> {
                    val p = item.problem
                    Text(
                        p.problem.ifBlank { stringResource(R.string.problems_untitled) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "${stringResource(if (p.isDraft) R.string.problems_draft else R.string.problems_title_single)} · ${formatDateTime(p.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.isDraft) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is HistoryItem.Mood -> {
                    val m = item.entry
                    Text(stringResource(R.string.mood_row_title, m.rating), style = MaterialTheme.typography.bodyLarge)
                    val emotions = m.emotions.joinToString(", ") { Emotions.label(res, it) }
                    Text(
                        listOf(formatDateTime(m.createdAt), emotions).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
