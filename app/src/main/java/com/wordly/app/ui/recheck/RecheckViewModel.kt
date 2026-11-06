package com.wordly.app.ui.recheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.data.WordRepository
import com.wordly.app.data.entity.Word
import com.wordly.app.ui.addword.splitTranslations
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One saved word the new lookup has something to say about.
 *
 * @param options fresh translation candidates; [choice] `null` means keep the current translation.
 * @param stressed the word with its stressed syllable marked, when it's missing or different.
 */
data class RecheckItem(
    val word: Word,
    val options: List<String>,
    val stressed: String?,
    val choice: String? = null,
    val applyStress: Boolean = stressed != null,
) {
    val hasChange: Boolean get() = choice != null || (applyStress && stressed != null)
}

data class RecheckUiState(
    val total: Int = 0,
    val checked: Int = 0,
    val running: Boolean = true,
    val items: List<RecheckItem> = emptyList(),
    val applying: Boolean = false,
    val appliedCount: Int? = null,
) {
    val changeCount: Int get() = items.count { it.hasChange }
}

/**
 * Re-runs the Serbian lookup over every saved word of [profileId] — for translations saved back
 * when only MyMemory was available ("ono — тот далекий") — and offers the results for review.
 * Nothing is overwritten until [apply], and translations are kept unless one is picked.
 */
class RecheckViewModel(
    private val repository: WordRepository,
    private val profileId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(RecheckUiState())
    val state: StateFlow<RecheckUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val words = repository.getAllOrdered(profileId)
            _state.update { it.copy(total = words.size) }
            for (word in words) {
                val found = runCatching { repository.lookupSerbian(word.text) }.getOrNull()
                if (found != null) {
                    val current = splitTranslations(word.translation)
                    val options = found.translations.take(4)
                    val stressed = found.stressed?.takeIf { it != word.transcription }
                    // Worth showing if the dictionary disagrees with what's saved, or adds stress.
                    // Case counts: "Лихорадка" (an MT engine capitalising a lone word) should be offered as "лихорадка".
                    val disagrees = options.isNotEmpty() && options.none { it in current }
                    if (disagrees || stressed != null) {
                        _state.update { it.copy(items = it.items + RecheckItem(word, if (disagrees) options else emptyList(), stressed)) }
                    }
                }
                _state.update { it.copy(checked = it.checked + 1) }
                delay(REQUEST_SPACING_MS) // be gentle with Wiktionary's rate limit
            }
            _state.update { it.copy(running = false) }
        }
    }

    fun choose(wordId: Long, option: String?) = updateItem(wordId) { it.copy(choice = option) }

    fun toggleStress(wordId: Long) = updateItem(wordId) { it.copy(applyStress = !it.applyStress) }

    fun apply() {
        val changes = _state.value.items.filter { it.hasChange }
        _state.update { it.copy(applying = true) }
        viewModelScope.launch {
            for (item in changes) {
                val w = item.word
                repository.updateWord(
                    id = w.id,
                    text = w.text,
                    translation = item.choice ?: w.translation,
                    transcription = if (item.applyStress && item.stressed != null) item.stressed else w.transcription,
                    audioUrl = w.audioUrl,
                    note = w.note,
                )
            }
            _state.update { it.copy(applying = false, appliedCount = changes.size) }
        }
    }

    private fun updateItem(wordId: Long, change: (RecheckItem) -> RecheckItem) = _state.update { s ->
        s.copy(items = s.items.map { if (it.word.id == wordId) change(it) else it })
    }

    private companion object {
        const val REQUEST_SPACING_MS = 300L
    }
}
