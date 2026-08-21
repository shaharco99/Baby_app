package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.entity.AppSettingsEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toAppSettings
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import java.util.UUID

/**
 * The couple's shared due date and baby name — a single row per workspace, upserted rather
 * than created/updated separately since there is only ever one.
 */
class AppSettingsRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val settings get() = database.appSettingsDao()
    private val operations get() = database.syncOperationDao()

    fun observe(workspaceId: String): Flow<AppSettings?> =
        settings.observe(workspaceId).map { it?.toAppSettings() }

    suspend fun save(
        workspaceId: String,
        userId: String,
        dueDate: LocalDate,
        babyName: String?,
        partnerOneName: String? = null,
        partnerTwoName: String? = null,
    ) {
        val timestamp = now()
        val existing = settings.find(workspaceId)

        val entity = if (existing != null) {
            existing.copy(
                dueDate = dueDate.toString(),
                babyName = babyName,
                partnerOneName = partnerOneName,
                partnerTwoName = partnerTwoName,
                sync = existing.sync.copy(
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.PENDING_UPDATE,
                    clientMutationId = newId(),
                ),
            )
        } else {
            AppSettingsEntity(
                id = newId(),
                dueDate = dueDate.toString(),
                babyName = babyName,
                partnerOneName = partnerOneName,
                partnerTwoName = partnerTwoName,
                sync = SyncMetaEntity(
                    workspaceId = workspaceId,
                    createdBy = userId,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.PENDING_UPLOAD,
                    clientMutationId = newId(),
                ),
            )
        }

        val operation = if (existing != null) SyncOperationType.UPDATE else SyncOperationType.CREATE

        database.withTransaction {
            settings.upsert(entity)
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = entity.id,
                    entityType = EntityType.SETTINGS,
                    operation = operation,
                    clientMutationId = entity.sync.clientMutationId ?: newId(),
                    createdAt = timestamp,
                ),
            )
            operations.removeSuperseded(entity.id, opId)
        }
        syncTrigger.syncNow()
    }
}
