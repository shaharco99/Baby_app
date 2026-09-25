package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.DiaperChangeEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/** Scoped to the workspace and the child, like feeds. */
@Dao
interface DiaperChangeDao {

    @Query(
        """
        SELECT * FROM diaper_changes
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
            AND changed_at BETWEEN :start AND :end
        ORDER BY changed_at DESC
        """,
    )
    fun observeInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): Flow<List<DiaperChangeEntity>>

    @Query("SELECT * FROM diaper_changes WHERE id = :id")
    suspend fun findById(id: String): DiaperChangeEntity?

    @Upsert
    suspend fun upsert(change: DiaperChangeEntity)

    @Query("SELECT * FROM diaper_changes WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<DiaperChangeEntity>

    @Query("UPDATE diaper_changes SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE diaper_changes
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM diaper_changes WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM diaper_changes WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
