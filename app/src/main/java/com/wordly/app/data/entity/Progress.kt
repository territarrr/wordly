package com.wordly.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table ([id] is always 0) holding the moving benchmark.
 *
 * [benchmarkOrderIndex] is a [Word.orderIndex] value: words at or below it are learned,
 * words above it are still new. `0` means nothing is learned yet (the first word gets
 * `orderIndex = 1`).
 */
@Entity(tableName = "progress")
data class Progress(
    @PrimaryKey val id: Int = 0,
    val benchmarkOrderIndex: Long = 0,
)
