package com.benekapps.myslnik.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.benekapps.myslnik.AppContainer
import com.benekapps.myslnik.MyslnikApplication

/** Creates a ViewModel with access to the app's dependencies. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: CreationExtras.(AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as MyslnikApplication).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
