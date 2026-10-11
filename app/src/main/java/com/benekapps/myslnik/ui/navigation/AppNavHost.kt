package com.benekapps.myslnik.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
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
import com.benekapps.myslnik.domain.Exercise
import com.benekapps.myslnik.ui.activities.ARG_DAY
import com.benekapps.myslnik.ui.activities.ActivitiesScreen
import com.benekapps.myslnik.ui.activities.ActivityEditScreen
import com.benekapps.myslnik.ui.activities.ActivityEditViewModel
import com.benekapps.myslnik.ui.experiments.ExperimentScreen
import com.benekapps.myslnik.ui.experiments.ExperimentsScreen
import com.benekapps.myslnik.ui.exercises.ExercisesScreen
import com.benekapps.myslnik.ui.exercises.KnowledgeCardScreen
import com.benekapps.myslnik.ui.exercises.KnowledgeScreen
import com.benekapps.myslnik.ui.export.ExportScreen
import com.benekapps.myslnik.ui.exposure.ExposureLadderScreen
import com.benekapps.myslnik.ui.exposure.ExposureLaddersScreen
import com.benekapps.myslnik.ui.exposure.ExposureStepScreen
import com.benekapps.myslnik.ui.history.HistoryItem
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
import com.benekapps.myslnik.ui.problems.ProblemDetailScreen
import com.benekapps.myslnik.ui.problems.ProblemWizardScreen
import com.benekapps.myslnik.ui.problems.ProblemsScreen
import com.benekapps.myslnik.ui.settings.BackupScreen
import com.benekapps.myslnik.ui.settings.SettingsScreen
import com.benekapps.myslnik.ui.sleep.SleepEntryScreen
import com.benekapps.myslnik.ui.sleep.SleepScreen
import com.benekapps.myslnik.ui.sos.GroundingScreen
import com.benekapps.myslnik.ui.support.SupportScreen
import com.benekapps.myslnik.ui.sos.SosScreen
import com.benekapps.myslnik.ui.thought.ThoughtDetailScreen
import com.benekapps.myslnik.ui.thought.ThoughtWizardScreen
import com.benekapps.myslnik.ui.worry.WorriesScreen
import com.benekapps.myslnik.ui.worry.WorryTimeScreen

object Routes {
    // Bottom bar
    const val HOME = "home"
    const val EXERCISES = "exercises"
    const val HISTORY = "history"
    const val KNOWLEDGE = "knowledge"
    const val SETTINGS = "settings"

    const val THOUGHT_WIZARD = "thought_wizard?id={id}"
    const val THOUGHT_DETAIL = "thought/{id}"
    const val MOOD = "mood"
    const val MOOD_ENTRY = "mood_entry?id={id}"
    const val MOOD_DETAIL = "mood_detail/{id}"
    const val ACTIVITIES = "activities"
    const val ACTIVITY_EDIT = "activity_edit?id={id}&day={day}"
    const val SLEEP = "sleep"
    const val SLEEP_ENTRY = "sleep_entry?id={id}"
    const val EXPOSURE = "exposure"
    const val LADDER = "ladder/{id}"
    const val STEP = "exposure_step/{id}"
    const val EXPERIMENTS = "experiments"
    const val EXPERIMENT = "experiment?id={id}"
    const val WORRIES = "worries"
    const val WORRY_TIME = "worry_time"
    const val PROBLEMS = "problems"
    const val PROBLEM_WIZARD = "problem?id={id}&text={text}"
    const val PROBLEM_DETAIL = "problem_detail/{id}"
    const val CARD = "card/{key}"
    const val SOS = "sos"
    const val GROUNDING = "grounding"
    const val EXPORT = "export"
    const val BACKUP = "backup"
    const val PIN_SETUP = "pin_setup"
    const val MODEL = "model"
    const val ABOUT = "about"
    const val SOURCES = "sources"
    const val PRIVACY = "privacy"
    const val SUPPORT = "support"

    fun thoughtWizard(id: Long = 0) = "thought_wizard?id=$id"
    fun thoughtDetail(id: Long) = "thought/$id"
    fun moodEntry(id: Long = 0) = "mood_entry?id=$id"
    fun moodDetail(id: Long) = "mood_detail/$id"
    fun activityEdit(id: Long = 0, day: Long = ActivityEditViewModel.NO_DAY) = "activity_edit?id=$id&day=$day"
    fun sleepEntry(id: Long = 0) = "sleep_entry?id=$id"
    fun ladder(id: Long) = "ladder/$id"
    fun step(id: Long) = "exposure_step/$id"
    fun experiment(id: Long = 0) = "experiment?id=$id"
    fun problemWizard(id: Long = 0, text: String = "") = "problem?id=$id&text=${Uri.encode(text)}"
    fun problemDetail(id: Long) = "problem_detail/$id"
    fun card(key: String) = "card/$key"

