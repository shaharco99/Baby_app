package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.entity.TaskEntity
import com.oryareach.core.database.mapper.toTask
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.Priority
import com.oryareach.core.model.Recurrence
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.Task
import com.oryareach.core.model.TaskCategory
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import java.util.UUID

/**
 * The write path for tasks: every mutation lands in Room and the outbox in one transaction,
 * then kicks the sync worker. The UI never talks to `TaskDao` directly.
 */
class TaskRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val tasks get() = database.taskDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeAll(workspaceId: String): Flow<List<Task>> =
        tasks.observeAll(workspaceId).map { list -> list.map { it.toTask() } }

    suspend fun lastActivityByOthers(workspaceId: String, selfUserId: String): Long? =
        tasks.lastActivityByOthers(workspaceId, selfUserId)

    suspend fun create(
        workspaceId: String,
        userId: String,
        title: String,
        category: TaskCategory,
        priority: Priority = Priority.NORMAL,
        assignee: Assignee? = null,
        note: String? = null,
        done: Boolean = false,
        dueDate: LocalDate? = null,
        recurrence: Recurrence? = null,
        tags: List<String> = emptyList(),
    ) {
        val timestamp = now()
        val entity = TaskEntity(
            id = newId(),
            title = title,
            category = category,
            priority = priority,
            done = done,
            dueDate = dueDate?.toString(),
            assignee = assignee,
            note = note,
            recurrenceFrequency = recurrence?.frequency,
            recurrenceInterval = recurrence?.interval,
            tags = tags,
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
        title: String,
        category: TaskCategory,
        priority: Priority,
        assignee: Assignee?,
        note: String?,
        dueDate: LocalDate?,
        recurrence: Recurrence?,
        tags: List<String>,
    ) {
        val existing = tasks.findById(id) ?: return
        val updated = existing.copy(
            title = title,
            category = category,
            priority = priority,
            assignee = assignee,
            note = note,
            dueDate = dueDate?.toString(),
            recurrenceFrequency = recurrence?.frequency,
            recurrenceInterval = recurrence?.interval,
            tags = tags,
        )
        enqueue(withBumpedSync(updated), SyncOperationType.UPDATE)
    }

    suspend fun toggleDone(id: String) {
        val existing = tasks.findById(id) ?: return
        enqueue(withBumpedSync(existing.copy(done = !existing.done)), SyncOperationType.UPDATE)
    }

    /**
     * Completing a recurring task both finishes this occurrence and schedules the next one —
     * the domain math for [nextDueDate] lives in `:core:domain` and is computed by the caller,
     * not here, so this data layer never has to depend on `:core:domain`'s business logic.
     * A no-op [nextDueDate] just completes the task, same as [toggleDone] toward done.
     */
    suspend fun completeAndScheduleNext(id: String, nextDueDate: LocalDate?) {
        val existing = tasks.findById(id) ?: return
        val timestamp = now()

        database.withTransaction {
            val completed = existing.copy(done = true)
            tasks.upsert(withBumpedSync(completed))
            search.index(EntityType.TASK, completed.id, completed.sync.workspaceId, completed.title, completed.searchBody())
            val completedOpId = operations.enqueue(
                SyncOperationEntity(
                    recordId = completed.id,
                    entityType = EntityType.TASK,
                    operation = SyncOperationType.UPDATE,
                    clientMutationId = newId(),
                    createdAt = timestamp,
                ),
            )
            operations.removeSuperseded(completed.id, completedOpId)

            if (nextDueDate != null) {
                val next = existing.copy(
                    id = newId(),
                    done = false,
                    dueDate = nextDueDate.toString(),
                    sync = existing.sync.copy(
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        syncStatus = SyncStatus.PENDING_UPLOAD,
                        clientMutationId = newId(),
                    ),
                )
                tasks.upsert(next)
                search.index(EntityType.TASK, next.id, next.sync.workspaceId, next.title, next.searchBody())
                val nextOpId = operations.enqueue(
                    SyncOperationEntity(
                        recordId = next.id,
                        entityType = EntityType.TASK,
                        operation = SyncOperationType.CREATE,
                        clientMutationId = next.sync.clientMutationId.orEmpty(),
                        createdAt = timestamp,
                    ),
                )
                operations.removeSuperseded(next.id, nextOpId)
            }
        }
        syncTrigger.syncNow()
    }

    suspend fun delete(id: String) {
        val timestamp = now()
        database.withTransaction {
            tasks.softDelete(id, timestamp)
            search.remove(id)
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = id,
                    entityType = EntityType.TASK,
                    operation = SyncOperationType.DELETE,
                    clientMutationId = newId(),
                    createdAt = timestamp,
                ),
            )
            operations.removeSuperseded(id, opId)
        }
        syncTrigger.syncNow()
    }

    /** Tags are searchable text too — `#medical` should surface a task tagged that way even
     * if the word never appears in its title or note. */
    private fun TaskEntity.searchBody(): String = (listOf(note.orEmpty()) + tags).joinToString(" ").trim()

    private fun withBumpedSync(entity: TaskEntity): TaskEntity = entity.copy(
        sync = entity.sync.copy(
            updatedAt = now(),
            syncStatus = SyncStatus.PENDING_UPDATE,
            clientMutationId = newId(),
        ),
    )

    private suspend fun enqueue(entity: TaskEntity, operation: SyncOperationType) {
        database.withTransaction {
            tasks.upsert(entity)
            search.index(EntityType.TASK, entity.id, entity.sync.workspaceId, entity.title, entity.searchBody())
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = entity.id,
                    entityType = EntityType.TASK,
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
