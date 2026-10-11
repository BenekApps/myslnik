package com.benekapps.myslnik.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.security.PinHasher
import com.benekapps.myslnik.lock.AppLockManager
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Biometrics {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, negative: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negative)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}

data class LockUiState(
    val checking: Boolean = false,
    val error: Int? = null,
    val lockedUntil: Long = 0,
)

class LockViewModel(
    private val settings: SettingsRepository,
    private val entries: () -> EntriesRepository,
    private val lockManager: AppLockManager,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun submit(pin: String) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            val s = settings.settings.first()
            val time = now()
            if (time < s.pinLockedUntil) {
                _state.update { it.copy(checking = false, error = R.string.lock_too_many, lockedUntil = s.pinLockedUntil) }
                return@launch
            }
            val hash = s.pinHash
            val ok = hash == null || withContext(Dispatchers.Default) { PinHasher.verify(pin, hash) }
            if (ok) {
                settings.clearFailedPins()
                _state.value = LockUiState()
                lockManager.unlock()
            } else {
                val attempts = s.failedPinAttempts + 1
                val lockedUntil = lockoutUntil(attempts, time)
                settings.recordFailedPin(attempts, lockedUntil)
                _state.update {
                    it.copy(
                        checking = false,
                        error = if (lockedUntil > time) R.string.lock_too_many else R.string.lock_wrong_pin,
                        lockedUntil = lockedUntil,
                    )
                }
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch {
            settings.clearFailedPins()
            lockManager.unlock()
        }
    }

    /** Forgotten PIN: the only way back in is to delete all entries. */
    fun resetEverything() {
        viewModelScope.launch {
            entries().deleteAll()
            settings.setPinHash(null)
            lockManager.unlock()
        }
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 15 * 60_000L

        /** 30 s after the 5th wrong PIN, doubling each time, at most 15 min. */
        fun lockoutUntil(attempts: Int, now: Long): Long {
            if (attempts < FREE_ATTEMPTS) return 0
            val exponent = (attempts - FREE_ATTEMPTS).coerceAtMost(10)
            return now + (BASE_LOCKOUT_MS shl exponent).coerceAtMost(MAX_LOCKOUT_MS)
        }
    }
}

@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    onSos: () -> Unit,
    viewModel: LockViewModel = appViewModel { c -> LockViewModel(c.settings, { c.entries }, c.lockManager) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var forgotDialog by rememberSaveable { mutableIntStateOf(0) }
    val canUseBiometric = remember(biometricEnabled) { biometricEnabled && Biometrics.isAvailable(context) }
    val promptTitle = stringResource(R.string.lock_biometric_title)
    val promptNegative = stringResource(R.string.lock_use_pin)
    val showPrompt = {
        (context.findActivity() as? FragmentActivity)?.let {
            Biometrics.authenticate(it, promptTitle, promptNegative, viewModel::unlockWithBiometric)
        }
    }
    LaunchedEffect(canUseBiometric) { if (canUseBiometric) showPrompt() }
    LaunchedEffect(state.error) { if (state.error != null) pin = "" }

    ScreenScaffold(title = stringResource(R.string.app_name), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .size(48.dp),
                )
                Text(
                    stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(PinHasher.MAX_LENGTH) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit(pin) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    onClick = { viewModel.submit(pin) },
                    enabled = pin.length >= PinHasher.MIN_LENGTH && !state.checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text(stringResource(R.string.lock_unlock)) }
                if (canUseBiometric) {
                    OutlinedButton(
                        onClick = { showPrompt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text(stringResource(R.string.lock_use_biometric)) }
                }
                Text(
                    stringResource(R.string.lock_sos_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
                TextButton(onClick = { forgotDialog = 1 }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.lock_forgot))
                }
            }
        }
    }

    when (forgotDialog) {
        1 -> AlertDialog(
            onDismissRequest = { forgotDialog = 0 },
            title = { Text(stringResource(R.string.lock_forgot_title)) },
            text = { Text(stringResource(R.string.lock_forgot_text)) },
            confirmButton = { TextButton(onClick = { forgotDialog = 2 }) { Text(stringResource(R.string.lock_forgot_reset)) } },
            dismissButton = { TextButton(onClick = { forgotDialog = 0 }) { Text(stringResource(R.string.cancel)) } },
        )
        2 -> AlertDialog(
            onDismissRequest = { forgotDialog = 0 },
            title = { Text(stringResource(R.string.lock_forgot_confirm_title)) },
            text = { Text(stringResource(R.string.lock_forgot_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    forgotDialog = 0
                    viewModel.resetEverything()
                }) { Text(stringResource(R.string.delete_everything)) }
            },
            dismissButton = { TextButton(onClick = { forgotDialog = 0 }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Enter a new PIN twice. Used in onboarding and settings. */
@Composable
fun PinSetupForm(onPinChosen: (String) -> Unit, modifier: Modifier = Modifier) {
    // Not saveable on purpose: a PIN should not end up in saved instance state.
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val submit = {
        error = when {
            !PinHasher.isValidPin(first) -> R.string.pin_invalid
            first != second -> R.string.pin_mismatch
            else -> null
        }
        if (error == null) onPinChosen(first)
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = first,
            onValueChange = { v -> first = v.filter { it.isDigit() }.take(PinHasher.MAX_LENGTH) },
            label = { Text(stringResource(R.string.pin_new_label)) },
            supportingText = { Text(stringResource(R.string.pin_rules, PinHasher.MIN_LENGTH, PinHasher.MAX_LENGTH)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = second,
            onValueChange = { v -> second = v.filter { it.isDigit() }.take(PinHasher.MAX_LENGTH) },
            label = { Text(stringResource(R.string.pin_repeat_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        error?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = submit,
            enabled = first.isNotEmpty() && second.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) { Text(stringResource(R.string.pin_set)) }
    }
}

class PinSetupViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun setPin(pin: String) {
        viewModelScope.launch {
            val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
            settings.setPinHash(hash)
            _done.value = true
        }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    }
}

/** Settings → set or change PIN, then offer fingerprint. */
@Composable
fun PinSetupScreen(
    onDone: () -> Unit,
    onSos: () -> Unit,
    viewModel: PinSetupViewModel = appViewModel { c -> PinSetupViewModel(c.settings) },
) {
    val done by viewModel.done.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.isAvailable(context) }
    ScreenScaffold(title = stringResource(R.string.pin_setup_title), onSos = onSos, onBack = onDone) { padding ->
        ScrollingContent(padding) {
            if (!done) {
                Text(stringResource(R.string.pin_setup_text), style = MaterialTheme.typography.bodyLarge)
                PinSetupForm(onPinChosen = viewModel::setPin, modifier = Modifier.padding(top = 16.dp))
            } else {
                Text(stringResource(R.string.pin_saved), style = MaterialTheme.typography.titleMedium)
                if (biometricAvailable) {
                    Text(
                        stringResource(R.string.biometric_offer),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.setBiometric(true)
                            onDone()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    ) { Text(stringResource(R.string.biometric_enable)) }
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.not_now))
                    }
                } else {
                    Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}
