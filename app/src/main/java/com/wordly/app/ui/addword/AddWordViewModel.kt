package com.wordly.app.ui.addword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.audio.Pronouncer
import com.wordly.app.data.DuplicateWordException
import com.wordly.app.data.WordRepository
import kotlinx.coroutines.async
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
    val translationOptions: List<String> = emptyList(),
    val lookedUp: Boolean = false,
    val looking: Boolean = false,
    val saving: Boolean = false,
    val textError: String? = null,
    val translationError: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
)

/** [wordId] `null` adds a new word; a real id loads and edits that existing word. */
class AddWordViewModel(
    private val repository: WordRepository,
    private val pronouncer: Pronouncer,
    private val wordId: Long? = null,
) : ViewModel() {

    val isEditing: Boolean get() = wordId != null

    private val _state = MutableStateFlow(AddWordUiState())
    val state: StateFlow<AddWordUiState> = _state.asStateFlow()

    init {
        if (wordId != null) {
            viewModelScope.launch {
                repository.getWord(wordId)?.let { word ->
                    _state.update {
                        it.copy(
                            text = word.text,
                            translation = word.translation,
                            transcription = word.transcription.orEmpty(),
                            audioUrl = word.audioUrl,
                        )
                    }
                }
            }
        }
    }

    fun onText(value: String) = _state.update { it.copy(text = value, textError = null) }

    fun onTranslation(value: String) =
        _state.update { it.copy(translation = value, translationError = null) }

    fun onTranscription(value: String) = _state.update { it.copy(transcription = value) }

    /** Picks one of the fetched [AddWordUiState.translationOptions] into the translation field. */
    fun pickTranslation(option: String) =
        _state.update { it.copy(translation = option, translationError = null) }

    fun previewAudio() {
        val s = _state.value
        if (s.text.isNotBlank()) pronouncer.pronounce(s.text, s.audioUrl)
    }

    /** Fetches IPA transcription + pronunciation audio, and candidate Russian translations. */
    fun lookup() {
        val word = _state.value.text.trim()
        if (word.isBlank()) {
            _state.update { it.copy(textError = "Введите английское слово") }
            return
        }
        _state.update { it.copy(looking = true, message = null) }
        viewModelScope.launch {
            val dictionaryDeferred = async { repository.lookup(word) }
            val translationsDeferred = async { repository.lookupTranslations(word) }
            val dictionary = dictionaryDeferred.await().getOrNull()
            val translations = translationsDeferred.await().getOrDefault(emptyList())

            val gotTranscription = dictionary?.transcription != null
            val gotAudio = dictionary?.audioUrl != null
            val gotTranslations = translations.isNotEmpty()

            _state.update { current ->
                current.copy(
                    looking = false,
                    lookedUp = true,
                    transcription = dictionary?.transcription ?: current.transcription,
                    audioUrl = dictionary?.audioUrl ?: current.audioUrl,
                    translationOptions = translations,
                    translation = current.translation.ifBlank { translations.firstOrNull().orEmpty() },
                    message = lookupResultMessage(gotTranslations, gotTranscription, gotAudio),
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
                    if (wordId != null) {
                        repository.updateWord(wordId, s.text, s.translation, s.transcription, s.audioUrl)
                    } else {
                        repository.addWord(s.text, s.translation, s.transcription, s.audioUrl)
                    }
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

    /** Deletes the word being edited. No-op when adding a new word (wordId is null). */
    fun delete() {
        val id = wordId ?: return
        viewModelScope.launch {
            repository.getWord(id)?.let { repository.deleteWord(it) }
            _state.update { it.copy(saved = true) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/**
 * Reports exactly which of the three lookups succeeded — the dictionary API commonly has
 * audio without an IPA transcription (or vice versa) for a given word, so "загружено" must
 * never be said for a part that came back empty.
 */
internal fun lookupResultMessage(
    gotTranslations: Boolean,
    gotTranscription: Boolean,
    gotAudio: Boolean,
): String {
    val loaded = buildList {
        if (gotTranslations) add("перевод")
        if (gotTranscription) add("транскрипция")
        if (gotAudio) add("озвучка")
    }
    if (loaded.isEmpty()) return "Не удалось получить данные — введите вручную"
    val missing = buildList {
        if (!gotTranslations) add("перевод")
        if (!gotTranscription) add("транскрипция")
        if (!gotAudio) add("озвучка")
    }
    return "Загружено: ${loaded.joinToString(", ")}" +
        if (missing.isEmpty()) "" else ". Не найдено: ${missing.joinToString(", ")}"
}
