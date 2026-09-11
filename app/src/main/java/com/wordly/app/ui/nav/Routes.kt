package com.wordly.app.ui.nav

import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.ReviewScope
import com.wordly.app.domain.model.SessionConfig

object Routes {
    const val HOME = "home"
    const val WORDS = "words"
    const val ADD = "add"
    const val EDIT_PATTERN = "edit/{wordId}"

    fun edit(wordId: Long): String = "edit/$wordId"

    const val SESSION_PATTERN =
        "session?mode={mode}&dir={dir}&shuffle={shuffle}" +
            "&scopeType={scopeType}&scopeCount={scopeCount}&scopeFromStart={scopeFromStart}" +
            "&scopeFrom={scopeFrom}&scopeTo={scopeTo}&batch={batch}&seed={seed}"

    fun session(config: SessionConfig): String = when (config) {
        is SessionConfig.Review -> {
            val (scopeType, count, fromStart, from, to) = encodeScope(config.scope)
            build(
                mode = "review",
                dir = config.direction,
                shuffle = config.shuffle,
                scopeType = scopeType,
                scopeCount = count,
                scopeFromStart = fromStart,
                scopeFrom = from,
                scopeTo = to,
                batch = 0,
                seed = config.seed,
            )
        }
        is SessionConfig.NewWords -> build(
            mode = "new",
            dir = config.direction,
            shuffle = false,
            scopeType = "all",
            scopeCount = 0,
            scopeFromStart = false,
            scopeFrom = 0,
            scopeTo = 0,
            batch = config.batchSize,
            seed = 0,
        )
    }

    private data class EncodedScope(
        val type: String,
        val count: Int,
        val fromStart: Boolean,
        val from: Int,
        val to: Int,
    )

    private fun encodeScope(scope: ReviewScope): EncodedScope = when (scope) {
        ReviewScope.All -> EncodedScope("all", 0, false, 0, 0)
        is ReviewScope.Window -> EncodedScope("window", scope.count, scope.fromStart, 0, 0)
        is ReviewScope.Range -> EncodedScope("range", 0, false, scope.from, scope.to)
    }

    private fun build(
        mode: String,
        dir: Direction,
        shuffle: Boolean,
        scopeType: String,
        scopeCount: Int,
        scopeFromStart: Boolean,
        scopeFrom: Int,
        scopeTo: Int,
        batch: Int,
        seed: Long,
    ) = "session?mode=$mode&dir=${dir.name}&shuffle=$shuffle" +
        "&scopeType=$scopeType&scopeCount=$scopeCount&scopeFromStart=$scopeFromStart" +
        "&scopeFrom=$scopeFrom&scopeTo=$scopeTo&batch=$batch&seed=$seed"
}
