package com.wordly.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A vocabulary item.
 *
 * @param orderIndex strictly increasing value assigned on insert (`max + 1`). Defines "order
 *   of addition" and stays stable when other rows are deleted. The benchmark is compared
 *   against this field.
 * @param normalized [com.wordly.app.domain.LearningEngine.normalize] of [text]; unique index
 *   enforces "no duplicate words".
 */
@Entity(
    tableName = "words",
    indices = [
        Index(value = ["normalized"], unique = true),
        Index(value = ["orderIndex"]),
    ],
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderIndex: Long,
    val text: String,
    val normalized: String,
    val translation: String,
    val transcription: String? = null,
    val audioUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
