package com.oryareach.core.network.presence

import com.oryareach.core.sync.PRESENCE_WINDOW_MILLIS
import com.oryareach.core.sync.PartnerPresence
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The heartbeat, and the answer it comes back with.
 *
 * One beat does both jobs: the upsert says this device is here, and the read that follows asks
 * who else in the workspace has said so lately. Two small requests on a 30-second timer, over
 * the connection the foreground poll already uses.
 *
 * Failure is swallowed, like the wake-up's: presence is an ornament on a sync that works
 * without it, and an offline moment should read as "no change" rather than break anything. The
 * flow keeps its last value until the next beat lands.
 */
class SupabasePartnerPresence(
    private val client: SupabaseClient,
    private val workspaceId: () -> String?,
    private val deviceId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) : PartnerPresence {

    private val state = MutableStateFlow(false)
    override val isPartnerHere: StateFlow<Boolean> = state.asStateFlow()

    @Serializable
    private data class PresenceRow(
        @SerialName("device_id") val deviceId: String,
        @SerialName("workspace_id") val workspaceId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("last_seen_at") val lastSeenAt: String,
    )

    @Serializable
    private data class SeenRow(
        @SerialName("user_id") val userId: String,
        @SerialName("last_seen_at") val lastSeenAt: String,
    )

    override suspend fun heartbeat() {
        val workspace = workspaceId() ?: return
        val device = deviceId() ?: return
        val userId = client.auth.currentUserOrNull()?.id ?: return

        try {
            client.postgrest.from(TABLE).upsert(
                PresenceRow(
                    deviceId = device,
                    workspaceId = workspace,
                    userId = userId,
                    // Sent rather than left to the column default: an upsert onto an existing
                    // row would otherwise keep the timestamp from the first insert.
                    lastSeenAt = Instant.fromEpochMilliseconds(now()).toString(),
                ),
            )

            val rows = client.postgrest.from(TABLE)
                .select {
                    filter {
                        eq("workspace_id", workspace)
                        neq("user_id", userId)
                    }
                }
                .decodeList<SeenRow>()

            // Somebody else, not this account's own second device: a phone and a tablet of the
            // same person being open is not the partner being here.
            state.value = rows.any { row ->
                val seen = runCatching { Instant.parse(row.lastSeenAt).toEpochMilliseconds() }.getOrNull()
                seen != null && now() - seen <= PRESENCE_WINDOW_MILLIS
            }
        } catch (_: Exception) {
            // Deliberately quiet: see the class comment.
        }
    }

    override suspend fun goodbye() {
        val device = deviceId() ?: return
        state.value = false
        try {
            client.postgrest.from(TABLE).delete {
                filter { eq("device_id", device) }
            }
        } catch (_: Exception) {
            // Same: a goodbye that does not land only means the row goes stale on its own.
        }
    }

    private companion object {
        const val TABLE = "device_presence"
    }
}
