package com.oryareach.feature.settings

import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.oryareach.core.database.importer.WebImportOutcome
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby

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
    @StringRes val googleCalendarError: Int? = null,
    val calendarPickerVisible: Boolean = false,
    val availableGoogleCalendars: List<GoogleCalendarOption> = emptyList(),
    val googleAccountLinked: Boolean = false,
    val googleAccountLinkBusy: Boolean = false,
    val googleAccountLinkError: Boolean = false,

    // The couple's children, and the feeding cadence that drives the reminder. Shared
    // workspace state rather than per-device preferences, so these come from Room, not
    // [com.oryareach.core.settings.SettingsPreferences].
    val children: List<Baby> = emptyList(),
    val activeBabyId: String? = null,
    val feedIntervalMinutes: Int = AppSettings.DEFAULT_FEED_INTERVAL_MINUTES,
    val pumpIntervalMinutes: Int = AppSettings.DEFAULT_PUMP_INTERVAL_MINUTES,
    /** The child whose birth details are open for editing, if any. */
    val editingChild: Baby? = null,
    val addChildVisible: Boolean = false,

    // Bringing in a JSON export from the retired web app.
    val importing: Boolean = false,
    val importResult: WebImportOutcome? = null,
) {
    val autoLockOptionMinutes: List<Int> get() = listOf(1, 5, 15, 30)

    /** Two hours to four, the range a newborn's feeds actually fall in. */
    val feedIntervalOptionMinutes: List<Int> get() = listOf(120, 150, 180, 210, 240)

    /** Starts lower than the feed range: establishing supply means pumping more often. */
    val pumpIntervalOptionMinutes: List<Int> get() = listOf(90, 120, 150, 180, 240)
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
