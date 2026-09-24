package com.oryareach.core.database.importer

import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.ImportantDateRepository
import com.oryareach.core.database.repository.ShoppingItemRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.domain.importer.parseWebSnapshot
import com.oryareach.core.domain.importer.toImportedSnapshot
import kotlinx.coroutines.flow.first

/** What an import came to: how much was added, or that the file was not an export at all. */
sealed interface WebImportOutcome {
    data class Success(val taskCount: Int, val shoppingCount: Int, val dateCount: Int) : WebImportOutcome
    data object InvalidFile : WebImportOutcome
}

/**
 * Brings a JSON export from the retired web app into the workspace.
 *
 * Lives here rather than in a feature so whichever screen offers it — Settings, today — does not
 * have to own five repositories' worth of writes. Every write goes through the ordinary
 * repositories, so imported rows encrypt and sync like anything typed in.
 *
 * Re-running it is safe: tasks and shopping items whose name is already there, and dates with
 * the same title on the same day, are skipped rather than duplicated.
 */
class WebImporter(
    private val settings: AppSettingsRepository,
    private val tasks: TaskRepository,
    private val shopping: ShoppingItemRepository,
    private val dates: ImportantDateRepository,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    suspend fun import(workspaceId: String, userId: String, json: String): WebImportOutcome {
        val snapshot = parseWebSnapshot(json) ?: return WebImportOutcome.InvalidFile
        val imported = snapshot.toImportedSnapshot(newId)

        // The partner names are this app's, not the export's; carried over so saving the
        // imported due date does not blank them.
        val current = settings.observe(workspaceId).first()
        settings.save(
            workspaceId = workspaceId,
            userId = userId,
            dueDate = imported.settings.dueDate,
            babyName = imported.settings.babyName,
            partnerOneName = current?.partnerOneName,
            partnerTwoName = current?.partnerTwoName,
        )

        val existingTasks = tasks.observeAll(workspaceId).first().map { it.title.key() }.toSet()
        val newTasks = imported.tasks.filter { it.title.key() !in existingTasks }
        newTasks.forEach { task ->
            tasks.create(
                workspaceId = workspaceId,
                userId = userId,
                title = task.title,
                category = task.category,
                priority = task.priority,
                assignee = task.assignee,
                note = task.note,
                done = task.done,
            )
        }

        val existingItems = shopping.observeAll(workspaceId).first().map { it.name.key() }.toSet()
        val newItems = imported.shoppingItems.filter { it.name.key() !in existingItems }
        newItems.forEach { item ->
            shopping.create(
                workspaceId = workspaceId,
                userId = userId,
                name = item.name,
                category = item.category,
                estimatedPrice = item.estimatedPrice,
                priority = item.priority,
                assignee = item.assignee,
                note = item.note,
                link = item.link,
            )
        }

        val existingDates = dates.observeAll(workspaceId).first().map { "${it.title.key()}|${it.date}" }.toSet()
        val newDates = imported.importantDates.filter { "${it.title.key()}|${it.date}" !in existingDates }
        newDates.forEach { date -> dates.create(workspaceId, userId, date.date, date.title, date.wish) }

        return WebImportOutcome.Success(newTasks.size, newItems.size, newDates.size)
    }

    private fun String.key(): String = trim().lowercase()
}
