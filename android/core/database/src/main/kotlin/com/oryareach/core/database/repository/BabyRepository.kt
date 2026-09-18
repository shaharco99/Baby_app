package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.entity.BabyEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toBaby
import com.oryareach.core.model.Baby
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID

/**
 * The write path for the couple's children. The sole writer of `app_settings.active_baby_id`:
 * which child everything points at is a consequence of [setActive], never set from two places.
 */
class BabyRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val babies get() = database.babyDao()
    private val settings get() = database.appSettingsDao()
    private val operations get() = database.syncOperationDao()

    fun observeAll(workspaceId: String): Flow<List<Baby>> =
        babies.observeAll(workspaceId).map { list -> list.map { it.toBaby() } }

    fun observeActive(workspaceId: String): Flow<Baby?> =
        babies.observeActive(workspaceId).map { it?.toBaby() }

    suspend fun findById(id: String): Baby? = babies.findById(id)?.toBaby()

    suspend fun create(
        workspaceId: String,
        userId: String,
        name: String?,
        dueDate: LocalDate?,
        makeActive: Boolean,
    ): String {
        val timestamp = now()
        val entity = BabyEntity(
            id = newId(),
            name = name,
            dueDate = dueDate?.toString(),
            birthDate = null,
            birthTime = null,
            birthWeightGrams = null,
            birthPlace = null,
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
            babies.upsert(entity)
            enqueue(entity.id, SyncOperationType.CREATE, entity.sync.clientMutationId, timestamp)
            if (makeActive) writeActive(workspaceId, entity.id, timestamp)
        }
        syncTrigger.syncNow()
        return entity.id
    }

    /** Birth details, filled in once the child arrives. Any argument left null clears its field. */
    suspend fun update(
        id: String,
        name: String?,
        dueDate: LocalDate?,
        birthDate: LocalDate?,
        birthTime: LocalTime?,
        birthWeightGrams: Int?,
        birthPlace: String?,
    ) {
        val existing = babies.findById(id) ?: return
        val timestamp = now()
        val entity = existing.copy(
            name = name,
            dueDate = dueDate?.toString(),
            birthDate = birthDate?.toString(),
            birthTime = birthTime?.toString(),
            birthWeightGrams = birthWeightGrams,
            birthPlace = birthPlace,
            sync = existing.sync.copy(
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            babies.upsert(entity)
            enqueue(entity.id, SyncOperationType.UPDATE, entity.sync.clientMutationId, timestamp)
        }
        syncTrigger.syncNow()
    }

    suspend fun setActive(workspaceId: String, babyId: String) {
        val timestamp = now()
        database.withTransaction { writeActive(workspaceId, babyId, timestamp) }
        syncTrigger.syncNow()
    }

    /**
     * Carries an install that predates per-child records over: the pregnancy stored on
     * `app_settings` becomes the workspace's first child. Idempotent — it does nothing once
     * `active_baby_id` is set, or when a child already exists (the partner's device may have
     * seeded first and the row arrived by sync).
     */
    suspend fun seedFromSettingsIfNeeded(workspaceId: String, userId: String) {
        val existing = settings.find(workspaceId) ?: return
        if (existing.activeBabyId != null) return

        val alreadyThere = babies.findAll(workspaceId).firstOrNull()
        if (alreadyThere != null) {
            setActive(workspaceId, alreadyThere.id)
            return
        }

        create(
            workspaceId = workspaceId,
            userId = userId,
            name = existing.babyName,
            dueDate = LocalDate.parse(existing.dueDate),
            makeActive = true,
        )
    }

    /** Must run inside a transaction — the settings row and its outbox entry move together. */
    private suspend fun writeActive(workspaceId: String, babyId: String, timestamp: Long) {
        val existing = settings.find(workspaceId) ?: return
        if (existing.activeBabyId == babyId) return

        val updated = existing.copy(
            activeBabyId = babyId,
            sync = existing.sync.copy(
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )
        settings.upsert(updated)
        val opId = operations.enqueue(
            SyncOperationEntity(
                recordId = updated.id,
                entityType = EntityType.SETTINGS,
                operation = SyncOperationType.UPDATE,
                clientMutationId = updated.sync.clientMutationId.orEmpty(),
                createdAt = timestamp,
            ),
        )
        operations.removeSuperseded(updated.id, opId)
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
                entityType = EntityType.BABY,
                operation = operation,
                clientMutationId = clientMutationId.orEmpty(),
                createdAt = timestamp,
            ),
        )
        operations.removeSuperseded(recordId, opId)
    }
}