    fun exercise(e: Exercise): String = when (e) {
        Exercise.THOUGHT_RECORD -> thoughtWizard()
        Exercise.MOOD -> MOOD
        Exercise.ACTIVITIES -> ACTIVITIES
        Exercise.SLEEP -> SLEEP
        Exercise.EXPOSURE -> EXPOSURE
        Exercise.EXPERIMENTS -> EXPERIMENTS
        Exercise.WORRY_TIME -> WORRIES
        Exercise.PROBLEM_SOLVING -> PROBLEMS
        Exercise.SOS -> SOS
    }
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.EXERCISES, R.string.nav_exercises, Icons.Filled.Create),
    TopLevel(Routes.HISTORY, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.KNOWLEDGE, R.string.nav_knowledge, Icons.Filled.Info),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/** Pops only from a settled screen, so a double tap or a late callback cannot pop twice. */
private fun NavController.back() {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        popBackStack()
    }
}

/** Closes a wizard and shows the saved entry (unless its detail screen is already underneath). */
private fun NavController.closeWizard(savedId: Long?, detailRoute: String, detail: (Long) -> String) {
    back()
    if (savedId != null && currentDestination?.route != detailRoute) navigate(detail(savedId))
}

private fun NavController.openItem(item: HistoryItem) {
    navigate(
        when (item) {
            is HistoryItem.Thought -> if (item.record.isDraft) Routes.thoughtWizard(item.id) else Routes.thoughtDetail(item.id)
            is HistoryItem.Mood -> Routes.moodDetail(item.id)
            is HistoryItem.Sleep -> Routes.sleepEntry(item.id)
            is HistoryItem.Experiment -> Routes.experiment(item.id)
            is HistoryItem.Problem -> if (item.problem.isDraft) Routes.problemWizard(item.id) else Routes.problemDetail(item.id)
        },
    )
}

