package com.wordly.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per profile holding that profile's moving benchmark.
 *
 * [benchmarkOrderIndex] is a [Word.orderIndex] value (scoped to the same [profileId]): words
 * at or below it are learned, words above it are still new. `0` means nothing is learned yet
 * (the first word gets `orderIndex = 1`).
 */
@Entity(tableName = "progress")
data class Progress(
    @PrimaryKey val profileId: Long,
    val benchmarkOrderIndex: Long = 0,
)
