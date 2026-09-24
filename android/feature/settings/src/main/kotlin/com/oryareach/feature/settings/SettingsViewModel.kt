package com.oryareach.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.calendar.GoogleCalendarSyncRepository
import com.oryareach.core.common.AppResult
import com.oryareach.core.crypto.RecoveryPhrase
import com.oryareach.core.database.importer.WebImporter
import com.oryareach.core.database.reminder.FeedingReminderRefresher
import com.oryareach.core.database.reminder.PumpReminderRefresher
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.model.Baby
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.security.DeviceIdentity
import com.oryareach.core.security.GoogleCalendarAuthManager
import com.oryareach.core.security.GoogleCalendarConnectResult
import com.oryareach.core.security.GoogleIdentitySignIn
import com.oryareach.core.network.push.PushTokenRepository
import com.oryareach.core.security.LocalDataWiper
import com.oryareach.core.security.SessionController
import com.oryareach.core.settings.ReminderScheduler
import com.oryareach.core.settings.SettingsPreferences
import com.oryareach.core.security.BuildConfig as SecurityBuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Stable
interface SettingsActions {
    fun onBiometricToggle(enabled: Boolean)
    fun onAutoLockMinutesChange(minutes: Int)
    fun onScreenshotsToggle(blocked: Boolean)
    fun onNotificationsToggle(enabled: Boolean)
    fun onNotificationPermissionResult(granted: Boolean)
    fun onLockNowClick()
    fun onShowRecoveryPhraseClick()
    fun onDismissRecoveryPhrase()
    fun onManageDevicesClick()
    fun onSignOutClick()
    fun onConnectGoogleAccountClick(context: Context)
    fun onConnectGoogleCalendarClick(context: Context)
    fun onGoogleCalendarResolutionResult(resultCode: Int, data: Intent?)
    fun onOpenCalendarPickerClick()
    fun onDismissCalendarPicker()
    fun onToggleCalendarSelection(calendarId: String)
    fun onDisconnectGoogleCalendarClick()
    fun onSetActiveChild(babyId: String)
    fun onImportJson(json: String)
    fun onDismissImportResult()
    fun onAddChildClick()
    fun onDismissAddChild()
    fun onAddChild(name: String, dueDate: LocalDate?, makeActive: Boolean)
    fun onEditChildClick(baby: Baby)
    fun onDismissEditChild()
    fun onUpdateChildBirthDetails(
        babyId: String,
        name: String,
        dueDate: LocalDate?,
        birthDate: LocalDate?,
        birthTime: LocalTime?,
        birthWeightGrams: Int?,
        birthPlace: String?,
    )
    fun onFeedIntervalChange(minutes: Int)
    fun onPumpIntervalChange(minutes: Int)
}

