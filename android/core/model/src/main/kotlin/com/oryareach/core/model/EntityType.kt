package com.oryareach.core.model

/**
 * Discriminates encrypted payloads on the server. Values must stay in sync with the
 * `public.entity_type` enum in `supabase/migrations/0001_init.sql`, so the wire name is
 * pinned here rather than derived from the Kotlin name.
 */
enum class EntityType(val wireName: String) {
    TASK("task"),
    SHOPPING_ITEM("shopping_item"),
    IMPORTANT_DATE("important_date"),
    FOLDER("folder"),
    DOCUMENT("document"),
    CYCLE("cycle"),
    CYCLE_ENTRY("cycle_entry"),
    SETTINGS("settings"),
    BABY("baby"),
    FEEDING_ENTRY("feeding_entry"),
    ;

    companion object {
        private val byWireName = entries.associateBy(EntityType::wireName)

        fun fromWireName(value: String): EntityType? = byWireName[value]
    }
}

enum class SyncOperationType {
    CREATE,
    UPDATE,
    DELETE,
}
