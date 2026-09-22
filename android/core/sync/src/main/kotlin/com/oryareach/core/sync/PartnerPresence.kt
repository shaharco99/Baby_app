package com.oryareach.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the partner has their phone open right now.
 *
 * Lives here, beside [PartnerWakeUp], for the same reason: features need to read it, `:app`
 * drives it, and neither should have to know that it is Supabase underneath. The network side
 * writes the heartbeat and reports what it read; everything else just collects [isPartnerHere].
 *
 * This is presence, not activity. The Book of Love used to guess at it from "edited a task in
 * the last five minutes", which is a different question and answered yes long after someone had
 * put the phone down.
 */
interface PartnerPresence {

    val isPartnerHere: StateFlow<Boolean>

    /** Says this device is here, then reads back whether anyone else is. Safe to call on a timer. */
    suspend fun heartbeat()

    /** Called when the app leaves the foreground, so a closed phone stops reading as present. */
    suspend fun goodbye()

    /** The seam's off switch: a build without a server behaves as it did before presence existed. */
    object None : PartnerPresence {
        private val state = MutableStateFlow(false)
        override val isPartnerHere: StateFlow<Boolean> = state.asStateFlow()
        override suspend fun heartbeat() = Unit
        override suspend fun goodbye() = Unit
    }
}

/**
 * How long a heartbeat counts for.
 *
 * Comfortably more than the 30s between beats, so one dropped request does not blink the dot
 * off, and short enough that a phone put down goes quiet within about a minute.
 */
const val PRESENCE_WINDOW_MILLIS = 90_000L
