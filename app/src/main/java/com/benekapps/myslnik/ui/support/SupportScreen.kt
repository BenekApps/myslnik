package com.benekapps.myslnik.ui.support

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.R
import com.benekapps.myslnik.support.SupportBilling
import com.benekapps.myslnik.support.SupportEvent
import com.benekapps.myslnik.support.SupportState
import com.benekapps.myslnik.ui.appViewModel
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.findActivity
import com.benekapps.myslnik.ui.components.rememberSnackbarHostState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupportViewModel(private val billing: SupportBilling) : ViewModel() {
    val state: StateFlow<SupportState> = billing.state
    private val _event = MutableStateFlow<SupportEvent?>(null)
    val event: StateFlow<SupportEvent?> = _event.asStateFlow()

    init {
        billing.connect()
        viewModelScope.launch { billing.events.collect { _event.value = it } }
    }

    fun buy(activity: android.app.Activity, productId: String) = billing.launch(activity, productId)
    fun eventShown() {
        _event.value = null
    }

    override fun onCleared() = billing.close()
}

private val productNames = mapOf(
    "support_small" to R.string.support_small,
    "support_medium" to R.string.support_medium,
    "support_large" to R.string.support_large,
)

/**
 * Voluntary support. Reached only from settings and "O aplikacji", never from
 * the start screen or anything near SOS.
 */
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onSos: () -> Unit,
    viewModel: SupportViewModel = appViewModel { c -> SupportViewModel(c.supportBilling()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = rememberSnackbarHostState()
    val pendingText = stringResource(R.string.support_pending)
    val failedText = stringResource(R.string.support_failed)

    LaunchedEffect(event) {
        when (event) {
            SupportEvent.PENDING -> {
                snackbar.showSnackbar(pendingText)
                viewModel.eventShown()
            }
            SupportEvent.FAILED -> {
                snackbar.showSnackbar(failedText)
                viewModel.eventShown()
            }
            else -> Unit
        }
    }

    ScreenScaffold(title = stringResource(R.string.support_title), onSos = onSos, onBack = onBack, snackbarHostState = snackbar) { padding ->
        ScrollingContent(padding) {
            stringArrayResource(R.array.support_paragraphs).forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
            }
            when (val s = state) {
                SupportState.Loading -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                }
                SupportState.Unavailable -> Text(
                    stringResource(R.string.support_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is SupportState.Ready -> s.products.forEach { p ->
                    OutlinedButton(
                        onClick = { context.findActivity()?.let { viewModel.buy(it, p.id) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.support_option, stringResource(productNames.getValue(p.id)), p.price),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.support_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    if (event == SupportEvent.THANKS) {
        AlertDialog(
            onDismissRequest = viewModel::eventShown,
            title = { Text(stringResource(R.string.support_thanks_title)) },
            text = { Text(stringResource(R.string.support_thanks_text)) },
            confirmButton = { TextButton(onClick = viewModel::eventShown) { Text(stringResource(R.string.ok)) } },
        )
    }
}
