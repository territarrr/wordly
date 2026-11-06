package com.wordly.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A vocabulary item.
 *
 * @param profileId which [com.wordly.app.domain.model.Profile] this word belongs to — word
 *   lists, order/benchmark are all scoped per profile.
 * @param orderIndex strictly increasing value (per [profileId]) assigned on insert (`max + 1`).
 *   Defines "order of addition" and stays stable when other rows are deleted. The benchmark is
 *   compared against this field.
 * @param normalized [com.wordly.app.domain.LearningEngine.normalize] of [text]; unique index
 *   on `(profileId, normalized)` enforces "no duplicate words" within a profile.
 * @param note free-form note/example sentence, shown on the back of the card.
 * @param correctCount / [wrongCount] how many times "Знаю"/"Не знаю" (or a right/wrong typed or
 *   picked answer) was recorded for this word, across all sessions — drives "Трудные слова".
 * @param srsBox / [dueAt] spaced-repetition state (see [com.wordly.app.domain.Srs]); `0`/`null`
 *   until the word is first learned.
 */
@Entity(
    tableName = "words",
    indices = [
        Index(value = ["profileId", "normalized"], unique = true),
        Index(value = ["orderIndex"]),
    ],
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val orderIndex: Long,
    val text: String,
    val normalized: String,
    val translation: String,
    val transcription: String? = null,
    val audioUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val note: String? = null,
    @ColumnInfo(defaultValue = "0") val correctCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val wrongCount: Int = 0,
    val lastReviewedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val srsBox: Int = 0,
    val dueAt: Long? = null,
)
