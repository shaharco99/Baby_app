package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.dao.CreatorCount
import com.oryareach.core.database.entity.FeedingEntryEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toFeedingEntry
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.settings.FeedingReminderScheduler
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The write path for logged feeds. Unlike a cycle entry there is no "one per day" rule: feeds
 * are appended, several an hour if that is how the night goes, and each is its own row.
 */
class FeedingEntryRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val reminders: FeedingReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val entries get() = database.feedingEntryDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeInRange(
        workspaceId: String,
        babyId: String,
        start: Long,
        end: Long,
    ): Flow<List<FeedingEntry>> =
        entries.observeInRange(workspaceId, babyId, start, end)
            .map { list -> list.map { it.toFeedingEntry() } }

    fun observeLatest(workspaceId: String, babyId: String): Flow<FeedingEntry?> =
        entries.observeLatest(workspaceId, babyId).map { it?.toFeedingEntry() }

    suspend fun findLatest(workspaceId: String, babyId: String): FeedingEntry? =
        entries.findLatest(workspaceId, babyId)?.toFeedingEntry()

    /** How many feeds each partner has logged, over the whole log. */
    suspend fun countByCreator(workspaceId: String, babyId: String): List<CreatorCount> =
        entries.countByCreator(workspaceId, babyId)

    suspend fun logFeed(
        workspaceId: String,
        babyId: String,
        userId: String,
        feedType: FeedType,
        breastMl: Int?,
        formulaMl: Int?,
        hadUrine: Boolean,
        hadStool: Boolean,
        note: String?,
        intervalMinutes: Int,
        fedAt: Long = now(),
    ): String {
        val timestamp = now()
        val entity = FeedingEntryEntity(
            id = newId(),
            babyId = babyId,
            fedAt = fedAt,
            feedType = feedType,
            breastMl = breastMl,
            formulaMl = formulaMl,
            amountMl = mirrorTotal(breastMl, formulaMl),
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
            entries.upsert(entity)
            search.index(EntityType.FEEDING_ENTRY, entity.id, workspaceId, "", entity.note.orEmpty())
            enqueue(entity.id, SyncOperationType.CREATE, entity.sync.clientMutationId, timestamp)
        }
        // Scheduled off the most recent feed on record rather than the one just written, and off
        // its own time rather than "now": a feed logged late still puts the reminder an interval
        // after it actually happened, while a retroactive entry older than the last feed leaves
        // the pending reminder where it is instead of dragging it into the past.
        val latest = entries.findLatest(workspaceId, babyId)
        reminders.scheduleNext(latest?.fedAt ?: fedAt, intervalMinutes)
        syncTrigger.syncNow()
        return entity.id
    }

    suspend fun update(
        id: String,
        feedType: FeedType,
        breastMl: Int?,
        formulaMl: Int?,
        hadUrine: Boolean,
        hadStool: Boolean,
        note: String?,
        fedAt: Long,
        intervalMinutes: Int,
    ) {
        val existing = entries.findById(id) ?: return
        val timestamp = now()
        val entity = existing.copy(
            fedAt = fedAt,
            feedType = feedType,
            breastMl = breastMl,
            formulaMl = formulaMl,
            amountMl = mirrorTotal(breastMl, formulaMl),
            hadUrine = hadUrine,
            hadStool = hadStool,
            note = note,
            sync = existing.sync.copy(
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            entries.upsert(entity)
            search.index(
                EntityType.FEEDING_ENTRY,
                entity.id,
                entity.sync.workspaceId,
                "",
                entity.note.orEmpty(),
            )
            enqueue(entity.id, SyncOperationType.UPDATE, entity.sync.clientMutationId, timestamp)
        }
        rescheduleFromLatest(entity.sync.workspaceId, entity.babyId, intervalMinutes)
        syncTrigger.syncNow()
    }

    /**
     * Undoes a [delete]. The row was only ever soft-deleted, so this is a matter of clearing the
     * tombstone and pushing it again — which is what lets the list offer an undo instead of
     * asking for a confirmation on every delete.
     */
    suspend fun restore(id: String, intervalMinutes: Int) {
        val existing = entries.findById(id) ?: return
        val timestamp = now()
        val entity = existing.copy(
            sync = existing.sync.copy(
                deletedAt = null,
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            entries.upsert(entity)
            search.index(
                EntityType.FEEDING_ENTRY,
                entity.id,
                entity.sync.workspaceId,
                "",
                entity.note.orEmpty(),
            )
            enqueue(entity.id, SyncOperationType.UPDATE, entity.sync.clientMutationId, timestamp)
        }
        rescheduleFromLatest(entity.sync.workspaceId, entity.babyId, intervalMinutes)
        syncTrigger.syncNow()
    }

    suspend fun delete(id: String, intervalMinutes: Int) {
        // Read before the soft delete: afterwards the row is filtered out of every query, and
        // the reminder still has to be re-derived for the child it belonged to.
        val existing = entries.findById(id) ?: return
        val timestamp = now()
        database.withTransaction {
            entries.softDelete(id, timestamp)
            search.remove(id)
            enqueue(id, SyncOperationType.DELETE, newId(), timestamp)
        }
        rescheduleFromLatest(existing.sync.workspaceId, existing.babyId, intervalMinutes)
        syncTrigger.syncNow()
    }

    /**
     * Puts the pending reminder back where the database says it belongs.
     *
     * Every write path ends here rather than only [logFeed]: editing the latest feed's time,
     * deleting it, or undoing that delete all move which feed the countdown runs from, and an
     * alarm left pointing at a feed that no longer exists rings at the wrong moment — or, after
     * the last feed is deleted, rings for a log with nothing in it.
     */
    private suspend fun rescheduleFromLatest(
        workspaceId: String,
        babyId: String,
        intervalMinutes: Int,
    ) {
        val latest = entries.findLatest(workspaceId, babyId)
        if (latest == null) reminders.cancel() else reminders.scheduleNext(latest.fedAt, intervalMinutes)
    }

    /**
     * The legacy [FeedingEntryEntity.amountMl] mirror. Null when neither source was measured,
     * so an unmeasured breastfeed still reads as unmeasured rather than as zero.
     */
    private fun mirrorTotal(breastMl: Int?, formulaMl: Int?): Int? =
        listOfNotNull(breastMl, formulaMl).takeIf { it.isNotEmpty() }?.sum()

    private suspend fun enqueue(
        recordId: String,
        operation: SyncOperationType,
        clientMutationId: String?,
        timestamp: Long,
    ) {
        val opId = operations.enqueue(
            SyncOperationEntity(
                recordId = recordId,
                entityType = EntityType.FEEDING_ENTRY,
                operation = operation,
                clientMutationId = clientMutationId.orEmpty(),
                createdAt = timestamp,
            ),
        )
        operations.removeSuperseded(recordId, opId)
    }
}
