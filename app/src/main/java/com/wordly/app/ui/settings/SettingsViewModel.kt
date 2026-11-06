package com.wordly.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.wordly.app.data.AppSettings
import com.wordly.app.data.ProfilePreferences
import com.wordly.app.data.ThemeMode
import com.wordly.app.domain.model.Profile
import com.wordly.app.notify.ReminderScheduler
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val profilePreferences: ProfilePreferences,
    private val settings: AppSettings,
    private val appContext: Context,
) : ViewModel() {

    val activeProfile: StateFlow<Profile> = profilePreferences.activeProfile
    val defaultProfile: StateFlow<Profile> = profilePreferences.defaultProfile
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val defaultBatch: StateFlow<Int> = settings.defaultBatch
    val dailyGoal: StateFlow<Int> = settings.dailyGoal
    val autoPronounce: StateFlow<Boolean> = settings.autoPronounce
    val reminder: StateFlow<AppSettings.Reminder> = settings.reminder

    fun selectActiveProfile(profile: Profile) = profilePreferences.setActiveProfile(profile)
    fun selectDefaultProfile(profile: Profile) = profilePreferences.setDefaultProfile(profile)
    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDefaultBatch(size: Int) = settings.setDefaultBatch(size)
    fun setDailyGoal(goal: Int) = settings.setDailyGoal(goal)
    fun setAutoPronounce(enabled: Boolean) = settings.setAutoPronounce(enabled)

    fun setReminder(reminder: AppSettings.Reminder) {
        settings.setReminder(reminder)
        ReminderScheduler.apply(appContext, reminder)
    }
}
