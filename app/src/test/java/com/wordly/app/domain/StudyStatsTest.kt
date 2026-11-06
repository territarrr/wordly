package com.wordly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyStatsTest {

    @Test
    fun `counts consecutive days ending today`() {
        assertEquals(3, StudyStats.streak(setOf(10, 9, 8, 6), today = 10))
    }

    @Test
    fun `a streak ending yesterday is still alive in the morning`() {
        assertEquals(2, StudyStats.streak(setOf(9, 8), today = 10))
    }

    @Test
    fun `gap of a full day breaks it`() {
        assertEquals(0, StudyStats.streak(setOf(8, 7), today = 10))
    }

    @Test
    fun `longest streak survives a break`() {
        val days = (1L..12L).toSet() + (20L..22L)
        assertEquals(12, StudyStats.longestStreak(days))
        assertEquals(0, StudyStats.longestStreak(emptySet()))
    }

    @Test
    fun `next milestone and the one to celebrate`() {
        assertEquals(10, StudyStats.nextMilestone(3)?.days)
        assertEquals(50, StudyStats.nextMilestone(10)?.days)
        assertEquals(null, StudyStats.nextMilestone(400))
        assertEquals(10, StudyStats.milestoneToCelebrate(10, lastCelebrated = 0)?.days)
        assertEquals(null, StudyStats.milestoneToCelebrate(12, lastCelebrated = 10))
        assertEquals(50, StudyStats.milestoneToCelebrate(55, lastCelebrated = 0)?.days)
        assertEquals(null, StudyStats.milestoneToCelebrate(9, lastCelebrated = 0))
    }

    @Test
    fun `reminder text`() {
        assertEquals(
            "Давай поучим новые слова" to "Серия: 5 дней 🔥 Не прерывай её.\nНовых слов ждут: 12, пора повторить: 3",
            StudyStats.reminderText(streak = 5, studiedToday = false, newCount = 12, dueCount = 3),
        )
        assertEquals(
            "Сегодня будет 10 дней подряд — не пропусти! 🔥",
            StudyStats.reminderText(streak = 9, studiedToday = false, newCount = 1, dueCount = 0).second.lines().first(),
        )
        assertEquals("Начни серию сегодня 🔥", StudyStats.reminderText(0, false, 0, 0).second)
        assertEquals("Давай повторим слова", StudyStats.reminderText(0, false, 0, 4).first)
        assertEquals("21 день", StudyStats.days(21))
    }
}
