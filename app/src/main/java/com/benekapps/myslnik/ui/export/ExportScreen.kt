package com.benekapps.myslnik.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.domain.Period
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.export.PdfExporter
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.formatDate
import com.benekapps.myslnik.ui.components.rememberSnackbarHostState
import com.benekapps.myslnik.ui.history.utcDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

data class ExportUiState(
    val period: Period = Period.MONTH,
    val customRange: TimeRange? = null,
    val includeThoughts: Boolean = true,
    val includeMood: Boolean = true,
    val working: Boolean = false,
    val file: File? = null,
    val message: Int? = null,
)

class ExportViewModel(
    private val repo: EntriesRepository,
    private val exporter: PdfExporter,
    private val exportDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun setPeriod(period: Period, custom: TimeRange? = null) =
        _state.update { it.copy(period = period, customRange = custom ?: it.customRange, file = null) }

    fun setIncludeThoughts(value: Boolean) = _state.update { it.copy(includeThoughts = value, file = null) }
    fun setIncludeMood(value: Boolean) = _state.update { it.copy(includeMood = value, file = null) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun generate() {
        val s = _state.value
        if (!s.includeThoughts && !s.includeMood) return
        _state.update { it.copy(working = true, file = null) }
        viewModelScope.launch {
            val result = runCatching {
                val time = now()
                val zone = ZoneId.systemDefault()
                val range = s.period.range(time, zone, s.customRange)
                val content = PdfExporter.Content(
                    range = range,
                    thoughtRecords = if (s.includeThoughts) repo.completedThoughtRecordsBetween(range.from, range.to) else null,
                    moodEntries = if (s.includeMood) repo.moodEntriesBetween(range.from, range.to) else null,
                )
                withContext(Dispatchers.IO) {
                    exportDir.mkdirs()
                    exportDir.listFiles()?.forEach { it.delete() }
                    val file = File(exportDir, "myslnik-${LocalDate.now(zone)}.pdf")
                    file.outputStream().use { exporter.write(it, content, time, zone) }
                    file
                }
            }
            _state.update {
                it.copy(
                    working = false,
                    file = result.getOrNull(),
                    message = if (result.isFailure) R.string.export_error else null,
                )
            }
        }
    }

    fun saveTo(context: Context, target: Uri) {
        val file = _state.value.file ?: return
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
            }.isSuccess
            _state.update { it.copy(message = if (ok) R.string.export_saved else R.string.export_error) }
        }
    }
}

fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.export_share_title)))
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    onSos: () -> Unit,
    viewModel: ExportViewModel = appViewModel { c ->
        ExportViewModel(c.entries, c.pdfExporter, c.exportDir)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = rememberSnackbarHostState()
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.saveTo(context, uri)
    }
    state.message?.let { message ->
        val text = stringResource(message)
        LaunchedEffect(message) {
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    ScreenScaffold(title = stringResource(R.string.export_title), onSos = onSos, onBack = onBack, snackbarHostState = snackbar) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.export_intro), style = MaterialTheme.typography.bodyLarge)
            SectionTitle(stringResource(R.string.export_period))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Period.WEEK to R.string.period_7_days,
                    Period.MONTH to R.string.period_30_days,
                    Period.QUARTER to R.string.period_90_days,
                    Period.ALL to R.string.period_all,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.period == value,
                        onClick = { viewModel.setPeriod(value) },
                        label = { Text(stringResource(label)) },
                    )
                }
                val custom = state.customRange
                FilterChip(
                    selected = state.period == Period.CUSTOM,
                    onClick = { pickingRange = true },
                    label = {
                        Text(
                            if (state.period == Period.CUSTOM && custom != null) {
                                stringResource(R.string.period_custom_value, formatDate(custom.from), formatDate(custom.to))
                            } else {
                                stringResource(R.string.period_custom)
                            },
                        )
                    },
                )
            }
            SectionTitle(stringResource(R.string.export_types))
            CheckRow(stringResource(R.string.filter_thoughts), state.includeThoughts, viewModel::setIncludeThoughts)
            CheckRow(stringResource(R.string.filter_mood), state.includeMood, viewModel::setIncludeMood)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(
                    stringResource(R.string.export_privacy_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Button(
                onClick = viewModel::generate,
                enabled = !state.working && (state.includeThoughts || state.includeMood),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.export_create))
            }
            if (state.working) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.export_working), modifier = Modifier.padding(start = 12.dp))
                }
            }
            state.file?.let { file ->
                Text(
                    stringResource(R.string.export_ready),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { sharePdf(context, file) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text(stringResource(R.string.export_share), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { saveLauncher.launch(file.name) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_save))
                    }
                }
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
                            viewModel.setPeriod(
                                Period.CUSTOM,
                                TimeRange.ofDays(utcDate(start), utcDate(end), ZoneId.systemDefault()),
                            )
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

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}
