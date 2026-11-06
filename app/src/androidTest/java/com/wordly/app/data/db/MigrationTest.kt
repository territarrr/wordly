package com.wordly.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a v3 database (schema exported in `app/schemas`) to the current version and checks
 * Room accepts it and existing rows survive with sane defaults. Uses its own throwaway DB file.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WordlyDatabase::class.java,
    )

    @Test
    fun migrate3To4_keepsWordsAndAddsStats() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO words (id, profileId, orderIndex, text, normalized, translation, createdAt, isFavorite) " +
                    "VALUES (1, 1, 1, 'cat', 'cat', 'кошка', 0, 1)",
            )
            execSQL("INSERT INTO progress (profileId, benchmarkOrderIndex) VALUES (1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT text, translation, isFavorite, correctCount, wrongCount, srsBox, dueAt, note FROM words").use { c ->
            c.moveToFirst()
            assertEquals("cat", c.getString(0))
            assertEquals("кошка", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals(0, c.getInt(4))
            assertEquals(0, c.getInt(5))
            assertEquals(true, c.isNull(6))
            assertEquals(true, c.isNull(7))
        }
        db.query("SELECT COUNT(*) FROM study_days").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
