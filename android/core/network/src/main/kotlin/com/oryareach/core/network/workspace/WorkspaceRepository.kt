package com.oryareach.core.network.workspace

import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A device that has published its public key and may be waiting for the workspace key. */
data class PartnerDevice(
    val deviceKeyId: String,
    val userId: String,
    val publicKey: ByteArray,
    val label: String?,
    val hasWrappedKey: Boolean,
    val isRevoked: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (
            other is PartnerDevice &&
                deviceKeyId == other.deviceKeyId &&
                userId == other.userId &&
                publicKey.contentEquals(other.publicKey) &&
                label == other.label &&
                hasWrappedKey == other.hasWrappedKey &&
                isRevoked == other.isRevoked
            )

    override fun hashCode(): Int {
        var result = deviceKeyId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + hasWrappedKey.hashCode()
        result = 31 * result + isRevoked.hashCode()
        return result
    }
}

interface WorkspaceRepository {
    suspend fun currentWorkspaceId(): AppResult<String?>
    suspend fun createWorkspace(): AppResult<String>
    suspend fun createInvitation(rawToken: String, expiresInHours: Int): AppResult<Unit>
    suspend fun acceptInvitation(rawToken: String): AppResult<String>
    suspend fun publishDeviceKey(workspaceId: String, publicKey: ByteArray, label: String): AppResult<String>
    suspend fun devices(workspaceId: String): AppResult<List<PartnerDevice>>
    suspend fun uploadWrappedKey(workspaceId: String, deviceKeyId: String, blob: ByteArray): AppResult<Unit>
    suspend fun wrappedKeyFor(deviceKeyId: String): AppResult<ByteArray?>

    /**
     * The emails of the workspace's other members — who a joiner is waiting on. Empty for a
     * non-member. Backed by `workspace_partner_emails` (migration 0010).
     */
    suspend fun partnerEmails(workspaceId: String): AppResult<List<String>>

    /**
     * Marks [deviceKeyId] as revoked. This stops it being offered for future key-wrap
     * grants (see PairingViewModel's pending-device filter) — it does not end that
     * device's ongoing Supabase Auth session or rotate the workspace key.
     */
    suspend fun revokeDevice(deviceKeyId: String): AppResult<Unit>
}

class SupabaseWorkspaceRepository(private val client: SupabaseClient) : WorkspaceRepository {

    @Serializable
    private data class MembershipRow(@SerialName("workspace_id") val workspaceId: String)

    @Serializable
    private data class DeviceKeyRow(
        val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("workspace_id") val workspaceId: String? = null,
        @SerialName("public_key") val publicKey: String,
        val label: String? = null,
        @SerialName("revoked_at") val revokedAt: String? = null,
    )

    @Serializable
    private data class WrappedKeyRow(
        @SerialName("device_key_id") val deviceKeyId: String,
        val blob: String,
    )

    override suspend fun currentWorkspaceId(): AppResult<String?> = attempt {
        client.postgrest.from("workspace_members")
            .select()
            .decodeList<MembershipRow>()
            .firstOrNull()
            ?.workspaceId
    }

    override suspend fun createWorkspace(): AppResult<String> = attempt {
        client.postgrest.rpc("create_workspace").data.trim('"')
    }

    override suspend fun createInvitation(rawToken: String, expiresInHours: Int): AppResult<Unit> =
        attempt {
            val userId = client.auth.currentUserOrNull()?.id
                ?: error("not signed in")
            val workspaceId = requireWorkspace()

            // Only the SHA-256 of the token is stored. The plaintext exists solely in the
            // inviter's hands, so a database dump yields no usable invitations.
            client.postgrest.from("couple_invitations").insert(
                buildJsonObject {
                    put("workspace_id", JsonPrimitive(workspaceId))
                    put("created_by", JsonPrimitive(userId))
                    put("token_hash", JsonPrimitive("\\x" + sha256Hex(rawToken)))
                    put("expires_at", JsonPrimitive(expiryTimestamp(expiresInHours)))
                },
            )
        }

    override suspend fun acceptInvitation(rawToken: String): AppResult<String> = attempt {
        client.postgrest
            .rpc("accept_invitation", buildJsonObject { put("raw_token", JsonPrimitive(rawToken)) })
            .data
            .trim('"')
    }

