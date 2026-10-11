package com.benekapps.myslnik.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Locks the app after it has been in the background for [timeoutMs].
 * Observes the process lifecycle, so opening the dialer or the share sheet
 * for a moment does not lock it. A new process always starts locked.
 */
class AppLockManager(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(true)

    /** Only meaningful when a PIN is set; see RootViewModel. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile
    var lockEnabled: Boolean = false

    private var stoppedAt: Long? = null

    fun unlock() {
        _locked.value = false
    }

    fun lock() {
        _locked.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        stoppedAt = clock()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = stoppedAt ?: return
        stoppedAt = null
        if (lockEnabled && clock() - since >= timeoutMs) lock()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}
