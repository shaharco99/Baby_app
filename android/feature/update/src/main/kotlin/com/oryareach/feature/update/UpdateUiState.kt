package com.oryareach.feature.update

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.oryareach.core.update.ReleaseManifest

@Immutable
data class UpdateUiState(
    // Persisted snapshot: what the last check found.
    val availableManifest: ReleaseManifest? = null,
    val mandatory: Boolean = false,

    // Transient UI-only.
    val checking: Boolean = false,
    // Outcome of a check the user asked for ("latest version" / "couldn't check"); the automatic
    // startup check stays silent. Null while checking, or when a newer version opened the dialog.
    @StringRes val manualCheckResult: Int? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloading: Boolean = false,
    val installing: Boolean = false,
    // A string resource, never the exception's own text: that was English whatever the
    // language, and read like "NetworkError(cause=…)" rather than what to do next.
    @StringRes val errorMessage: Int? = null,
) {
    val visible: Boolean get() = availableManifest != null
    val downloadFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

sealed interface UpdateEffect {
    data class OpenRelease(val url: String) : UpdateEffect
    data class LaunchInstallConfirmation(val intent: android.content.Intent) : UpdateEffect
}
