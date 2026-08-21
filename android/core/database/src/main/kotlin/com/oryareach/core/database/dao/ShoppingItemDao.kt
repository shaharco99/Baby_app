package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.ShoppingItemEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {

    @Query(
        """
        SELECT * FROM shopping_items
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY created_at ASC
        """,
    )
    fun observeAll(workspaceId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun findById(id: String): ShoppingItemEntity?

    /** Recency proxy for "is the partner around right now" — no live presence channel exists. */
    @Query(
        """
        SELECT MAX(updated_at) FROM shopping_items
        WHERE workspace_id = :workspaceId AND created_by != :selfUserId AND deleted_at IS NULL
        """,
    )
    suspend fun lastActivityByOthers(workspaceId: String, selfUserId: String): Long?

    @Upsert
    suspend fun upsert(item: ShoppingItemEntity)

    @Query("SELECT * FROM shopping_items WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<ShoppingItemEntity>

    @Query("UPDATE shopping_items SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE shopping_items
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM shopping_items WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
