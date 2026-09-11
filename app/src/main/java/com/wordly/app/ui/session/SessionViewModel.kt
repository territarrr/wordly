package com.wordly.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.AppContainer
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.LearningEngine
import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.ReviewScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val loading: Boolean = true,
    val direction: Direction = Direction.EN_TO_RU,
    val isNewWordsMode: Boolean = false,
    val cards: List<Word> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val finished: Boolean = false,
    val committed: Boolean = false,
) {
    val current: Word? get() = cards.getOrNull(index)
    val progressLabel: String get() = "${(index + 1).coerceAtMost(cards.size.coerceAtLeast(1))} / ${cards.size}"
}

class SessionViewModel(
    private val container: AppContainer,
    handle: SavedStateHandle,
) : ViewModel() {

    private val mode: String = handle["mode"] ?: "review"
    private val direction: Direction =
        runCatching { Direction.valueOf(handle["dir"] ?: "") }.getOrDefault(Direction.EN_TO_RU)
    private val shuffle: Boolean = (handle.get<String>("shuffle")).toBoolean()
    private val scope: ReviewScope = decodeScope(handle)
    private val batchSize: Int = handle.get<String>("batch")?.toIntOrNull() ?: 20
    private val seed: Long = handle.get<String>("seed")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(SessionUiState(direction = direction))
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val all = container.repository.getAllOrdered()
        val benchmark = container.repository.currentBenchmark()
        val cards = if (mode == "new") {
            LearningEngine.newBatch(all, benchmark, batchSize)
        } else {
            val slice = LearningEngine.learnedSlice(all = all, benchmark = benchmark, scope = scope)
            LearningEngine.orderSession(slice, shuffle, seed)
        }
        _state.update {
            it.copy(
                loading = false,
                isNewWordsMode = mode == "new",
                cards = cards,
                index = 0,
                revealed = false,
                finished = cards.isEmpty(),
            )
        }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    fun pronounceCurrent() {
        _state.value.current?.let { container.pronouncer.pronounce(it) }
    }

    fun next() {
        val s = _state.value
        if (s.finished) return
        if (s.index + 1 >= s.cards.size) {
            _state.update { it.copy(finished = true) }
            if (!s.isNewWordsMode && s.cards.isNotEmpty()) {
                container.notifier.reviewFinished(s.cards.size)
            }
        } else {
            _state.update { it.copy(index = it.index + 1, revealed = false) }
        }
    }

    /** New-words mode: confirm the batch is learned and advance the benchmark past it. */
    fun confirmLearned() = viewModelScope.launch {
        val batch = _state.value.cards
        val newBenchmark = LearningEngine.nextBenchmark(batch, container.repository.currentBenchmark())
        container.repository.setBenchmark(newBenchmark)
        _state.update { it.copy(committed = true) }
    }

    /** New-words mode: not learned yet — run the same batch again, benchmark untouched. */
    fun repeatBatch() = _state.update {
        it.copy(index = 0, revealed = false, finished = false, committed = false)
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
