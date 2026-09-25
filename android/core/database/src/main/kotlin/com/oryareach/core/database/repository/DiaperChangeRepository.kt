package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.DiaperChangeEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toDiaperChange
import com.oryareach.core.model.DiaperChange
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The write path for nappy changes logged on the nappy screen. Changes marked on a feed are
 * written by [FeedingEntryRepository] as part of the feed and never pass through here.
 */
class DiaperChangeRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val changes get() = database.diaperChangeDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): Flow<List<DiaperChange>> =
        changes.observeInRange(workspaceId, babyId, start, end)
            .map { list -> list.map { it.toDiaperChange() } }

    suspend fun logChange(
        workspaceId: String,
        babyId: String,
        userId: String,
        hadUrine: Boolean,
        hadStool: Boolean,
        note: String?,
        changedAt: Long = now(),
    ): String {
        val timestamp = now()
        val entity = DiaperChangeEntity(
            id = newId(),
            babyId = babyId,
            changedAt = changedAt,
            hadUrine = hadUrine,
            hadStool = hadStool,
            note = note,
            sync = SyncMetaEntity(
                workspaceId = workspaceId,
                createdBy = userId,
                createdAt = timestamp,
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPLOAD,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            changes.upsert(entity)
            search.index(EntityType.DIAPER_CHANGE, entity.id, workspaceId, "", entity.note.orEmpty())
            enqueue(entity.id, SyncOperationType.CREATE, entity.sync.clientMutationId, timestamp)
        }
        syncTrigger.syncNow()
        return entity.id
    }

    suspend fun update(
        id: String,
        hadUrine: Boolean,
        hadStool: Boolean,
        note: String?,
        changedAt: Long,
    ) {
        val existing = changes.findById(id) ?: return
        val timestamp = now()
        val entity = existing.copy(
            changedAt = changedAt,
            hadUrine = hadUrine,
            hadStool = hadStool,
            note = note,
            sync = existing.sync.copy(
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )
        write(entity, SyncOperationType.UPDATE, timestamp)
    }

    /** Undoes a [delete]: the row was only soft-deleted, so the tombstone is cleared and pushed. */
    suspend fun restore(id: String) {
        val existing = changes.findById(id) ?: return
        val timestamp = now()
        val entity = existing.copy(
            sync = existing.sync.copy(
                deletedAt = null,
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )
        write(entity, SyncOperationType.UPDATE, timestamp)
    }

    suspend fun delete(id: String) {
        val timestamp = now()
        database.withTransaction {
            changes.softDelete(id, timestamp)
            search.remove(id)
            enqueue(id, SyncOperationType.DELETE, newId(), timestamp)
        }
        syncTrigger.syncNow()
    }

    private suspend fun write(entity: DiaperChangeEntity, operation: SyncOperationType, timestamp: Long) {
        database.withTransaction {
            changes.upsert(entity)
            search.index(EntityType.DIAPER_CHANGE, entity.id, entity.sync.workspaceId, "", entity.note.orEmpty())
            enqueue(entity.id, operation, entity.sync.clientMutationId, timestamp)
        }
        syncTrigger.syncNow()
    }

    private suspend fun enqueue(
        recordId: String,
        operation: SyncOperationType,
        clientMutationId: String?,
        timestamp: Long,
    ) {
        val opId = operations.enqueue(
            SyncOperationEntity(
                recordId = recordId,
                entityType = EntityType.DIAPER_CHANGE,
                operation = operation,
                clientMutationId = clientMutationId.orEmpty(),
                createdAt = timestamp,
            ),
        )
        operations.removeSuperseded(recordId, opId)
    }
}