class SettingsViewModel(
    private val preferences: SettingsPreferences,
    private val reminders: ReminderScheduler,
    private val identity: DeviceIdentity,
    private val session: SessionController,
    private val auth: AuthRepository,
    private val localDataWiper: LocalDataWiper,
    private val pushTokens: PushTokenRepository,
    private val googleCalendarAuth: GoogleCalendarAuthManager,
    private val googleCalendarSync: GoogleCalendarSyncRepository,
    private val babies: BabyRepository,
    private val appSettings: AppSettingsRepository,
    private val feedingReminders: FeedingReminderRefresher,
    private val pumpReminders: PumpReminderRefresher,
    private val webImporter: WebImporter,
    private val workspaceId: () -> String?,
) : ViewModel(), SettingsActions {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            googleCalendarConnected = googleCalendarAuth.isConnected(),
            googleCalendarAccountEmail = googleCalendarAuth.connectedAccountEmail(),
            googleAccountLinked = auth.isGoogleLinked(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var selectedCalendarIds: Set<String> = emptySet()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.biometricUnlockEnabled,
                preferences.autoLockTimeoutMinutes,
                preferences.screenshotsBlocked,
                preferences.notificationsEnabled,
            ) { biometric, autoLock, screenshots, notifications ->
                Preferences(biometric, autoLock, screenshots, notifications)
            }.collect { prefs ->
                set {
                    it.copy(
                        biometricUnlockEnabled = prefs.biometric,
                        autoLockTimeoutMinutes = prefs.autoLock,
                        screenshotsBlocked = prefs.screenshots,
                        notificationsEnabled = prefs.notifications,
                    )
                }
            }
        }
        viewModelScope.launch {
            preferences.selectedGoogleCalendarIds.collect { ids -> selectedCalendarIds = ids }
        }
        workspaceId()?.let { id ->
            viewModelScope.launch {
                combine(babies.observeAll(id), appSettings.observe(id)) { children, settings ->
                    children to settings
                }.collect { (children, settings) ->
                    set {
                        it.copy(
                            children = children,
                            activeBabyId = settings?.activeBabyId,
                            feedIntervalMinutes = settings?.feedIntervalMinutes
                                ?: it.feedIntervalMinutes,
                            pumpIntervalMinutes = settings?.pumpIntervalMinutes
                                ?: it.pumpIntervalMinutes,
                        )
                    }
                }
            }
        }
    }

    override fun onBiometricToggle(enabled: Boolean) {
        viewModelScope.launch { preferences.setBiometricUnlockEnabled(enabled) }
    }

    override fun onAutoLockMinutesChange(minutes: Int) {
        viewModelScope.launch { preferences.setAutoLockTimeoutMinutes(minutes) }
    }

    override fun onScreenshotsToggle(blocked: Boolean) {
        viewModelScope.launch { preferences.setScreenshotsBlocked(blocked) }
    }

    override fun onNotificationsToggle(enabled: Boolean) {
        if (enabled) {
            _effects.trySend(SettingsEffect.RequestNotificationPermission)
        } else {
            viewModelScope.launch { preferences.setNotificationsEnabled(false) }
            reminders.cancel()
        }
    }

    override fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) return
        viewModelScope.launch { preferences.setNotificationsEnabled(true) }
        reminders.schedule()
    }

    override fun onLockNowClick() {
        if (!_uiState.value.biometricUnlockEnabled) return
        session.lock()
    }

    override fun onShowRecoveryPhraseClick() {
        val key = identity.workspaceKey() ?: return
        set { it.copy(recoveryPhrase = RecoveryPhrase.encode(key)) }
    }

    override fun onDismissRecoveryPhrase() = set { it.copy(recoveryPhrase = null) }

    override fun onManageDevicesClick() {
        _effects.trySend(SettingsEffect.NavigateToDeviceManagement)
    }

    override fun onSignOutClick() {
        if (_uiState.value.busy) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
            // Before `forget()`, which takes the device id with it: a row left behind would
            // have the partner's phone pushing to a device that is no longer in the workspace,
            // on every write, forever.
            pushTokens.unregister(identity.pushDeviceId)
            auth.signOut()
            identity.forget()
            session.signOut()
            // Restarts the process — nothing after this point runs; see LocalDataWiper's
            // doc comment for why the database can't just be swapped out in place.
            localDataWiper.wipeAndRestart()
        }
    }

    /** Same Credential Manager flow the login screen uses (see `:feature:auth`'s
     * `GoogleSignInButton`), just handed to [AuthRepository.linkGoogleIdentity] instead of
     * `signInWithGoogle` — attaches Google to the already-signed-in user rather than resolving a
     * (possibly different) one. Requires the user to already be signed in, which this screen
     * only renders for. */
    override fun onConnectGoogleAccountClick(context: Context) {
        if (_uiState.value.googleAccountLinkBusy) return
        set { it.copy(googleAccountLinkBusy = true, googleAccountLinkError = false) }

        viewModelScope.launch {
            val clientId = SecurityBuildConfig.GOOGLE_WEB_CLIENT_ID
            if (clientId.isBlank()) {
                set { it.copy(googleAccountLinkBusy = false, googleAccountLinkError = true) }
                return@launch
            }

            GoogleIdentitySignIn.getIdToken(context, clientId).fold(
                onSuccess = { idToken ->
                    when (auth.linkGoogleIdentity(idToken)) {
                        is AppResult.Success -> set {
                            it.copy(googleAccountLinkBusy = false, googleAccountLinked = true)
                        }
                        is AppResult.Failure -> set {
                            it.copy(googleAccountLinkBusy = false, googleAccountLinkError = true)
                        }
                    }
                },
                onFailure = { error ->
                    // A dismissed account picker is silent, same as the login screen's handling
                    // — it's not a failure worth surfacing.
                    val cancelled = error::class.simpleName == "GetCredentialCancellationException"
                    set { it.copy(googleAccountLinkBusy = false, googleAccountLinkError = !cancelled) }
                },
            )
        }
    }

    override fun onConnectGoogleCalendarClick(context: Context) {
        if (_uiState.value.googleCalendarBusy) return
        set { it.copy(googleCalendarBusy = true, googleCalendarError = null) }
        viewModelScope.launch {
            applyGoogleCalendarResult(googleCalendarAuth.connect(context))
        }
    }

    override fun onGoogleCalendarResolutionResult(resultCode: Int, data: Intent?) {
        set { it.copy(googleCalendarBusy = true, googleCalendarError = null) }
        viewModelScope.launch {
            applyGoogleCalendarResult(googleCalendarAuth.completeResolution(resultCode, data))
        }
    }

    private suspend fun applyGoogleCalendarResult(result: GoogleCalendarConnectResult) {
        when (result) {
            is GoogleCalendarConnectResult.Connected -> {
                set {
                    it.copy(
                        googleCalendarBusy = false,
                        googleCalendarConnected = true,
                        googleCalendarAccountEmail = googleCalendarAuth.connectedAccountEmail(),
                    )
                }
                onOpenCalendarPickerClick()
            }
            is GoogleCalendarConnectResult.ResolutionRequired -> {
                set { it.copy(googleCalendarBusy = false) }
                _effects.trySend(SettingsEffect.LaunchGoogleCalendarResolution(result.intentSender))
            }
            is GoogleCalendarConnectResult.Failed -> {
                set { it.copy(googleCalendarBusy = false, googleCalendarError = R.string.settings_google_calendar_error_connect) }
            }
        }
    }

    override fun onOpenCalendarPickerClick() {
        set { it.copy(calendarPickerVisible = true, googleCalendarBusy = true, googleCalendarError = null) }
        viewModelScope.launch {
            googleCalendarSync.fetchCalendarList()
                .onSuccess { calendars ->
                    set {
                        it.copy(
                            googleCalendarBusy = false,
                            availableGoogleCalendars = calendars.map { entry ->
                                GoogleCalendarOption(
                                    id = entry.id,
                                    summary = entry.summary,
                                    selected = entry.id in selectedCalendarIds,
                                )
                            },
                        )
                    }
                }
                .onFailure { error ->
                    set { it.copy(googleCalendarBusy = false, googleCalendarError = R.string.settings_google_calendar_error_list) }
                }
        }
    }

    override fun onDismissCalendarPicker() = set { it.copy(calendarPickerVisible = false) }

    override fun onToggleCalendarSelection(calendarId: String) {
        val newSelection = if (calendarId in selectedCalendarIds) {
            selectedCalendarIds - calendarId
        } else {
            selectedCalendarIds + calendarId
        }
        selectedCalendarIds = newSelection
        set {
            it.copy(
                availableGoogleCalendars = it.availableGoogleCalendars.map { option ->
                    if (option.id == calendarId) option.copy(selected = !option.selected) else option
                },
            )
        }
        viewModelScope.launch {
            preferences.setSelectedGoogleCalendarIds(newSelection)
            googleCalendarSync.refresh(newSelection.toList())
        }
    }

    override fun onDisconnectGoogleCalendarClick() {
        googleCalendarAuth.disconnect()
        selectedCalendarIds = emptySet()
        set {
            it.copy(
                googleCalendarConnected = false,
                googleCalendarAccountEmail = null,
                availableGoogleCalendars = emptyList(),
                calendarPickerVisible = false,
            )
        }
        viewModelScope.launch {
            preferences.setSelectedGoogleCalendarIds(emptySet())
            googleCalendarSync.refresh(emptyList())
        }
    }

    /**
     * Additive and re-runnable — see [WebImporter]. Local writes only, but a big export is a few
     * hundred encrypted rows, so the button still shows it is working.
     */
    override fun onImportJson(json: String) {
        val workspace = workspaceId() ?: return
        if (_uiState.value.importing) return
        set { it.copy(importing = true) }

        viewModelScope.launch {
            val outcome = webImporter.import(workspace, auth.currentUserId().orEmpty(), json)
            set { it.copy(importing = false, importResult = outcome) }
        }
    }

    override fun onDismissImportResult() = set { it.copy(importResult = null) }

    override fun onSetActiveChild(babyId: String) {
        val workspace = workspaceId() ?: return
        viewModelScope.launch {
            babies.setActive(workspace, babyId)
            // Switching child changes whose feeds the reminder is counting from.
            feedingReminders.refresh()
        }
    }

    override fun onAddChildClick() = set { it.copy(addChildVisible = true) }
    override fun onDismissAddChild() = set { it.copy(addChildVisible = false) }

    override fun onAddChild(name: String, dueDate: LocalDate?, makeActive: Boolean) {
        val workspace = workspaceId() ?: return
        viewModelScope.launch {
            babies.create(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                name = name.ifBlank { null },
                dueDate = dueDate,
                makeActive = makeActive,
            )
            if (makeActive) feedingReminders.refresh()
            set { it.copy(addChildVisible = false) }
        }
    }

    override fun onEditChildClick(baby: Baby) = set { it.copy(editingChild = baby) }
    override fun onDismissEditChild() = set { it.copy(editingChild = null) }

    override fun onUpdateChildBirthDetails(
        babyId: String,
        name: String,
        dueDate: LocalDate?,
        birthDate: LocalDate?,
        birthTime: LocalTime?,
        birthWeightGrams: Int?,
        birthPlace: String?,
    ) {
        viewModelScope.launch {
            babies.update(
                id = babyId,
                name = name.ifBlank { null },
                dueDate = dueDate,
                birthDate = birthDate,
                birthTime = birthTime,
                birthWeightGrams = birthWeightGrams,
                birthPlace = birthPlace,
            )
            set { it.copy(editingChild = null) }
        }
    }

    /** Changing the cadence invalidates the pending reminder, so it is re-derived right after. */
    override fun onFeedIntervalChange(minutes: Int) {
        val workspace = workspaceId() ?: return
        viewModelScope.launch {
            val current = appSettings.observe(workspace).first() ?: return@launch
            appSettings.save(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                dueDate = current.dueDate,
                babyName = current.babyName,
                partnerOneName = current.partnerOneName,
                partnerTwoName = current.partnerTwoName,
                feedIntervalMinutes = minutes,
            )
            feedingReminders.refresh()
        }
    }

    /** Same as [onFeedIntervalChange], against the pumping schedule's own pending reminder. */
    override fun onPumpIntervalChange(minutes: Int) {
        val workspace = workspaceId() ?: return
        viewModelScope.launch {
            val current = appSettings.observe(workspace).first() ?: return@launch
            appSettings.save(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                dueDate = current.dueDate,
                babyName = current.babyName,
                partnerOneName = current.partnerOneName,
                partnerTwoName = current.partnerTwoName,
                pumpIntervalMinutes = minutes,
            )
            pumpReminders.refresh()
        }
    }

    private fun set(block: (SettingsUiState) -> SettingsUiState) {
        _uiState.value = block(_uiState.value)
    }

    private data class Preferences(
        val biometric: Boolean,
        val autoLock: Int,
        val screenshots: Boolean,
        val notifications: Boolean,
    )
}
