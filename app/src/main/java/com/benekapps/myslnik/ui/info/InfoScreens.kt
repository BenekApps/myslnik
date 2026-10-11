package com.benekapps.myslnik.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.benekapps.myslnik.R
import com.benekapps.myslnik.ui.components.ScreenScaffold
import com.benekapps.myslnik.ui.components.ScrollingContent
import com.benekapps.myslnik.ui.components.SectionTitle
import com.benekapps.myslnik.ui.components.openUrl

/** Short psychoeducation: situation → thoughts → emotions, body, behaviour. */
@Composable
fun CognitiveModelScreen(onBack: () -> Unit, onSos: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.model_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.model_intro), style = MaterialTheme.typography.bodyLarge)
            val diagramDescription = stringResource(R.string.model_diagram_description)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .semantics(mergeDescendants = true) { contentDescription = diagramDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModelBox(stringResource(R.string.model_situation), MaterialTheme.colorScheme.surfaceContainerHigh)
                Arrow()
                ModelBox(stringResource(R.string.model_thoughts), MaterialTheme.colorScheme.primaryContainer)
                Arrow()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModelBox(stringResource(R.string.model_emotions), MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                    ModelBox(stringResource(R.string.model_body), MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                    ModelBox(stringResource(R.string.model_behaviour), MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                }
            }
            stringArrayResource(R.array.model_paragraphs).forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
            }
            SectionTitle(stringResource(R.string.model_example_title))
            Text(stringResource(R.string.model_example), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ModelBox(text: String, color: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Arrow() {
    Text("↓", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
}

@Composable
fun AboutScreen(onBack: () -> Unit, onSupport: () -> Unit, onSos: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val sourceUrl = stringResource(R.string.source_code_url)
    ScreenScaffold(title = stringResource(R.string.about_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            stringArrayResource(R.array.about_paragraphs).forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            }
            SectionTitle(stringResource(R.string.about_review_title))
            Text(stringResource(R.string.about_review_text), style = MaterialTheme.typography.bodyLarge)
            SectionTitle(stringResource(R.string.about_license_title))
            Text(stringResource(R.string.about_license_text), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { openUrl(context, sourceUrl) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.about_source_code))
            }
            OutlinedButton(onClick = { openUrl(context, "$sourceUrl/issues") }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.about_report_issue))
            }
            SectionTitle(stringResource(R.string.support_title))
            Text(stringResource(R.string.support_subtitle), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onSupport, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.support_title))
            }
        }
    }
}

@Composable
fun SourcesScreen(onBack: () -> Unit, onSos: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.sources_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Text(stringResource(R.string.sources_intro), style = MaterialTheme.typography.bodyLarge)
            stringArrayResource(R.array.sources).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onSos: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.privacy_title), onSos = onSos, onBack = onBack) { padding ->
        ScrollingContent(padding) {
            stringArrayResource(R.array.privacy_paragraphs).forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}
