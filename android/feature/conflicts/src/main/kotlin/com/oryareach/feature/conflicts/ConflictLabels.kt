package com.oryareach.feature.conflicts

import androidx.annotation.StringRes
import com.oryareach.core.model.EntityType

@StringRes
internal fun EntityType.labelRes(): Int = when (this) {
    EntityType.TASK -> R.string.conflicts_type_task
    EntityType.SHOPPING_ITEM -> R.string.conflicts_type_shopping_item
    EntityType.IMPORTANT_DATE -> R.string.conflicts_type_important_date
    EntityType.FOLDER -> R.string.conflicts_type_folder
    EntityType.DOCUMENT -> R.string.conflicts_type_document
    EntityType.CYCLE -> R.string.conflicts_type_cycle
    EntityType.CYCLE_ENTRY -> R.string.conflicts_type_cycle_entry
    EntityType.SETTINGS -> R.string.conflicts_type_settings
    EntityType.BABY -> R.string.conflicts_type_baby
    EntityType.FEEDING_ENTRY -> R.string.conflicts_type_feeding_entry
    EntityType.PUMP_SESSION -> R.string.conflicts_type_pump_session
    EntityType.VITAMIN_DOSE -> R.string.conflicts_type_vitamin_dose
    EntityType.DIAPER_CHANGE -> R.string.conflicts_type_diaper_change
}
