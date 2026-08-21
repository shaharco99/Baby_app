package com.oryareach.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds shopping items and important dates — Phase 3's port of the web app's remaining data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_items` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `estimated_price` INTEGER,
                `actual_price` INTEGER,
                `priority` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `assignee` TEXT,
                `note` TEXT,
                `link` TEXT,
                `alternatives` TEXT NOT NULL,
                `chosen_alternative_id` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_sync_status` ON `shopping_items` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_updated_at` " +
                "ON `shopping_items` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_category` " +
                "ON `shopping_items` (`workspace_id`, `category`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `important_dates` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `wish` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_important_dates_sync_status` ON `important_dates` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_important_dates_workspace_id_updated_at` " +
                "ON `important_dates` (`workspace_id`, `updated_at`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_important_dates_date` ON `important_dates` (`date`)")
    }
}

/** Adds `app_settings` — the couple's shared due date and baby name, for the home dashboard. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `id` TEXT NOT NULL,
                `dueDate` TEXT NOT NULL,
                `babyName` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_settings_sync_status` ON `app_settings` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_app_settings_workspace_id_updated_at` " +
                "ON `app_settings` (`workspace_id`, `updated_at`)",
        )
    }
}

/** Adds `folders` — the shared document tree (Phase 4). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `folders` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `parent_id` TEXT,
                `path` TEXT NOT NULL,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_sync_status` ON `folders` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_workspace_id_updated_at` " +
                "ON `folders` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_workspace_id_parent_id` " +
                "ON `folders` (`workspace_id`, `parent_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_workspace_id_path` " +
                "ON `folders` (`workspace_id`, `path`)",
        )
    }
}

/** Adds `documents` — file metadata; the bytes themselves live in Supabase Storage. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `documents` (
                `id` TEXT NOT NULL,
                `folder_id` TEXT,
                `name` TEXT NOT NULL,
                `mime_type` TEXT NOT NULL,
                `size_bytes` INTEGER NOT NULL,
                `sha256` TEXT NOT NULL,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_sync_status` ON `documents` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_updated_at` " +
                "ON `documents` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_folder_id` " +
                "ON `documents` (`workspace_id`, `folder_id`)",
        )
    }
}

/** Adds `task_id` — lets a document be attached to a task independently of folder placement. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documents` ADD COLUMN `task_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_task_id` " +
                "ON `documents` (`workspace_id`, `task_id`)",
        )
    }
}

/** Adds `cycle_entries` (daily flow/symptoms/mood/pain/note logs, distinct from a period's
 * start/end in `menstrual_cycles`) and `documents.cycle_id`, so a document can be attached to
 * a logged period the same way one can already be attached to a task. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cycle_entries` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `flow` TEXT,
                `symptoms` TEXT NOT NULL,
                `mood` TEXT NOT NULL,
                `pain` TEXT,
                `note` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_entries_sync_status` ON `cycle_entries` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_entries_workspace_id_updated_at` " +
                "ON `cycle_entries` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_entries_workspace_id_date` " +
                "ON `cycle_entries` (`workspace_id`, `date`)",
        )

        db.execSQL("ALTER TABLE `documents` ADD COLUMN `cycle_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_cycle_id` " +
                "ON `documents` (`workspace_id`, `cycle_id`)",
        )
    }
}

/** Creates the FTS4 search index (see `SearchIndexEntity`'s doc comment for why FTS4, not the
 * FTS5 the ADR names — Room has no `@Fts5` annotation) and backfills it from every
 * already-synced row, so search works immediately rather than only for things edited after
 * the upgrade. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `search_index` USING FTS4(
                `entityType` UNINDEXED,
                `recordId` UNINDEXED,
                `workspaceId` UNINDEXED,
                `title`,
                `body`
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'task', id, workspace_id, title, COALESCE(note, '')
            FROM tasks WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'shopping_item', id, workspace_id, name, COALESCE(note, '')
            FROM shopping_items WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'important_date', id, workspace_id, title, COALESCE(wish, '')
            FROM important_dates WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'folder', id, workspace_id, name, ''
            FROM folders WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'document', id, workspace_id, name, ''
            FROM documents WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'cycle', id, workspace_id, '', COALESCE(note, '')
            FROM menstrual_cycles WHERE deleted_at IS NULL AND note IS NOT NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'cycle_entry', id, workspace_id, '', COALESCE(note, '')
            FROM cycle_entries WHERE deleted_at IS NULL AND note IS NOT NULL
            """.trimIndent(),
        )
    }
}

/** Adds recurrence to tasks — `null`/`null` means "does not repeat", the default for every
 * existing row. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence_frequency` TEXT")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence_interval` INTEGER")
    }
}

/** Adds free-form tags to tasks, stored as a JSON array (same pattern as `cycle_entries`'
 * `symptoms`/`mood`) — `'[]'` for every existing row. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
    }
}

/** Adds a small on-device-generated thumbnail for image documents — see `Document`'s doc
 * comment for why this rides along in the metadata record instead of being fetched from
 * Storage. Null for every existing row and every non-image document; nothing backfills it,
 * since there is no thumbnail to generate without the original bytes in hand (which would
 * mean downloading and decrypting every existing image document during a migration — an
 * unbounded, network-dependent operation a schema migration must never do). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documents` ADD COLUMN `thumbnail_base64` TEXT")
    }
}

/** `estimated_price`/`actual_price` were `INTEGER`, so a form entry like "5804.25" got its
 * decimal point stripped before parsing and landed as 580425. Widens both columns (and the
 * JSON-serialized `alternatives.price` field, which needs no migration) to `REAL`. SQLite has
 * no `ALTER COLUMN`, so the table is rebuilt. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `shopping_items_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `estimated_price` REAL,
                `actual_price` REAL,
                `priority` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `assignee` TEXT,
                `note` TEXT,
                `link` TEXT,
                `alternatives` TEXT NOT NULL,
                `chosen_alternative_id` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `shopping_items_new`
            SELECT `id`, `name`, `category`, `estimated_price`, `actual_price`, `priority`, `status`,
                `assignee`, `note`, `link`, `alternatives`, `chosen_alternative_id`, `workspace_id`,
                `created_by`, `created_at`, `updated_at`, `deleted_at`, `version`, `sync_status`,
                `client_mutation_id`
            FROM `shopping_items`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `shopping_items`")
        db.execSQL("ALTER TABLE `shopping_items_new` RENAME TO `shopping_items`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_sync_status` ON `shopping_items` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_updated_at` " +
                "ON `shopping_items` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_category` " +
                "ON `shopping_items` (`workspace_id`, `category`)",
        )
    }
}

/** Lets a document attach to a shopping item — e.g. a receipt photo — the same way one already
 * attaches to a task or a cycle entry. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documents` ADD COLUMN `shopping_item_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_shopping_item_id` " +
                "ON `documents` (`workspace_id`, `shopping_item_id`)",
        )
    }
}

/** Adds `cached_calendar_events` — the Google Calendar read-only integration's local cache
 * (phase 1, docs/specs/03-google-calendar-integration.md). Deliberately not a synced entity: no
 * `sync_status`/`workspace_id`/etc columns, and not part of [RoomSyncStore]'s `EntityType` set —
 * see [CachedCalendarEventEntity]'s doc comment. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_calendar_events` (
                `id` TEXT NOT NULL,
                `calendar_id` TEXT NOT NULL,
                `event_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `start_at` TEXT NOT NULL,
                `end_at` TEXT NOT NULL,
                `all_day` INTEGER NOT NULL,
                `fetched_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_calendar_events_calendar_id` " +
                "ON `cached_calendar_events` (`calendar_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_calendar_events_start_at` " +
                "ON `cached_calendar_events` (`start_at`)",
        )
    }
}

/** Lets the couple name themselves (`app_settings`) instead of seeing "Partner 1"/"Partner 2",
 * and lets a shopping item's "other" assignee carry an optional name instead of the generic
 * label (`shopping_items`) — see [Assignee]'s doc comment. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `partner_one_name` TEXT")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `partner_two_name` TEXT")
        db.execSQL("ALTER TABLE `shopping_items` ADD COLUMN `custom_assignee_name` TEXT")
    }
}
