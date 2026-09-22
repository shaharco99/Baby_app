package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.VitaminDoseEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Reads are scoped to the workspace *and* the child, like feeds: a second child gets its own
 * doses and its own reminder.
 *
 * "Was today's dose given" is asked as a range over the local day, computed by the caller — the
 * database stores instants and has no opinion about which time zone the phone is in.
 */
@Dao
interface VitaminDoseDao {

    @Query(
        """
        SELECT * FROM vitamin_doses
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
            AND given_at BETWEEN :start AND :end
        ORDER BY given_at DESC
        """,
    )
    fun observeInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): Flow<List<VitaminDoseEntity>>

    @Query(
        """
        SELECT * FROM vitamin_doses
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
            AND given_at BETWEEN :start AND :end
        ORDER BY given_at DESC
        LIMIT 1
        """,
    )
    suspend fun findInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): VitaminDoseEntity?

    @Query("SELECT * FROM vitamin_doses WHERE id = :id")
    suspend fun findById(id: String): VitaminDoseEntity?

    @Upsert
    suspend fun upsert(dose: VitaminDoseEntity)

    @Query("SELECT * FROM vitamin_doses WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<VitaminDoseEntity>

    @Query("UPDATE vitamin_doses SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE vitamin_doses
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM vitamin_doses WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM vitamin_doses WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?
}
