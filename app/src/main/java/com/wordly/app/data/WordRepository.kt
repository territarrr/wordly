package com.wordly.app.data

import com.wordly.app.data.db.ProgressDao
import com.wordly.app.data.db.StudyDayDao
import com.wordly.app.data.db.WordDao
import com.wordly.app.data.entity.StudyDay
import com.wordly.app.data.entity.Word
import com.wordly.app.data.remote.DictionaryApi
import com.wordly.app.data.remote.GoogleSheetsApi
import com.wordly.app.data.remote.TranslationApi
import com.wordly.app.data.remote.WiktionaryApi
import com.wordly.app.data.remote.WiktionaryParsing
import com.wordly.app.data.remote.WordLookup
import com.wordly.app.domain.CsvExport
import com.wordly.app.domain.CsvImport
import com.wordly.app.domain.GoogleSheetsUrl
import com.wordly.app.domain.LearningEngine
import com.wordly.app.domain.SerbianScript
import com.wordly.app.domain.Srs
import com.wordly.app.domain.TranslationRanking
import com.wordly.app.translate.FreeDictTranslator
import com.wordly.app.translate.MlKitTranslator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Thrown by [WordRepository.addWord]/[WordRepository.updateWord] when the word already exists. */
class DuplicateWordException(val word: String) : Exception("Word already exists: $word")

/** Serbian lookup result: Russian candidates, and the word with its stressed syllable marked ("hvála"). */
data class SerbianLookup(val translations: List<String>, val stressed: String?)

/** Outcome of [WordRepository.importCsv]. */
data class ImportResult(
    val imported: Int,
    val duplicates: Int,
    val missingWord: Int,
    val missingTranslation: Int,
)

