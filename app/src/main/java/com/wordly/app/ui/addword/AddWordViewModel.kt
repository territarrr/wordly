package com.wordly.app.ui.addword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.audio.Pronouncer
import com.wordly.app.data.DuplicateWordException
import com.wordly.app.data.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddWordUiState(
    val text: String = "",
    val translation: String = "",
    val transcription: String = "",
    val audioUrl: String? = null,
    val looking: Boolean = false,
    val saving: Boolean = false,
    val textError: String? = null,
    val translationError: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
)

class AddWordViewModel(
    private val repository: WordRepository,
    private val pronouncer: Pronouncer,
) : ViewModel() {

    private val _state = MutableStateFlow(AddWordUiState())
    val state: StateFlow<AddWordUiState> = _state.asStateFlow()

    fun onText(value: String) = _state.update { it.copy(text = value, textError = null) }

    fun onTranslation(value: String) =
        _state.update { it.copy(translation = value, translationError = null) }

    fun onTranscription(value: String) = _state.update { it.copy(transcription = value) }

    fun previewAudio() {
        val s = _state.value
        if (s.text.isNotBlank()) pronouncer.pronounce(s.text, s.audioUrl)
    }

    fun lookup() {
        val word = _state.value.text.trim()
        if (word.isBlank()) {
            _state.update { it.copy(textError = "Введите английское слово") }
            return
        }
        _state.update { it.copy(looking = true, message = null) }
        viewModelScope.launch {
            val result = repository.lookup(word)
            _state.update { current ->
                result.fold(
                    onSuccess = { lookup ->
                        val hasData = lookup.transcription != null || lookup.audioUrl != null
                        current.copy(
                            looking = false,
                            transcription = lookup.transcription ?: current.transcription,
                            audioUrl = lookup.audioUrl ?: current.audioUrl,
                            message = if (hasData) "Данные из словаря загружены"
                            else "В словаре нет транскрипции для этого слова",
                        )
                    },
                    onFailure = {
                        current.copy(
                            looking = false,
                            message = "Не удалось получить данные из словаря — введите вручную",
                        )
                    },
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        val textError: String? = if (s.text.isBlank()) "Введите английское слово" else null
        val translationError: String? =
            if (s.translation.isBlank()) "Введите перевод" else null
        if (textError == null && translationError == null) {
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                runCatching {
                    repository.addWord(s.text, s.translation, s.transcription, s.audioUrl)
                }.fold(
                    onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                    onFailure = { error ->
                        val msg = when (error) {
                            is DuplicateWordException -> "Такое слово уже есть в базе"
                            else -> "Не удалось сохранить: ${error.message}"
                        }
                        _state.update {
                            it.copy(
                                saving = false,
                                textError = if (error is DuplicateWordException) msg else it.textError,
                                message = if (error is DuplicateWordException) null else msg,
                            )
                        }
                    },
                )
            }
            return
        }
        _state.update { it.copy(textError = textError, translationError = translationError) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
