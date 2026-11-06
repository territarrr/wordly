package com.wordly.app.ui.addword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.audio.Pronouncer
import com.wordly.app.data.DuplicateWordException
import com.wordly.app.data.WordRepository
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.model.Profile
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddWordUiState(
    val text: String = "",
    val translation: String = "",
    val note: String = "",
    /** What the last lookup put into [translation] — replaced by the next lookup unless the user changed it. */
    val autoFilledTranslation: String? = null,
    val transcription: String = "",
    val audioUrl: String? = null,
    val translationOptions: List<String> = emptyList(),
    val wordOptions: List<String> = emptyList(),
    val lookedUp: Boolean = false,
    val looking: Boolean = false,
    val saving: Boolean = false,
    /** The existing word [text] clashes with, shown with its translation instead of a bare error. */
    val duplicate: Word? = null,
    /** Words already in the dictionary whose translation matches what's typed in "Перевод". */
    val translationMatches: List<Word> = emptyList(),
    val textError: String? = null,
    val translationError: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
) {
    val isDuplicate: Boolean get() = duplicate != null

    /**
     * The translation after a lookup that found [found]: fills an empty field, and replaces what
     * an earlier lookup put there (the word has probably been corrected since), but never
     * overwrites anything typed or picked by hand.
     */
    fun refilledTranslation(found: List<String>): String =
        if (translation.isBlank() || translation == autoFilledTranslation) found.firstOrNull() ?: translation else translation
}

private const val DUPLICATE_MESSAGE = "Такое слово уже есть в базе"

