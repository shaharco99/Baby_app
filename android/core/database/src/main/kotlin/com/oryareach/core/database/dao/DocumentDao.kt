package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.DocumentEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    /** `folderId = null` (root) needs its own query: SQL `= NULL` never matches. */
    @Query(
        """
        SELECT * FROM documents
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND folder_id IS :folderId
        ORDER BY name ASC
        """,
    )
    fun observeInFolder(workspaceId: String, folderId: String?): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND task_id = :taskId
        ORDER BY name ASC
        """,
    )
    fun observeForTask(workspaceId: String, taskId: String): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND cycle_id = :cycleId
        ORDER BY name ASC
        """,
    )
    fun observeForCycle(workspaceId: String, cycleId: String): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND shopping_item_id = :shoppingItemId
        ORDER BY name ASC
        """,
    )
    fun observeForShoppingItem(workspaceId: String, shoppingItemId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun findById(id: String): DocumentEntity?

    @Upsert
    suspend fun upsert(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<DocumentEntity>

    @Query("UPDATE documents SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE documents
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM documents WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
