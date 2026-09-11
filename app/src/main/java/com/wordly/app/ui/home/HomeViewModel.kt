package com.wordly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.data.WordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeStats(
    val total: Int = 0,
    val learned: Int = 0,
    val newCount: Int = 0,
)

class HomeViewModel(repository: WordRepository) : ViewModel() {

    val stats: StateFlow<HomeStats> =
        combine(repository.observeWords(), repository.observeBenchmark()) { words, benchmark ->
            val learned = words.count { it.orderIndex <= benchmark }
            HomeStats(total = words.size, learned = learned, newCount = words.size - learned)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())
}
