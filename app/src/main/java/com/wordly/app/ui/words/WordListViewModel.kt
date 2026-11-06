package com.wordly.app.ui.words

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.audio.Pronouncer
import com.wordly.app.data.WordRepository
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordListUiState(
    val words: List<Word> = emptyList(),
    val benchmark: Long = 0L,
)

class WordListViewModel(
    private val repository: WordRepository,
    private val pronouncer: Pronouncer,
    private val profileId: Long,
) : ViewModel() {

    private val profile: Profile = Profile.fromId(profileId)

    val state: StateFlow<WordListUiState> =
        combine(repository.observeWords(profileId), repository.observeBenchmark(profileId)) { words, benchmark ->
            WordListUiState(words, benchmark)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordListUiState())

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage

    fun pronounce(word: Word) = pronouncer.pronounce(word, profile.ttsLanguageTag)

    /** Last batch removed by [delete], kept so the snackbar's "Отменить" can put it back. */
    private var lastDeleted: List<Word> = emptyList()

    fun delete(words: List<Word>) {
        lastDeleted = words
        viewModelScope.launch { repository.deleteWords(words) }
    }

    fun undoDelete() {
        val words = lastDeleted
        lastDeleted = emptyList()
        viewModelScope.launch { repository.restoreWords(words) }
    }

    fun toggleFavorite(word: Word) =
        viewModelScope.launch { repository.setFavorite(word.id, !word.isFavorite) }

    /** Favorites every word in [words], or unfavorites them all if they already all are. */
    fun toggleFavorite(words: List<Word>) = viewModelScope.launch {
        repository.setFavorite(words.map { it.id }, !words.all { it.isFavorite })
    }

    /** Moves the "выучено до сюда" line so [word] (and everything before it) counts as learned. */
    fun setBenchmarkHere(word: Word) =
        viewModelScope.launch { repository.setBenchmark(profileId, word.orderIndex) }

    fun clearAllWords() = viewModelScope.launch { repository.clearAllWords(profileId) }

    /** Words still without a transcription — how many the fill could work on. */
    val missingTranscription: StateFlow<Int> = repository.observeMissingPronunciation(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Writes the whole dictionary as CSV (see [WordRepository.exportCsv]) to a user-picked file. */
    fun exportTo(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val csv = repository.exportCsv(profileId)
                resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                    ?: error("no output stream")
            }
            _exportMessage.update {
                if (result.isSuccess) "Экспортировано слов: ${state.value.words.size}" else "Не удалось сохранить файл"
            }
        }
    }

    fun consumeExportMessage() = _exportMessage.update { null }
}
