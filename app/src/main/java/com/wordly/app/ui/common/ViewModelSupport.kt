package com.wordly.app.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.wordly.app.AppContainer
import com.wordly.app.WordlyApp

/** Access to the app-wide [AppContainer] from inside a `viewModel { ... }` initializer. */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as WordlyApp).container
