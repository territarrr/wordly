package com.wordly.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.AppContainer
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.LearningEngine
import com.wordly.app.domain.Quiz
import com.wordly.app.domain.Srs
import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.Profile
import com.wordly.app.domain.model.QuizType
import com.wordly.app.domain.model.ReviewScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionMode { REVIEW, NEW_WORDS, FAVORITES, DUE, HARD }

data class SessionUiState(
    val loading: Boolean = true,
    val direction: Direction = Direction.EN_TO_RU,
    val quiz: QuizType = QuizType.CARDS,
    val mode: SessionMode = SessionMode.REVIEW,
    val cards: List<Word> = emptyList(),
    val index: Int = 0,
    /** Answer side visible: after a flip (cards) or after an answer was checked (other modes). */
    val revealed: Boolean = false,
    val finished: Boolean = false,
    val committed: Boolean = false,
    /** Word id → answered right, for the current round. */
    val answers: Map<Long, Boolean> = emptyMap(),
    /** True while re-running just the previous round's mistakes. */
    val mistakesRound: Boolean = false,
    /** Typing/listening: what's typed so far, and the verdict once checked. */
    val input: String = "",
    val verdict: Quiz.Verdict? = null,
    /** Multiple choice: the options for the current card, and the one tapped. */
    val options: List<String> = emptyList(),
    val picked: String? = null,
    /** New-words finish: which words are ticked as learned. */
    val knownSelection: Set<Long> = emptySet(),
) {
    val current: Word? get() = cards.getOrNull(index)
    val progressLabel: String get() = "${(index + 1).coerceAtMost(cards.size.coerceAtLeast(1))} / ${cards.size}"
    val mistakes: List<Word> get() = cards.filter { answers[it.id] == false }
    val correctCount: Int get() = answers.count { it.value }
    val answered: Boolean get() = current?.let { it.id in answers } == true
}

