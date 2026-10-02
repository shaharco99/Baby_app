package com.oryareach.core.domain.identity

/**
 * Israeli ID numbers (teudat zehut): nine digits, the last a check digit.
 *
 * Shorter numbers exist on older cards and are the same number with leading zeros left off, so
 * they are padded before checking rather than rejected. A failed check is shown as a warning, not
 * a refusal — the point is to catch a mistyped digit, and the couple may also store a number that
 * is not Israeli.
 */
const val ID_NUMBER_LENGTH = 9

/** Digits only, at most [ID_NUMBER_LENGTH] of them — what an ID field keeps of what is typed. */
fun idNumberInput(raw: String): String = raw.filter(Char::isDigit).take(ID_NUMBER_LENGTH)

/**
 * True when [id] passes the check digit. Each digit is multiplied by 1 and 2 in turn, a two-digit
 * product has its digits added, and the total must be a multiple of ten.
 */
fun isValidIsraeliId(id: String): Boolean {
    if (id.isEmpty() || id.length > ID_NUMBER_LENGTH || !id.all(Char::isDigit)) return false
    val padded = id.padStart(ID_NUMBER_LENGTH, '0')
    if (padded.all { it == '0' }) return false
    val sum = padded.mapIndexed { index, char ->
        val product = (char - '0') * (if (index % 2 == 0) 1 else 2)
        if (product > 9) product - 9 else product
    }.sum()
    return sum % 10 == 0
}
