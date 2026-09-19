package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.common.AppResult
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toEntity
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.CycleEntry
import com.oryareach.core.model.Document
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.model.Folder
import com.oryareach.core.model.ImportantDate
import com.oryareach.core.model.MenstrualCycle
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.Task
import com.oryareach.core.sync.RecordCodec
import com.oryareach.core.sync.RemoteRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

data class Conflict(
    val recordId: String,
    val entityType: EntityType,
    val localTitle: String,
    val localUpdatedAt: Long,
    val serverTitle: String,
    val serverUpdatedAt: Long,
)

/**
 * Surfaces the rows [com.oryareach.core.database.sync.RoomSyncStore.markConflict] parked —
 * a local edit the server rejected because the other device's write landed first. Nothing
 * about a conflict is resolved automatically anywhere in this app; this is the only place
 * one gets resolved, and only by a person choosing a side.
 */
class ConflictRepository(
    private val database: OrYareachDatabase,
    private val codec: RecordCodec,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val state get() = database.syncStateDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeConflicts(): Flow<List<Conflict>> = state.observeConflicts().map { conflicts ->
        conflicts.mapNotNull { conflict ->
            val serverTitle = decodeTitle(conflict.entityType, conflict.recordId, conflict.serverCiphertext) ?: return@mapNotNull null
            val local = localTitleAndUpdatedAt(conflict.entityType, conflict.recordId) ?: return@mapNotNull null
            Conflict(
                recordId = conflict.recordId,
                entityType = conflict.entityType,
                localTitle = local.first,
                localUpdatedAt = local.second,
                serverTitle = serverTitle,
                serverUpdatedAt = conflict.serverUpdatedAt,
            )
        }
    }

    /** Keeps this device's edit: re-queues it for push, based on the server's version so the
     * next push is not immediately rejected as stale again. */
    suspend fun keepLocal(recordId: String) {
        val conflict = state.conflict(recordId) ?: return
        database.withTransaction {
            bumpLocalVersion(conflict.entityType, recordId, conflict.serverVersion)
            operations.enqueue(
                SyncOperationEntity(
                    recordId = recordId,
                    entityType = conflict.entityType,
                    operation = SyncOperationType.UPDATE,
                    clientMutationId = newId(),
                    createdAt = now(),
                ),
            )
            state.clearConflict(recordId)
        }
    }

    /** Keeps the partner's edit: overwrites the local row with the server's content and marks
     * it synced — there is nothing left to push, the server already has this version. */
    suspend fun keepServer(recordId: String) {
        val conflict = state.conflict(recordId) ?: return
        val workspace = localWorkspaceId(conflict.entityType, recordId) ?: return
        val decoded = codec.decode(conflict.entityType, recordId, conflict.serverCiphertext)
        if (decoded !is AppResult.Success) return

        val record = RemoteRecord(
            id = recordId,
            entityType = conflict.entityType,
            ciphertext = conflict.serverCiphertext,
            version = conflict.serverVersion,
            updatedAt = conflict.serverUpdatedAt,
            deletedAt = null,
        )

        database.withTransaction {
            applyServerCopy(conflict.entityType, workspace, record, decoded.data)
            operations.removeByRecord(recordId)
            state.clearConflict(recordId)
        }
    }

    private suspend fun applyServerCopy(entityType: EntityType, workspace: String, record: RemoteRecord, payload: String) {
        when (entityType) {
            EntityType.TASK -> {
                val task = runCatching { json.decodeFromString<Task>(payload) }.getOrNull() ?: return
                database.taskDao().upsert(task.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, task.title, task.note.orEmpty())
            }
            EntityType.SHOPPING_ITEM -> {
                val item = runCatching { json.decodeFromString<ShoppingItem>(payload) }.getOrNull() ?: return
                database.shoppingItemDao().upsert(item.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, item.name, item.note.orEmpty())
            }
            EntityType.IMPORTANT_DATE -> {
                val date = runCatching { json.decodeFromString<ImportantDate>(payload) }.getOrNull() ?: return
                database.importantDateDao().upsert(date.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, date.title, date.wish.orEmpty())
            }
            EntityType.SETTINGS -> {
                val settings = runCatching { json.decodeFromString<AppSettings>(payload) }.getOrNull() ?: return
                database.appSettingsDao().upsert(settings.toEntity(workspace, record, now()))
            }
            EntityType.FOLDER -> {
                val folder = runCatching { json.decodeFromString<Folder>(payload) }.getOrNull() ?: return
                database.folderDao().upsert(folder.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, folder.name, "")
            }
            EntityType.DOCUMENT -> {
                val document = runCatching { json.decodeFromString<Document>(payload) }.getOrNull() ?: return
                database.documentDao().upsert(document.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, document.name, "")
            }
            EntityType.CYCLE -> {
                val cycle = runCatching { json.decodeFromString<MenstrualCycle>(payload) }.getOrNull() ?: return
                database.menstrualCycleDao().upsert(cycle.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, "", cycle.note.orEmpty())
            }
            EntityType.CYCLE_ENTRY -> {
                val entry = runCatching { json.decodeFromString<CycleEntry>(payload) }.getOrNull() ?: return
                database.cycleEntryDao().upsert(entry.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, "", entry.note.orEmpty())
            }
            EntityType.BABY -> {
                val baby = runCatching { json.decodeFromString<Baby>(payload) }.getOrNull() ?: return
                database.babyDao().upsert(baby.toEntity(workspace, record, now()))
            }
            EntityType.FEEDING_ENTRY -> {
                val feed = runCatching { json.decodeFromString<FeedingEntry>(payload) }.getOrNull() ?: return
                database.feedingEntryDao().upsert(feed.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, "", feed.note.orEmpty())
            }
            EntityType.PUMP_SESSION -> {
                val session = runCatching { json.decodeFromString<PumpSession>(payload) }.getOrNull() ?: return
                database.pumpSessionDao().upsert(session.toEntity(workspace, record, now()))
                search.index(entityType, recordId(record), workspace, "", session.note.orEmpty())
            }
        }
    }

    private fun recordId(record: RemoteRecord): String = record.id

    private suspend fun bumpLocalVersion(entityType: EntityType, recordId: String, baseVersion: Int) {
        when (entityType) {
            EntityType.TASK -> database.taskDao().findById(recordId)?.let {
                database.taskDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.SHOPPING_ITEM -> database.shoppingItemDao().findById(recordId)?.let {
                database.shoppingItemDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.IMPORTANT_DATE -> database.importantDateDao().findById(recordId)?.let {
                database.importantDateDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.SETTINGS -> database.appSettingsDao().findById(recordId)?.let {
                database.appSettingsDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.FOLDER -> database.folderDao().findById(recordId)?.let {
                database.folderDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.DOCUMENT -> database.documentDao().findById(recordId)?.let {
                database.documentDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.CYCLE -> database.menstrualCycleDao().findById(recordId)?.let {
                database.menstrualCycleDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.CYCLE_ENTRY -> database.cycleEntryDao().findById(recordId)?.let {
                database.cycleEntryDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.BABY -> database.babyDao().findById(recordId)?.let {
                database.babyDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.FEEDING_ENTRY -> database.feedingEntryDao().findById(recordId)?.let {
                database.feedingEntryDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
            EntityType.PUMP_SESSION -> database.pumpSessionDao().findById(recordId)?.let {
                database.pumpSessionDao().upsert(it.copy(sync = it.sync.copy(version = baseVersion, syncStatus = SyncStatus.PENDING_UPDATE)))
            }
        }
    }

    private suspend fun localWorkspaceId(entityType: EntityType, recordId: String): String? = when (entityType) {
        EntityType.TASK -> database.taskDao().findById(recordId)?.sync?.workspaceId
        EntityType.SHOPPING_ITEM -> database.shoppingItemDao().findById(recordId)?.sync?.workspaceId
        EntityType.IMPORTANT_DATE -> database.importantDateDao().findById(recordId)?.sync?.workspaceId
        EntityType.SETTINGS -> database.appSettingsDao().findById(recordId)?.sync?.workspaceId
        EntityType.FOLDER -> database.folderDao().findById(recordId)?.sync?.workspaceId
        EntityType.DOCUMENT -> database.documentDao().findById(recordId)?.sync?.workspaceId
        EntityType.CYCLE -> database.menstrualCycleDao().findById(recordId)?.sync?.workspaceId
        EntityType.CYCLE_ENTRY -> database.cycleEntryDao().findById(recordId)?.sync?.workspaceId
        EntityType.BABY -> database.babyDao().findById(recordId)?.sync?.workspaceId
        EntityType.FEEDING_ENTRY -> database.feedingEntryDao().findById(recordId)?.sync?.workspaceId
        EntityType.PUMP_SESSION -> database.pumpSessionDao().findById(recordId)?.sync?.workspaceId
    }

    private suspend fun localTitleAndUpdatedAt(entityType: EntityType, recordId: String): Pair<String, Long>? = when (entityType) {
        EntityType.TASK -> database.taskDao().findById(recordId)?.let { it.title to it.sync.updatedAt }
        EntityType.SHOPPING_ITEM -> database.shoppingItemDao().findById(recordId)?.let { it.name to it.sync.updatedAt }
        EntityType.IMPORTANT_DATE -> database.importantDateDao().findById(recordId)?.let { it.title to it.sync.updatedAt }
        EntityType.SETTINGS -> database.appSettingsDao().findById(recordId)?.let { it.dueDate to it.sync.updatedAt }
        EntityType.FOLDER -> database.folderDao().findById(recordId)?.let { it.name to it.sync.updatedAt }
        EntityType.DOCUMENT -> database.documentDao().findById(recordId)?.let { it.name to it.sync.updatedAt }
        EntityType.CYCLE -> database.menstrualCycleDao().findById(recordId)?.let { it.startDate to it.sync.updatedAt }
        EntityType.CYCLE_ENTRY -> database.cycleEntryDao().findById(recordId)?.let { (it.note ?: it.date) to it.sync.updatedAt }
        EntityType.BABY -> database.babyDao().findById(recordId)?.let { (it.name ?: it.id) to it.sync.updatedAt }
        EntityType.FEEDING_ENTRY -> database.feedingEntryDao().findById(recordId)?.let {
            (it.note ?: it.fedAt.toString()) to it.sync.updatedAt
        }
        EntityType.PUMP_SESSION -> database.pumpSessionDao().findById(recordId)?.let {
            (it.note ?: it.startedAt.toString()) to it.sync.updatedAt
        }
    }

    private fun decodeTitle(entityType: EntityType, recordId: String, ciphertext: ByteArray): String? {
        val decoded = codec.decode(entityType, recordId, ciphertext)
        if (decoded !is AppResult.Success) return null
        val payload = decoded.data
        return runCatching {
            when (entityType) {
                EntityType.TASK -> json.decodeFromString<Task>(payload).title
                EntityType.SHOPPING_ITEM -> json.decodeFromString<ShoppingItem>(payload).name
                EntityType.IMPORTANT_DATE -> json.decodeFromString<ImportantDate>(payload).title
                EntityType.SETTINGS -> json.decodeFromString<AppSettings>(payload).dueDate.toString()
                EntityType.FOLDER -> json.decodeFromString<Folder>(payload).name
                EntityType.DOCUMENT -> json.decodeFromString<Document>(payload).name
                EntityType.CYCLE -> json.decodeFromString<MenstrualCycle>(payload).startDate.toString()
                EntityType.CYCLE_ENTRY -> json.decodeFromString<CycleEntry>(payload).let { it.note ?: it.date.toString() }
                EntityType.BABY -> json.decodeFromString<Baby>(payload).let { it.name ?: it.id }
                EntityType.FEEDING_ENTRY -> json.decodeFromString<FeedingEntry>(payload).let {
                    it.note ?: it.fedAtEpochMillis.toString()
                }
                EntityType.PUMP_SESSION -> json.decodeFromString<PumpSession>(payload).let {
                    it.note ?: it.startedAtEpochMillis.toString()
                }
            }
        }.getOrNull()
    }
}
