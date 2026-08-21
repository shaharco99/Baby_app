package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.TaskEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * Tombstoned rows are excluded everywhere in the UI queries; they exist only so the
     * delete can be pushed to the server and then to the other device.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY done ASC, created_at ASC
        """,
    )
    fun observeAll(workspaceId: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND category = :category
        ORDER BY done ASC, created_at ASC
        """,
    )
    fun observeByCategory(workspaceId: String, category: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: String): TaskEntity?

    /** Recency proxy for "is the partner around right now" — no live presence channel exists. */
    @Query(
        """
        SELECT MAX(updated_at) FROM tasks
        WHERE workspace_id = :workspaceId AND created_by != :selfUserId AND deleted_at IS NULL
        """,
    )
    suspend fun lastActivityByOthers(workspaceId: String, selfUserId: String): Long?

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND done = 0
        """,
    )
    fun observeOpenCount(workspaceId: String): Flow<Int>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<TaskEntity>

    @Query("UPDATE tasks SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    /** Soft delete, so the removal can propagate rather than silently reappearing on pull. */
    @Query(
        """
        UPDATE tasks
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    /** Only used once the server has confirmed the tombstone. */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM tasks WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
