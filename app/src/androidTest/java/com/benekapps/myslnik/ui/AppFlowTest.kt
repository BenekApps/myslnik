package com.benekapps.myslnik.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.benekapps.myslnik.MainActivity
import com.benekapps.myslnik.MyslnikApplication
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.security.PinHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end flows on the real app: SOS, thought record, lock, PDF export. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyslnikApplication).container
    private fun str(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Before
    fun setUp() = runBlocking {
        container.settings.setOnboardingDone()
        container.settings.setPinHash(null)
        container.entries.deleteAll()
        container.lockManager.unlock()
        Intents.init()
        // Don't really open the dialer or the share sheet.
        intending(hasAction(Intent.ACTION_DIAL)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        rule.waitUntil(10_000) { rule.onAllNodesWithText(str(R.string.home_new_thought)).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() {
        Intents.release()
        runBlocking { container.settings.setPinHash(null) }
    }

    @Test
    fun sosIsOneTapFromHomeAndDialsTheNumber() {
        rule.onNodeWithContentDescription(str(R.string.sos_button_description)).performClick()
        rule.onNodeWithText(str(R.string.sos_title)).assertExists()
        rule.onNodeWithText(str(R.string.call_number, "116 123")).performClick()
        intended(allOf(hasAction(Intent.ACTION_DIAL), hasData("tel:116123")))
        rule.onNodeWithText(str(R.string.call_number, "112")).performScrollTo().assertExists()
    }

    @Test
    fun thoughtRecordFromStartToSavedDetail() {
        rule.onNodeWithText(str(R.string.home_new_thought)).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Szefowa się nie przywitała")
        closeSoftKeyboard()
        next()
        rule.onNodeWithText(str(R.string.emotion_anxiety)).performScrollTo().performClick()
        next()
        rule.onNode(hasSetTextAction()).performTextInput("Jest na mnie zła")
        closeSoftKeyboard()
        next()
        rule.onNodeWithText(str(R.string.distortion_mind_reading)).performScrollTo().performClick()
        next()
        rule.onNode(hasSetTextAction()).performTextInput("Nie przywitała się")
        closeSoftKeyboard()
        next()
        rule.onNode(hasSetTextAction()).performTextInput("Rozmawiała przez telefon")
        closeSoftKeyboard()
        next()
        rule.onNode(hasSetTextAction()).performTextInput("Mogła być zajęta")
        closeSoftKeyboard()
        rule.onNodeWithText(str(R.string.tr_finish)).performClick()

        // Detail screen shows what was saved.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Mogła być zajęta").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Szefowa się nie przywitała").assertExists()
        val saved = runBlocking { container.entries.observeThoughtRecords().first().single() }
        check(!saved.isDraft)
        check(saved.distortions == listOf("mind_reading"))
        check(saved.automaticThoughts == listOf("Jest na mnie zła"))
    }

    @Test
    fun leavingTheWizardKeepsADraft() {
        rule.onNodeWithText(str(R.string.home_new_thought)).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Niedokończona sytuacja")
        closeSoftKeyboard()
        rule.onNodeWithContentDescription(str(R.string.back)).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(str(R.string.continue_draft)).fetchSemanticsNodes().isNotEmpty()
        }
        val draft = runBlocking { container.entries.observeLatestDraft().first() }
        check(draft?.situation == "Niedokończona sytuacja")
    }

    @Test
    fun lockScreenHasSosAndUnlocksWithPin() {
        runBlocking { container.settings.setPinHash(PinHasher.hash("2580", iterations = 1_000)) }
        container.lockManager.lock()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.lock_title)).fetchSemanticsNodes().isNotEmpty() }

        // SOS works without unlocking.
        rule.onNodeWithContentDescription(str(R.string.sos_button_description)).performClick()
        rule.onNodeWithText(str(R.string.sos_title)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.back)).performClick()

        rule.onNode(hasSetTextAction()).performTextInput("1111")
        closeSoftKeyboard()
        rule.onNodeWithText(str(R.string.lock_unlock)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.lock_wrong_pin)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNode(hasSetTextAction()).performTextInput("2580")
        closeSoftKeyboard()
        rule.onNodeWithText(str(R.string.lock_unlock)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.lock_title)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(str(R.string.home_new_thought)).assertExists()
    }

    @Test
    fun hiddenAppContentIsNotReachableWhileLocked() {
        runBlocking { container.settings.setPinHash(PinHasher.hash("2580", iterations = 1_000)) }
        container.lockManager.lock()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.lock_title)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.home_new_thought)).assertDoesNotExist()
    }

    @Test
    fun moodEntryAndPdfExport() {
        rule.onNodeWithText(str(R.string.home_mood_button)).performScrollTo().performClick()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(str(R.string.mood_rating_description, 6))))
            .performScrollTo()
            .performClick()
        rule.onNodeWithText(str(R.string.save)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.home_mood_today, 6)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(str(R.string.nav_history)).performClick()
        rule.onNodeWithContentDescription(str(R.string.export_title)).performClick()
        rule.onNodeWithText(str(R.string.export_create)).performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(str(R.string.export_ready)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText(str(R.string.export_share))).performScrollTo().performClick()
        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    private fun next() {
        rule.onNodeWithText(str(R.string.tr_next)).performClick()
        rule.waitForIdle()
    }
}
