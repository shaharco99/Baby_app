package com.oryareach.core.database.mapper

import com.oryareach.core.database.entity.AppSettingsEntity
import com.oryareach.core.database.entity.BabyEntity
import com.oryareach.core.database.entity.CycleEntryEntity
import com.oryareach.core.database.entity.DocumentEntity
import com.oryareach.core.database.entity.FeedingEntryEntity
import com.oryareach.core.database.entity.FolderEntity
import com.oryareach.core.database.entity.ImportantDateEntity
import com.oryareach.core.database.entity.MenstrualCycleEntity
import com.oryareach.core.database.entity.PumpSessionEntity
import com.oryareach.core.database.entity.VitaminDoseEntity
import com.oryareach.core.database.entity.DiaperChangeEntity
import com.oryareach.core.database.entity.ShoppingItemEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.TaskEntity
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.CycleEntry
import com.oryareach.core.model.Document
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.model.Folder
import com.oryareach.core.model.ImportantDate
import com.oryareach.core.model.MenstrualCycle
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.VitaminDose
import com.oryareach.core.model.DiaperChange
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.Task
import com.oryareach.core.sync.RemoteRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
    activeBabyId = activeBabyId,
    feedIntervalMinutes = feedIntervalMinutes,
    pumpIntervalMinutes = pumpIntervalMinutes,
    vitaminDMinuteOfDay = vitaminDMinuteOfDay,
)

fun BabyEntity.toBaby() = Baby(
    id = id,
    name = name,
    dueDate = dueDate?.let(LocalDate::parse),
    birthDate = birthDate?.let(LocalDate::parse),
    birthTime = birthTime?.let(LocalTime::parse),
    birthWeightGrams = birthWeightGrams,
    birthPlace = birthPlace,
)

fun FeedingEntryEntity.toFeedingEntry() = FeedingEntry(
    id = id,
    babyId = babyId,
    fedAtEpochMillis = fedAt,
    feedType = feedType,
    breastMl = breastMl,
    formulaMl = formulaMl,
    amountMl = amountMl,
    hadUrine = hadUrine,
    hadStool = hadStool,
    diaperChanged = diaperChanged,
    note = note,
)

fun PumpSessionEntity.toPumpSession() = PumpSession(
    id = id,
    startedAtEpochMillis = startedAt,
    endedAtEpochMillis = endedAt,
    side = side,
    amountMl = amountMl,
    note = note,
    pausedMillis = pausedMillis,
    pausedAtEpochMillis = pausedAt,
)

fun VitaminDoseEntity.toVitaminDose() = VitaminDose(
    id = id,
    babyId = babyId,
    givenAtEpochMillis = givenAt,
    kind = kind,
    note = note,
)

fun DiaperChangeEntity.toDiaperChange() = DiaperChange(
    id = id,
    babyId = babyId,
    changedAtEpochMillis = changedAt,
    hadUrine = hadUrine,
    hadStool = hadStool,
    note = note,
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
    activeBabyId = activeBabyId,
    feedIntervalMinutes = feedIntervalMinutes,
    pumpIntervalMinutes = pumpIntervalMinutes,
    vitaminDMinuteOfDay = vitaminDMinuteOfDay,
    sync = record.toSyncMeta(workspaceId, now),
)

fun Baby.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = BabyEntity(
    id = id,
    name = name,
    dueDate = dueDate?.toString(),
    birthDate = birthDate?.toString(),
    birthTime = birthTime?.toString(),
    birthWeightGrams = birthWeightGrams,
    birthPlace = birthPlace,
    sync = record.toSyncMeta(workspaceId, now),
)

fun FeedingEntry.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = FeedingEntryEntity(
    id = id,
    babyId = babyId,
    fedAt = fedAtEpochMillis,
    feedType = feedType,
    breastMl = breastMl,
    formulaMl = formulaMl,
    // Written from the total rather than carried across: a record that arrived from an older
    // build has only `amountMl`, and a newer one may have two amounts and a stale mirror.
    amountMl = totalMl,
    hadUrine = hadUrine,
    hadStool = hadStool,
    diaperChanged = diaperChanged,
    note = note,
    sync = record.toSyncMeta(workspaceId, now),
)

fun PumpSession.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = PumpSessionEntity(
    id = id,
    startedAt = startedAtEpochMillis,
    endedAt = endedAtEpochMillis,
    side = side,
    amountMl = amountMl,
    note = note,
    pausedMillis = pausedMillis,
    pausedAt = pausedAtEpochMillis,
    sync = record.toSyncMeta(workspaceId, now),
)

fun VitaminDose.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = VitaminDoseEntity(
    id = id,
    babyId = babyId,
    givenAt = givenAtEpochMillis,
    kind = kind,
    note = note,
    sync = record.toSyncMeta(workspaceId, now),
)

fun DiaperChange.toEntity(workspaceId: String, record: RemoteRecord, now: Long) = DiaperChangeEntity(
    id = id,
    babyId = babyId,
    changedAt = changedAtEpochMillis,
    hadUrine = hadUrine,
    hadStool = hadStool,
    note = note,
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
