package com.oryareach.core.model

import kotlinx.serialization.Serializable

/**
 * Metadata for one uploaded file. The bytes themselves live in Supabase Storage, encrypted,
 * at a deterministic `{workspaceId}/{id}` path — never inside this record.
 */
@Serializable
data class Document(
    val id: String,
    val folderId: String? = null,
    /** Set when this document is attached to a task rather than (or in addition to) filed in
     * a folder — a task attachment and a folder placement are independent. */
    val taskId: String? = null,
    /** Set when attached to a logged period — e.g. an ultrasound report tied to that cycle. */
    val cycleId: String? = null,
    /** Set when this document is a receipt (or similar) attached to a shopping item. */
    val shoppingItemId: String? = null,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** SHA-256 of the *ciphertext*, hex-encoded — integrity can be checked without decrypting. */
    val sha256: String,
    /**
     * A small downsampled preview, Base64-encoded JPEG, only ever set for image documents
     * (an `image/` MIME type).
     * Generated once on-device at upload time and carried in this small metadata record (which
     * already syncs through the normal encrypted-JSON path) rather than fetched from Storage —
     * there is no cheap way to fetch "just a thumbnail" of an end-to-end encrypted blob; the
     * smallest fetchable unit is the whole file. Storing a tiny preview here means neither
     * device has to download and decrypt the full original just to render a folder listing.
     */
    val thumbnailBase64: String? = null,
)
