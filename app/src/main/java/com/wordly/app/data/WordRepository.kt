package com.wordly.app.data

import com.wordly.app.data.db.ProgressDao
import com.wordly.app.data.db.WordDao
import com.wordly.app.data.entity.Word
import com.wordly.app.data.remote.DictionaryApi
import com.wordly.app.data.remote.WordLookup
import com.wordly.app.domain.LearningEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thrown by [WordRepository.addWord] when the word is already in the database. */
class DuplicateWordException(val word: String) : Exception("Word already exists: $word")

class WordRepository(
    private val wordDao: WordDao,
    private val progressDao: ProgressDao,
    private val dictionaryApi: DictionaryApi,
) {

    fun observeWords(): Flow<List<Word>> = wordDao.observeAllOrdered()

    fun observeBenchmark(): Flow<Long> = progressDao.observeBenchmark().map { it ?: 0L }

    suspend fun getAllOrdered(): List<Word> = wordDao.getAllOrdered()

    suspend fun currentBenchmark(): Long = progressDao.currentBenchmark()

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

    suspend fun deleteWord(word: Word) = wordDao.delete(word)

    suspend fun setBenchmark(orderIndex: Long) {
        progressDao.ensureExists()
        progressDao.setBenchmark(orderIndex)
    }

    /** Looks up IPA transcription and a pronunciation audio URL. Never throws. */
    suspend fun lookup(word: String): Result<WordLookup> = runCatching {
        val entries = dictionaryApi.lookup(word.trim())
        val transcription = entries.firstNotNullOfOrNull { entry ->
            entry.phonetic?.takeIf { it.isNotBlank() }
                ?: entry.phonetics.firstNotNullOfOrNull { it.text?.takeIf(String::isNotBlank) }
        }
        val audio = entries
            .flatMap { it.phonetics }
            .firstNotNullOfOrNull { it.audio?.takeIf(String::isNotBlank) }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        WordLookup(transcription, audio)
    }
}
