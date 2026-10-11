package com.benekapps.myslnik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.AppContainer
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.ThemeMode
import com.benekapps.myslnik.ui.components.ConfirmDialog
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.lock.LockScreen
import com.benekapps.myslnik.ui.navigation.MainApp
import com.benekapps.myslnik.ui.onboarding.OnboardingScreen
import com.benekapps.myslnik.ui.sos.GroundingScreen
import com.benekapps.myslnik.ui.sos.SosScreen
import com.benekapps.myslnik.ui.theme.MyslnikTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { LOADING, DATABASE_ERROR, ONBOARDING, READY }

data class RootState(
    val phase: Phase = Phase.LOADING,
    val locked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val secureScreen: Boolean = true,
    val biometricEnabled: Boolean = false,
)

private enum class DbState { OPENING, OPEN, FAILED }

class RootViewModel(private val container: AppContainer) : ViewModel() {
    private val db = MutableStateFlow(DbState.OPENING)

    val state: StateFlow<RootState> = combine(db, container.settings.settings, container.lockManager.locked) { db, s, locked ->
        RootState(
            phase = when {
                db == DbState.OPENING -> Phase.LOADING
                db == DbState.FAILED -> Phase.DATABASE_ERROR
                !s.onboardingDone -> Phase.ONBOARDING
                else -> Phase.READY
            },
            locked = s.lockEnabled && locked,
            themeMode = s.themeMode,
            secureScreen = s.secureScreen,
            biometricEnabled = s.biometricEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState())

    init {
        viewModelScope.launch {
            // Without a PIN there is nothing to unlock. Decided once at start,
            // so setting a PIN later does not lock the app straight away.
            if (!container.settings.settings.first().lockEnabled) container.lockManager.unlock()
        }
        openDatabase()
    }

    fun openDatabase() {
        db.value = DbState.OPENING
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { container.openDatabase() }.isSuccess }
            db.value = if (ok) DbState.OPEN else DbState.FAILED
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.resetDatabase() }
            container.settings.setPinHash(null)
            container.lockManager.unlock()
            openDatabase()
        }
    }
}

@Composable
fun MyslnikRoot(viewModel: RootViewModel, onSecureScreen: (Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(state.secureScreen) { onSecureScreen(state.secureScreen) }

    MyslnikTheme(state.themeMode) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            when (state.phase) {
                Phase.LOADING -> Box(Modifier.fillMaxSize())
                Phase.DATABASE_ERROR -> WithSos { onSos ->
                    DatabaseErrorScreen(onRetry = viewModel::openDatabase, onReset = viewModel::resetDatabase, onSos = onSos)
                }
                Phase.ONBOARDING -> WithSos { onSos -> OnboardingScreen(onSos = onSos) }
                Phase.READY -> Box(Modifier.fillMaxSize()) {
                    // The app stays composed under the lock screen so nothing typed is lost,
                    // but it is not drawn and hidden from accessibility services.
                    MainApp(
                        modifier = if (state.locked) {
                            Modifier
                                .alpha(0f)
                                .clearAndSetSemantics { }
                        } else {
                            Modifier
                        },
                    )
                    if (state.locked) {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                            WithSos { onSos -> LockScreen(biometricEnabled = state.biometricEnabled, onSos = onSos) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SOS and grounding for screens outside the main navigation (lock screen,
 * onboarding, errors). Keeps the content's state while SOS is open.
 */
@Composable
fun WithSos(content: @Composable (onSos: () -> Unit) -> Unit) {
    var screen by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    when (screen) {
        0 -> holder.SaveableStateProvider("content") { content { screen = 1 } }
        1 -> {
            BackHandler { screen = 0 }
            SosScreen(onBack = { screen = 0 }, onGrounding = { screen = 2 })
        }
        else -> {
            BackHandler { screen = 1 }
            GroundingScreen(onBack = { screen = 1 }, onOpenSos = { screen = 1 })
        }
    }
}

@Composable
private fun DatabaseErrorScreen(onRetry: () -> Unit, onReset: () -> Unit, onSos: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.app_name), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.db_error_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.db_error_text),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.db_error_reset))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.db_error_reset),
            text = stringResource(R.string.db_error_reset_text),
            confirmLabel = stringResource(R.string.delete_everything),
            onConfirm = {
                confirm = false
                onReset()
            },
            onDismiss = { confirm = false },
        )
    }
}
