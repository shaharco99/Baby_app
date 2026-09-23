package com.oryareach.feature.update

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.common.AppResult
import com.oryareach.core.update.DownloadProgress
import com.oryareach.core.update.InstallOutcome
import com.oryareach.core.update.ReleaseChecker
import com.oryareach.core.update.UpdateAvailability
import com.oryareach.core.update.UpdateDownloader
import com.oryareach.core.update.UpdateInstaller
import com.oryareach.core.update.UpdateState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Stable
interface UpdateActions {
    fun onCheckNow()
    fun onInstall()
    fun onViewRelease()
    fun onLater()
    fun onSkip()
}

/**
 * A mandatory update ([UpdateUiState.mandatory]) ignores "later"/"skip": the screen hosting
 * this stays blocking until the install completes, per the walking-skeleton plan's
 * mandatory-update requirement.
 */
class UpdateViewModel(
    private val checker: ReleaseChecker,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
    private val state: UpdateState,
) : ViewModel(), UpdateActions {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UpdateEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        check(manual = false)
    }

    override fun onCheckNow() = check(manual = true)

    override fun onInstall() {
        val manifest = _uiState.value.availableManifest ?: return
        if (_uiState.value.downloading || _uiState.value.installing) return

        viewModelScope.launch {
            set { it.copy(downloading = true, errorMessage = null) }
            val progressJob = launch {
                downloader.progress.collect { progress ->
                    if (progress is DownloadProgress.InProgress) {
                        set { it.copy(downloadedBytes = progress.bytesDownloaded, totalBytes = progress.totalBytes) }
                    }
                }
            }

            val downloadResult = downloader.download(manifest)
            progressJob.cancel()

            when (val result = downloadResult) {
                is AppResult.Failure -> set {
                    it.copy(downloading = false, errorMessage = R.string.update_failed_download)
                }
                is AppResult.Success -> {
                    set { it.copy(downloading = false, installing = true) }
                    installer.install(result.data).collect { outcome ->
                        when (outcome) {
                            InstallOutcome.Success -> set { it.copy(installing = false, availableManifest = null) }
                            is InstallOutcome.PendingUserAction ->
                                _effects.trySend(UpdateEffect.LaunchInstallConfirmation(outcome.intent))
                            is InstallOutcome.Failed -> set {
                                it.copy(installing = false, errorMessage = R.string.update_failed_install)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onViewRelease() {
        _uiState.value.availableManifest?.let { _effects.trySend(UpdateEffect.OpenRelease(it.releaseUrl)) }
    }

    override fun onLater() {
        if (_uiState.value.mandatory) return
        set { it.copy(availableManifest = null) }
    }

    override fun onSkip() {
        if (_uiState.value.mandatory) return
        val version = _uiState.value.availableManifest?.version ?: return
        viewModelScope.launch { state.skip(version) }
        set { it.copy(availableManifest = null) }
    }

    private fun check(manual: Boolean) {
        set { it.copy(checking = true, manualCheckResult = null) }
        viewModelScope.launch {
            state.recordCheck(System.currentTimeMillis())

            when (val result = checker.check()) {
                is AppResult.Failure -> set {
                    it.copy(checking = false, manualCheckResult = if (manual) R.string.update_check_failed else null)
                }
                is AppResult.Success -> when (val availability = result.data) {
                    UpdateAvailability.UpToDate -> set {
                        it.copy(checking = false, manualCheckResult = if (manual) R.string.settings_up_to_date else null)
                    }

                    is UpdateAvailability.Available -> {
                        val skipped = !manual && state.isSkipped(availability.manifest.version)
                        set {
                            it.copy(
                                checking = false,
                                availableManifest = if (skipped) null else availability.manifest,
                                mandatory = false,
                            )
                        }
                        if (!skipped) state.recordNotified(availability.manifest.version)
                    }

                    is UpdateAvailability.Mandatory -> {
                        set { it.copy(checking = false, availableManifest = availability.manifest, mandatory = true) }
                        state.recordNotified(availability.manifest.version)
                    }
                }
            }
        }
    }

    private fun set(block: (UpdateUiState) -> UpdateUiState) {
        _uiState.value = block(_uiState.value)
    }
}
