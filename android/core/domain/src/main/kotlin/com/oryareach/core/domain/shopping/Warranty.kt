package com.oryareach.core.domain.shopping

import com.oryareach.core.model.ShoppingItem
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The day a warranty runs out: [purchaseDate] plus [warrantyMonths] months. Null unless both
 * inputs are present and the period is positive — a read-only value, never stored.
 */
fun warrantyEndDate(purchaseDate: LocalDate?, warrantyMonths: Int?): LocalDate? {
    if (purchaseDate == null || warrantyMonths == null || warrantyMonths <= 0) return null
    return purchaseDate.plus(DatePeriod(months = warrantyMonths))
}

fun ShoppingItem.warrantyEndDate(): LocalDate? = warrantyEndDate(purchaseDate, warrantyMonths)
