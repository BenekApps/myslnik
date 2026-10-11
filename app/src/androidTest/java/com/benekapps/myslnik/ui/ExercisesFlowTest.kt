package com.benekapps.myslnik.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.benekapps.myslnik.MainActivity
import com.benekapps.myslnik.MyslnikApplication
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.WorryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end flows for the 1.1–1.3 modules. */
@RunWith(AndroidJUnit4::class)
class ExercisesFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyslnikApplication).container
    private fun str(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Before
    fun setUp() {
        runBlocking {
            container.settings.setOnboardingDone()
            container.settings.setPinHash(null)
            container.entries.deleteAll()
        }
        container.lockManager.unlock()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(str(R.string.home_new_thought)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.nav_exercises)).performClick()
    }

    private fun open(title: Int) {
        rule.onNodeWithText(str(title)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun sleepDiaryNightIsSavedWithEfficiency() {
        open(R.string.sleep_title)
        rule.onNodeWithText(str(R.string.sleep_add)).performClick()
        // The default night: 23:00–06:45 in bed, 6 h 50 min asleep = 88 %.
        rule.onNodeWithText(str(R.string.save)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.sleep_summary_efficiency, 88)).fetchSemanticsNodes().isNotEmpty() }
        val entry = runBlocking { container.db.sleep().observeAll().first().single() }
        check(entry.quality == 3)
    }

    @Test
    fun activityIsPlannedAndRated() {
        open(R.string.activities_title)
        rule.onNodeWithText(str(R.string.activities_add)).performClick()
        rule.onNode(hasSetTextAction() and hasText(str(R.string.activities_what))).performTextInput("Spacer z psem")
        rule.onNodeWithText(str(R.string.save)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Spacer z psem").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(str(R.string.activities_mark_done, "Spacer z psem")).performClick()
        rule.onNodeWithText(str(R.string.save)).performClick()
        rule.waitUntil(5_000) {
            runBlocking { container.db.activities().getAll().singleOrNull()?.done == true }
        }
    }

    @Test
    fun worryGoesToProblemSolving() {
        open(R.string.worry_title)
        rule.onNode(hasSetTextAction()).performTextInput("Rachunki za prąd")
        rule.onNodeWithText(str(R.string.worry_postpone)).performClick()
        rule.onNodeWithText(str(R.string.worry_start)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.worry_solve)).performScrollTo().performClick()
        // The problem wizard opens with the worry as the problem.
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.problem_step_define_title)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Rachunki za prąd").assertExists()
        val worry = runBlocking { container.db.worries().getAll().single() }
        check(worry.status == WorryStatus.PROBLEM_SOLVING)
    }

    @Test
    fun knowledgeCardLeadsToItsExercise() {
        rule.onNodeWithText(str(R.string.nav_knowledge)).performClick()
        rule.onNodeWithText(str(R.string.card_activation_title)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.knowledge_try)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.activities_add)).fetchSemanticsNodes().isNotEmpty() }
    }
}
