package com.oryareach.feature.pairing

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * Where this device is in getting a workspace and the key to read it.
 *
 * These are genuinely different screens rather than flags on one screen, because the device
 * can only be in one of them and each has its own irreversible step.
 */
@Immutable
sealed interface PairingStage {
    /** Reading stored state at launch. */
    data object Loading : PairingStage

    /** Signed in, no workspace yet. */
    data object Choose : PairingStage

    /**
     * A new workspace was created and its key generated. The phrase is shown exactly once,
     * and is the only way back in if both phones are lost.
     */
    data class ShowRecoveryPhrase(val words: List<String>) : PairingStage

    /** Entering the code the partner read out. */
    data object EnterCode : PairingStage

    /** Joined the workspace, but the partner has not yet released the key to this device. */
    data object AwaitingKey : PairingStage

    /**
     * Typing back the 24-word phrase to recover the key directly, without waiting for a
     * partner device to approve this one — the only path back in when there is no other
     * device left to ask (see `docs/architecture/007-encryption.md`). Reachable from [Choose]
     * (signed in, never joined a workspace on this device) and from [AwaitingKey] (joined,
     * but waiting is slower than just recovering directly).
     */
    data object EnterRecoveryPhrase : PairingStage

    /** This device holds the key and can show an invitation for the partner. */
    data class Ready(
        val inviteCode: String? = null,
        val pendingDevices: List<PendingDevice> = emptyList(),
        val revocableDevices: List<PairedDevice> = emptyList(),
    ) : PairingStage
}

@Immutable
data class PendingDevice(
    val deviceKeyId: String,
    val label: String,
)

/** A device other than this one that already holds the key, and so can be revoked. */
@Immutable
data class PairedDevice(
    val deviceKeyId: String,
    val label: String,
)

@Immutable
data class PairingUiState(
    val stage: PairingStage = PairingStage.Loading,
    val enteredCode: String = "",
    val phraseConfirmed: Boolean = false,
    val recoveryPhraseInput: String = "",
    val busy: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
    val canSubmitCode: Boolean get() = enteredCode.length == CODE_LENGTH && !busy

    companion object {
        const val CODE_LENGTH = 20
    }
}

/** Whether [PairingScreen] should offer a way to sign out and try a different account from
 * this stage — every stage before the workspace is actually opened, where getting stuck on the
 * wrong account with no way back would otherwise strand the user (see [PairingActions.onSignOut]
 * doc comment). Excludes [PairingStage.Loading] (nothing to back out of yet) and
 * [PairingStage.ShowRecoveryPhrase]/[PairingStage.Ready] (the workspace is already this
 * device's; Settings' own sign-out, reachable once inside the app, is one tap away for the
 * `Ready` case, and backing out mid-`ShowRecoveryPhrase` would abandon a just-created workspace
 * without the one chance to write its phrase down). */
val PairingStage.allowsSignOut: Boolean
    get() = this is PairingStage.Choose ||
        this is PairingStage.EnterCode ||
        this is PairingStage.AwaitingKey ||
        this is PairingStage.EnterRecoveryPhrase

sealed interface PairingEffect {
    /** The device now has a workspace and a key: the app proper can open. */
    data object Completed : PairingEffect
}
