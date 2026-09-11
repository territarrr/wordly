package com.wordly.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordly.app.data.db.WordDao
import com.wordly.app.data.db.WordlyDatabase
import com.wordly.app.data.entity.Word
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordDaoTest {

    private lateinit var db: WordlyDatabase
    private lateinit var dao: WordDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WordlyDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.wordDao()
    }

    @After
    fun tearDown() = db.close()

    private fun word(order: Long, normalized: String) = Word(
        orderIndex = order,
        text = normalized,
        normalized = normalized,
        translation = "t$order",
    )

    @Test
    fun uniqueNormalizedIndexRejectsDuplicate() = runTest {
        dao.insert(word(1, "apple"))
        val error = runCatching { dao.insert(word(2, "apple")) }.exceptionOrNull()
        assertEquals(
            android.database.sqlite.SQLiteConstraintException::class.java,
            error?.javaClass,
        )
    }

    @Test
    fun maxOrderIndexTracksInsertsAndSurvivesMiddleDelete() = runTest {
        val id1 = dao.insert(word(1, "a"))
        dao.insert(word(2, "b"))
        val id3 = dao.insert(word(3, "c"))
        assertEquals(3L, dao.maxOrderIndex())

        dao.delete(word(2, "b").copy(id = dao.findByNormalized("b")!!.id))
        assertEquals(3L, dao.maxOrderIndex())

        // Ensure remaining rows keep their original order indices.
        val remaining = dao.getAllOrdered().map { it.orderIndex }
        assertEquals(listOf(1L, 3L), remaining)
        assertEquals(id1, dao.findByNormalized("a")!!.id)
        assertEquals(id3, dao.findByNormalized("c")!!.id)
    }
}
