package com.wordly.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.data.AppSettings
import com.wordly.app.data.WordRepository
import com.wordly.app.domain.StudyStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val totalCards: Int = 0,
    val studiedDays: Int = 0,
    /** Share of right answers over all recorded answers, 0..100, or null before any answer. */
    val accuracy: Int? = null,
    val learned: Int = 0,
    val total: Int = 0,
    val goal: Int = 20,
    val calendar: List<List<StudyStats.CalendarDay>> = emptyList(),
    val weekly: List<Int> = emptyList(),
)

class StatsViewModel(repository: WordRepository, settings: AppSettings, profileId: Long) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(
        repository.observeStudyDays(profileId),
        repository.observeWords(profileId),
        repository.observeBenchmark(profileId),
        settings.dailyGoal,
    ) { days, words, benchmark, goal ->
        val today = LocalDate.now().toEpochDay()
        val byDay = days.associate { it.epochDay to it.cards }
        val studied = days.filter { it.cards > 0 }.map { it.epochDay }.toSet()
        val answers = words.sumOf { it.correctCount + it.wrongCount }
        val calendar = StudyStats.calendar(byDay, today, WEEKS)
        StatsUiState(
            loading = false,
            streak = StudyStats.streak(studied, today),
            bestStreak = StudyStats.longestStreak(studied),
            totalCards = days.sumOf { it.cards },
            studiedDays = studied.size,
            accuracy = if (answers == 0) null else words.sumOf { it.correctCount } * 100 / answers,
            learned = words.count { it.orderIndex <= benchmark },
            total = words.size,
            goal = goal,
            calendar = calendar,
            weekly = StudyStats.weeklyTotals(calendar),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private companion object {
        /** ~4 months fits a phone's width at a readable cell size. */
        const val WEEKS = 17
    }
}
