package com.wordly.app.data

import com.wordly.app.data.db.ProgressDao
import com.wordly.app.data.db.WordDao
import com.wordly.app.data.entity.Word
import com.wordly.app.data.remote.DictionaryApi
import com.wordly.app.data.remote.TranslationApi
import com.wordly.app.data.remote.WiktionaryApi
import com.wordly.app.data.remote.WiktionaryParsing
import com.wordly.app.data.remote.WordLookup
import com.wordly.app.domain.LearningEngine
import com.wordly.app.domain.TranslationRanking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thrown by [WordRepository.addWord]/[WordRepository.updateWord] when the word already exists. */
class DuplicateWordException(val word: String) : Exception("Word already exists: $word")

class WordRepository(
    private val wordDao: WordDao,
    private val progressDao: ProgressDao,
    private val dictionaryApi: DictionaryApi,
    private val translationApi: TranslationApi,
    private val wiktionaryApi: WiktionaryApi,
) {

    fun observeWords(): Flow<List<Word>> = wordDao.observeAllOrdered()

    fun observeBenchmark(): Flow<Long> = progressDao.observeBenchmark().map { it ?: 0L }

    suspend fun getAllOrdered(): List<Word> = wordDao.getAllOrdered()

    suspend fun currentBenchmark(): Long = progressDao.currentBenchmark()

    suspend fun getWord(id: Long): Word? = wordDao.getById(id)

    suspend fun findDuplicate(text: String): Word? =
        wordDao.findByNormalized(LearningEngine.normalize(text))

    /**
     * Inserts a new word at the end of the addition order.
     *
     * @throws DuplicateWordException if a word with the same normalized text exists.
     */
    suspend fun addWord(
        text: String,
        translation: String,
        transcription: String?,
        audioUrl: String?,
    ) {
        val normalized = LearningEngine.normalize(text)
        require(normalized.isNotEmpty()) { "Word text is blank" }
        if (wordDao.findByNormalized(normalized) != null) throw DuplicateWordException(text.trim())
        wordDao.insert(
            Word(
                orderIndex = wordDao.maxOrderIndex() + 1,
                text = text.trim(),
                normalized = normalized,
                translation = translation.trim(),
                transcription = transcription?.trim()?.ifEmpty { null },
                audioUrl = audioUrl?.trim()?.ifEmpty { null },
            ),
        )
    }

    /**
     * Updates an existing word in place, preserving its `id`/`orderIndex`/`createdAt`.
     *
     * @throws DuplicateWordException if another word already has the same normalized text.
     */
    suspend fun updateWord(
        id: Long,
        text: String,
        translation: String,
        transcription: String?,
        audioUrl: String?,
    ) {
        val existing = wordDao.getById(id) ?: return
        val normalized = LearningEngine.normalize(text)
        require(normalized.isNotEmpty()) { "Word text is blank" }
        val duplicate = wordDao.findByNormalized(normalized)
        if (duplicate != null && duplicate.id != id) throw DuplicateWordException(text.trim())
        wordDao.update(
            existing.copy(
                text = text.trim(),
                normalized = normalized,
                translation = translation.trim(),
                transcription = transcription?.trim()?.ifEmpty { null },
                audioUrl = audioUrl?.trim()?.ifEmpty { null },
            ),
        )
    }

    suspend fun deleteWord(word: Word) = wordDao.delete(word)

    suspend fun setBenchmark(orderIndex: Long) {
        progressDao.ensureExists()
        progressDao.setBenchmark(orderIndex)
    }

    /**
     * Looks up IPA transcription and a pronunciation audio URL, trying api.dictionaryapi.dev
     * and en.wiktionary.org in parallel and preferring whichever found data. Wiktionary (backed
     * by Wikimedia) is far more reliably available than the much smaller dictionaryapi.dev
     * project, which dictionaryapi.dev then fills gaps in when it does happen to have data
     * Wiktionary's page structure didn't yield for that particular word. Never throws.
     */
    suspend fun lookup(word: String): Result<WordLookup> = coroutineScope {
        val trimmed = word.trim()
        val dictionaryDeferred = async { runCatching { fetchFromDictionary(trimmed) } }
        val wiktionaryDeferred = async { runCatching { fetchFromWiktionary(trimmed) } }
        val dictionary = dictionaryDeferred.await().getOrNull()
        val wiktionary = wiktionaryDeferred.await().getOrNull()

        val transcription = wiktionary?.transcription ?: dictionary?.transcription
        val audioUrl = wiktionary?.audioUrl ?: dictionary?.audioUrl

        if (transcription != null || audioUrl != null) {
            Result.success(WordLookup(transcription, audioUrl))
        } else {
            Result.failure(NoSuchElementException("No dictionary data found for \"$trimmed\""))
        }
    }

    private suspend fun fetchFromDictionary(word: String): WordLookup {
        val entries = dictionaryApi.lookup(word)
        val transcription = entries.firstNotNullOfOrNull { entry ->
            entry.phonetic?.takeIf { it.isNotBlank() }
                ?: entry.phonetics.firstNotNullOfOrNull { it.text?.takeIf(String::isNotBlank) }
        }
        val audio = entries
            .flatMap { it.phonetics }
            .firstNotNullOfOrNull { it.audio?.takeIf(String::isNotBlank) }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        return WordLookup(transcription, audio)
    }

    private suspend fun fetchFromWiktionary(word: String): WordLookup {
        val sections = wiktionaryApi.sections(word).parse?.sections.orEmpty()
        val sectionIndex = WiktionaryParsing.findEnglishPronunciationSectionIndex(sections)
            ?: return WordLookup(null, null)
        val html = wiktionaryApi.sectionHtml(word, sectionIndex).parse?.text.orEmpty()
        return WordLookup(
            transcription = WiktionaryParsing.extractFirstIpa(html),
            audioUrl = WiktionaryParsing.extractFirstAudioUrl(html),
        )
    }

    /**
     * Candidate Russian translations for an English word, short/standalone-looking ones
     * first — see [TranslationRanking] for why MyMemory needs that cleanup.
     */
    suspend fun lookupTranslations(word: String): Result<List<String>> = runCatching {
        val response = translationApi.translate(word.trim())
        val raw = buildList {
            response.matches.forEach { it.translation?.let(::add) }
            response.responseData?.translatedText?.let(::add)
        }
        TranslationRanking.rank(raw)
    }
}
