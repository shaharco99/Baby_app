package com.oryareach.feature.search

import androidx.annotation.StringRes
import com.oryareach.core.model.EntityType

@StringRes
internal fun EntityType.labelRes(): Int = when (this) {
    EntityType.TASK -> R.string.search_type_task
    EntityType.SHOPPING_ITEM -> R.string.search_type_shopping_item
    EntityType.IMPORTANT_DATE -> R.string.search_type_important_date
    EntityType.FOLDER -> R.string.search_type_folder
    EntityType.DOCUMENT -> R.string.search_type_document
    EntityType.CYCLE -> R.string.search_type_cycle
    EntityType.CYCLE_ENTRY -> R.string.search_type_cycle_entry
    EntityType.FEEDING_ENTRY -> R.string.search_type_feeding_entry
    EntityType.PUMP_SESSION -> R.string.search_type_pump_session
    // Unreachable in practice: settings and children are never indexed (SearchIndexer is never
    // called for them — there's exactly one settings row per workspace, and a child's record is
    // a name and some birth details the switcher already shows). Still needs a branch since
    // EntityType is exhaustive here.
    EntityType.SETTINGS, EntityType.BABY -> R.string.search_type_other
}
