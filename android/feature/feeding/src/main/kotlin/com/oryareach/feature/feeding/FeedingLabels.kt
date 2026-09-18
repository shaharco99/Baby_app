package com.oryareach.feature.feeding

import androidx.annotation.StringRes
import com.oryareach.core.model.FeedType

@StringRes
internal fun FeedType.labelRes(): Int = when (this) {
    FeedType.BREAST_MILK -> R.string.feeding_type_breast_milk
    FeedType.FORMULA -> R.string.feeding_type_formula
    FeedType.SOLID -> R.string.feeding_type_solid
}

@StringRes
internal fun HistoryView.labelRes(): Int = when (this) {
    HistoryView.LIST -> R.string.feeding_view_list
    HistoryView.TABLE -> R.string.feeding_view_table
}
