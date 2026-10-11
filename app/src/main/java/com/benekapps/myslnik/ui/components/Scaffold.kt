package com.benekapps.myslnik.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.benekapps.myslnik.R

/** Max width of reading content on tablets and landscape. */
val ContentMaxWidth = 640.dp

/** The SOS button shown in the top bar of every screen, also when locked. */
@Composable
fun SosButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sos_button_description)
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(end = 8.dp)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.Phone, contentDescription = null)
        Text(stringResource(R.string.sos), modifier = Modifier.padding(start = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyslnikTopBar(
    title: String,
    onSos: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            actions()
            if (onSos != null) SosButton(onSos)
        },
    )
}

/**
 * Standard screen: top bar with SOS, insets (including the keyboard) handled,
 * content centred and width-limited.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onSos: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { MyslnikTopBar(title = title, onSos = onSos, onBack = onBack, actions = actions) },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding -> content(padding) }
}

/** A scrolling, centred column for most screens. */
@Composable
fun ScrollingContent(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun rememberSnackbarHostState() = remember { SnackbarHostState() }
