package com.wordly.app.ui.nav

import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.SessionConfig

object Routes {
    const val HOME = "home"
    const val WORDS = "words"
    const val ADD = "add"
    const val EDIT_PATTERN = "edit/{wordId}"

    fun edit(wordId: Long): String = "edit/$wordId"

    const val SESSION_PATTERN =
        "session?mode={mode}&dir={dir}&shuffle={shuffle}&window={window}&batch={batch}&seed={seed}"

    fun session(config: SessionConfig): String = when (config) {
        is SessionConfig.Review -> build(
            mode = "review",
            dir = config.direction,
            shuffle = config.shuffle,
            window = config.window ?: -1,
            batch = 0,
            seed = config.seed,
        )
        is SessionConfig.NewWords -> build(
            mode = "new",
            dir = config.direction,
            shuffle = false,
            window = -1,
            batch = config.batchSize,
            seed = 0,
        )
    }

    private fun build(
        mode: String,
        dir: Direction,
        shuffle: Boolean,
        window: Int,
        batch: Int,
        seed: Long,
    ) = "session?mode=$mode&dir=${dir.name}&shuffle=$shuffle&window=$window&batch=$batch&seed=$seed"
}
