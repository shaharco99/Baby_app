package com.oryareach.core.domain.importer

import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.ImportantDate
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus
import com.oryareach.core.model.Task
import com.oryareach.core.model.TaskCategory
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * The web app stored these Hebrew strings directly as data (see `src/types/models.ts`); the
 * Android app uses enums with bilingual `@StringRes` labels instead. This table is the one
 * place that bridges the two — every category/priority/assignee/status literal the web export
 * can contain must be listed here, or an imported record silently falls back to "Other".
 */
private val shoppingCategoryByHebrew = mapOf(
    "תינוקייה" to ShoppingCategory.NURSERY,
    "בגדים" to ShoppingCategory.CLOTHING,
    "האכלה" to ShoppingCategory.FEEDING,
    "טיפוח ובריאות" to ShoppingCategory.CARE_AND_HEALTH,
    "בטיחות" to ShoppingCategory.SAFETY,
    "ציוד ליולדת" to ShoppingCategory.MATERNITY_SUPPLIES,
    "אחר" to ShoppingCategory.OTHER,
)

private val taskCategoryByHebrew = mapOf(
    "הכנת הבית" to TaskCategory.HOME_PREP,
    "מסמכים וביטוח" to TaskCategory.DOCUMENTS_AND_INSURANCE,
    "רפואי" to TaskCategory.MEDICAL,
    "תיק ליולדת" to TaskCategory.HOSPITAL_BAG,
    "לתינוק" to TaskCategory.FOR_THE_BABY,
    "אחר" to TaskCategory.OTHER,
)

private val assigneeByHebrew = mapOf(
    "שחר" to Assignee.PARTNER_ONE,
    "טופז" to Assignee.PARTNER_TWO,
    "שניהם" to Assignee.BOTH,
)

private fun priorityOf(raw: String): Priority = when (raw) {
    "low" -> Priority.LOW
    "high" -> Priority.HIGH
    else -> Priority.NORMAL
}

private fun statusOf(raw: String): ShoppingStatus = when (raw) {
    "ordered" -> ShoppingStatus.ORDERED
    "bought" -> ShoppingStatus.BOUGHT
    else -> ShoppingStatus.NEED
}

data class ImportedSnapshot(
    val settings: AppSettings,
    val tasks: List<Task>,
    val shoppingItems: List<ShoppingItem>,
    val importantDates: List<ImportantDate>,
)

private val importJson = Json { ignoreUnknownKeys = true }

/** Structural check before the file is trusted, mirroring `isValidAppSnapshot` on the web side. */
fun parseWebSnapshot(json: String): WebSnapshot? = runCatching {
    importJson.decodeFromString<WebSnapshot>(json).takeIf { it.version == 1 }
}.getOrNull()

fun WebSnapshot.toImportedSnapshot(newId: () -> String): ImportedSnapshot = ImportedSnapshot(
    settings = AppSettings(
        id = newId(),
        dueDate = LocalDate.parse(settings.dueDate),
        babyName = settings.babyName,
    ),
    tasks = tasks.map { it.toTask(newId) },
    shoppingItems = shoppingItems.map { it.toShoppingItem(newId) },
    importantDates = importantDates.map { it.toImportantDate(newId) },
)

private fun WebTask.toTask(newId: () -> String) = Task(
    id = newId(),
    title = title,
    category = taskCategoryByHebrew[category] ?: TaskCategory.OTHER,
    priority = priorityOf(priority),
    done = done,
    dueDate = dueDate?.let(LocalDate::parse),
    assignee = assigneeByHebrew[assignee],
    note = note,
)

private fun WebShoppingItem.toShoppingItem(newId: () -> String): ShoppingItem {
    // Alternatives get fresh ids too; chosenAlternativeId must be re-keyed alongside them
    // rather than carried over verbatim, or it would point at an id that no longer exists.
    val idsBySourceId = alternatives.associate { it.id to newId() }
    val newAlternatives = alternatives.map { alt ->
        ShoppingAlternative(
            id = idsBySourceId.getValue(alt.id),
            name = alt.name,
            price = alt.price,
            link = alt.link,
            note = alt.note,
        )
    }

    return ShoppingItem(
        id = newId(),
        name = name,
        category = shoppingCategoryByHebrew[category] ?: ShoppingCategory.OTHER,
        estimatedPrice = estimatedPrice,
        actualPrice = actualPrice,
        priority = priorityOf(priority),
        status = statusOf(status),
        assignee = assigneeByHebrew[assignee],
        note = note,
        link = link,
        alternatives = newAlternatives,
        chosenAlternativeId = chosenAlternativeId?.let { idsBySourceId[it] },
    )
}

private fun WebImportantDate.toImportantDate(newId: () -> String) = ImportantDate(
    id = newId(),
    date = LocalDate.parse(date),
    title = title,
    wish = wish,
)
