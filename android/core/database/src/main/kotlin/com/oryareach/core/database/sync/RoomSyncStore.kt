package com.oryareach.core.database.sync

import androidx.room.withTransaction
import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.SyncConflictEntity
import com.oryareach.core.database.entity.SyncCursorEntity
import com.oryareach.core.database.mapper.toEntity
import com.oryareach.core.database.mapper.toTask
import com.oryareach.core.database.mapper.toCycle
import com.oryareach.core.database.mapper.toImportantDate
import com.oryareach.core.database.mapper.toShoppingItem
import com.oryareach.core.database.mapper.toAppSettings
import com.oryareach.core.database.mapper.toFolder
import com.oryareach.core.database.mapper.toDocument
import com.oryareach.core.database.mapper.toCycleEntry
import com.oryareach.core.database.mapper.toBaby
import com.oryareach.core.database.mapper.toFeedingEntry
import com.oryareach.core.database.mapper.toPumpSession
import com.oryareach.core.database.mapper.toVitaminDose
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
import com.oryareach.core.model.VitaminDose
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.Task
import com.oryareach.core.sync.PushRequest
import com.oryareach.core.sync.RecordCodec
import com.oryareach.core.sync.RemoteRecord
import com.oryareach.core.sync.SyncStore
import kotlinx.serialization.json.Json

/**
 * The local half of sync, over Room.
 *
 * Serializes a record, hands it to the codec to be encrypted, and only then lets it near the
 * network. On the way back it decrypts, deserializes and upserts. Records whose payload will
 * not decrypt are skipped rather than dropped: they stay on the server, so a device that
 * later gets the right key still receives them.
 */
