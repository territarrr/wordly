package com.wordly.app.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("INSERT OR IGNORE INTO progress (profileId, benchmarkOrderIndex) VALUES (:profileId, 0)")
    suspend fun ensureExists(profileId: Long)

    @Query("SELECT benchmarkOrderIndex FROM progress WHERE profileId = :profileId")
    fun observeBenchmark(profileId: Long): Flow<Long?>

    @Query("SELECT COALESCE((SELECT benchmarkOrderIndex FROM progress WHERE profileId = :profileId), 0)")
    suspend fun currentBenchmark(profileId: Long): Long

    @Query("UPDATE progress SET benchmarkOrderIndex = :orderIndex WHERE profileId = :profileId")
    suspend fun setBenchmark(profileId: Long, orderIndex: Long)
}
