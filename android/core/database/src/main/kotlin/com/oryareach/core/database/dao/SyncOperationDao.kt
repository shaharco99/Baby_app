package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.model.SyncOperationType

@Dao
interface SyncOperationDao {

    @Insert
    suspend fun enqueue(operation: SyncOperationEntity): Long

    /** FIFO: operations on the same record must reach the server in the order they happened. */
    @Query("SELECT * FROM sync_operations ORDER BY created_at ASC, id ASC LIMIT :limit")
    suspend fun peek(limit: Int): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM sync_operations WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("UPDATE sync_operations SET attempts = attempts + 1, last_error = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String?)

    /**
     * Superseded operations for a record are dropped when a newer one is enqueued, so an
     * offline burst of edits uploads once rather than replaying every intermediate state.
     */
    @Query("DELETE FROM sync_operations WHERE record_id = :recordId AND id < :beforeId")
    suspend fun removeSuperseded(recordId: String, beforeId: Long)

    @Query("DELETE FROM sync_operations WHERE record_id = :recordId")
    suspend fun removeByRecord(recordId: String)

    @Query(
        """
        UPDATE sync_operations
        SET attempts = attempts + 1, last_error = :error
        WHERE record_id = :recordId
        """,
    )
    suspend fun recordFailureByRecord(recordId: String, error: String?)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_operations WHERE record_id = :recordId)")
    suspend fun hasPending(recordId: String): Boolean

    /**
     * What is queued for this record. Used to tell a queued deletion apart from a queued edit:
     * the two look identical from the row itself, which still holds its content either way.
     */
    @Query("SELECT operation FROM sync_operations WHERE record_id = :recordId")
    suspend fun pendingOperations(recordId: String): List<SyncOperationType>
}
