package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.ShoppingItemEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toShoppingItem
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.sync.SyncTrigger
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** The write path for shopping items — same outbox pattern as [TaskRepository]. */
class ShoppingItemRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val items get() = database.shoppingItemDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeAll(workspaceId: String): Flow<List<ShoppingItem>> =
        items.observeAll(workspaceId).map { list -> list.map { it.toShoppingItem() } }

    suspend fun lastActivityByOthers(workspaceId: String, selfUserId: String): Long? =
        items.lastActivityByOthers(workspaceId, selfUserId)

    suspend fun create(
        workspaceId: String,
        userId: String,
        name: String,
        category: ShoppingCategory,
        estimatedPrice: Double? = null,
        priority: Priority = Priority.NORMAL,
        assignee: Assignee? = null,
        customAssigneeName: String? = null,
        note: String? = null,
        link: String? = null,
        purchaseDate: LocalDate? = null,
        warrantyMonths: Int? = null,
    ) {
        val timestamp = now()
        val entity = ShoppingItemEntity(
            id = newId(),
            name = name,
            category = category,
            estimatedPrice = estimatedPrice,
            actualPrice = null,
            priority = priority,
            status = ShoppingStatus.NEED,
            assignee = assignee,
            customAssigneeName = customAssigneeName,
            note = note,
            link = link,
            alternatives = emptyList(),
            chosenAlternativeId = null,
            purchaseDate = purchaseDate?.toString(),
            warrantyMonths = warrantyMonths,
            sync = SyncMetaEntity(
                workspaceId = workspaceId,
                createdBy = userId,
                createdAt = timestamp,
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPLOAD,
                clientMutationId = newId(),
            ),
        )
        enqueue(entity, SyncOperationType.CREATE)
    }

    suspend fun update(
        id: String,
        name: String,
        category: ShoppingCategory,
        estimatedPrice: Double?,
        actualPrice: Double?,
        priority: Priority,
        assignee: Assignee?,
        customAssigneeName: String?,
        note: String?,
        link: String?,
        alternatives: List<ShoppingAlternative>,
        chosenAlternativeId: String?,
        purchaseDate: LocalDate?,
        warrantyMonths: Int?,
    ) {
        val existing = items.findById(id) ?: return
        val updated = existing.copy(
            name = name,
            category = category,
            estimatedPrice = estimatedPrice,
            actualPrice = actualPrice,
            priority = priority,
            assignee = assignee,
            customAssigneeName = customAssigneeName,
            note = note,
            link = link,
            alternatives = alternatives,
            chosenAlternativeId = chosenAlternativeId,
            purchaseDate = purchaseDate?.toString(),
            warrantyMonths = warrantyMonths,
        )
        enqueue(withBumpedSync(updated), SyncOperationType.UPDATE)
    }

    suspend fun setStatus(id: String, status: ShoppingStatus) {
        val existing = items.findById(id) ?: return
        enqueue(withBumpedSync(existing.copy(status = status)), SyncOperationType.UPDATE)
    }

    suspend fun delete(id: String) {
        val timestamp = now()
        database.withTransaction {
            items.softDelete(id, timestamp)
            search.remove(id)
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = id,
                    entityType = EntityType.SHOPPING_ITEM,
                    operation = SyncOperationType.DELETE,
                    clientMutationId = newId(),
                    createdAt = timestamp,
                ),
            )
            operations.removeSuperseded(id, opId)
        }
        syncTrigger.syncNow()
    }

    private fun withBumpedSync(entity: ShoppingItemEntity): ShoppingItemEntity = entity.copy(
        sync = entity.sync.copy(
            updatedAt = now(),
            syncStatus = SyncStatus.PENDING_UPDATE,
            clientMutationId = newId(),
        ),
    )

    private suspend fun enqueue(entity: ShoppingItemEntity, operation: SyncOperationType) {
        database.withTransaction {
            items.upsert(entity)
            search.index(EntityType.SHOPPING_ITEM, entity.id, entity.sync.workspaceId, entity.name, entity.note.orEmpty())
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = entity.id,
                    entityType = EntityType.SHOPPING_ITEM,
                    operation = operation,
                    clientMutationId = entity.sync.clientMutationId ?: newId(),
                    createdAt = now(),
                ),
            )
            operations.removeSuperseded(entity.id, opId)
        }
        syncTrigger.syncNow()
    }
}
