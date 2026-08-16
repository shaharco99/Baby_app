package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        Index(value = ["workspace_id", "folder_id"]),
        Index(value = ["workspace_id", "task_id"]),
        Index(value = ["workspace_id", "cycle_id"]),
        Index(value = ["workspace_id", "shopping_item_id"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "cycle_id") val cycleId: String?,
    @ColumnInfo(name = "shopping_item_id") val shoppingItemId: String?,
    val name: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val sha256: String,
    @ColumnInfo(name = "thumbnail_base64") val thumbnailBase64: String?,
    @Embedded val sync: SyncMetaEntity,
)