/** [wordId] `null` adds a new word; a real id loads and edits that existing word. */
class AddWordViewModel(
    private val repository: WordRepository,
    private val pronouncer: Pronouncer,
    private val profileId: Long,
    private val wordId: Long? = null,
) : ViewModel() {

    val isEditing: Boolean get() = wordId != null
    val profile: Profile = Profile.fromId(profileId)

    private val _state = MutableStateFlow(AddWordUiState())
    val state: StateFlow<AddWordUiState> = _state.asStateFlow()

    private var duplicateCheckJob: Job? = null
    private var translationSearchJob: Job? = null

    init {
        if (wordId != null) {
            viewModelScope.launch {
                repository.getWord(wordId)?.let { word ->
                    _state.update {
                        it.copy(
                            text = word.text,
                            translation = word.translation,
                            transcription = word.transcription.orEmpty(),
                            note = word.note.orEmpty(),
                            audioUrl = word.audioUrl,
                        )
                    }
                }
            }
        }
    }

    fun onText(value: String) {
        _state.update { it.copy(text = value, textError = null, duplicate = null) }
        checkDuplicate(value)
    }


    fun onNote(value: String) = _state.update { it.copy(note = value) }

    /**
     * Re-checks for a duplicate on every keystroke so the "Подтянуть перевод…" button (there's
     * no point looking up a word already in the dictionary) disables itself immediately, instead
     * of only surfacing the clash as a save-time error.
     */
    private fun checkDuplicate(text: String) {
        duplicateCheckJob?.cancel()
        if (text.isBlank()) return
        duplicateCheckJob = viewModelScope.launch {
            val duplicate = repository.findDuplicate(profileId, text, excludeId = wordId)
            if (duplicate != null) {
                _state.update { it.copy(duplicate = duplicate, textError = DUPLICATE_MESSAGE) }
            }
        }
    }

    fun onTranslation(value: String) {
        _state.update { it.copy(translation = value, translationError = null) }
        searchByTranslation(value)
    }

    /**
     * Local reverse lookup: as the Russian is typed, lists words already saved with that
     * translation — so you can find the original word you already have instead of adding it twice.
     */
    private fun searchByTranslation(translation: String) {
        translationSearchJob?.cancel()
        translationSearchJob = viewModelScope.launch {
            val matches = repository.searchByTranslation(profileId, translation, excludeId = wordId)
            _state.update { it.copy(translationMatches = matches) }
        }
    }

    fun onTranscription(value: String) = _state.update { it.copy(transcription = value) }

    /**
     * Toggles one of the fetched [AddWordUiState.translationOptions] in/out of the (comma
     * -separated) translation field — several variants can be kept side by side, and the
     * field stays freely editable to add ones of your own alongside them.
     */
    fun pickTranslation(option: String) = _state.update { current ->
        val selected = splitTranslations(current.translation)
        val updated = if (option in selected) selected - option else selected + option
        current.copy(translation = updated.joinToString(", "), translationError = null)
    }.also { searchByTranslation(_state.value.translation) }

    /** Plays an already saved word (e.g. the duplicate), using its own stored audio. */
    fun pronounce(word: Word) = pronouncer.pronounce(word, profile.ttsLanguageTag)

    fun previewAudio() {
        val s = _state.value
        if (s.text.isNotBlank()) pronouncer.pronounce(s.text, s.audioUrl, profile.ttsLanguageTag)
    }

    /**
     * Fetches candidate Russian translations, plus IPA transcription + pronunciation audio when
     * [Profile.hasTranscriptionLookup] is true (English) — Serbian has no English-Wiktionary
     * -shaped dictionary source for those, only a single on-device ML Kit translation guess.
     */
    /**
     * Only ever on request (button or Enter in "Слово") — never while typing: an automatic lookup
     * fired on a pause, found a half-typed word, and filled in a wrong translation that then stuck.
     */
    fun lookup() {
        val word = _state.value.text.trim()
        if (word.isBlank()) {
            _state.update { it.copy(textError = "Введите слово") }
            return
        }
        _state.update { it.copy(looking = true, message = null) }
        viewModelScope.launch {
            if (profile.hasTranscriptionLookup) {
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
                        translation = current.refilledTranslation(translations),
                        autoFilledTranslation = translations.firstOrNull(),
                        message = lookupResultMessage(gotTranslations, gotTranscription, gotAudio),
                    )
                }
            } else {
                val translations = repository
                    .lookupTranslationOnly(profile.translateLanguageCode, word)
                    .getOrDefault(emptyList())
                _state.update { current ->
                    current.copy(
                        looking = false,
                        lookedUp = true,
                        translationOptions = translations,
                        translation = current.refilledTranslation(translations),
                        autoFilledTranslation = translations.firstOrNull(),
                        message = when {
                            translations.isNotEmpty() -> "Загружено: перевод"
                            else -> "Не удалось получить перевод — введите вручную"
                        },
                    )
                }
            }
        }
    }

    /**
     * Reverse of [lookup]: given a Russian translation already typed in, fetches candidate
     * words in [Profile.translateLanguageCode] for the "Слово" field — for when you know the
     * Russian word but not how to write it in the profile's language.
     */
    fun lookupByTranslation() {
        val translation = _state.value.translation.trim()
        if (translation.isBlank()) {
            _state.update { it.copy(translationError = "Введите перевод (рус.)") }
            return
        }
        _state.update { it.copy(looking = true, message = null) }
        viewModelScope.launch {
            val options = repository
                .lookupReverseTranslation(profile.translateLanguageCode, translation)
                .getOrDefault(emptyList())
            _state.update {
                it.copy(
                    looking = false,
                    wordOptions = options,
                    message = if (options.isNotEmpty()) {
                        "Загружено: варианты слова"
                    } else {
                        "Не удалось подобрать слово — введите вручную"
                    },
                )
            }
            if (_state.value.text.isBlank()) options.firstOrNull()?.let(::onText)
        }
    }

    /** Fills the "Слово" field from one of the fetched [AddWordUiState.wordOptions]. */
    fun pickWord(option: String) = onText(option)

    /** [addAnother]: stay on a cleared form for the next word instead of closing the screen. */
    fun save(addAnother: Boolean = false) {
        val s = _state.value
        val textError: String? = if (s.text.isBlank()) "Введите слово" else null
        val translationError: String? =
            if (s.translation.isBlank()) "Введите перевод" else null
        if (textError == null && translationError == null) {
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                runCatching {
                    if (wordId != null) {
                        repository.updateWord(wordId, s.text, s.translation, s.transcription, s.audioUrl, s.note)
                    } else {
                        repository.addWord(profileId, s.text, s.translation, s.transcription, s.audioUrl, s.note)
                    }
                }.fold(
                    onSuccess = {
                        if (addAnother) {
                            _state.value = AddWordUiState(message = "Сохранено: «${s.text.trim()}»")
                        } else {
                            _state.update { it.copy(saving = false, saved = true) }
                        }
                    },
                    onFailure = { error ->
                        val msg = when (error) {
                            is DuplicateWordException -> DUPLICATE_MESSAGE
                            else -> "Не удалось сохранить: ${error.message}"
                        }
                        _state.update {
                            it.copy(
                                saving = false,
                                textError = if (error is DuplicateWordException) msg else it.textError,
                                message = if (error is DuplicateWordException) null else msg,
                            )
                        }
                        if (error is DuplicateWordException) checkDuplicate(s.text)
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

/** Splits a comma-separated translation field into its trimmed, non-blank parts. */
internal fun splitTranslations(translation: String): List<String> =
    translation.split(",").map { it.trim() }.filter { it.isNotBlank() }

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