    override suspend fun publishDeviceKey(
        workspaceId: String,
        publicKey: ByteArray,
        label: String,
    ): AppResult<String> = attempt {
        val userId = client.auth.currentUserOrNull()?.id ?: error("not signed in")

        client.postgrest.from("device_keys").insert(
            buildJsonObject {
                put("user_id", JsonPrimitive(userId))
                put("workspace_id", JsonPrimitive(workspaceId))
                put("public_key", JsonPrimitive("\\x" + publicKey.toHex()))
                put("label", JsonPrimitive(label))
            },
        ) {
            select()
        }.decodeList<DeviceKeyRow>().first().id
    }

    override suspend fun devices(workspaceId: String): AppResult<List<PartnerDevice>> = attempt {
        val keys = client.postgrest.from("device_keys").select().decodeList<DeviceKeyRow>()
        val wrapped = client.postgrest.from("wrapped_workspace_keys")
            .select()
            .decodeList<WrappedKeyRow>()
            .map { it.deviceKeyId }
            .toSet()

        // RLS returns every device in every workspace this user belongs to; only this one's count.
        keys.filter { it.workspaceId == workspaceId }.map { row ->
            PartnerDevice(
                deviceKeyId = row.id,
                userId = row.userId,
                publicKey = row.publicKey.fromPostgresHex(),
                label = row.label,
                hasWrappedKey = row.id in wrapped,
                isRevoked = row.revokedAt != null,
            )
        }
    }

    override suspend fun partnerEmails(workspaceId: String): AppResult<List<String>> = attempt {
        client.postgrest
            .rpc("workspace_partner_emails", buildJsonObject { put("ws", JsonPrimitive(workspaceId)) })
            .decodeList<String>()
    }

    override suspend fun revokeDevice(deviceKeyId: String): AppResult<Unit> = attempt<Unit> {
        client.postgrest.rpc(
            "revoke_device_key",
            buildJsonObject { put("target_device_key_id", JsonPrimitive(deviceKeyId)) },
        )
    }

    override suspend fun uploadWrappedKey(
        workspaceId: String,
        deviceKeyId: String,
        blob: ByteArray,
    ): AppResult<Unit> = attempt {
        val userId = client.auth.currentUserOrNull()?.id ?: error("not signed in")

        client.postgrest.from("wrapped_workspace_keys").insert(
            buildJsonObject {
                put("workspace_id", JsonPrimitive(workspaceId))
                put("device_key_id", JsonPrimitive(deviceKeyId))
                put("blob", JsonPrimitive("\\x" + blob.toHex()))
                put("created_by", JsonPrimitive(userId))
            },
        )
    }

    override suspend fun wrappedKeyFor(deviceKeyId: String): AppResult<ByteArray?> = attempt {
        client.postgrest.from("wrapped_workspace_keys")
            .select()
            .decodeList<WrappedKeyRow>()
            .firstOrNull { it.deviceKeyId == deviceKeyId }
            ?.blob
            ?.fromPostgresHex()
    }

    private suspend fun requireWorkspace(): String =
        (currentWorkspaceId() as? AppResult.Success)?.data ?: error("no workspace")

    private inline fun <T> attempt(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: Exception) {
        AppResult.Failure(e.toWorkspaceError())
    }
}

internal fun Exception.toWorkspaceError(): AppError {
    val text = message.orEmpty().lowercase()
    return when {
        this is java.net.UnknownHostException -> AppError.Network.Offline
        this is java.net.SocketTimeoutException -> AppError.Network.Timeout
        "invalid invitation" in text -> AppError.Network.Server(status = 422)
        "already has" in text || "check_violation" in text -> AppError.Network.Server(status = 409)
        else -> AppError.Unexpected(message ?: this::class.simpleName.orEmpty())
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Postgres returns `bytea` over PostgREST as a `\x`-prefixed hex string. */
private fun String.fromPostgresHex(): ByteArray {
    val body = removePrefix("\\x")
    return ByteArray(body.length / 2) { i ->
        body.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

private fun sha256Hex(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(Charsets.UTF_8)).toHex()
}

private fun expiryTimestamp(hours: Int): String =
    java.time.Instant.now().plusSeconds(hours * 3600L).toString()
