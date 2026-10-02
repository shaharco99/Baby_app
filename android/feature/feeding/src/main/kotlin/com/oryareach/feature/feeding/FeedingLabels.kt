package com.oryareach.feature.feeding

import androidx.annotation.StringRes
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.PumpSide

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

@StringRes
internal fun PumpSide.labelRes(): Int = when (this) {
    PumpSide.LEFT -> R.string.feeding_side_left
    PumpSide.RIGHT -> R.string.feeding_side_right
    PumpSide.BOTH -> R.string.feeding_side_both
}

@StringRes
internal fun FeedKind.labelRes(): Int = when (this) {
    FeedKind.NURSING -> R.string.feeding_kind_nursing
    FeedKind.BOTTLE -> R.string.feeding_kind_bottle
    FeedKind.SOLID -> R.string.feeding_type_solid
}
