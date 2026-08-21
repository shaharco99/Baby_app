package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingStatus
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_items",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        Index(value = ["workspace_id", "category"]),
    ],
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: ShoppingCategory,
    @ColumnInfo(name = "estimated_price") val estimatedPrice: Double?,
    @ColumnInfo(name = "actual_price") val actualPrice: Double?,
    val priority: Priority,
    val status: ShoppingStatus,
    val assignee: Assignee?,
    @ColumnInfo(name = "custom_assignee_name") val customAssigneeName: String?,
    val note: String?,
    val link: String?,
    /** Serialized as JSON — a handful of nested alternatives per item, not a queryable list. */
    val alternatives: List<ShoppingAlternative>,
    @ColumnInfo(name = "chosen_alternative_id") val chosenAlternativeId: String?,
    @Embedded val sync: SyncMetaEntity,
)
