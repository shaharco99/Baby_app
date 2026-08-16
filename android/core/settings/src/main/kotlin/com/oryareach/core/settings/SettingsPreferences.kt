package com.oryareach.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app-settings")

/**
 * Device-local app preferences — never synced, never part of the encrypted workspace.
 * Deliberately outside it for the same reason as `:core:update`'s `UpdateState`: a locked app
 * still needs to read whether biometric unlock is on and how long the auto-lock timeout is,
 * and none of these values (a boolean, a duration) reveal anything about workspace content.
 *
 * Auto-lock only actually re-locks the session when [biometricUnlockEnabled] is true: without
 * a biometric/device-credential gate there is nothing that can safely re-open a locked session
 * (this app has no separate password — the workspace key is device-bound), so locking with
 * nothing able to unlock it again would just strand the user.
 */
class SettingsPreferences(private val context: Context) {

    private object Keys {
        val biometricUnlockEnabled = booleanPreferencesKey("biometric_unlock_enabled")
        val autoLockTimeoutMinutes = intPreferencesKey("auto_lock_timeout_minutes")
        val screenshotsBlocked = booleanPreferencesKey("screenshots_blocked")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val selectedGoogleCalendarIds = stringSetPreferencesKey("selected_google_calendar_ids")
    }

    val biometricUnlockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.biometricUnlockEnabled] ?: false }

    val autoLockTimeoutMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.autoLockTimeoutMinutes] ?: DEFAULT_AUTO_LOCK_MINUTES }

    /** Defaults to blocking screenshots — this app exists to keep its content private, and an
     * opt-in default would leave most users unprotected without ever knowing the option exists. */
    val screenshotsBlocked: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.screenshotsBlocked] ?: true }

    val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.notificationsEnabled] ?: false }

    /** Which of the connected Google account's calendars to show on the Calendar page. Empty
     * until the user picks at least one — see `:feature:settings`'s calendar picker. Device-local
     * like the rest of this file: which calendars a device shows is not couple-shared data. */
    val selectedGoogleCalendarIds: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.selectedGoogleCalendarIds] ?: emptySet() }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.biometricUnlockEnabled] = enabled }
    }

    suspend fun setAutoLockTimeoutMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.autoLockTimeoutMinutes] = minutes }
    }

    suspend fun setScreenshotsBlocked(blocked: Boolean) {
        context.settingsDataStore.edit { it[Keys.screenshotsBlocked] = blocked }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.notificationsEnabled] = enabled }
    }

    suspend fun setSelectedGoogleCalendarIds(ids: Set<String>) {
        context.settingsDataStore.edit { it[Keys.selectedGoogleCalendarIds] = ids }
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_MINUTES = 5
    }
}
