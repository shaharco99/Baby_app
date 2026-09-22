package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.entity.VitaminDoseEntity
import com.oryareach.core.database.mapper.toVitaminDose
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.SupplementKind
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.VitaminDose
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The write path for the daily supplement tick.
 *
 * There is no "undo a miss" and no row for a day that was skipped: a dose exists or it does not.
 * Un-ticking is a real delete, tombstoned like every other, so a tick on one phone and an
 * un-tick on the other resolve the same way any other edit does.
 */
class VitaminDoseRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val doses get() = database.vitaminDoseDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): Flow<List<VitaminDose>> =
        doses.observeInRange(workspaceId, babyId, start, end)
            .map { list -> list.map { it.toVitaminDose() } }

    /** The dose for one local day, if there is one. The caller works out the day's bounds. */
    suspend fun findInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): VitaminDose? = doses.findInRange(workspaceId, babyId, start, end)?.toVitaminDose()

    suspend fun logDose(
        workspaceId: String,
        babyId: String,
        userId: String,
        kind: SupplementKind = SupplementKind.VITAMIN_D,
        note: String? = null,
        givenAt: Long = now(),
    ): String {
        val timestamp = now()
        val entity = VitaminDoseEntity(
            id = newId(),
            babyId = babyId,
            givenAt = givenAt,
            kind = kind,
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
            doses.upsert(entity)
            search.index(EntityType.VITAMIN_DOSE, entity.id, workspaceId, "", entity.note.orEmpty())
            enqueue(entity.id, SyncOperationType.CREATE, entity.sync.clientMutationId, timestamp)
        }
        syncTrigger.syncNow()
        return entity.id
    }

    suspend fun delete(id: String) {
        val timestamp = now()
        database.withTransaction {
            doses.softDelete(id, timestamp)
            search.remove(id)
            enqueue(id, SyncOperationType.DELETE, newId(), timestamp)
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
                entityType = EntityType.VITAMIN_DOSE,
                operation = operation,
                clientMutationId = clientMutationId.orEmpty(),
                createdAt = timestamp,
            ),
        )
        operations.removeSuperseded(recordId, opId)
    }
}
