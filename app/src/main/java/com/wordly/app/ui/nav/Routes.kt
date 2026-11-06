package com.wordly.app.ui.nav

import com.wordly.app.domain.model.ReviewScope
import com.wordly.app.domain.model.SessionConfig

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val WORDS_PATTERN = "words?profileId={profileId}"
    const val ADD_PATTERN = "add?profileId={profileId}"
    const val IMPORT_PATTERN = "import?profileId={profileId}"
    const val EDIT_PATTERN = "edit/{wordId}?profileId={profileId}"

    fun words(profileId: Long): String = "words?profileId=$profileId"
    fun add(profileId: Long): String = "add?profileId=$profileId"
    fun import(profileId: Long): String = "import?profileId=$profileId"
    fun edit(wordId: Long, profileId: Long): String = "edit/$wordId?profileId=$profileId"

    const val SESSION_PATTERN =
        "session?mode={mode}&dir={dir}&quiz={quiz}&shuffle={shuffle}" +
            "&scopeType={scopeType}&scopeCount={scopeCount}&scopeFromStart={scopeFromStart}" +
            "&scopeFrom={scopeFrom}&scopeTo={scopeTo}&batch={batch}&seed={seed}&profileId={profileId}"

    /**
     * `batch` doubles as the card limit for [SessionConfig.Due]; `scope*` are only meaningful for
     * [SessionConfig.Review]/[SessionConfig.Favorites] and left at "all" otherwise.
     */
    fun session(config: SessionConfig, profileId: Long): String {
        val (mode, shuffle, scope, batch, seed) = when (config) {
            is SessionConfig.Review -> Parts("review", config.shuffle, config.scope, 0, config.seed)
            is SessionConfig.NewWords -> Parts("new", config.shuffle, ReviewScope.All, config.batchSize, config.seed)
            is SessionConfig.Favorites -> Parts("favorites", config.shuffle, config.scope, 0, config.seed)
            is SessionConfig.Due -> Parts("due", true, ReviewScope.All, config.limit, config.seed)
            is SessionConfig.Hard -> Parts("hard", true, ReviewScope.All, 0, config.seed)
        }
        val s = encodeScope(scope)
        return "session?mode=$mode&dir=${config.direction.name}&quiz=${config.quiz.name}&shuffle=$shuffle" +
            "&scopeType=${s.type}&scopeCount=${s.count}&scopeFromStart=${s.fromStart}" +
            "&scopeFrom=${s.from}&scopeTo=${s.to}&batch=$batch&seed=$seed&profileId=$profileId"
    }

    private data class Parts(
        val mode: String,
        val shuffle: Boolean,
        val scope: ReviewScope,
        val batch: Int,
        val seed: Long,
    )

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
}
