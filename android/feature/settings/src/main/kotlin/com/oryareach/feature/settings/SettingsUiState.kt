package com.oryareach.feature.settings

import android.content.IntentSender
import androidx.compose.runtime.Immutable

/** One entry in the "choose which calendars to show" picker — a Google calendar plus whether
 * this device currently shows it on the Calendar page. */
data class GoogleCalendarOption(
    val id: String,
    val summary: String,
    val selected: Boolean,
)

@Immutable
data class SettingsUiState(
    val biometricUnlockEnabled: Boolean = false,
    val autoLockTimeoutMinutes: Int = 5,
    val screenshotsBlocked: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val recoveryPhrase: List<String>? = null,
    val busy: Boolean = false,
    val googleCalendarConnected: Boolean = false,
    val googleCalendarAccountEmail: String? = null,
    val googleCalendarBusy: Boolean = false,
    val googleCalendarError: String? = null,
    val calendarPickerVisible: Boolean = false,
    val availableGoogleCalendars: List<GoogleCalendarOption> = emptyList(),
) {
    val autoLockOptionMinutes: List<Int> get() = listOf(1, 5, 15, 30)
}

sealed interface SettingsEffect {
    /** Handled in `:app`, which is the only place both `:feature:settings` and
     * `:feature:pairing` are visible — feature modules must not depend on each other. */
    data object NavigateToDeviceManagement : SettingsEffect

    /** Handled in `:app`: launches the system notification-permission prompt on Android 13+.
     * The result comes back via [SettingsActions.onNotificationPermissionResult]. */
    data object RequestNotificationPermission : SettingsEffect

    /** First-time Google Calendar consent needs the account picker/consent screen — handled
     * inside this screen (not `:app`) via `rememberLauncherForActivityResult`, same pattern as
     * `:core:scanner`'s `DocumentScanner`. The result comes back via
     * [SettingsActions.onGoogleCalendarResolutionResult]. */
    data class LaunchGoogleCalendarResolution(val intentSender: IntentSender) : SettingsEffect
}
