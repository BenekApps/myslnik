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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Share
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
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.hotThought
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.domain.Period
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.formatDate
import com.benekapps.myslnik.ui.components.formatDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
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
}

enum class EntryFilter { ALL, THOUGHTS, MOOD }

fun combineHistory(thoughts: List<ThoughtRecord>, moods: List<MoodEntry>): List<HistoryItem> =
    (thoughts.map { HistoryItem.Thought(it) } + moods.map { HistoryItem.Mood(it) })
        .sortedByDescending { it.time }

class HistoryViewModel(repo: EntriesRepository) : ViewModel() {
    val items: StateFlow<List<HistoryItem>?> =
        combine(repo.observeThoughtRecords(), repo.observeMoodEntries(), ::combineHistory)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenThought: (ThoughtRecord) -> Unit,
    onOpenMood: (Long) -> Unit,
    onExport: () -> Unit,
    onSos: () -> Unit,
    viewModel: HistoryViewModel = appViewModel { c -> HistoryViewModel(c.entries) },
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
        item.time in range && when (filter) {
            EntryFilter.ALL -> true
            EntryFilter.THOUGHTS -> item is HistoryItem.Thought
            EntryFilter.MOOD -> item is HistoryItem.Mood
        }
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
                listOf(
                    EntryFilter.ALL to R.string.filter_all,
                    EntryFilter.THOUGHTS to R.string.filter_thoughts,
                    EntryFilter.MOOD to R.string.filter_mood,
                ).forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(stringResource(label)) })
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
                HistoryRow(
                    item,
                    onClick = {
                        when (item) {
                            is HistoryItem.Thought -> onOpenThought(item.record)
                            is HistoryItem.Mood -> onOpenMood(item.entry.id)
                        }
                    },
                )
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
            if (item is HistoryItem.Thought) Icons.Filled.Create else Icons.Filled.Face,
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
