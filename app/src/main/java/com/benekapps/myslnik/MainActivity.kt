package com.benekapps.myslnik

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.benekapps.myslnik.domain.AppLocale
import com.benekapps.myslnik.domain.appLocale
import com.benekapps.myslnik.ui.MyslnikRoot
import com.benekapps.myslnik.ui.RootViewModel

/**
 * AppCompatActivity: in-app language switch on every Android version
 * (it is also the FragmentActivity that BiometricPrompt needs).
 */
class MainActivity : AppCompatActivity() {

    private val rootViewModel: RootViewModel by viewModels {
        viewModelFactory { initializer { RootViewModel((application as MyslnikApplication).container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLocale.current = resources.appLocale()
        // Secure by default until the setting is read: no content in recent
        // apps or screenshots.
        setSecureScreen(true)
        setContent {
            MyslnikRoot(viewModel = rootViewModel, onSecureScreen = ::setSecureScreen)
        }
    }

    private fun setSecureScreen(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
