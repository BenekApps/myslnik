package com.benekapps.myslnik.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.AppSettings
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.formatDateTime
import com.benekapps.myslnik.ui.history.HistoryItem
import com.benekapps.myslnik.ui.history.HistoryRow
import com.benekapps.myslnik.ui.history.combineHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class HomeUiState(
    val todayMood: MoodEntry? = null,
    val draft: ThoughtRecord? = null,
    val recent: List<HistoryItem> = emptyList(),
    val showBackupReminder: Boolean = false,
)

class HomeViewModel(
    repo: EntriesRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repo.observeThoughtRecords(),
        repo.observeMoodEntries(),
        settings.settings,
    ) { thoughts, moods, s ->
        val time = now()
        val today = TimeRange.lastDays(1, time, ZoneId.systemDefault())
        HomeUiState(
            todayMood = moods.firstOrNull { it.createdAt in today },
            draft = thoughts.filter { it.isDraft }.maxByOrNull { it.updatedAt },
            recent = combineHistory(thoughts.filterNot { it.isDraft }, moods).take(RECENT_COUNT),
            showBackupReminder = shouldRemindAboutBackup(thoughts.count { !it.isDraft } + moods.size, s, time),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun snoozeBackupReminder() {
        viewModelScope.launch { settings.snoozeBackupReminder(now() + SNOOZE_MS) }
    }

    companion object {
        const val RECENT_COUNT = 5
        const val MIN_ENTRIES_FOR_REMINDER = 10
        const val BACKUP_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val SNOOZE_MS = 14L * 24 * 60 * 60 * 1000

        /** Reminds once there is something worth keeping and no recent backup. */
        fun shouldRemindAboutBackup(entryCount: Int, s: AppSettings, now: Long): Boolean =
            entryCount >= MIN_ENTRIES_FOR_REMINDER &&
                now >= s.backupReminderSnoozedUntil &&
                (s.lastBackupAt == null || now - s.lastBackupAt > BACKUP_MAX_AGE_MS)
    }
}

@Composable
fun HomeScreen(
    onNewThought: () -> Unit,
    onContinueDraft: (Long) -> Unit,
    onNewMood: () -> Unit,
    onOpenThought: (ThoughtRecord) -> Unit,
    onOpenMood: (Long) -> Unit,
    onOpenModel: () -> Unit,
    onOpenBackup: () -> Unit,
    onSos: () -> Unit,
    viewModel: HomeViewModel = appViewModel { c -> HomeViewModel(c.entries, c.settings) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.app_name), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.home_mood_question),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    state.todayMood?.let {
                        Text(
                            stringResource(R.string.home_mood_today, it.rating),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = onNewMood,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(stringResource(if (state.todayMood == null) R.string.home_mood_button else R.string.home_mood_button_again))
                    }
                }
            }

            Button(
                onClick = onNewThought,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(top = 16.dp),
            ) {
                Icon(Icons.Filled.Create, contentDescription = null)
                Text(
                    stringResource(R.string.home_new_thought),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            state.draft?.let { draft ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.home_draft, formatDateTime(draft.updatedAt)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = { onContinueDraft(draft.id) }) {
                            Text(stringResource(R.string.continue_draft))
                        }
                    }
                }
            }

            if (state.showBackupReminder) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.home_backup_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.home_backup_text), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onOpenBackup) { Text(stringResource(R.string.home_backup_now)) }
                            TextButton(onClick = viewModel::snoozeBackupReminder) { Text(stringResource(R.string.later)) }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.home_recent))
            if (state.recent.isEmpty()) {
                Text(
                    stringResource(R.string.home_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.recent.forEach { item ->
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

            OutlinedCard(
                onClick = onOpenModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(stringResource(R.string.model_card_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.model_card_text), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
