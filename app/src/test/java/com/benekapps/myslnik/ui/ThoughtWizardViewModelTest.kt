package com.benekapps.myslnik.ui

import androidx.lifecycle.SavedStateHandle
import com.benekapps.myslnik.FakeEntriesRepository
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.ui.thought.ThoughtWizardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThoughtWizardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeEntriesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.wizard(id: Long = 0) =
        ThoughtWizardViewModel(repo, SavedStateHandle(mapOf("id" to id)), backgroundScope, now = { 1_000 })

    @Test
    fun `nothing is saved while the record is empty`() = runTest(dispatcher) {
        val vm = wizard()
        vm.next()
        vm.next()
        advanceUntilIdle()
        assertTrue(repo.thoughts.value.isEmpty())
    }

    @Test
    fun `draft is saved after every step with the step to resume at`() = runTest(dispatcher) {
        val vm = wizard()
        vm.setSituation("Spotkanie w pracy")
        vm.next()
        advanceUntilIdle()
        var saved = repo.thoughts.value.single()
        assertTrue(saved.isDraft)
        assertEquals(1, saved.draftStep)
        assertEquals("Spotkanie w pracy", saved.situation)

        vm.toggleEmotion("anxiety")
        vm.setIntensityBefore("anxiety", 70)
        vm.next()
        advanceUntilIdle()
        saved = repo.thoughts.value.single()
        assertEquals(2, saved.draftStep)
        assertEquals(70, saved.emotions.single().intensityBefore)
    }

    @Test
    fun `resuming a draft opens the saved step`() = runTest(dispatcher) {
        repo.saveThoughtRecord(ThoughtRecord(createdAt = 1, updatedAt = 1, isDraft = true, draftStep = 4, situation = "x"))
        val vm = wizard(id = 1)
        advanceUntilIdle()
        assertEquals(4, vm.state.value.step)
    }

    @Test
    fun `typed but not added thought is kept when going on`() = runTest(dispatcher) {
        val vm = wizard()
        vm.setPendingThought("  Na pewno się pomylę  ")
        vm.next()
        advanceUntilIdle()
        val r = vm.state.value.record
        assertEquals(listOf("Na pewno się pomylę"), r.automaticThoughts)
        assertEquals(0, r.hotThoughtIndex)
    }

    @Test
    fun `removing the hot thought clears or moves the choice`() = runTest(dispatcher) {
        val vm = wizard()
        listOf("a", "b", "c").forEach {
            vm.setPendingThought(it)
            vm.addPendingThought()
        }
        vm.setHotThought(2)
        vm.removeThought(0)
        assertEquals(1, vm.state.value.record.hotThoughtIndex)
        vm.removeThought(1)
        assertNull(vm.state.value.record.hotThoughtIndex.takeIf { vm.state.value.record.automaticThoughts.size > 1 })
        assertEquals(listOf("b"), vm.state.value.record.automaticThoughts)
        assertEquals(0, vm.state.value.record.hotThoughtIndex)
    }

    @Test
    fun `after ratings start at the before values`() = runTest(dispatcher) {
        val vm = wizard()
        vm.toggleEmotion("sadness")
        vm.setIntensityBefore("sadness", 60)
        repeat(ThoughtWizardViewModel.LAST_STEP) { vm.next() }
        assertEquals(60, vm.state.value.record.emotions.single().intensityAfter)
    }

    @Test
    fun `finishing marks the record complete`() = runTest(dispatcher) {
        val vm = wizard()
        vm.setSituation("x")
        vm.finish()
        advanceUntilIdle()
        val saved = repo.thoughts.value.single()
        assertFalse(saved.isDraft)
        assertTrue(vm.state.value.closed)
        assertEquals(saved.id, vm.state.value.closedWithId)
    }

    @Test
    fun `crisis card appears once for a very strong emotion`() = runTest(dispatcher) {
        val vm = wizard()
        vm.toggleEmotion("anxiety")
        vm.setIntensityBefore("anxiety", 85)
        assertFalse(vm.state.value.crisisVisible)
        vm.setIntensityBefore("anxiety", 95)
        assertTrue(vm.state.value.crisisVisible)
        vm.dismissCrisis()
        vm.setIntensityBefore("anxiety", 100)
        assertFalse(vm.state.value.crisisVisible)
    }

    @Test
    fun `deleting removes the draft and nothing is saved afterwards`() = runTest(dispatcher) {
        val vm = wizard()
        vm.setSituation("x")
        vm.next()
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        vm.saveDraft()
        advanceUntilIdle()
        assertTrue(repo.thoughts.value.isEmpty())
    }
}
