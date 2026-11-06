package com.wordly.app.data

import android.content.Context
import com.wordly.app.domain.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Two distinct, deliberately separate pieces of state -- collapsing them into one caused
 * switching the current profile to silently overwrite what the app opens to next time:
 * - [activeProfile]: which [Profile] every screen shows *right now*. In-memory only for the
 *   process lifetime, starts out equal to [defaultProfile] on launch, and changing it (e.g. from
 *   [com.wordly.app.ui.settings.SettingsScreen]) redraws every screen already showing (like Home)
 *   immediately, but never touches the persisted default -- so you can switch to studying English
 *   for this session while Serbian stays what the app opens to next time.
 * - [defaultProfile]: which [Profile] the app opens to on next launch. A single Long in
 *   SharedPreferences (no need for Room/DataStore's async API for one value), changed only via
 *   its own explicit setting in Settings.
 */
class ProfilePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _defaultProfile = MutableStateFlow(
        Profile.fromId(prefs.getLong(KEY_DEFAULT_PROFILE_ID, Profile.ENGLISH.id)),
    )
    val defaultProfile: StateFlow<Profile> = _defaultProfile.asStateFlow()

    private val _activeProfile = MutableStateFlow(_defaultProfile.value)
    val activeProfile: StateFlow<Profile> = _activeProfile.asStateFlow()

    fun setActiveProfile(profile: Profile) {
        _activeProfile.value = profile
    }

    fun setDefaultProfile(profile: Profile) {
        _defaultProfile.value = profile
        prefs.edit().putLong(KEY_DEFAULT_PROFILE_ID, profile.id).apply()
    }

    private companion object {
        const val PREFS_NAME = "wordly_prefs"
        const val KEY_DEFAULT_PROFILE_ID = "default_profile_id"
    }
}
