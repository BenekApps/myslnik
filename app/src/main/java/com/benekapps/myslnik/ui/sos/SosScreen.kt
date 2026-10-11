package com.benekapps.myslnik.ui.sos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.benekapps.myslnik.R
import com.benekapps.myslnik.domain.Helplines
import com.benekapps.myslnik.ui.components.ContentMaxWidth
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.dial
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.benekapps.myslnik.ui.components.PolishLocale

/** Help numbers. Works without internet and without unlocking the app. */
@Composable
fun SosScreen(onBack: () -> Unit, onGrounding: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val helplines = remember(resources) { Helplines.load(resources) }
    val verifiedOn = remember(resources) {
        val date = LocalDate.parse(resources.getString(R.string.helplines_verified_on))
        DateTimeFormatter.ofPattern("d MMMM yyyy", PolishLocale).format(date)
    }
    ScreenScaffold(title = stringResource(R.string.sos_title), onSos = null, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.sos_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            helplines.forEach { h ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            h.number,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(h.name, style = MaterialTheme.typography.titleMedium)
                        Text(h.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            h.hours,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(
                            onClick = { dial(context, h.dialNumber) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = null)
                            Text(
                                stringResource(R.string.call_number, h.number),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            SectionTitle(stringResource(R.string.grounding_title))
            Text(stringResource(R.string.grounding_card_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onGrounding, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.grounding_start))
            }
            Text(
                stringResource(R.string.helplines_verified_label, verifiedOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

private val groundingSteps = listOf(
    R.string.grounding_step_see,
    R.string.grounding_step_touch,
    R.string.grounding_step_hear,
    R.string.grounding_step_smell,
    R.string.grounding_step_taste,
)

/** 5-4-3-2-1: one step per screen, large text. */
@Composable
fun GroundingScreen(onBack: () -> Unit, onOpenSos: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val done = step >= groundingSteps.size
    ScreenScaffold(title = stringResource(R.string.grounding_title), onSos = onOpenSos, onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { (step.coerceAtMost(groundingSteps.size)) / groundingSteps.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = ContentMaxWidth)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!done) {
                    Text(
                        (groundingSteps.size - step).toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(groundingSteps[step]),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.grounding_take_time),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.grounding_done),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!done) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tr_back))
                        }
                    }
                    Button(onClick = { step++ }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tr_next))
                    }
                } else {
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.grounding_again))
                    }
                    Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.grounding_finish))
                    }
                }
            }
        }
    }
}