class SessionViewModel(
    private val container: AppContainer,
    private val handle: SavedStateHandle,
) : ViewModel() {

    private val mode: String = handle["mode"] ?: "review"
    /** Starts as chosen on Home; flips for a "same words, other direction" round (see [repeatReversed]). */
    private var direction: Direction =
        runCatching { Direction.valueOf(handle[KEY_DIRECTION] ?: handle["dir"] ?: "") }.getOrDefault(Direction.EN_TO_RU)
    private val quiz: QuizType =
        runCatching { QuizType.valueOf(handle["quiz"] ?: "") }.getOrDefault(QuizType.CARDS)
    private val shuffle: Boolean = (handle.get<String>("shuffle")).toBoolean()
    private val scope: ReviewScope = decodeScope(handle)
    private val batchSize: Int = handle.get<String>("batch")?.toIntOrNull()?.takeIf { it > 0 } ?: 20
    private val seed: Long = handle.get<String>("seed")?.toLongOrNull() ?: 0L
    val profileId: Long = handle.get<String>("profileId")?.toLongOrNull() ?: Profile.ENGLISH.id
    val profile: Profile = Profile.fromId(profileId)

    /** How many times the cards were re-dealt — mixed into the shuffle seed so each round gets a new order. */
    private var round: Int = handle[KEY_ROUND_NO] ?: 0

    private val _state = MutableStateFlow(SessionUiState(direction = direction, quiz = quiz))
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    /** Whole profile, for multiple-choice distractors. */
    private var pool: List<Word> = emptyList()

    /** The full session as first loaded — the mistakes round narrows [SessionUiState.cards]. */
    private var fullCards: List<Word> = emptyList()

    /** Words already moved in the spaced-repetition schedule this session (only the first answer counts). */
    private val scheduled = mutableSetOf<Long>()

    init { load() }

    private fun load() = viewModelScope.launch {
        val all = container.repository.getAllOrdered(profileId)
        pool = all
        val benchmark = container.repository.currentBenchmark(profileId)
        val sessionMode = when (mode) {
            "new" -> SessionMode.NEW_WORDS
            "favorites" -> SessionMode.FAVORITES
            "due" -> SessionMode.DUE
            "hard" -> SessionMode.HARD
            else -> SessionMode.REVIEW
        }
        val built = when (sessionMode) {
            SessionMode.NEW_WORDS -> LearningEngine.orderSession(LearningEngine.newBatch(all, benchmark, batchSize), shuffle, seed)
            SessionMode.FAVORITES -> LearningEngine.orderSession(LearningEngine.favoritesSlice(all, scope), shuffle, seed)
            SessionMode.REVIEW -> LearningEngine.orderSession(
                LearningEngine.learnedSlice(all = all, benchmark = benchmark, scope = scope),
                shuffle,
                seed,
            )
            SessionMode.DUE -> LearningEngine.orderSession(
                Srs.dueSlice(all, benchmark, System.currentTimeMillis(), batchSize),
                shuffle = true,
                seed = seed,
            )
            SessionMode.HARD -> LearningEngine.orderSession(LearningEngine.hardSlice(all), shuffle = true, seed = seed)
        }
        // Due/hard slices (and a just-committed batch) change as words get answered, so after
        // process death rebuild the exact same cards from their saved ids instead of re-selecting.
        val byId = all.associateBy { it.id }
        fullCards = handle.get<LongArray>(KEY_FULL)?.toList()?.mapNotNull { byId[it] } ?: built

        // Restore progress after process death — the card list itself is rebuilt deterministically above.
        val roundIds = handle.get<LongArray>(KEY_ROUND)?.toSet()
        val cards = if (roundIds != null) fullCards.filter { it.id in roundIds } else fullCards
        persistFull()
        val answerIds = handle.get<LongArray>(KEY_ANSWER_IDS) ?: LongArray(0)
        val answerVals = handle.get<BooleanArray>(KEY_ANSWER_VALS) ?: BooleanArray(0)
        val answers = answerIds.indices.associate { answerIds[it] to answerVals[it] }
        handle.get<LongArray>(KEY_SCHEDULED)?.let { scheduled += it.toList() }
        val finished = handle.get<Boolean>(KEY_FINISHED) ?: cards.isEmpty()

        _state.update {
            it.copy(
                loading = false,
                mode = sessionMode,
                cards = cards,
                index = (handle.get<Int>(KEY_INDEX) ?: 0).coerceIn(0, (cards.size - 1).coerceAtLeast(0)),
                answers = answers,
                mistakesRound = roundIds != null,
                finished = finished,
                committed = handle.get<Boolean>(KEY_COMMITTED) ?: false,
                knownSelection = handle.get<LongArray>(KEY_KNOWN)?.toSet() ?: emptySet(),
            )
        }
        onCardShown()
    }

    // ---- Cards mode ----

    /** Whether the foreign word is spoken as soon as it appears — shared with Settings. */
    val autoPronounce: StateFlow<Boolean> = container.settings.autoPronounce

    /** Top-bar toggle; turning it on speaks the current word right away if it's showing. */
    fun toggleAutoPronounce() {
        val enabled = !autoPronounce.value
        container.settings.setAutoPronounce(enabled)
        val s = _state.value
        if (enabled && (direction == Direction.EN_TO_RU || s.revealed)) pronounceCurrent()
    }

    /** Tapping the card flips it back and forth (in listening mode: plays the word again). */
    fun toggleReveal() {
        if (quiz == QuizType.LISTENING && !_state.value.revealed) return pronounceCurrent()
        if (quiz != QuizType.CARDS) return
        _state.update { it.copy(revealed = !it.revealed) }
        if (_state.value.revealed) onRevealed()
    }

    /** "Знаю"/"Не знаю" on a flipped card: record it and move on. */
    fun answer(known: Boolean) {
        record(known)
        next()
    }

    // ---- Typing / listening ----

    fun onInput(text: String) = _state.update { if (it.verdict == null) it.copy(input = text) else it }

    fun submitTyped() {
        val s = _state.value
        val word = s.current ?: return
        if (s.verdict != null) return next()
        val expected = if (quiz == QuizType.LISTENING) word.text else Quiz.answerOf(word, direction)
        val verdict = Quiz.check(s.input, expected)
        _state.update { it.copy(verdict = verdict, revealed = true) }
        record(verdict != Quiz.Verdict.WRONG)
        onRevealed()
    }

    /** Typing: "Не помню" — show the answer and count it as a miss. */
    fun giveUp() {
        if (_state.value.verdict != null) return
        _state.update { it.copy(verdict = Quiz.Verdict.WRONG, revealed = true) }
        record(false)
        onRevealed()
    }

    // ---- Multiple choice ----

    fun pick(option: String) {
        val s = _state.value
        val word = s.current ?: return
        if (s.picked != null) return
        _state.update { it.copy(picked = option, revealed = true) }
        record(option == Quiz.answerOf(word, direction))
        onRevealed()
    }

    // ---- Navigation ----

    fun pronounceCurrent() {
        _state.value.current?.let { container.pronouncer.pronounce(it, profile.ttsLanguageTag) }
    }

    /** Updates the in-memory card too — [SessionUiState.cards] is a one-shot snapshot, not an observed flow. */
    fun toggleFavorite() {
        val word = _state.value.current ?: return
        val newValue = !word.isFavorite
        replaceCard(word.copy(isFavorite = newValue))
        viewModelScope.launch { container.repository.setFavorite(word.id, newValue) }
    }

    fun next() {
        val s = _state.value
        if (s.finished) return
        if (s.index + 1 >= s.cards.size) {
            finish()
        } else {
            _state.update { it.copy(index = it.index + 1).resetCard() }
            persist()
            onCardShown()
        }
    }

    /** Swipe back to the previous card (no-op on the first one). */
    fun previous() {
        val s = _state.value
        if (s.finished || s.index == 0) return
        _state.update { it.copy(index = it.index - 1).resetCard() }
        persist()
        onCardShown()
    }

    private fun finish() {
        val s = _state.value
        _state.update {
            it.copy(
                finished = true,
                // Pre-tick every word not explicitly answered "Не знаю".
                knownSelection = it.cards.filter { w -> it.answers[w.id] != false }.map { w -> w.id }.toSet(),
            )
        }
        persist()
        if (s.mode != SessionMode.NEW_WORDS && s.cards.isNotEmpty() && !s.mistakesRound) {
            container.notifier.reviewFinished(s.cards.size)
        }
    }

    /** Finish screen: run just the words answered wrong, again. */
    fun repeatMistakes() {
        val mistakes = _state.value.mistakes
        if (mistakes.isEmpty()) return
        _state.update {
            it.copy(cards = mistakes, index = 0, answers = emptyMap(), finished = false, mistakesRound = true).resetCard()
        }
        persist()
        onCardShown()
    }

    // ---- New-words finish ----

    fun toggleKnown(id: Long) = _state.update {
        it.copy(knownSelection = if (id in it.knownSelection) it.knownSelection - id else it.knownSelection + id)
    }.also { persist() }

    /** New-words mode: the ticked words become learned (see `WordRepository.commitBatch`). */
    fun confirmLearned() = viewModelScope.launch {
        val s = _state.value
        container.repository.commitBatch(profileId, fullCards, s.knownSelection)
        _state.update { it.copy(committed = true) }
        persist()
    }

    /** New-words mode: not learned yet — run the same batch again (re-shuffled), benchmark untouched. */
    fun repeatBatch() = restart(reverse = false)

    /**
     * Finish screen: the same words again in the other direction (EN→RU becomes RU→EN), so each
     * word is practised both ways. Answers here still count for stats, but not twice for the
     * spaced-repetition schedule; a not-yet-confirmed new-words batch is confirmed after it as usual.
     */
    fun repeatReversed() = restart(reverse = true)

    private fun restart(reverse: Boolean) {
        if (reverse) direction = if (direction == Direction.EN_TO_RU) Direction.RU_TO_EN else Direction.EN_TO_RU
        round++
        if (shuffle) fullCards = LearningEngine.orderSession(fullCards, shuffle = true, seed = seed + round)
        persistFull()
        _state.update {
            it.copy(
                direction = direction,
                cards = fullCards,
                index = 0,
                finished = false,
                // A reversed round after confirming is just extra practice — the batch stays confirmed.
                committed = it.committed && reverse,
                answers = emptyMap(),
                mistakesRound = false,
            ).resetCard()
        }
        persist()
        onCardShown()
    }

    /**
     * Re-reads the session's words from the database — called when coming back from editing one
     * of them, so the card shows the fixed text/translation. A word deleted there leaves the session.
     */
    fun refreshCards() = viewModelScope.launch {
        if (_state.value.loading) return@launch
        val fresh = container.repository.getAllOrdered(profileId).associateBy { it.id }
        pool = fresh.values.toList()
        fullCards = fullCards.mapNotNull { fresh[it.id] }
        persistFull()
        _state.update { s ->
            val cards = s.cards.mapNotNull { fresh[it.id] }
            s.copy(
                cards = cards,
                index = s.index.coerceAtMost((cards.size - 1).coerceAtLeast(0)),
                finished = s.finished || cards.isEmpty(),
            )
        }
    }

    // ---- Internals ----

    private fun record(correct: Boolean) {
        val word = _state.value.current ?: return
        _state.update { it.copy(answers = it.answers + (word.id to correct)) }
        val updateSchedule = !_state.value.mistakesRound && scheduled.add(word.id)
        persist()
        viewModelScope.launch {
            val updated = container.repository.recordAnswer(profileId, word, correct, updateSchedule)
            replaceCard(updated.copy(isFavorite = _state.value.cards.firstOrNull { it.id == word.id }?.isFavorite ?: updated.isFavorite))
        }
    }

    private fun replaceCard(word: Word) = _state.update { s ->
        s.copy(cards = s.cards.map { if (it.id == word.id) word else it })
    }

    private fun SessionUiState.resetCard() =
        copy(revealed = false, input = "", verdict = null, picked = null, options = emptyList())

    private fun onCardShown() {
        val s = _state.value
        val word = s.current ?: return
        if (s.finished) return
        if (quiz == QuizType.CHOICE) {
            _state.update { it.copy(options = Quiz.choiceOptions(word, pool, direction, seed + word.id)) }
        }
        val autoSpeak = container.settings.autoPronounce.value
        if (quiz == QuizType.LISTENING || (autoSpeak && direction == Direction.EN_TO_RU)) pronounceCurrent()
    }

    private fun onRevealed() {
        if (quiz != QuizType.LISTENING && container.settings.autoPronounce.value && direction == Direction.RU_TO_EN) {
            pronounceCurrent()
        }
    }

    private fun persistFull() {
        handle[KEY_FULL] = fullCards.map { it.id }.toLongArray()
        handle[KEY_DIRECTION] = direction.name
        handle[KEY_ROUND_NO] = round
    }

    private fun persist() {
        val s = _state.value
        handle[KEY_INDEX] = s.index
        handle[KEY_FINISHED] = s.finished
        handle[KEY_COMMITTED] = s.committed
        handle[KEY_ANSWER_IDS] = s.answers.keys.toLongArray()
        handle[KEY_ANSWER_VALS] = s.answers.values.toBooleanArray()
        handle[KEY_ROUND] = if (s.mistakesRound) s.cards.map { it.id }.toLongArray() else null
        handle[KEY_KNOWN] = s.knownSelection.toLongArray()
        handle[KEY_SCHEDULED] = scheduled.toLongArray()
    }

    private companion object {
        const val KEY_FULL = "s_full"
        const val KEY_DIRECTION = "s_direction"
        const val KEY_ROUND_NO = "s_round_no"
        const val KEY_INDEX = "s_index"
        const val KEY_FINISHED = "s_finished"
        const val KEY_COMMITTED = "s_committed"
        const val KEY_ANSWER_IDS = "s_answer_ids"
        const val KEY_ANSWER_VALS = "s_answer_vals"
        const val KEY_ROUND = "s_round"
        const val KEY_KNOWN = "s_known"
        const val KEY_SCHEDULED = "s_scheduled"
    }
}

/** Reconstructs a [ReviewScope] from the primitive `scope*` nav args written by `Routes.session()`. */
private fun decodeScope(handle: SavedStateHandle): ReviewScope =
    when (handle.get<String>("scopeType")) {
        "window" -> ReviewScope.Window(
            count = handle.get<String>("scopeCount")?.toIntOrNull() ?: 0,
            fromStart = (handle.get<String>("scopeFromStart")).toBoolean(),
        )
        "range" -> ReviewScope.Range(
            from = handle.get<String>("scopeFrom")?.toIntOrNull() ?: 1,
            to = handle.get<String>("scopeTo")?.toIntOrNull() ?: 1,
        )
        else -> ReviewScope.All
    }