@Composable
fun MainApp(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }
    val sos = { navController.navigate(Routes.SOS) { launchSingleTop = true } }
    val openExercise = { e: Exercise -> navController.navigate(Routes.exercise(e)) }

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
            val idArg = navArgument("id") { type = NavType.LongType; defaultValue = 0L }

            composable(Routes.HOME) {
                HomeScreen(
                    onNewThought = { navController.navigate(Routes.thoughtWizard()) },
                    onContinueDraft = { navController.navigate(Routes.thoughtWizard(it)) },
                    onNewMood = { navController.navigate(Routes.moodEntry()) },
                    onOpenItem = navController::openItem,
                    onOpenActivities = { navController.navigate(Routes.ACTIVITIES) },
                    onOpenModel = { navController.navigate(Routes.MODEL) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onSos = sos,
                )
            }
            composable(Routes.EXERCISES) { ExercisesScreen(onOpen = openExercise, onSos = sos) }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenItem = navController::openItem,
                    onExport = { navController.navigate(Routes.EXPORT) },
                    onSos = sos,
                )
            }
            composable(Routes.KNOWLEDGE) {
                KnowledgeScreen(
                    onOpenCard = { navController.navigate(Routes.card(it)) },
                    onOpenModel = { navController.navigate(Routes.MODEL) },
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
                    onSupport = { navController.navigate(Routes.SUPPORT) },
                    onSos = sos,
                )
            }

            // Thought record and mood
            composable(Routes.THOUGHT_WIZARD, arguments = listOf(idArg)) {
                ThoughtWizardScreen(
                    onClose = { navController.closeWizard(it, Routes.THOUGHT_DETAIL, Routes::thoughtDetail) },
                    onSos = sos,
                )
            }
            composable(Routes.THOUGHT_DETAIL, arguments = listOf(idArg)) {
                ThoughtDetailScreen(
                    onBack = { navController.back() },
                    onEdit = { navController.navigate(Routes.thoughtWizard(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.MOOD) {
                MoodScreen(
                    onBack = { navController.back() },
                    onNewEntry = { navController.navigate(Routes.moodEntry()) },
                    onOpenEntry = { navController.navigate(Routes.moodDetail(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.MOOD_ENTRY, arguments = listOf(idArg)) {
                MoodEntryScreen(onDone = { navController.back() }, onSos = sos)
            }
            composable(Routes.MOOD_DETAIL, arguments = listOf(idArg)) {
                MoodDetailScreen(
                    onBack = { navController.back() },
                    onEdit = { navController.navigate(Routes.moodEntry(it)) },
                    onSos = sos,
                )
            }

            // 1.1
            composable(Routes.ACTIVITIES) {
                ActivitiesScreen(
                    onBack = { navController.back() },
                    onAdd = { navController.navigate(Routes.activityEdit(day = it)) },
                    onEdit = { navController.navigate(Routes.activityEdit(id = it)) },
                    onSos = sos,
                )
            }
            composable(
                Routes.ACTIVITY_EDIT,
                arguments = listOf(idArg, navArgument(ARG_DAY) { type = NavType.LongType; defaultValue = ActivityEditViewModel.NO_DAY }),
            ) { ActivityEditScreen(onDone = { navController.back() }, onSos = sos) }
            composable(Routes.SLEEP) {
                SleepScreen(
                    onBack = { navController.back() },
                    onNew = { navController.navigate(Routes.sleepEntry()) },
                    onOpen = { navController.navigate(Routes.sleepEntry(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.SLEEP_ENTRY, arguments = listOf(idArg)) {
                SleepEntryScreen(onDone = { navController.back() }, onSos = sos)
            }

            // 1.2
            composable(Routes.EXPOSURE) {
                ExposureLaddersScreen(
                    onBack = { navController.back() },
                    onOpen = { navController.navigate(Routes.ladder(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.LADDER, arguments = listOf(idArg)) {
                ExposureLadderScreen(
                    onBack = { navController.back() },
                    onOpenStep = { navController.navigate(Routes.step(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.STEP, arguments = listOf(idArg)) {
                ExposureStepScreen(onBack = { navController.back() }, onSos = sos)
            }
            composable(Routes.EXPERIMENTS) {
                ExperimentsScreen(
                    onBack = { navController.back() },
                    onNew = { navController.navigate(Routes.experiment()) },
                    onOpen = { navController.navigate(Routes.experiment(it)) },
                    onSos = sos,
                )
            }
            composable(Routes.EXPERIMENT, arguments = listOf(idArg)) {
                ExperimentScreen(onDone = { navController.back() }, onSos = sos)
            }
            composable(Routes.WORRIES) {
                WorriesScreen(
                    onBack = { navController.back() },
                    onStartWorryTime = { navController.navigate(Routes.WORRY_TIME) },
                    onSos = sos,
                )
            }
            composable(Routes.WORRY_TIME) {
                WorryTimeScreen(
                    onBack = { navController.back() },
                    onSolve = { navController.navigate(Routes.problemWizard(text = it)) },
                    onSos = sos,
                )
            }
            composable(Routes.PROBLEMS) {
                ProblemsScreen(
                    onBack = { navController.back() },
                    onNew = { navController.navigate(Routes.problemWizard()) },
                    onOpen = { p -> navController.navigate(if (p.isDraft) Routes.problemWizard(p.id) else Routes.problemDetail(p.id)) },
                    onSos = sos,
                )
            }
            composable(
                Routes.PROBLEM_WIZARD,
                arguments = listOf(idArg, navArgument("text") { type = NavType.StringType; defaultValue = "" }),
            ) {
                ProblemWizardScreen(
                    onClose = { navController.closeWizard(it, Routes.PROBLEM_DETAIL, Routes::problemDetail) },
                    onSos = sos,
                )
            }
            composable(Routes.PROBLEM_DETAIL, arguments = listOf(idArg)) {
                ProblemDetailScreen(
                    onBack = { navController.back() },
                    onEdit = { navController.navigate(Routes.problemWizard(it)) },
                    onSos = sos,
                )
            }

            // 1.3
            composable(Routes.CARD, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                KnowledgeCardScreen(
                    key = entry.arguments?.getString("key").orEmpty(),
                    onBack = { navController.back() },
                    onOpenExercise = openExercise,
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
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.back() }, onSupport = { navController.navigate(Routes.SUPPORT) }, onSos = sos)
            }
            composable(Routes.SUPPORT) { SupportScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.SOURCES) { SourcesScreen(onBack = { navController.back() }, onSos = sos) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.back() }, onSos = sos) }
        }
    }
}
