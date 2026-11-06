package com.wordly.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.wordly.app.data.entity.StudyDay
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDayDao {

    @Query("SELECT * FROM study_days WHERE profileId = :profileId ORDER BY epochDay DESC")
    fun observe(profileId: Long): Flow<List<StudyDay>>

    @Query("INSERT OR IGNORE INTO study_days (profileId, epochDay, cards) VALUES (:profileId, :epochDay, 0)")
    suspend fun ensureExists(profileId: Long, epochDay: Long)

    @Query("UPDATE study_days SET cards = cards + :count WHERE profileId = :profileId AND epochDay = :epochDay")
    suspend fun increment(profileId: Long, epochDay: Long, count: Int)

    /** No SQLite UPSERT on minSdk 26 (needs 3.24), so insert-or-ignore then increment. */
    @Transaction
    suspend fun add(profileId: Long, epochDay: Long, count: Int) {
        ensureExists(profileId, epochDay)
        increment(profileId, epochDay, count)
    }

    @Query("DELETE FROM study_days WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)
}
