package com.benekapps.myslnik.ui.thought

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.db.EmotionRating
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.isEmpty
import com.benekapps.myslnik.domain.CrisisCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WizardUiState(
    val loading: Boolean = true,
    val record: ThoughtRecord = ThoughtRecord(createdAt = 0, updatedAt = 0),
    val step: Int = 0,
    /** Text typed in the "new thought" field but not added yet. */
    val pendingThought: String = "",
    val crisisVisible: Boolean = false,
    /** Set when the wizard should close; holds the saved record id, or null if nothing was saved. */
    val closedWithId: Long? = null,
    val closed: Boolean = false,
)

/**
 * The 7-step thought record. The draft is saved after every step, when the
 * app goes to the background and when the screen is left, so nothing typed
 * is lost. Saving runs in [persistScope] so it survives the ViewModel.
 */
class ThoughtWizardViewModel(
    private val repo: EntriesRepository,
    savedStateHandle: SavedStateHandle,
    private val persistScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val requestedId: Long = savedStateHandle.get<Long>(ARG_ID) ?: 0L
    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    private val saveMutex = Mutex()
    private var crisisShown = false

    init {
        viewModelScope.launch {
            val existing = if (requestedId > 0) repo.getThoughtRecord(requestedId) else null
            val time = now()
            _state.value = if (existing != null) {
                WizardUiState(
                    loading = false,
                    record = existing,
                    step = if (existing.isDraft) existing.draftStep.coerceIn(0, LAST_STEP) else 0,
                )
            } else {
                WizardUiState(loading = false, record = ThoughtRecord(createdAt = time, updatedAt = time))
            }
        }
    }

    private fun edit(transform: (ThoughtRecord) -> ThoughtRecord) {
        _state.update { it.copy(record = transform(it.record)) }
    }

    fun setSituation(text: String) = edit { it.copy(situation = text) }

    fun toggleEmotion(key: String) = edit { r ->
        if (r.emotions.any { it.emotion == key }) {
            r.copy(emotions = r.emotions.filterNot { it.emotion == key })
        } else {
            r.copy(emotions = r.emotions + EmotionRating(key, DEFAULT_INTENSITY))
        }
    }

    fun setIntensityBefore(key: String, value: Int) {
        edit { r -> r.copy(emotions = r.emotions.map { if (it.emotion == key) it.copy(intensityBefore = value) else it }) }
        checkCrisis(value)
    }

    fun setIntensityAfter(key: String, value: Int) {
        edit { r -> r.copy(emotions = r.emotions.map { if (it.emotion == key) it.copy(intensityAfter = value) else it }) }
        checkCrisis(value)
    }

    fun setPendingThought(text: String) = _state.update { it.copy(pendingThought = text) }

    fun addPendingThought() {
        val text = _state.value.pendingThought.trim()
        if (text.isEmpty()) return
        _state.update { s ->
            val thoughts = s.record.automaticThoughts + text
            s.copy(
                pendingThought = "",
                record = s.record.copy(
                    automaticThoughts = thoughts,
                    hotThoughtIndex = s.record.hotThoughtIndex ?: (thoughts.size - 1).takeIf { thoughts.size == 1 },
                ),
            )
        }
    }

    fun removeThought(index: Int) = edit { r ->
        val thoughts = r.automaticThoughts.filterIndexed { i, _ -> i != index }
        val hot = r.hotThoughtIndex
        r.copy(
            automaticThoughts = thoughts,
            hotThoughtIndex = when {
                hot == null || hot == index -> if (thoughts.size == 1) 0 else null
                hot > index -> hot - 1
                else -> hot
            },
        )
    }

    fun setHotThought(index: Int) = edit { it.copy(hotThoughtIndex = index) }

    fun toggleDistortion(key: String) = edit { r ->
        r.copy(distortions = if (key in r.distortions) r.distortions - key else r.distortions + key)
    }

    fun setEvidenceFor(text: String) = edit { it.copy(evidenceFor = text) }
    fun setEvidenceAgainst(text: String) = edit { it.copy(evidenceAgainst = text) }
    fun setAlternativeThought(text: String) = edit { it.copy(alternativeThought = text) }

    fun dismissCrisis() = _state.update { it.copy(crisisVisible = false) }

    fun next() {
        addPendingThought()
        val step = _state.value.step
        if (step >= LAST_STEP) return finish()
        if (step + 1 == LAST_STEP) prefillAfterRatings()
        _state.update { it.copy(step = step + 1) }
        saveDraft()
    }

    /** Goes one step back; returns false on the first step (the screen should close). */
    fun back(): Boolean {
        val step = _state.value.step
        if (step == 0) return false
        _state.update { it.copy(step = step - 1) }
        saveDraft()
        return true
    }

    fun saveDraft(): Job = persistScope.launch { persist(complete = false) }

    fun finish() {
        addPendingThought()
        persistScope.launch {
            if (_state.value.record.isEmpty()) {
                _state.update { it.copy(closed = true) }
                return@launch
            }
            val id = persist(complete = true)
            _state.update { it.copy(closed = true, closedWithId = id) }
        }
    }

    fun delete() {
        persistScope.launch {
            saveMutex.withLock {
                val id = _state.value.record.id
                if (id != 0L) repo.deleteThoughtRecord(id)
                _state.update { it.copy(closed = true) }
            }
        }
    }

    override fun onCleared() {
        if (!_state.value.closed) saveDraft()
    }

    /** "After" ratings start at the "before" value, so the user only moves what changed. */
    private fun prefillAfterRatings() = edit { r ->
        r.copy(emotions = r.emotions.map { if (it.intensityAfter == null) it.copy(intensityAfter = it.intensityBefore) else it })
    }

    private fun checkCrisis(value: Int) {
        if (!crisisShown && CrisisCheck.isEmotionCritical(value)) {
            crisisShown = true
            _state.update { it.copy(crisisVisible = true) }
        }
    }

    private suspend fun persist(complete: Boolean): Long? = saveMutex.withLock {
        val s = _state.value
        if (s.loading || (s.closed && !complete)) return null
        val record = s.record
        if (record.id == 0L && record.isEmpty()) return null
        val toSave = record.copy(
            updatedAt = now(),
            draftStep = s.step,
            isDraft = record.isDraft && !complete,
        )
        val id = repo.saveThoughtRecord(toSave)
        _state.update { it.copy(record = it.record.copy(id = id, isDraft = toSave.isDraft)) }
        id
    }

    companion object {
        const val ARG_ID = "id"
        const val STEP_COUNT = 7
        const val LAST_STEP = STEP_COUNT - 1
        const val DEFAULT_INTENSITY = 50
    }
}
