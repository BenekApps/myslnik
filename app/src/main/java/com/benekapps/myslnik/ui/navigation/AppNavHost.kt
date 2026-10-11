package com.benekapps.myslnik.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.ui.export.ExportScreen
import com.benekapps.myslnik.ui.history.HistoryScreen
import com.benekapps.myslnik.ui.home.HomeScreen
import com.benekapps.myslnik.ui.info.AboutScreen
import com.benekapps.myslnik.ui.info.CognitiveModelScreen
import com.benekapps.myslnik.ui.info.PrivacyScreen
import com.benekapps.myslnik.ui.info.SourcesScreen
import com.benekapps.myslnik.ui.lock.PinSetupScreen
import com.benekapps.myslnik.ui.mood.MoodDetailScreen
import com.benekapps.myslnik.ui.mood.MoodEntryScreen
import com.benekapps.myslnik.ui.mood.MoodScreen
import com.benekapps.myslnik.ui.settings.BackupScreen
import com.benekapps.myslnik.ui.settings.SettingsScreen
import com.benekapps.myslnik.ui.sos.GroundingScreen
import com.benekapps.myslnik.ui.sos.SosScreen
import com.benekapps.myslnik.ui.thought.ThoughtDetailScreen
import com.benekapps.myslnik.ui.thought.ThoughtWizardScreen

object Routes {
    const val HOME = "home"
    const val MOOD = "mood"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val THOUGHT_WIZARD = "thought_wizard?id={id}"
    const val THOUGHT_DETAIL = "thought/{id}"
    const val MOOD_ENTRY = "mood_entry?id={id}"
    const val MOOD_DETAIL = "mood_detail/{id}"
    const val SOS = "sos"
    const val GROUNDING = "grounding"
    const val EXPORT = "export"
    const val BACKUP = "backup"
    const val PIN_SETUP = "pin_setup"
    const val MODEL = "model"
    const val ABOUT = "about"
    const val SOURCES = "sources"
    const val PRIVACY = "privacy"

    fun thoughtWizard(id: Long = 0) = "thought_wizard?id=$id"
    fun thoughtDetail(id: Long) = "thought/$id"
    fun moodEntry(id: Long = 0) = "mood_entry?id=$id"
    fun moodDetail(id: Long) = "mood_detail/$id"
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.MOOD, R.string.nav_mood, Icons.Filled.Face),
    TopLevel(Routes.HISTORY, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/** Pops only from a settled screen, so a double tap or a late callback cannot pop twice. */
private fun NavController.back() {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        popBackStack()
    }
}

@Composable
fun MainApp(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }
    val sos = { navController.navigate(Routes.SOS) { launchSingleTop = true } }
    val openThought = { r: ThoughtRecord ->
        navController.navigate(if (r.isDraft) Routes.thoughtWizard(r.id) else Routes.thoughtDetail(r.id))
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            val idArg = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })

            composable(Routes.HOME) {
                HomeScreen(
                    onNewThought = { navController.navigate(Routes.thoughtWizard()) },
                    onContinueDraft = { navController.navigate(Routes.thoughtWizard(it)) },
                    onNewMood = { navController.navigate(Routes.moodEntry()) },
                    onOpenThought = openThought,
                    onOpenMood = { navController.navigate(Routes.moodDetail(it)) },
                    onOpenModel = { navController.navigate(Routes.MODEL) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onSos = sos,
                )
            }
            composable(Routes.MOOD) {
                MoodScreen(
                    onNewEntry = { navController.navigate(Routes.moodEntry()) },
                    onOpenEntry = { navController.navigate(Routes.moodDetail(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenThought = openThought,
                    onOpenMood = { navController.navigate(Routes.moodDetail(it)) },
                    onExport = { navController.navigate(Routes.EXPORT) },
                    onSos = sos,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onSetPin = { navController.navigate(Routes.PIN_SETUP) },
                    onBackup = { navController.navigate(Routes.BACKUP) },
                    onExport = { navController.navigate(Routes.EXPORT) },
                    onModel = { navController.navigate(Routes.MODEL) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                    onSources = { navController.navigate(Routes.SOURCES) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onSos = sos,
                )
            }
            composable(Routes.THOUGHT_WIZARD, arguments = idArg) {
                ThoughtWizardScreen(
                    onClose = { savedId ->
                        navController.back()
                        if (savedId != null && navController.currentDestination?.route != Routes.THOUGHT_DETAIL) {
                            navController.navigate(Routes.thoughtDetail(savedId))
                        }
                    },
                    onSos = sos,
                )
            }
            composable(Routes.THOUGHT_DETAIL, arguments = idArg) {
                ThoughtDetailScreen(
                    onBack = { navController.back() },
                    onEdit = { navController.navigate(Routes.thoughtWizard(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.MOOD_ENTRY, arguments = idArg) {
                MoodEntryScreen(onDone = { navController.back() }, onSos = sos)
            }
            composable(Routes.MOOD_DETAIL, arguments = idArg) {
                MoodDetailScreen(
                    onBack = { navController.back() },
                    onEdit = { navController.navigate(Routes.moodEntry(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.SOS) {
                SosScreen(
                    onBack = { navController.back() },
                    onGrounding = { navController.navigate(Routes.GROUNDING) },
                )
            }
            composable(Routes.GROUNDING) {
                GroundingScreen(
                    onBack = { navController.back() },
                    onOpenSos = {
                        if (!navController.popBackStack(Routes.SOS, inclusive = false)) sos()
                    },
                )
            }
            composable(Routes.EXPORT) { ExportScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.BACKUP) { BackupScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.PIN_SETUP) { PinSetupScreen(onDone = { navController.back() }, onSos = sos) }
            composable(Routes.MODEL) { CognitiveModelScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.SOURCES) { SourcesScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.back() }, onSos = sos) }
        }
    }
}