class RoomSyncStore(
    private val database: OrYareachDatabase,
    private val codec: RecordCodec,
    private val workspaceId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SyncStore {

    private val tasks get() = database.taskDao()
    private val cycles get() = database.menstrualCycleDao()
    private val shoppingItems get() = database.shoppingItemDao()
    private val importantDates get() = database.importantDateDao()
    private val appSettings get() = database.appSettingsDao()
    private val folders get() = database.folderDao()
    private val documents get() = database.documentDao()
    private val cycleEntries get() = database.cycleEntryDao()
    private val babies get() = database.babyDao()
    private val feedingEntries get() = database.feedingEntryDao()
    private val pumpSessions get() = database.pumpSessionDao()
    private val vitaminDoses get() = database.vitaminDoseDao()
    private val search = SearchIndexer(database)
    private val operations get() = database.syncOperationDao()
    private val state get() = database.syncStateDao()

    override suspend fun pendingChanges(limit: Int): List<PushRequest> =
        operations.peek(limit).mapNotNull { operation ->
            val payload = serialize(operation.entityType, operation.recordId) ?: return@mapNotNull null
            val encoded = codec.encode(operation.entityType, operation.recordId, payload.json)

            when (encoded) {
                is AppResult.Failure -> null
                is AppResult.Success -> PushRequest(
                    recordId = operation.recordId,
                    entityType = operation.entityType,
                    operation = operation.operation,
                    ciphertext = encoded.data,
                    baseVersion = payload.version,
                    clientMutationId = operation.clientMutationId,
                )
            }
        }

    override suspend fun markSynced(recordId: String, version: Int) {
        database.withTransaction {
            when (entityTypeOf(recordId)) {
                EntityType.CYCLE -> cycles.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.SHOPPING_ITEM -> shoppingItems.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.IMPORTANT_DATE -> importantDates.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.SETTINGS -> appSettings.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.FOLDER -> folders.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.DOCUMENT -> documents.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.CYCLE_ENTRY -> cycleEntries.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.BABY -> babies.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.FEEDING_ENTRY -> feedingEntries.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.PUMP_SESSION -> pumpSessions.markSynced(recordId, SyncStatus.SYNCED, version)
                EntityType.VITAMIN_DOSE -> vitaminDoses.markSynced(recordId, SyncStatus.SYNCED, version)
                else -> tasks.markSynced(recordId, SyncStatus.SYNCED, version)
            }
            operations.removeByRecord(recordId)
            state.clearConflict(recordId)
        }
    }

    override suspend fun markConflict(recordId: String, server: RemoteRecord) {
        if (isSameContentAsLocal(recordId, server)) {
            markSynced(recordId, server.version)
            return
        }

        database.withTransaction {
            state.saveConflict(
                SyncConflictEntity(
                    recordId = recordId,
                    entityType = server.entityType,
                    serverCiphertext = server.ciphertext,
                    serverVersion = server.version,
                    serverUpdatedAt = server.updatedAt,
                    detectedAt = now(),
                ),
            )
            when (server.entityType) {
                EntityType.CYCLE -> cycles.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.SHOPPING_ITEM -> shoppingItems.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.IMPORTANT_DATE -> importantDates.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.SETTINGS -> appSettings.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.FOLDER -> folders.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.DOCUMENT -> documents.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.CYCLE_ENTRY -> cycleEntries.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.BABY -> babies.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.FEEDING_ENTRY -> feedingEntries.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.PUMP_SESSION -> pumpSessions.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                EntityType.VITAMIN_DOSE -> vitaminDoses.markSynced(recordId, SyncStatus.CONFLICT, server.version)
                else -> tasks.markSynced(recordId, SyncStatus.CONFLICT, server.version)
            }
            // The queued operation is dropped: replaying it would just conflict again. The
            // local edit is still in the row, and the server's copy is parked alongside it.
            operations.removeByRecord(recordId)
        }
    }

    /**
     * Whether the server's copy says exactly what this device's copy says.
     *
     * A conflict is by definition the same record edited twice, but that does not mean the two
     * versions differ: both of you logging the same feed from the same phone-in-hand, or the same
     * edit arriving twice, ends with two writes carrying identical content. Asking which of two
     * identical versions to keep is a question with no answer, so it is not asked — the server's
     * version number is adopted and the matter is closed.
     *
     * Compared as decrypted payloads, never as ciphertext: every encryption uses a fresh nonce, so
     * two encryptions of the same plaintext never match.
     *
     * A queued deletion is never auto-resolved. The row still holds its content while the delete
     * is pending, so it would compare equal to the server's copy and the deletion would be
     * silently dropped — the one case where "identical" is not the same as "nothing to decide".
     */
    private suspend fun isSameContentAsLocal(recordId: String, server: RemoteRecord): Boolean {
        if (operations.pendingOperations(recordId).contains(SyncOperationType.DELETE)) return false

        val local = serialize(server.entityType, recordId)?.json ?: return false
        val decoded = codec.decode(server.entityType, recordId, server.ciphertext)
        return decoded is AppResult.Success && decoded.data == local
    }

    override suspend fun recordFailure(recordId: String, error: AppError) {
        operations.recordFailureByRecord(recordId, error.toString())
    }

    override suspend fun applyRemote(records: List<RemoteRecord>) {
        val workspace = workspaceId() ?: return

        database.withTransaction {
            for (record in records) {
                // A record with a local edit still queued is left alone; overwriting it here
                // would silently discard the user's unsent change. A record already flagged as
                // conflicted needs the same guard: markConflict() drops the queued operation
                // (replaying it would just conflict again), so hasPending() alone stops
                // protecting it — without this check, the pull() that runs right after push()
                // in the same sync() cycle would immediately overwrite the local side with the
                // server's, destroying the very copy the conflict-resolution UI needs to offer
                // as "keep this device's version" before the user ever sees it.
                if (operations.hasPending(record.id)) continue
                if (state.conflict(record.id) != null) continue

                val decoded = codec.decode(record.entityType, record.id, record.ciphertext)
                if (decoded !is AppResult.Success) continue

                when (record.entityType) {
                    EntityType.CYCLE -> {
                        val cycle = runCatching {
                            json.decodeFromString<MenstrualCycle>(decoded.data)
                        }.getOrNull() ?: continue
                        cycles.upsert(cycle.toEntity(workspace, record, now()))
                        reindex(EntityType.CYCLE, record, workspace, "", cycle.note.orEmpty())
                    }

                    EntityType.TASK -> {
                        val task = runCatching {
                            json.decodeFromString<Task>(decoded.data)
                        }.getOrNull() ?: continue
                        tasks.upsert(task.toEntity(workspace, record, now()))
                        reindex(EntityType.TASK, record, workspace, task.title, task.note.orEmpty())
                    }

                    EntityType.SHOPPING_ITEM -> {
                        val item = runCatching {
                            json.decodeFromString<ShoppingItem>(decoded.data)
                        }.getOrNull() ?: continue
                        shoppingItems.upsert(item.toEntity(workspace, record, now()))
                        reindex(EntityType.SHOPPING_ITEM, record, workspace, item.name, item.note.orEmpty())
                    }

                    EntityType.IMPORTANT_DATE -> {
                        val date = runCatching {
                            json.decodeFromString<ImportantDate>(decoded.data)
                        }.getOrNull() ?: continue
                        importantDates.upsert(date.toEntity(workspace, record, now()))
                        reindex(EntityType.IMPORTANT_DATE, record, workspace, date.title, date.wish.orEmpty())
                    }

                    EntityType.SETTINGS -> {
                        val settings = runCatching {
                            json.decodeFromString<AppSettings>(decoded.data)
                        }.getOrNull() ?: continue
                        appSettings.upsert(settings.toEntity(workspace, record, now()))
                    }

                    EntityType.FOLDER -> {
                        val folder = runCatching {
                            json.decodeFromString<Folder>(decoded.data)
                        }.getOrNull() ?: continue
                        folders.upsert(folder.toEntity(workspace, record, now()))
                        reindex(EntityType.FOLDER, record, workspace, folder.name, "")
                    }

                    EntityType.DOCUMENT -> {
                        val document = runCatching {
                            json.decodeFromString<Document>(decoded.data)
                        }.getOrNull() ?: continue
                        documents.upsert(document.toEntity(workspace, record, now()))
                        reindex(EntityType.DOCUMENT, record, workspace, document.name, "")
                    }

                    EntityType.CYCLE_ENTRY -> {
                        val entry = runCatching {
                            json.decodeFromString<CycleEntry>(decoded.data)
                        }.getOrNull() ?: continue
                        cycleEntries.upsert(entry.toEntity(workspace, record, now()))
                        reindex(EntityType.CYCLE_ENTRY, record, workspace, "", entry.note.orEmpty())
                    }

                    EntityType.BABY -> {
                        val baby = runCatching {
                            json.decodeFromString<Baby>(decoded.data)
                        }.getOrNull() ?: continue
                        babies.upsert(baby.toEntity(workspace, record, now()))
                    }

                    EntityType.FEEDING_ENTRY -> {
                        val feed = runCatching {
                            json.decodeFromString<FeedingEntry>(decoded.data)
                        }.getOrNull() ?: continue
                        feedingEntries.upsert(feed.toEntity(workspace, record, now()))
                        reindex(EntityType.FEEDING_ENTRY, record, workspace, "", feed.note.orEmpty())
                    }

                    EntityType.PUMP_SESSION -> {
                        val session = runCatching {
                            json.decodeFromString<PumpSession>(decoded.data)
                        }.getOrNull() ?: continue
                        pumpSessions.upsert(session.toEntity(workspace, record, now()))
                        reindex(EntityType.PUMP_SESSION, record, workspace, "", session.note.orEmpty())
                    }

                    EntityType.VITAMIN_DOSE -> {
                        val dose = runCatching {
                            json.decodeFromString<VitaminDose>(decoded.data)
                        }.getOrNull() ?: continue
                        vitaminDoses.upsert(dose.toEntity(workspace, record, now()))
                        reindex(EntityType.VITAMIN_DOSE, record, workspace, "", dose.note.orEmpty())
                    }
                }
            }
        }
    }

    /** Mirrors an incoming record's tombstone state into the search index too — a remote
     * delete must remove it from search results just as reliably as a local one does. */
    private suspend fun reindex(entityType: EntityType, record: RemoteRecord, workspace: String, title: String, body: String) {
        if (record.deletedAt != null) {
            search.remove(record.id)
        } else {
            search.index(entityType, record.id, workspace, title, body)
        }
    }

    override suspend fun pullCursor(): Long? = workspaceId()?.let { state.cursor(it) }

    override suspend fun savePullCursor(cursor: Long) {
        val workspace = workspaceId() ?: return
        state.saveCursor(SyncCursorEntity(workspace, cursor))
    }

    private data class Payload(val json: String, val version: Int)

    private suspend fun serialize(type: EntityType, recordId: String): Payload? = when (type) {
        EntityType.CYCLE -> cycles.findById(recordId)?.let {
            Payload(json.encodeToString(it.toCycle()), it.sync.version)
        }

        EntityType.TASK -> tasks.findById(recordId)?.let {
            Payload(json.encodeToString(it.toTask()), it.sync.version)
        }

        EntityType.SHOPPING_ITEM -> shoppingItems.findById(recordId)?.let {
            Payload(json.encodeToString(it.toShoppingItem()), it.sync.version)
        }

        EntityType.IMPORTANT_DATE -> importantDates.findById(recordId)?.let {
            Payload(json.encodeToString(it.toImportantDate()), it.sync.version)
        }

        EntityType.SETTINGS -> appSettings.findById(recordId)?.let {
            Payload(json.encodeToString(it.toAppSettings()), it.sync.version)
        }

        EntityType.FOLDER -> folders.findById(recordId)?.let {
            Payload(json.encodeToString(it.toFolder()), it.sync.version)
        }

        EntityType.DOCUMENT -> documents.findById(recordId)?.let {
            Payload(json.encodeToString(it.toDocument()), it.sync.version)
        }

        EntityType.CYCLE_ENTRY -> cycleEntries.findById(recordId)?.let {
            Payload(json.encodeToString(it.toCycleEntry()), it.sync.version)
        }

        EntityType.BABY -> babies.findById(recordId)?.let {
            Payload(json.encodeToString(it.toBaby()), it.sync.version)
        }

        EntityType.FEEDING_ENTRY -> feedingEntries.findById(recordId)?.let {
            Payload(json.encodeToString(it.toFeedingEntry()), it.sync.version)
        }

        EntityType.PUMP_SESSION -> pumpSessions.findById(recordId)?.let {
            Payload(json.encodeToString(it.toPumpSession()), it.sync.version)
        }

        EntityType.VITAMIN_DOSE -> vitaminDoses.findById(recordId)?.let {
            Payload(json.encodeToString(it.toVitaminDose()), it.sync.version)
        }
    }

    /** Every syncable table is checked in turn; `TASK` is the fallback for a row not found
     * anywhere, matching this store's original two-table behavior rather than crashing. */
    private suspend fun entityTypeOf(recordId: String): EntityType = when {
        cycles.findById(recordId) != null -> EntityType.CYCLE
        shoppingItems.findById(recordId) != null -> EntityType.SHOPPING_ITEM
        importantDates.findById(recordId) != null -> EntityType.IMPORTANT_DATE
        appSettings.findById(recordId) != null -> EntityType.SETTINGS
        folders.findById(recordId) != null -> EntityType.FOLDER
        documents.findById(recordId) != null -> EntityType.DOCUMENT
        cycleEntries.findById(recordId) != null -> EntityType.CYCLE_ENTRY
        babies.findById(recordId) != null -> EntityType.BABY
        feedingEntries.findById(recordId) != null -> EntityType.FEEDING_ENTRY
        pumpSessions.findById(recordId) != null -> EntityType.PUMP_SESSION
        vitaminDoses.findById(recordId) != null -> EntityType.VITAMIN_DOSE
        else -> EntityType.TASK
    }
}
