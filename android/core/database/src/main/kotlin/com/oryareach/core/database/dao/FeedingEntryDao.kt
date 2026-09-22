package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oryareach.core.database.entity.FeedingEntryEntity
import com.oryareach.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Every read is scoped to one child: the one being *viewed*, which is the active child unless
 * the switcher is pointed at an older sibling's history.
 */
@Dao
interface FeedingEntryDao {

    @Query(
        """
        SELECT * FROM feeding_entries
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
            AND fed_at BETWEEN :start AND :end
        ORDER BY fed_at DESC
        """,
    )
    fun observeInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): Flow<List<FeedingEntryEntity>>

    /** Drives the countdown to the next feed. */
    @Query(
        """
        SELECT * FROM feeding_entries
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
        ORDER BY fed_at DESC
        LIMIT 1
        """,
    )
    fun observeLatest(workspaceId: String, babyId: String): Flow<FeedingEntryEntity?>

    @Query(
        """
        SELECT * FROM feeding_entries
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
        ORDER BY fed_at DESC
        LIMIT 1
        """,
    )
    suspend fun findLatest(workspaceId: String, babyId: String): FeedingEntryEntity?

    @Query("SELECT * FROM feeding_entries WHERE id = :id")
    suspend fun findById(id: String): FeedingEntryEntity?

    @Upsert
    suspend fun upsert(entry: FeedingEntryEntity)

    @Query("SELECT * FROM feeding_entries WHERE sync_status != :synced")
    suspend fun pendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<FeedingEntryEntity>

    @Query("UPDATE feeding_entries SET sync_status = :status, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, status: SyncStatus, version: Int)

    @Query(
        """
        UPDATE feeding_entries
        SET deleted_at = :deletedAt, sync_status = :status, updated_at = :deletedAt
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
        status: SyncStatus = SyncStatus.PENDING_DELETE,
    )

    @Query("DELETE FROM feeding_entries WHERE id = :id")
    suspend fun purge(id: String)

    @Query("SELECT MAX(updated_at) FROM feeding_entries WHERE workspace_id = :workspaceId")
    suspend fun latestUpdatedAt(workspaceId: String): Long?

    /**
     * How many feeds each person has logged for this child.
     *
     * Grouped in SQL rather than counted in memory because the caller wants the whole log, not
     * the fortnight on screen, and there is no reason to carry every row up for a count.
     */
    @Query(
        """
        SELECT created_by AS createdBy, COUNT(*) AS count FROM feeding_entries
        WHERE workspace_id = :workspaceId AND baby_id = :babyId AND deleted_at IS NULL
        GROUP BY created_by
        """,
    )
    suspend fun countByCreator(workspaceId: String, babyId: String): List<CreatorCount>
}

/** One person's share of a log. [createdBy] is the auth user id that wrote the rows. */
data class CreatorCount(val createdBy: String, val count: Int)
