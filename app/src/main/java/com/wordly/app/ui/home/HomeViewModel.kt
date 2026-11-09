package com.wordly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.data.AppSettings
import com.wordly.app.data.ProfilePreferences
import com.wordly.app.data.WordRepository
import com.wordly.app.domain.LearningEngine
import com.wordly.app.domain.Srs
import com.wordly.app.domain.StudyStats
import com.wordly.app.domain.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.wordly.app.data.entity.StudyDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeStats(
    val total: Int = 0,
    val learned: Int = 0,
    val newCount: Int = 0,
    val favoritesCount: Int = 0,
    /** Learned words the spaced-repetition schedule says are due now. */
    val dueCount: Int = 0,
    /** What's left of today's "На сегодня" quota: at most [dailyReview] a day, however many are due. */
    val dueToday: Int = 0,
    val dailyReview: Int = 50,
    val hardCount: Int = 0,
    val todayCards: Int = 0,
    val streak: Int = 0,
    /** Longest streak ever — milestone badges stay earned after a break. */
    val bestStreak: Int = 0,
    /** Cards per day for the calendar preview, weeks as columns (see [StudyStats.calendar]). */
    val calendar: List<List<StudyStats.CalendarDay>> = emptyList(),
)

/**
 * The active profile isn't owned here -- it lives in [ProfilePreferences] (persisted, shared
 * app-wide) and is switched from [com.wordly.app.ui.settings.SettingsScreen] or the title on
 * Home, so Home just observes it and its stats redraw automatically whenever that changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: WordRepository,
    private val profilePreferences: ProfilePreferences,
    private val settings: AppSettings,
) : ViewModel() {

    val activeProfile: StateFlow<Profile> = profilePreferences.activeProfile
    val dailyGoal: StateFlow<Int> = settings.dailyGoal
    val defaultBatch: StateFlow<Int> = settings.defaultBatch
    val autoPronounce: StateFlow<Boolean> = settings.autoPronounce

    val stats: StateFlow<HomeStats> = activeProfile.flatMapLatest { profile ->
        repository.scheduleBacklog(profile.id)
        combine(
            repository.observeWords(profile.id),
            repository.observeBenchmark(profile.id),
            repository.observeStudyDays(profile.id),
            settings.dailyReview,
            settings.dueChanges,
        ) { words, benchmark, days, dailyReview, _ ->
            val learned = words.count { it.orderIndex <= benchmark }
            val today = LocalDate.now().toEpochDay()
            val dueCount = Srs.dueCount(words, benchmark, System.currentTimeMillis())
            val quotaLeft = (dailyReview - settings.dueDone(profile.id, today)).coerceAtLeast(0)
            HomeStats(
                total = words.size,
                learned = learned,
                newCount = words.size - learned,
                favoritesCount = words.count { it.isFavorite },
                dueCount = dueCount,
                dueToday = minOf(dueCount, quotaLeft),
                dailyReview = dailyReview,
                hardCount = words.count(LearningEngine::isHard),
                todayCards = days.firstOrNull { it.epochDay == today }?.cards ?: 0,
                streak = StudyStats.streak(studied(days), today),
                bestStreak = StudyStats.longestStreak(studied(days)),
                calendar = StudyStats.calendar(days.associate { it.epochDay to it.cards }, today, CALENDAR_WEEKS),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    private val celebrationDismissed = MutableStateFlow(0)

    /** A streak milestone just reached and not congratulated yet in this profile, if any. */
    val celebration: StateFlow<StudyStats.Milestone?> =
        combine(stats, activeProfile, celebrationDismissed) { s, profile, _ ->
            StudyStats.milestoneToCelebrate(s.streak, settings.celebratedMilestone(profile.id))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissCelebration(milestone: StudyStats.Milestone) {
        settings.setCelebratedMilestone(activeProfile.value.id, milestone.days)
        celebrationDismissed.value++
    }

    fun switchProfile(profile: Profile) = profilePreferences.setActiveProfile(profile)

    fun setAutoPronounce(enabled: Boolean) = settings.setAutoPronounce(enabled)
}

private const val CALENDAR_WEEKS = 17

private fun studied(days: List<StudyDay>): Set<Long> = days.filter { it.cards > 0 }.map { it.epochDay }.toSet()
