package com.oryareach.core.sync

/**
 * Asks the server to wake the workspace's other devices.
 *
 * Called after this device has pushed something, so the partner's phone syncs and re-derives
 * its own reminders instead of ringing at a time this device has already moved. It carries no
 * content — only "this workspace changed" — because the server cannot read what changed and is
 * not meant to be able to.
 *
 * An interface with a do-nothing default so `:core:sync` stays free of Supabase and Firebase,
 * and so a build with push unconfigured behaves exactly as this one did before: everything
 * still syncs, the other phone just finds out on its next poll.
 */
fun interface PartnerWakeUp {
    suspend fun wakePartners()

    companion object {
        val None = PartnerWakeUp { }
    }
}
