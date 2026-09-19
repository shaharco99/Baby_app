package com.oryareach.feature.pumping

import androidx.annotation.StringRes
import com.oryareach.core.model.PumpSide

@StringRes
internal fun PumpSide.labelRes(): Int = when (this) {
    PumpSide.LEFT -> R.string.pumping_side_left
    PumpSide.RIGHT -> R.string.pumping_side_right
    PumpSide.BOTH -> R.string.pumping_side_both
}

@StringRes
internal fun PumpHistoryView.labelRes(): Int = when (this) {
    PumpHistoryView.LIST -> R.string.pumping_view_list
    PumpHistoryView.TABLE -> R.string.pumping_view_table
}
