package com.wordly.app.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.audio.Pronouncer
import com.wordly.app.data.WordRepository
import com.wordly.app.data.entity.Word
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordListUiState(
    val words: List<Word> = emptyList(),
    val benchmark: Long = 0L,
)

class WordListViewModel(
    private val repository: WordRepository,
    private val pronouncer: Pronouncer,
) : ViewModel() {

    val state: StateFlow<WordListUiState> =
        combine(repository.observeWords(), repository.observeBenchmark()) { words, benchmark ->
            WordListUiState(words, benchmark)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordListUiState())

    fun pronounce(word: Word) = pronouncer.pronounce(word)

    fun delete(word: Word) = viewModelScope.launch { repository.deleteWord(word) }
}
