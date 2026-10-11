package com.benekapps.myslnik.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.AppSettings
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.ThemeMode
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.formatDate
import com.benekapps.myslnik.ui.components.rememberSnackbarHostState
import com.benekapps.myslnik.ui.lock.Biometrics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val entries: EntriesRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun disableLock() = viewModelScope.launch { settings.setPinHash(null) }
    fun setBiometric(enabled: Boolean) = viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    fun setSecureScreen(enabled: Boolean) = viewModelScope.launch { settings.setSecureScreen(enabled) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun deleteAllEntries(onDone: () -> Unit) = viewModelScope.launch {
        entries.deleteAll()
        onDone()
    }
}

@Composable
fun SettingsScreen(
    onSetPin: () -> Unit,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onModel: () -> Unit,
    onAbout: () -> Unit,
    onSources: () -> Unit,
    onPrivacy: () -> Unit,
    onSos: () -> Unit,
    viewModel: SettingsViewModel = appViewModel { c -> SettingsViewModel(c.settings, c.entries) },
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.isAvailable(context) }
    var dialog by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = rememberSnackbarHostState()
    val deletedMessage = stringResource(R.string.settings_deleted)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    ScreenScaffold(title = stringResource(R.string.nav_settings), onSos = onSos, snackbarHostState = snackbar) { padding ->
        ScrollingContent(padding) {
            SectionTitle(stringResource(R.string.settings_security))
            SwitchRow(
                title = stringResource(R.string.settings_lock),
                subtitle = stringResource(R.string.settings_lock_text),
                checked = s.lockEnabled,
                onChange = { if (it) onSetPin() else dialog = DIALOG_DISABLE_LOCK },
            )
            if (s.lockEnabled) {
                ClickRow(stringResource(R.string.settings_change_pin), onClick = onSetPin)
                if (biometricAvailable) {
                    SwitchRow(
                        title = stringResource(R.string.settings_biometric),
                        checked = s.biometricEnabled,
                        onChange = { viewModel.setBiometric(it) },
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.settings_secure_screen),
                subtitle = stringResource(R.string.settings_secure_screen_text),
                checked = s.secureScreen,
                onChange = { viewModel.setSecureScreen(it) },
            )

            SectionTitle(stringResource(R.string.settings_data))
            ClickRow(
                stringResource(R.string.backup_title),
                subtitle = s.lastBackupAt?.let { stringResource(R.string.backup_last, formatDate(it)) }
                    ?: stringResource(R.string.backup_never),
                onClick = onBackup,
            )
            ClickRow(stringResource(R.string.export_title), subtitle = stringResource(R.string.settings_export_text), onClick = onExport)
            ClickRow(stringResource(R.string.settings_delete_all), onClick = { dialog = DIALOG_DELETE_ALL })

            SectionTitle(stringResource(R.string.settings_theme))
            listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            ).forEach { (mode, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = s.themeMode == mode, role = Role.RadioButton, onClick = { viewModel.setTheme(mode) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.themeMode == mode, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }

            SectionTitle(stringResource(R.string.settings_info))
            ClickRow(stringResource(R.string.model_title), onClick = onModel)
            ClickRow(stringResource(R.string.about_title), onClick = onAbout)
            ClickRow(stringResource(R.string.sources_title), onClick = onSources)
            ClickRow(stringResource(R.string.privacy_title), onClick = onPrivacy)
        }
    }

    when (dialog) {
        DIALOG_DISABLE_LOCK -> ConfirmDialog(
            title = stringResource(R.string.settings_lock_off_title),
            text = stringResource(R.string.settings_lock_off_text),
            confirmLabel = stringResource(R.string.settings_lock_off_confirm),
            onConfirm = {
                dialog = 0
                viewModel.disableLock()
            },
            onDismiss = { dialog = 0 },
        )
        DIALOG_DELETE_ALL -> ConfirmDialog(
            title = stringResource(R.string.settings_delete_all_title),
            text = stringResource(R.string.settings_delete_all_text),
            confirmLabel = stringResource(R.string.delete_everything),
            onConfirm = {
                dialog = 0
                viewModel.deleteAllEntries { scope.launch { snackbar.showSnackbar(deletedMessage) } }
            },
            onDismiss = { dialog = 0 },
        )
    }
}

private const val DIALOG_DISABLE_LOCK = 1
private const val DIALOG_DELETE_ALL = 2

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
    HorizontalDivider()
}

@Composable
fun ClickRow(title: String, onClick: () -> Unit, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}
