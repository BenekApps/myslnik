package com.benekapps.myslnik

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyslnikApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val c = AppContainer(this)
        container = c
        ProcessLifecycleOwner.get().lifecycle.addObserver(c.lockManager)
        c.appScope.launch {
            c.settings.settings.collect { c.lockManager.lockEnabled = it.lockEnabled }
        }
        // PDFs are only kept until the next start; the share target has read them by then.
        c.appScope.launch(Dispatchers.IO) { c.exportDir.deleteRecursively() }
    }
}
