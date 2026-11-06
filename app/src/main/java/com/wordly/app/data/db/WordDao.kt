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

    @Query("SELECT * FROM words WHERE profileId = :profileId ORDER BY orderIndex ASC")
    fun observeAllOrdered(profileId: Long): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE profileId = :profileId ORDER BY orderIndex ASC")
    suspend fun getAllOrdered(profileId: Long): List<Word>

    @Query("SELECT * FROM words WHERE profileId = :profileId AND normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(profileId: Long, normalized: String): Word?

    @Query("SELECT * FROM words WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Word?

    @Query("SELECT COALESCE(MAX(orderIndex), 0) FROM words WHERE profileId = :profileId")
    suspend fun maxOrderIndex(profileId: Long): Long

    /** Aborts (throws) on the unique `(profileId, normalized)` index — enforces "no duplicate words". */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: Word): Long

    @Update
    suspend fun update(word: Word)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(words: List<Word>)

    @Query("UPDATE words SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE words SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, isFavorite: Boolean)

    @Update
    suspend fun updateAll(words: List<Word>)

    @Delete
    suspend fun delete(word: Word)

    @Delete
    suspend fun deleteAll(words: List<Word>)

    /** Puts back rows removed by [deleteAll] with their original ids (undo). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(words: List<Word>)

    @Query("DELETE FROM words WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)
}
