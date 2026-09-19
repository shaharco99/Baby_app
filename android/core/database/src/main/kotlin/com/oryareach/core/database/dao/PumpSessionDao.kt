package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.PumpSessionEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Reads are scoped to the workspace, not to a child: pumping is the mother's record.
 *
 * The running session is found by `ended_at IS NULL` rather than tracked separately — see
 * [PumpSessionEntity].
 */
@Dao
interface PumpSessionDao {

    @Query(
        """
        SELECT * FROM pump_sessions
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
            AND started_at BETWEEN :start AND :end
        ORDER BY started_at DESC
        """,
    )
    fun observeInRange(workspaceId: String, start: Long, end: Long): Flow<List<PumpSessionEntity>>

    /** Drives the countdown to the next pump. Includes a running session — it is the latest one. */
    @Query(
        """
        SELECT * FROM pump_sessions
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY started_at DESC
        LIMIT 1
        """,
    )
    fun observeLatest(workspaceId: String): Flow<PumpSessionEntity?>

    @Query(
        """
        SELECT * FROM pump_sessions
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY started_at DESC
        LIMIT 1
        """,
    )
    suspend fun findLatest(workspaceId: String): PumpSessionEntity?

    /** Drives the on-screen timer: null means nothing is running right now. */
    @Query(
        """
        SELECT * FROM pump_sessions
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND ended_at IS NULL
        ORDER BY started_at DESC
        LIMIT 1
        """,
    )
    fun observeRunning(workspaceId: String): Flow<PumpSessionEntity?>

    @Query(
        """
        SELECT * FROM pump_sessions
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND ended_at IS NULL
        ORDER BY started_at DESC
        LIMIT 1
        """,
    )
    suspend fun findRunning(workspaceId: String): PumpSessionEntity?

    @Query("SELECT * FROM pump_sessions WHERE id = :id")
    suspend fun findById(id: String): PumpSessionEntity?

    @Upsert
    suspend fun upsert(session: PumpSessionEntity)

    @Query("SELECT * FROM pump_sessions WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<PumpSessionEntity>

    @Query("UPDATE pump_sessions SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE pump_sessions
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM pump_sessions WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM pump_sessions WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
