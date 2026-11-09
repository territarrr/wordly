package com.wordly.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

/**
 * App-wide user settings, persisted in SharedPreferences (same store as [ProfilePreferences]).
 * Each value is exposed as a [StateFlow] so screens redraw as soon as it's changed in Settings.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _defaultBatch = MutableStateFlow(prefs.getInt(KEY_BATCH, 20))
    /** Pre-selected "Учить новые" batch size. */
    val defaultBatch: StateFlow<Int> = _defaultBatch.asStateFlow()

    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_GOAL, 20))
    /** Cards per day shown as the goal on Home. */
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _dailyReview = MutableStateFlow(prefs.getInt(KEY_DAILY_REVIEW, 50))
    /** "На сегодня" quota: how many due words to review per day at most, picked at random. */
    val dailyReview: StateFlow<Int> = _dailyReview.asStateFlow()

    private val _dueChanges = MutableStateFlow(0)
    /** Ticks whenever a "На сегодня" answer is counted or taken back — for screens showing what's left. */
    val dueChanges: StateFlow<Int> = _dueChanges.asStateFlow()

    private val _autoPronounce = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PRONOUNCE, false))
    /** Speak the foreign word automatically when its side of the card appears. */
    val autoPronounce: StateFlow<Boolean> = _autoPronounce.asStateFlow()

    private val _reminder = MutableStateFlow(
        Reminder(
            // On by default, at noon — the daily nudge is part of the app, not an opt-in extra.
            enabled = prefs.getBoolean(KEY_REMINDER_ON, true),
            hour = prefs.getInt(KEY_REMINDER_HOUR, 12),
            minute = prefs.getInt(KEY_REMINDER_MINUTE, 0),
        ),
    )
    val reminder: StateFlow<Reminder> = _reminder.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setDefaultBatch(size: Int) {
        _defaultBatch.value = size
        prefs.edit().putInt(KEY_BATCH, size).apply()
    }

    fun setDailyGoal(goal: Int) {
        _dailyGoal.value = goal
        prefs.edit().putInt(KEY_GOAL, goal).apply()
    }

    fun setDailyReview(count: Int) {
        _dailyReview.value = count
        prefs.edit().putInt(KEY_DAILY_REVIEW, count).apply()
    }

    /** "На сегодня" words already answered in [profileId] on [epochDay] (stored as "day:count"). */
    fun dueDone(profileId: Long, epochDay: Long): Int {
        val (day, count) = prefs.getString("$KEY_DUE_DONE$profileId", null)?.split(':')
            ?.takeIf { it.size == 2 } ?: return 0
        return if (day.toLongOrNull() == epochDay) count.toIntOrNull() ?: 0 else 0
    }

    /** Counts one "На сегодня" answer (or, with -1, takes one back) on [epochDay]. */
    fun addDueDone(profileId: Long, epochDay: Long, delta: Int) {
        val count = (dueDone(profileId, epochDay) + delta).coerceAtLeast(0)
        prefs.edit().putString("$KEY_DUE_DONE$profileId", "$epochDay:$count").apply()
        _dueChanges.value++
    }

    fun setAutoPronounce(enabled: Boolean) {
        _autoPronounce.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PRONOUNCE, enabled).apply()
    }

    fun setReminder(reminder: Reminder) {
        _reminder.value = reminder
        prefs.edit()
            .putBoolean(KEY_REMINDER_ON, reminder.enabled)
            .putInt(KEY_REMINDER_HOUR, reminder.hour)
            .putInt(KEY_REMINDER_MINUTE, reminder.minute)
            .apply()
    }

    /** Highest streak milestone (days) already congratulated in [profileId] — each is shown once. */
    fun celebratedMilestone(profileId: Long): Int = prefs.getInt("$KEY_CELEBRATED$profileId", 0)

    fun setCelebratedMilestone(profileId: Long, days: Int) {
        prefs.edit().putInt("$KEY_CELEBRATED$profileId", days).apply()
    }

    data class Reminder(val enabled: Boolean, val hour: Int, val minute: Int) {
        val timeLabel: String get() = "%02d:%02d".format(hour, minute)
    }

    private companion object {
        const val PREFS_NAME = "wordly_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_BATCH = "default_batch"
        const val KEY_GOAL = "daily_goal"
        const val KEY_DAILY_REVIEW = "daily_review"
        const val KEY_DUE_DONE = "due_done_"
        const val KEY_AUTO_PRONOUNCE = "auto_pronounce"
        const val KEY_REMINDER_ON = "reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val KEY_CELEBRATED = "celebrated_milestone_"
    }
}
