package com.benekapps.myslnik.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.benekapps.myslnik.R
import com.benekapps.myslnik.domain.AppLocale
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.domain.Helplines
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Formats follow the language of the texts on screen (AppLocale, set by
// MainActivity, which is recreated when the language changes).

fun formatDateTime(time: Long, locale: Locale = AppLocale.current): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM, HH:mm", locale).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

fun formatDate(time: Long, locale: Locale = AppLocale.current): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

fun formatDay(date: java.time.LocalDate, locale: Locale = AppLocale.current): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date)

fun formatShortDate(date: java.time.LocalDate, locale: Locale = AppLocale.current): String =
    DateTimeFormatter.ofPattern("d.MM", locale).format(date)

/** Minutes from midnight as "07:05". */
fun formatClock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)

fun formatDecimal(value: Double, locale: Locale = AppLocale.current): String = String.format(locale, "%.1f", value)

/** Opens the dialer with the number filled in. Needs no permission and works offline. */
fun dial(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_dialer, number), Toast.LENGTH_LONG).show()
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}

tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * Shown once, gently, after a very high emotion or very low mood rating.
 * It never blocks anything.
 */
@Composable
fun CrisisSupportCard(onOpenSos: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val helplines = remember(resources) { Helplines.short(resources) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    stringResource(R.string.crisis_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Text(stringResource(R.string.crisis_card_text), style = MaterialTheme.typography.bodyMedium)
            helplines.forEach { h ->
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
            TextButton(onClick = onOpenSos, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.crisis_card_more))
            }
        }
    }
}

/** Emotion chips from the catalog plus custom ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionPicker(
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    val customSelected = selected.filter { Emotions.isCustom(it) }
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Emotions.all.forEach { def ->
                val isSelected = def.key in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(def.key) },
                    label = { Text(stringResource(def.label)) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
            customSelected.forEach { key ->
                FilterChip(
                    selected = true,
                    onClick = { onToggle(key) },
                    label = { Text(Emotions.label(resources, key)) },
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                val add = {
                    if (customText.isNotBlank()) {
                        val key = Emotions.custom(customText)
                        if (key !in selected) onToggle(key)
                    }
                    customText = ""
                    adding = false
                }
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.take(Emotions.CUSTOM_MAX_LENGTH) },
                    label = { Text(stringResource(R.string.emotion_custom_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = add) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.emotion_custom_add))
                }
            }
        } else {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.emotion_custom_button), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** 0–100 slider in steps of 5, announced by TalkBack as "70 na 100". */
@Composable
fun IntensitySlider(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = stringResource(R.string.intensity_state, value)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange((it / 5f).roundToInt() * 5) },
            valueRange = 0f..100f,
            steps = 19,
            // 19 tick marks look busy; the number above shows the value.
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { stateDescription = state },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A labelled block of text in detail screens; hidden when empty. */
@Composable
fun LabeledText(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Text(value.trim(), style = MaterialTheme.typography.bodyLarge)
}
