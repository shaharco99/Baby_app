package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.BabyEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {

    /** The switcher list — newest first, so the child currently being cared for leads. */
    @Query(
        """
        SELECT * FROM babies
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY created_at DESC
        """,
    )
    fun observeAll(workspaceId: String): Flow<List<BabyEntity>>

    /** The child `app_settings.active_baby_id` points at, or null before the seed has run. */
    @Query(
        """
        SELECT b.* FROM babies b
        INNER JOIN app_settings s
            ON s.active_baby_id = b.id AND s.workspace_id = b.workspace_id
        WHERE b.workspace_id = :workspaceId AND b.deleted_at IS NULL AND s.deleted_at IS NULL
        LIMIT 1
        """,
    )
    fun observeActive(workspaceId: String): Flow<BabyEntity?>

    @Query(
        """
        SELECT * FROM babies
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY created_at DESC
        """,
    )
    suspend fun findAll(workspaceId: String): List<BabyEntity>

    @Query("SELECT * FROM babies WHERE id = :id")
    suspend fun findById(id: String): BabyEntity?

    @Upsert
    suspend fun upsert(baby: BabyEntity)

    @Query("SELECT * FROM babies WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<BabyEntity>

    @Query("UPDATE babies SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE babies
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM babies WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM babies WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
