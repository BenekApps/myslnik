package com.benekapps.myslnik.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.security.PinHasher
import com.benekapps.myslnik.domain.AppLanguage
import com.benekapps.myslnik.domain.Helplines
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.dial
import com.benekapps.myslnik.ui.lock.Biometrics
import com.benekapps.myslnik.ui.lock.PinSetupForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(private val settings: SettingsRepository) : ViewModel() {
    /** Same as in settings: stored for background work, then AppCompat recreates the screen. */
    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settings.setLanguage(language.tag)
        AppLanguage.apply(language)
    }

    fun finish(pin: String?, biometric: Boolean) {
        viewModelScope.launch {
            if (pin != null) {
                settings.setPinHash(withContext(Dispatchers.Default) { PinHasher.hash(pin) })
                settings.setBiometricEnabled(biometric)
            }
            settings.setOnboardingDone()
        }
    }
}

private const val PAGES = 4

/** Four short screens: language, what it is, what it is not (with help numbers), the lock. */
@Composable
fun OnboardingScreen(
    onSos: () -> Unit,
    viewModel: OnboardingViewModel = appViewModel { c -> OnboardingViewModel(c.settings) },
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    // Not saveable on purpose: a PIN should not end up in saved instance state.
    var chosenPin by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.isAvailable(context) }
    BackHandler(enabled = page > 0) { page-- }

    ScreenScaffold(title = stringResource(R.string.app_name), onSos = onSos) { padding ->
        ScrollingContent(padding) {
            LinearProgressIndicator(
                progress = { (page + 1) / PAGES.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
            when (page) {
                0 -> {
                    Title(stringResource(R.string.onboarding_language_title))
                    // The language the texts are shown in: Polish also when the phone uses neither.
                    val shown = if (stringResource(R.string.content_locale).startsWith("uk")) {
                        AppLanguage.UKRAINIAN
                    } else {
                        AppLanguage.POLISH
                    }
                    listOf(
                        AppLanguage.POLISH to stringResource(R.string.language_name_pl),
                        AppLanguage.UKRAINIAN to stringResource(R.string.language_name_uk),
                    ).forEach { (language, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = shown == language,
                                    role = Role.RadioButton,
                                    onClick = { if (shown != language) viewModel.setLanguage(language) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = shown == language, onClick = null)
                            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Text(
                        stringResource(R.string.onboarding_language_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                1 -> {
                    Title(stringResource(R.string.onboarding_1_title))
                    Paragraphs(R.array.onboarding_1_paragraphs)
                }
                2 -> {
                    Title(stringResource(R.string.onboarding_2_title))
                    Paragraphs(R.array.onboarding_2_paragraphs)
                    Helplines.short(LocalResources.current).forEach { h ->
                        FilledTonalButton(
                            onClick = { dial(context, h.dialNumber) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = null)
                            Text(
                                stringResource(R.string.call_number_named, h.number, h.name),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.onboarding_2_sos),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                else -> {
                    Title(stringResource(R.string.onboarding_3_title))
                    Paragraphs(R.array.onboarding_3_paragraphs)
                    val pin = chosenPin
                    if (pin == null) {
                        PinSetupForm(onPinChosen = { chosenPin = it }, modifier = Modifier.padding(top = 8.dp))
                        TextButton(
                            onClick = { viewModel.finish(pin = null, biometric = false) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(R.string.onboarding_skip_lock)) }
                    } else if (biometricAvailable) {
                        Text(
                            stringResource(R.string.biometric_offer),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = { viewModel.finish(pin, biometric = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) { Text(stringResource(R.string.biometric_enable)) }
                        OutlinedButton(
                            onClick = { viewModel.finish(pin, biometric = false) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.not_now)) }
                    } else {
                        Text(stringResource(R.string.pin_saved), style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { viewModel.finish(pin, biometric = false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) { Text(stringResource(R.string.onboarding_start)) }
                    }
                }
            }
            if (page < PAGES - 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (page > 0) {
                        OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tr_back))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Button(onClick = { page++ }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tr_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Paragraphs(arrayRes: Int) {
    Column {
        stringArrayResource(arrayRes).forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}
