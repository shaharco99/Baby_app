package com.oryareach.core.network.push

import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import com.oryareach.core.sync.PartnerWakeUp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * This device's FCM registration, and the request that wakes the other one.
 *
 * Both halves of the same idea, so they stay in one place: a device that has not registered
 * cannot be woken, and waking is pointless if nothing registered.
 */
interface PushTokenRepository {

    /**
     * Records this device's current FCM token against the workspace. Safe to call on every
     * launch — it is an upsert keyed by the device, not an append.
     */
    suspend fun register(workspaceId: String, deviceId: String, token: String): AppResult<Unit>

    /** Drops this device's registration, so a signed-out phone stops being woken. */
    suspend fun unregister(deviceId: String): AppResult<Unit>
}

class SupabasePushTokenRepository(
    private val client: SupabaseClient,
) : PushTokenRepository {

    @Serializable
    private data class TokenRow(
        @SerialName("device_id") val deviceId: String,
        @SerialName("workspace_id") val workspaceId: String,
        @SerialName("user_id") val userId: String,
        val token: String,
    )

    override suspend fun register(
        workspaceId: String,
        deviceId: String,
        token: String,
    ): AppResult<Unit> = attempt {
        val userId = client.auth.currentUserOrNull()?.id ?: error("not signed in")
        client.postgrest.from("device_push_tokens").upsert(
            TokenRow(
                deviceId = deviceId,
                workspaceId = workspaceId,
                userId = userId,
                token = token,
            ),
        )
    }

    override suspend fun unregister(deviceId: String): AppResult<Unit> = attempt {
        client.postgrest.from("device_push_tokens").delete {
            filter { eq("device_id", deviceId) }
        }
    }

    private inline fun <T> attempt(block: () -> T): AppResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unexpected(e.message ?: e::class.simpleName.orEmpty()))
    }
}

/**
 * Asks `notify-workspace` (supabase/functions) to wake the workspace's other devices.
 *
 * Failure is swallowed on purpose. A wake-up is an optimisation over the poll that was already
 * there — if it does not go through, the partner's phone finds out on its next foreground poll,
 * exactly as it did before push existed. Failing the sync over it would turn a working sync
 * into a broken one.
 */
class SupabasePartnerWakeUp(
    private val client: SupabaseClient,
    private val workspaceId: () -> String?,
    private val deviceId: () -> String?,
) : PartnerWakeUp {

    override suspend fun wakePartners() {
        val workspace = workspaceId() ?: return
        try {
            client.functions.invoke(FUNCTION) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("workspaceId", JsonPrimitive(workspace))
                        deviceId()?.let { put("deviceId", JsonPrimitive(it)) }
                    },
                )
            }
        } catch (_: Exception) {
            // Deliberately quiet: see the class comment.
        }
    }

    private companion object {
        const val FUNCTION = "notify-workspace"
    }
}
