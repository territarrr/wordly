package com.wordly.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordly.app.data.entity.Word
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM words ORDER BY orderIndex ASC")
    fun observeAllOrdered(): Flow<List<Word>>

    @Query("SELECT * FROM words ORDER BY orderIndex ASC")
    suspend fun getAllOrdered(): List<Word>

    @Query("SELECT * FROM words WHERE normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): Word?

    @Query("SELECT * FROM words WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Word?

    @Query("SELECT COALESCE(MAX(orderIndex), 0) FROM words")
    suspend fun maxOrderIndex(): Long

    /** Aborts (throws) on the unique `normalized` index — enforces "no duplicate words". */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: Word): Long

    @Update
    suspend fun update(word: Word)

    @Delete
    suspend fun delete(word: Word)
}
