package com.oryareach.core.database.mapper

import com.oryareach.core.database.entity.AppSettingsEntity
import com.oryareach.core.database.entity.CycleEntryEntity
import com.oryareach.core.database.entity.DocumentEntity
import com.oryareach.core.database.entity.FolderEntity
import com.oryareach.core.database.entity.ImportantDateEntity
import com.oryareach.core.database.entity.MenstrualCycleEntity
import com.oryareach.core.database.entity.ShoppingItemEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.TaskEntity
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.CycleEntry
import com.oryareach.core.model.Document
import com.oryareach.core.model.Folder
import com.oryareach.core.model.ImportantDate
import com.oryareach.core.model.MenstrualCycle
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.Task
import com.oryareach.core.sync.RemoteRecord
import kotlinx.datetime.LocalDate

/**
 * Entity to domain and back.
 *
 * Dates are stored as ISO-8601 text so that ordering as text matches ordering as a date,
 * which lets SQLite sort and range-filter them without a conversion.
 */

fun TaskEntity.toTask() = Task(
    id = id,
    title = title,
    category = category,
    priority = priority,
    done = done,
    dueDate = dueDate?.let(LocalDate::parse),
    assignee = assignee,
    note = note,
    recurrence = recurrenceFrequency?.let { com.oryareach.core.model.Recurrence(it, recurrenceInterval ?: 1) },
    tags = tags,
)

fun ShoppingItemEntity.toShoppingItem() = ShoppingItem(
    id = id,
    name = name,
    category = category,
    estimatedPrice = estimatedPrice,
    actualPrice = actualPrice,
    priority = priority,
    status = status,
    assignee = assignee,
    customAssigneeName = customAssigneeName,
    note = note,
    link = link,
    alternatives = alternatives,
    chosenAlternativeId = chosenAlternativeId,
    purchaseDate = purchaseDate?.let(LocalDate::parse),
    warrantyMonths = warrantyMonths,
)

fun AppSettingsEntity.toAppSettings() = AppSettings(
    id = id,
    dueDate = LocalDate.parse(dueDate),
    babyName = babyName,
    partnerOneName = partnerOneName,
    partnerTwoName = partnerTwoName,
)

fun FolderEntity.toFolder() = Folder(id = id, name = name, parentId = parentId, path = path)

fun DocumentEntity.toDocument() = Document(
    id = id,
    folderId = folderId,
    taskId = taskId,
    cycleId = cycleId,
    shoppingItemId = shoppingItemId,
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    thumbnailBase64 = thumbnailBase64,
)

fun ImportantDateEntity.toImportantDate() = ImportantDate(
    id = id,
    date = LocalDate.parse(date),
    title = title,
    wish = wish,
)

fun MenstrualCycleEntity.toCycle() = MenstrualCycle(
    id = id,
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    note = note,
)

fun CycleEntryEntity.toCycleEntry() = CycleEntry(
    id = id,
    date = LocalDate.parse(date),
    flow = flow,
    symptoms = symptoms,
    mood = mood,
    pain = pain,
    note = note,
)

/** Builds the local row for a record that arrived from the server, already synced. */
fun Task.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = TaskEntity(
    id = id,
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
    sync = record.toSyncMeta(workspaceId, now),
)

fun ShoppingItem.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = ShoppingItemEntity(
    id = id,
    name = name,
    category = category,
    estimatedPrice = estimatedPrice,
    actualPrice = actualPrice,
    priority = priority,
    status = status,
    assignee = assignee,
    customAssigneeName = customAssigneeName,
    note = note,
    link = link,
    alternatives = alternatives,
    chosenAlternativeId = chosenAlternativeId,
    purchaseDate = purchaseDate?.toString(),
    warrantyMonths = warrantyMonths,
    sync = record.toSyncMeta(workspaceId, now),
)

fun AppSettings.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = AppSettingsEntity(
    id = id,
    dueDate = dueDate.toString(),
    babyName = babyName,
    partnerOneName = partnerOneName,
    partnerTwoName = partnerTwoName,
    sync = record.toSyncMeta(workspaceId, now),
)

fun Folder.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = FolderEntity(
    id = id,
    name = name,
    parentId = parentId,
    path = path,
    sync = record.toSyncMeta(workspaceId, now),
)

fun Document.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = DocumentEntity(
    id = id,
    folderId = folderId,
    taskId = taskId,
    cycleId = cycleId,
    shoppingItemId = shoppingItemId,
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    thumbnailBase64 = thumbnailBase64,
    sync = record.toSyncMeta(workspaceId, now),
)

fun ImportantDate.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = ImportantDateEntity(
    id = id,
    date = date.toString(),
    title = title,
    wish = wish,
    sync = record.toSyncMeta(workspaceId, now),
)

fun MenstrualCycle.toEntity(workspaceId: String, record: RemoteRecord, now: Long) =
    MenstrualCycleEntity(
        id = id,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        note = note,
        sync = record.toSyncMeta(workspaceId, now),
    )

fun CycleEntry.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = CycleEntryEntity(
    id = id,
    date = date.toString(),
    flow = flow,
    symptoms = symptoms,
    mood = mood,
    pain = pain,
    note = note,
    sync = record.toSyncMeta(workspaceId, now),
)

private fun RemoteRecord.toSyncMeta(workspaceId: String, now: Long) = SyncMetaEntity(
    workspaceId = workspaceId,
    // The server owns attribution; a pulled row carries no local author.
    createdBy = "",
    createdAt = now,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    version = version,
    syncStatus = SyncStatus.SYNCED,
    clientMutationId = null,
)