class WordRepository(
    private val wordDao: WordDao,
    private val progressDao: ProgressDao,
    private val studyDayDao: StudyDayDao,
    private val dictionaryApi: DictionaryApi,
    private val wiktionaryApi: WiktionaryApi,
    private val ruWiktionaryApi: WiktionaryApi,
    private val mlKitTranslator: MlKitTranslator,
    private val freeDictTranslator: FreeDictTranslator,
    private val translationApi: TranslationApi,
    private val googleSheetsApi: GoogleSheetsApi,
) {

    fun observeWords(profileId: Long): Flow<List<Word>> = wordDao.observeAllOrdered(profileId)

    fun observeBenchmark(profileId: Long): Flow<Long> =
        progressDao.observeBenchmark(profileId).map { it ?: 0L }

    suspend fun getAllOrdered(profileId: Long): List<Word> = wordDao.getAllOrdered(profileId)

    /** Per-day answered-card counts, newest first — for the streak and daily goal. */
    fun observeStudyDays(profileId: Long): Flow<List<StudyDay>> = studyDayDao.observe(profileId)

    /** CSV in the same shape [importCsv] reads — lets the whole dictionary round-trip as a backup. */
    suspend fun exportCsv(profileId: Long): String = CsvExport.build(wordDao.getAllOrdered(profileId))

    suspend fun currentBenchmark(profileId: Long): Long = progressDao.currentBenchmark(profileId)

    suspend fun getWord(id: Long): Word? = wordDao.getById(id)

    /** [excludeId] lets an in-progress edit not flag a word's own unchanged text as a duplicate. */
    suspend fun findDuplicate(profileId: Long, text: String, excludeId: Long? = null): Word? =
        wordDao.findByNormalized(profileId, LearningEngine.normalize(text))?.takeIf { it.id != excludeId }

    /** Words already in the dictionary whose translation matches [query] — see [LearningEngine.searchByTranslation]. */
    suspend fun searchByTranslation(profileId: Long, query: String, excludeId: Long? = null): List<Word> =
        LearningEngine.searchByTranslation(wordDao.getAllOrdered(profileId).filter { it.id != excludeId }, query)

    /**
     * Inserts a new word at the end of the addition order.
     *
     * @throws DuplicateWordException if a word with the same normalized text exists in this profile.
     */
    suspend fun addWord(
        profileId: Long,
        text: String,
        translation: String,
        transcription: String?,
        audioUrl: String?,
        note: String? = null,
    ) {
        val normalized = LearningEngine.normalize(text)
        require(normalized.isNotEmpty()) { "Word text is blank" }
        if (wordDao.findByNormalized(profileId, normalized) != null) throw DuplicateWordException(text.trim())
        wordDao.insert(
            Word(
                profileId = profileId,
                orderIndex = wordDao.maxOrderIndex(profileId) + 1,
                text = text.trim(),
                normalized = normalized,
                translation = translation.trim(),
                transcription = transcription?.trim()?.ifEmpty { null },
                audioUrl = audioUrl?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
            ),
        )
    }

    /**
     * Updates an existing word in place, preserving its `id`/`profileId`/`orderIndex`/`createdAt`.
     *
     * @throws DuplicateWordException if another word already has the same normalized text.
     */
    suspend fun updateWord(
        id: Long,
        text: String,
        translation: String,
        transcription: String?,
        audioUrl: String?,
        note: String? = null,
    ) {
        val existing = wordDao.getById(id) ?: return
        val normalized = LearningEngine.normalize(text)
        require(normalized.isNotEmpty()) { "Word text is blank" }
        val duplicate = wordDao.findByNormalized(existing.profileId, normalized)
        if (duplicate != null && duplicate.id != id) throw DuplicateWordException(text.trim())
        wordDao.update(
            existing.copy(
                text = text.trim(),
                normalized = normalized,
                translation = translation.trim(),
                transcription = transcription?.trim()?.ifEmpty { null },
                audioUrl = audioUrl?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
            ),
        )
    }

    suspend fun deleteWord(word: Word) = wordDao.delete(word)

    suspend fun setFavorite(id: Long, isFavorite: Boolean) = wordDao.setFavorite(id, isFavorite)

    suspend fun setFavorite(ids: List<Long>, isFavorite: Boolean) = wordDao.setFavorite(ids, isFavorite)

    suspend fun deleteWords(words: List<Word>) = wordDao.deleteAll(words)

    /** Undo for [deleteWords]/[deleteWord]: re-inserts with the original ids and `orderIndex`. */
    suspend fun restoreWords(words: List<Word>) = wordDao.restore(words)

    /**
     * Records one answer to [word]: bumps its right/wrong counters and today's study count, and —
     * when [updateSchedule] and the word is learned — moves it in the spaced-repetition schedule.
     * [updateSchedule] is false for the second pass over a session's mistakes, so re-answering a
     * word minutes later doesn't count as a real review. Returns the updated word.
     */
    suspend fun recordAnswer(
        profileId: Long,
        word: Word,
        correct: Boolean,
        updateSchedule: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Word {
        val current = wordDao.getById(word.id) ?: word
        var updated = current.copy(
            correctCount = current.correctCount + if (correct) 1 else 0,
            wrongCount = current.wrongCount + if (correct) 0 else 1,
            lastReviewedAt = now,
        )
        if (updateSchedule && current.orderIndex <= progressDao.currentBenchmark(profileId)) {
            updated = Srs.onAnswer(updated, correct, now)
        }
        wordDao.update(updated)
        studyDayDao.add(profileId, LocalDate.now().toEpochDay(), 1)
        return updated
    }

    /** See [Srs.spreadBacklog]; a no-op once every learned word has a schedule. */
    suspend fun scheduleBacklog(profileId: Long, now: Long = System.currentTimeMillis()) {
        val backlog = Srs.spreadBacklog(wordDao.getAllOrdered(profileId), progressDao.currentBenchmark(profileId), now)
        if (backlog.isNotEmpty()) wordDao.updateAll(backlog)
    }

    /**
     * Confirms a new-words batch word by word — see [LearningEngine.partialCommit]. Known words
     * become learned and enter the spaced-repetition schedule; the rest stay first in line.
     */
    suspend fun commitBatch(profileId: Long, batch: List<Word>, knownIds: Set<Long>, now: Long = System.currentTimeMillis()) {
        val fresh = batch.mapNotNull { wordDao.getById(it.id) }
        val current = progressDao.currentBenchmark(profileId)
        val (reindexed, benchmark) = LearningEngine.partialCommit(fresh, knownIds, current)
        val byId = reindexed.associateBy { it.id }
        val updates = fresh.map { w ->
            val moved = byId[w.id] ?: w
            if (w.id in knownIds) Srs.onLearned(moved, now) else moved
        }
        wordDao.updateAll(updates)
        setBenchmark(profileId, benchmark)
    }

    /** Deletes every word in this profile and resets its benchmark — irreversible, gated by a typed confirmation in the UI. */
    suspend fun clearAllWords(profileId: Long) {
        wordDao.deleteAll(profileId)
        studyDayDao.deleteAll(profileId)
        progressDao.ensureExists(profileId)
        progressDao.setBenchmark(profileId, 0)
    }

    /**
     * Bulk-imports a CSV export (word, transcription, translation columns — see [CsvImport])
     * into [profileId]. Each valid, non-duplicate row is appended at the end of the addition
     * order, same as [addWord]; duplicates (by [LearningEngine.normalize]) are silently skipped
     * rather than treated as an error, since importing the same sheet twice should be harmless.
     */
    suspend fun importCsv(profileId: Long, csv: String): ImportResult {
        val parsed = CsvImport.parse(csv)
        var imported = 0
        var duplicates = 0
        for (row in parsed.words) {
            val normalized = LearningEngine.normalize(row.text)
            if (wordDao.findByNormalized(profileId, normalized) != null) {
                duplicates++
                continue
            }
            wordDao.insert(
                Word(
                    profileId = profileId,
                    orderIndex = wordDao.maxOrderIndex(profileId) + 1,
                    text = row.text.trim(),
                    normalized = normalized,
                    translation = row.translation.trim(),
                    transcription = row.transcription?.trim()?.ifEmpty { null },
                    audioUrl = null,
                ),
            )
            imported++
        }
        return ImportResult(
            imported = imported,
            duplicates = duplicates,
            missingWord = parsed.missingWord,
            missingTranslation = parsed.missingTranslation,
        )
    }

    /**
     * Same as [importCsv], but fetches the CSV from a pasted Google Sheets URL first (see
     * [GoogleSheetsUrl]) — only works when the sheet is shared as "Anyone with the link", since
     * this is a plain unauthenticated request, not a Sheets API call.
     */
    suspend fun importFromGoogleSheetsUrl(profileId: Long, url: String): Result<ImportResult> = runCatching {
        val csvUrl = GoogleSheetsUrl.toCsvExportUrl(url)
            ?: throw IllegalArgumentException("Не похоже на ссылку на Google Таблицу")
        val response = googleSheetsApi.fetchCsv(csvUrl)
        val body = response.body()?.string().orEmpty()
        val contentType = response.headers().get("Content-Type").orEmpty()
        val looksLikeCsv = response.isSuccessful &&
            !contentType.contains("html", ignoreCase = true) &&
            !body.trimStart().startsWith("<")
        if (!looksLikeCsv) {
            throw IllegalStateException(
                "Не удалось получить таблицу — откройте доступ по ссылке " +
                    "(\"Доступно всем, у кого есть ссылка\") и попробуйте снова",
            )
        }
        importCsv(profileId, body)
    }

    suspend fun setBenchmark(profileId: Long, orderIndex: Long) {
        progressDao.ensureExists(profileId)
        progressDao.setBenchmark(profileId, orderIndex)
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
     * Candidate Russian translations for an English word, from three sources in parallel,
     * merged and ranked together:
     * - [FreeDictTranslator] — an offline dictionary asset, real senses, best coverage of
     *   multi-word expressions (e.g. phrasal verbs) the other two often miss entirely.
     * - en.wiktionary.org's "Translations" table — can add senses FreeDict lacks for a word.
     * - [MlKitTranslator] — no quota/key/cost since it runs locally, but a single NMT guess;
     *   kept as a fallback for words neither dictionary source has an entry for.
     * Only fails when none of the three found anything.
     */
    suspend fun lookupTranslations(word: String): Result<List<String>> = coroutineScope {
        val trimmed = word.trim()
        val freeDictDeferred = async { freeDictTranslator.translate(trimmed) }
        val wiktionaryDeferred = async { runCatching { fetchTranslationsFromWiktionary(trimmed) } }
        val mlKitDeferred = async { mlKitTranslator.translate("en", "ru", trimmed) }
        val freeDict = freeDictDeferred.await()
        val wiktionary = wiktionaryDeferred.await()
        val mlKit = mlKitDeferred.await()

        val raw = buildList {
            addAll(freeDict)
            wiktionary.getOrNull()?.let(::addAll)
            mlKit?.let(::add)
        }.filter(::hasCyrillic) // ML Kit echoes words it doesn't know ("squirel" → "squirel")
        if (raw.isNotEmpty()) {
            Result.success(TranslationRanking.rank(raw))
        } else {
            Result.failure(
                wiktionary.exceptionOrNull() ?: NoSuchElementException("No translation found for \"$trimmed\""),
            )
        }
    }

    private suspend fun fetchTranslationsFromWiktionary(word: String): List<String> {
        val sections = wiktionaryApi.sections(word).parse?.sections.orEmpty()
        val sectionIndex = WiktionaryParsing.findEnglishTranslationsSectionIndex(sections)
            ?: return emptyList()
        val html = wiktionaryApi.sectionHtml(word, sectionIndex).parse?.text.orEmpty()
        return WiktionaryParsing.extractRussianTranslations(html)
    }

    /**
     * Translation-only lookup for profiles with no English-Wiktionary-shaped dictionary source
     * (see [com.wordly.app.domain.model.Profile.hasTranscriptionLookup]) — no transcription/audio,
     * just candidate Russian translations, tried in parallel:
     * - [MlKitTranslator] — on-device, but its language list doesn't cover every profile (e.g.
     *   it has no Serbian model at all, unlike Croatian/Slovenian/Macedonian/Bulgarian which it
     *   does support), so it's a bonus when available, not something to depend on.
     * - MyMemory ([TranslationApi]) — a free, no-key, arbitrary-language-pair cloud API; the
     *   only source here that actually covers Serbian. Dropped for English elsewhere in this
     *   file (see [lookupTranslations]'s doc) because FreeDict/Wiktionary are simply better for
     *   English, but for profiles with no dictionary-grade source at all it's this or nothing.
     *   Only `responseData.translatedText` is used — MyMemory's own single best translation for
     *   exactly the queried text — never `matches`, which are whole-sentence examples pulled
     *   from its translation-memory corpus (e.g. querying "dobar" can match a stored sentence
     *   like "Добрый день, Татьяна") rather than a word-for-word translation.
     */
    suspend fun lookupTranslationOnly(sourceLanguageCode: String, word: String): Result<List<String>> = coroutineScope {
        val trimmed = word.trim()
        if (sourceLanguageCode == "sr") {
            val serbian = lookupSerbian(trimmed)
            return@coroutineScope if (serbian.translations.isNotEmpty()) {
                Result.success(serbian.translations)
            } else {
                Result.failure(NoSuchElementException("No translation found for \"$trimmed\""))
            }
        }
        val mlKitDeferred = async { mlKitTranslator.translate(sourceLanguageCode, "ru", trimmed) }
        val myMemoryDeferred = async {
            runCatching { translationApi.translate(trimmed, "$sourceLanguageCode|ru") }
        }
        val mlKit = mlKitDeferred.await()
        val myMemory = myMemoryDeferred.await()

        val raw = buildList {
            mlKit?.let(::add)
            myMemory.getOrNull()?.responseData?.translatedText?.let(::add)
        }
        if (raw.isNotEmpty()) {
            Result.success(TranslationRanking.rank(raw))
        } else {
            Result.failure(
                myMemory.exceptionOrNull() ?: NoSuchElementException("No translation found for \"$trimmed\""),
            )
        }
    }

    /**
     * Reverse of [lookupTranslationOnly]: candidate words in [targetLanguageCode] for a Russian
     * [word] — for filling in the "Слово" field when you know the Russian translation but not how
     * to write it in the profile's language. Same two parallel sources and the same reasoning for
     * why they're the only ones here (see [lookupTranslationOnly]'s doc): neither FreeDict nor the
     * Wiktionary parsing in this file is reversible starting from a Russian headword.
     */
    suspend fun lookupReverseTranslation(
        targetLanguageCode: String,
        word: String,
        preferCyrillic: Boolean = false,
    ): Result<List<String>> = coroutineScope {
        val trimmed = word.trim()
        if (targetLanguageCode == "sr") return@coroutineScope lookupReverseSerbian(trimmed, preferCyrillic)
        val mlKitDeferred = async { mlKitTranslator.translate("ru", targetLanguageCode, trimmed) }
        val myMemoryDeferred = async {
            runCatching { translationApi.translate(trimmed, "ru|$targetLanguageCode") }
        }
        val mlKit = mlKitDeferred.await()
        val myMemory = myMemoryDeferred.await()

        val raw = buildList {
            mlKit?.let(::add)
            myMemory.getOrNull()?.responseData?.translatedText?.let(::add)
        }
        if (raw.isNotEmpty()) {
            Result.success(TranslationRanking.rank(raw))
        } else {
            Result.failure(
                myMemory.exceptionOrNull() ?: NoSuchElementException("No word found for \"$trimmed\""),
            )
        }
    }

    /**
     * Serbian → Russian. ML Kit has no Serbian model at all (it silently returns nothing for
     * "sr"), which left only MyMemory — the same poor-quality source dropped for English. So:
     * 1. ru.wiktionary's Serbian (or Serbo-Croatian/Croatian/Bosnian) "Значение" — direct Russian,
     *    best quality, patchy coverage;
     * 2. en.wiktionary's "Serbo-Croatian" glosses (broad coverage, both scripts), each English
     *    gloss turned into Russian by the offline FreeDict dictionary and ML Kit en→ru;
     * 3. ML Kit Croatian→Russian on the Latin spelling — close enough for most shared vocabulary;
     * 4. MyMemory only if all of the above came back empty.
     * Each dictionary is tried with the word as typed and in the other script (see [SerbianScript]).
     */
    suspend fun lookupSerbian(word: String): SerbianLookup = coroutineScope {
        val variants = SerbianScript.variants(word)
        val latin = variants.first { !SerbianScript.isCyrillic(it) }
        val ruDeferred = async { runCatching { firstNonEmpty(variants) { fetchSerbianFromRuWiktionary(it) } }.getOrDefault(emptyList()) }
        val enDeferred = async {
            runCatching {
                variants.firstNotNullOfOrNull { variant -> fetchSerbianFromEnWiktionary(variant).takeIf { it.isNotEmpty() } }
            }.getOrNull() ?: EnSerbianEntry(emptyList(), null)
        }
        val croatianDeferred = async { mlKitQuick("hr", latin) }
        val en = enDeferred.await()
        val stressed = en.headword
            ?.let(SerbianScript::simplifyAccent)
            ?.let { if (SerbianScript.isCyrillic(word) != SerbianScript.isCyrillic(it)) convertScript(it, toCyrillic = SerbianScript.isCyrillic(word)) else it }
            // Only if it really is this word (a page can redirect to a related lemma).
            ?.takeIf { LearningEngine.normalize(SerbianScript.stripAccents(it)) == LearningEngine.normalize(word) }

        // The first gloss is the main sense: two dictionary answers for it, one for the rarer rest.
        // FreeDict is useless for tiny function words ("it" → "ИТ, ай-ти"), and ML Kit on a lone
        // English word guesses wildly ("nice" → "Ницца"), so ML Kit only fills in where FreeDict can't.
        val glossTranslations = en.glosses.take(3).mapIndexed { i, gloss ->
            async {
                val dictionary = if (gloss.length <= 3) emptyList() else freeDictTranslator.translate(gloss).take(if (i == 0) 2 else 1)
                dictionary.ifEmpty { listOfNotNull(mlKitQuick("en", gloss)) }
            }
        }.flatMap { it.await() }

        // Russian Wiktionary is a real sr→ru dictionary, so it always leads; the machine-assisted
        // rest is ordered by agreement — a candidate several sources produced comes first.
        val ru = ruDeferred.await()
        val assisted = (glossTranslations + listOfNotNull(croatianDeferred.await()))
            .map { it.trim().lowercase() }
            .filter(::hasCyrillic) // drops untranslated echoes like "sanely"
        val byVotes = assisted.groupingBy { it }.eachCount().let { votes -> assisted.distinct().sortedByDescending { votes[it] } }
        val ranked = TranslationRanking.rank(ru + byVotes, limit = 8, sourceWord = word, keepOrder = true)
        if (ranked.isNotEmpty()) return@coroutineScope SerbianLookup(ranked, stressed)

        val myMemory = runCatching { translationApi.translate(word, "sr|ru") }
        val fallback = listOfNotNull(myMemory.getOrNull()?.responseData?.translatedText)
        SerbianLookup(TranslationRanking.rank(fallback, sourceWord = word), stressed)
    }

    /** Transliterates [text] keeping its combining stress mark on the right letter. */
    private fun convertScript(text: String, toCyrillic: Boolean): String {
        val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val converted = if (toCyrillic) SerbianScript.toCyrillic(decomposed) else SerbianScript.toLatin(decomposed)
        return java.text.Normalizer.normalize(converted, java.text.Normalizer.Form.NFC)
    }

    /**
     * Russian → Serbian candidates for the "Слово" field: the Serbian rows of ru.wiktionary's
     * "Перевод" table for that Russian word, plus ML Kit Russian→Croatian (no Serbian model
     * exists; Croatian Latin spelling is shared with Serbian for most words). Returned in the
     * script most of the profile's words use. MyMemory only if both come back empty.
     */
    private suspend fun lookupReverseSerbian(word: String, preferCyrillic: Boolean): Result<List<String>> = coroutineScope {
        val wiktionaryDeferred = async { runCatching { fetchSerbianFromRuEntry(word.lowercase()) }.getOrDefault(emptyList()) }
        val croatianDeferred = async { withTimeoutOrNull(ML_KIT_TIMEOUT_MS) { mlKitTranslator.translate("ru", "hr", word) } }
        val raw = wiktionaryDeferred.await() + listOfNotNull(croatianDeferred.await()?.lowercase())
        val inScript = raw.map { if (SerbianScript.isCyrillic(it) == preferCyrillic) it else convertScript(it, preferCyrillic) }
        val ranked = TranslationRanking.rank(inScript, limit = 6, keepOrder = true)
        if (ranked.isNotEmpty()) return@coroutineScope Result.success(ranked)

        val myMemory = runCatching { translationApi.translate(word, "ru|sr") }
        val fallback = listOfNotNull(myMemory.getOrNull()?.responseData?.translatedText)
        if (fallback.isNotEmpty()) Result.success(TranslationRanking.rank(fallback)) else Result.failure(NoSuchElementException(word))
    }

    private suspend fun fetchSerbianFromRuEntry(russian: String): List<String> {
        val sections = ruWiktionaryApi.sections(russian).parse?.sections.orEmpty()
        val index = WiktionaryParsing.findSubsectionIndex(sections, setOf("Русский"), "Перевод") ?: return emptyList()
        return WiktionaryParsing.extractSerbianTranslations(ruWiktionaryApi.sectionHtml(russian, index).parse?.text.orEmpty())
    }

    /** True when most of the profile's words are written in Cyrillic (Serbian can be either). */
    suspend fun prefersCyrillic(profileId: Long): Boolean {
        val words = wordDao.getAllOrdered(profileId)
        return words.count { SerbianScript.isCyrillic(it.text) } * 2 > words.size
    }

    /**
     * ML Kit → Russian, but never holding up the lookup for more than a few seconds: the first
     * call for a language downloads its model (tens of MB). The download carries on in the
     * background, so later lookups get ML Kit's answer too.
     */
    private suspend fun mlKitQuick(sourceLanguageCode: String, text: String): String? =
        withTimeoutOrNull(ML_KIT_TIMEOUT_MS) { mlKitTranslator.translate(sourceLanguageCode, "ru", text) }

    private fun hasCyrillic(text: String): Boolean = text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

    private suspend fun firstNonEmpty(variants: List<String>, fetch: suspend (String) -> List<String>): List<String> {
        for (variant in variants) {
            val found = fetch(variant)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private suspend fun fetchSerbianFromRuWiktionary(word: String): List<String> {
        val sections = ruWiktionaryApi.sections(word).parse?.sections.orEmpty()
        val index = WiktionaryParsing.findSubsectionIndex(sections, RU_WIKTIONARY_SERBIAN_HEADINGS, "Значение")
            ?: return emptyList()
        val html = ruWiktionaryApi.sectionHtml(word, index).parse?.text.orEmpty()
        return WiktionaryParsing.extractRussianMeanings(html)
    }

    private data class EnSerbianEntry(val glosses: List<String>, val headword: String?) {
        fun isNotEmpty() = glosses.isNotEmpty() || headword != null
    }

    /** English glosses and the accented headword from en.wiktionary's "Serbo-Croatian" section. */
    private suspend fun fetchSerbianFromEnWiktionary(word: String): EnSerbianEntry {
        val sections = wiktionaryApi.sections(word).parse?.sections.orEmpty()
        val index = WiktionaryParsing.findLanguageSectionIndex(sections, "Serbo-Croatian") ?: return EnSerbianEntry(emptyList(), null)
        val html = wiktionaryApi.sectionHtml(word, index).parse?.text.orEmpty()
        return EnSerbianEntry(
            glosses = WiktionaryParsing.extractEnglishGlosses(html),
            headword = WiktionaryParsing.extractSerboCroatianHeadword(html, cyrillic = SerbianScript.isCyrillic(word)),
        )
    }

    private companion object {
        /** Serbian is filed under any of these on ru.wiktionary, depending on the entry. */
        const val ML_KIT_TIMEOUT_MS = 4_000L

        val RU_WIKTIONARY_SERBIAN_HEADINGS = setOf("Сербский", "Сербохорватский", "Хорватский", "Боснийский", "Черногорский")
    }
}
