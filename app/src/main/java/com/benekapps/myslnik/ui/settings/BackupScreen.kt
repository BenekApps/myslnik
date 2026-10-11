package com.benekapps.myslnik.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.backup.BackupData
import com.benekapps.myslnik.data.backup.BackupManager
import com.benekapps.myslnik.data.backup.BackupSerializer
import com.benekapps.myslnik.data.security.BackupCrypto
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.formatDate
import com.benekapps.myslnik.ui.components.formatDateTime
import com.benekapps.myslnik.ui.components.rememberSnackbarHostState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BackupUiState(
    val working: Boolean = false,
    /** Password chosen for a new backup, waiting for the user to pick a file. */
    val pendingPassword: CharArray? = null,
    /** File picked for restore, waiting for its password. */
    val restoreSource: Uri? = null,
    /** Decrypted backup waiting for confirmation. */
    val restoreCandidate: BackupData? = null,
    val message: Int? = null,
)

class BackupViewModel(
    private val backup: BackupManager,
    settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    val lastBackupAt: StateFlow<Long?> = settings.settings.map { it.lastBackupAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choosePassword(password: CharArray) = _state.update { it.copy(pendingPassword = password) }

    fun createBackup(target: Uri?) {
        val password = _state.value.pendingPassword ?: return
        _state.update { it.copy(pendingPassword = null) }
        if (target == null) return password.fill('\u0000')
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val result = runCatching { backup.createBackup(target, password) }
            password.fill('\u0000')
            _state.update {
                it.copy(working = false, message = if (result.isSuccess) R.string.backup_created else R.string.backup_create_error)
            }
        }
    }

    fun pickRestoreSource(source: Uri?) = _state.update { it.copy(restoreSource = source) }

    fun decrypt(password: CharArray) {
        val source = _state.value.restoreSource ?: return
        _state.update { it.copy(restoreSource = null, working = true) }
        viewModelScope.launch {
            val result = runCatching { backup.readBackup(source, password) }
            password.fill('\u0000')
            val error = when (result.exceptionOrNull()) {
                null -> null
                is BackupCrypto.WrongPasswordException -> R.string.backup_wrong_password
                is BackupCrypto.NotABackupException -> R.string.backup_not_a_backup
                is BackupSerializer.UnsupportedBackupException -> R.string.backup_newer_version
                else -> R.string.backup_read_error
            }
            _state.update { it.copy(working = false, restoreCandidate = result.getOrNull(), message = error) }
        }
    }

    fun confirmRestore() {
        val data = _state.value.restoreCandidate ?: return
        _state.update { it.copy(restoreCandidate = null, working = true) }
        viewModelScope.launch {
            val ok = runCatching { backup.restore(data) }.isSuccess
            _state.update { it.copy(working = false, message = if (ok) R.string.backup_restored else R.string.backup_read_error) }
        }
    }

    fun cancelRestore() = _state.update { it.copy(restoreCandidate = null, restoreSource = null) }
    fun messageShown() = _state.update { it.copy(message = null) }
}

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onSos: () -> Unit,
    viewModel: BackupViewModel = appViewModel { c -> BackupViewModel(c.backupManager, c.settings) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    var askNewPassword by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> viewModel.createBackup(uri) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.pickRestoreSource(uri)
    }
    val fileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())

    LaunchedEffect(state.pendingPassword) {
        if (state.pendingPassword != null) createLauncher.launch(fileName)
    }
    state.message?.let { message ->
        val text = stringResource(message)
        LaunchedEffect(message) {
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    ScreenScaffold(title = stringResource(R.string.backup_title), onSos = onSos, onBack = onBack, snackbarHostState = snackbar) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyLarge)
            Text(
                lastBackupAt?.let { stringResource(R.string.backup_last, formatDate(it)) } ?: stringResource(R.string.backup_never),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(
                    stringResource(R.string.backup_password_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Button(
                onClick = { askNewPassword = true },
                enabled = !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
            if (state.working) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.backup_working), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    if (askNewPassword) {
        NewPasswordDialog(
            onConfirm = {
                askNewPassword = false
                viewModel.choosePassword(it)
            },
            onDismiss = { askNewPassword = false },
        )
    }
    if (state.restoreSource != null) {
        PasswordDialog(onConfirm = viewModel::decrypt, onDismiss = viewModel::cancelRestore)
    }
    state.restoreCandidate?.let { data ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_confirm_text,
                        formatDateTime(data.createdAt),
                        data.thoughtRecords.count { !it.isDraft },
                        data.moodEntries.size,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.backup_restore_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NewPasswordDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    val valid = first.length >= BackupCrypto.MIN_PASSWORD_LENGTH && first == second && understood
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column {
                PasswordField(first, { first = it }, stringResource(R.string.backup_password_label))
                PasswordField(second, { second = it }, stringResource(R.string.backup_password_repeat))
                if (first.isNotEmpty() && first.length < BackupCrypto.MIN_PASSWORD_LENGTH) {
                    Text(
                        stringResource(R.string.backup_password_too_short, BackupCrypto.MIN_PASSWORD_LENGTH),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (second.isNotEmpty() && first != second) {
                    Text(
                        stringResource(R.string.backup_password_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Text(
                        stringResource(R.string.backup_password_understood),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(first.toCharArray()) }, enabled = valid) {
                Text(stringResource(R.string.backup_choose_place))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasswordDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_enter_password)) },
        text = { PasswordField(password, { password = it }, stringResource(R.string.backup_password_label)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}
