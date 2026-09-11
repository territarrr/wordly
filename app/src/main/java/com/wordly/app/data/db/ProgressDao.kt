package com.wordly.app.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("INSERT OR IGNORE INTO progress (id, benchmarkOrderIndex) VALUES (0, 0)")
    suspend fun ensureExists()

    @Query("SELECT benchmarkOrderIndex FROM progress WHERE id = 0")
    fun observeBenchmark(): Flow<Long?>

    @Query("SELECT COALESCE((SELECT benchmarkOrderIndex FROM progress WHERE id = 0), 0)")
    suspend fun currentBenchmark(): Long

    @Query("UPDATE progress SET benchmarkOrderIndex = :orderIndex WHERE id = 0")
    suspend fun setBenchmark(orderIndex: Long)
}
