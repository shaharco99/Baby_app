package com.oryareach.core.database.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.room.withTransaction
import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import com.oryareach.core.crypto.RecordCipher
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.DocumentEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toDocument
import com.oryareach.core.model.Document
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.sync.DocumentBlobStore
import com.oryareach.core.sync.SyncTrigger
import com.oryareach.core.sync.WorkspaceKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * The write path for documents. Unlike every other entity, a document has two payloads: the
 * small metadata record (synced through the normal `records` table, like a task or folder)
 * and the file bytes themselves, encrypted the same way but stored in Supabase Storage at a
 * deterministic `{workspaceId}/{documentId}` path — too large to fit the `records` row shape,
 * and there is no reason to hold file bytes in memory just to route them through
 * [com.oryareach.core.sync.RecordCodec]'s JSON layer.
 */
class DocumentRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val blobStore: DocumentBlobStore,
    private val keys: WorkspaceKeyProvider,
    private val cipher: RecordCipher = RecordCipher(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val documents get() = database.documentDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeInFolder(workspaceId: String, folderId: String?): Flow<List<Document>> =
        documents.observeInFolder(workspaceId, folderId).map { list -> list.map { it.toDocument() } }

    fun observeForTask(workspaceId: String, taskId: String): Flow<List<Document>> =
        documents.observeForTask(workspaceId, taskId).map { list -> list.map { it.toDocument() } }

    fun observeForCycle(workspaceId: String, cycleId: String): Flow<List<Document>> =
        documents.observeForCycle(workspaceId, cycleId).map { list -> list.map { it.toDocument() } }

    fun observeForShoppingItem(workspaceId: String, shoppingItemId: String): Flow<List<Document>> =
        documents.observeForShoppingItem(workspaceId, shoppingItemId).map { list -> list.map { it.toDocument() } }

    suspend fun upload(
        workspaceId: String,
        userId: String,
        folderId: String? = null,
        taskId: String? = null,
        cycleId: String? = null,
        shoppingItemId: String? = null,
        name: String,
        mimeType: String,
        bytes: ByteArray,
    ): AppResult<Document> {
        val key = keys.current() ?: return AppResult.Failure(AppError.Crypto.KeyUnavailable)
        val id = newId()

        val ciphertext = cipher.encrypt(key, bytes, associatedData(id))
        val uploaded = blobStore.upload("$workspaceId/$id", ciphertext)
        if (uploaded is AppResult.Failure) return AppResult.Failure(uploaded.error)

        val timestamp = now()
        val entity = DocumentEntity(
            id = id,
            folderId = folderId,
            taskId = taskId,
            cycleId = cycleId,
            shoppingItemId = shoppingItemId,
            name = name,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256Hex(ciphertext),
            thumbnailBase64 = generateThumbnail(bytes, mimeType),
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
        return AppResult.Success(entity.toDocument())
    }

    /** Downloads, verifies the ciphertext hash, then decrypts — in that order, so a corrupted
     * or tampered blob is caught before it is ever handed to the cipher. */
    suspend fun download(id: String): AppResult<ByteArray> {
        val entity = documents.findById(id) ?: return AppResult.Failure(AppError.Unexpected("document not found"))
        val key = keys.current() ?: return AppResult.Failure(AppError.Crypto.KeyUnavailable)

        val downloaded = blobStore.download("${entity.sync.workspaceId}/$id")
        val ciphertext = when (downloaded) {
            is AppResult.Failure -> return AppResult.Failure(downloaded.error)
            is AppResult.Success -> downloaded.data
        }

        if (!sha256Hex(ciphertext).equals(entity.sha256, ignoreCase = true)) {
            return AppResult.Failure(AppError.Crypto.DecryptionFailed)
        }

        return cipher.decrypt(key, ciphertext, associatedData(id))
    }

    suspend fun rename(id: String, name: String) {
        val existing = documents.findById(id) ?: return
        enqueue(withBumpedSync(existing.copy(name = name)), SyncOperationType.UPDATE)
    }

    suspend fun move(id: String, folderId: String?) {
        val existing = documents.findById(id) ?: return
        enqueue(withBumpedSync(existing.copy(folderId = folderId)), SyncOperationType.UPDATE)
    }

    suspend fun delete(id: String) {
        val timestamp = now()
        database.withTransaction {
            documents.softDelete(id, timestamp)
            search.remove(id)
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = id,
                    entityType = EntityType.DOCUMENT,
                    operation = SyncOperationType.DELETE,
                    clientMutationId = newId(),
                    createdAt = timestamp,
                ),
            )
            operations.removeSuperseded(id, opId)
        }
        syncTrigger.syncNow()
        // The blob itself is left in Storage — deletion propagates through this tombstone,
        // same as every other entity; reclaiming orphaned blobs is a background-job concern,
        // not something a foreground delete should block on.
    }

    /** Downsamples an image to a small preview before it's ever encrypted — `bytes` here is
     * the plaintext, still in memory for the encrypt call right after this one, so generating
     * the thumbnail costs no extra decrypt/fetch. `inSampleSize` decoding (not decode-then-scale)
     * keeps peak memory bounded even for a large photo. Anything not an image, or any image
     * this device's decoder can't handle, just gets no thumbnail — a missing preview is a
     * cosmetic fallback (the generic file icon), never a failure that should block the upload. */
    private fun generateThumbnail(bytes: ByteArray, mimeType: String): String? {
        if (!mimeType.startsWith("image/")) return null

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION &&
                bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION
            ) {
                sampleSize *= 2
            }

            val sampled = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return null

            val scale = THUMBNAIL_MAX_DIMENSION.toFloat() / maxOf(sampled.width, sampled.height)
            val thumbnail = if (scale < 1f) {
                Bitmap.createScaledBitmap(sampled, (sampled.width * scale).toInt(), (sampled.height * scale).toInt(), true)
            } else {
                sampled
            }

            val output = ByteArrayOutputStream()
            thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun withBumpedSync(entity: DocumentEntity): DocumentEntity = entity.copy(
        sync = entity.sync.copy(
            updatedAt = now(),
            syncStatus = SyncStatus.PENDING_UPDATE,
            clientMutationId = newId(),
        ),
    )

    private fun associatedData(id: String): ByteArray = "${EntityType.DOCUMENT.wireName}:$id".toByteArray(Charsets.UTF_8)

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private suspend fun enqueue(entity: DocumentEntity, operation: SyncOperationType) {
        database.withTransaction {
            documents.upsert(entity)
            search.index(EntityType.DOCUMENT, entity.id, entity.sync.workspaceId, entity.name, "")
            val opId = operations.enqueue(
                SyncOperationEntity(
                    recordId = entity.id,
                    entityType = EntityType.DOCUMENT,
                    operation = operation,
                    clientMutationId = entity.sync.clientMutationId ?: newId(),
                    createdAt = now(),
                ),
            )
            operations.removeSuperseded(entity.id, opId)
        }
        syncTrigger.syncNow()
    }

    private companion object {
        const val THUMBNAIL_MAX_DIMENSION = 160
        const val THUMBNAIL_JPEG_QUALITY = 70
    }
}
