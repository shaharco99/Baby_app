package com.oryareach.feature.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.ImportantDateRepository
import com.oryareach.core.database.repository.ShoppingItemRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.domain.importer.parseWebSnapshot
import com.oryareach.core.domain.importer.toImportedSnapshot
import com.oryareach.core.domain.pregnancy.dueDateFromLastPeriod
import com.oryareach.core.domain.pregnancy.getPregnancyProgress
import com.oryareach.core.domain.pregnancy.lastPeriodFromDueDate
import com.oryareach.core.domain.shopping.calculateBudget
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Stable
interface HomeActions {
    fun onEditDueDate()
    fun onDismissSheet()
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onLastPeriodChange(value: LocalDate)
    fun onBabyNameChange(value: String)
    fun onPartnerOneNameChange(value: String)
    fun onPartnerTwoNameChange(value: String)
    fun onSubmit()
    fun onImportJson(json: String)
    fun onDismissImportResult()
    fun onRefresh()
    fun onMoonLongPress()
    fun onDismissBookOfLove()
}

/**
 * The workspace id is read once, same as every other tab's ViewModel: routing already
 * guarantees a paired, unlocked device by the time this screen is reachable.
 */
class HomeViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val taskRepository: TaskRepository,
    private val shoppingRepository: ShoppingItemRepository,
    private val importantDateRepository: ImportantDateRepository,
    private val auth: AuthRepository,
    private val syncEngine: SyncEngine,
    private val workspaceId: () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) : ViewModel(), HomeActions {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                combine(
                    settingsRepository.observe(id),
                    taskRepository.observeAll(id),
                    shoppingRepository.observeAll(id),
                ) { settings, tasks, items ->
                    val budget = calculateBudget(items)
                    HomeUiState(
                        dueDate = settings?.dueDate,
                        babyName = settings?.babyName,
                        partnerOneName = settings?.partnerOneName,
                        partnerTwoName = settings?.partnerTwoName,
                        openTaskCount = tasks.count { !it.done },
                        budgetEstimated = budget.estimatedTotal,
                        budgetSpent = budget.spentTotal,
                        progress = settings?.dueDate?.let { getPregnancyProgress(it, today()) },
                    )
                }.collect { computed ->
                    set { current ->
                        computed.copy(
                            sheetVisible = current.sheetVisible,
                            datePickerVisible = current.datePickerVisible,
                            editingLastPeriodDate = current.editingLastPeriodDate,
                            editingBabyName = current.editingBabyName,
                            editingPartnerOneName = current.editingPartnerOneName,
                            editingPartnerTwoName = current.editingPartnerTwoName,
                        )
                    }
                }
            }
        }
    }

    override fun onEditDueDate() = set {
        it.copy(
            sheetVisible = true,
            editingLastPeriodDate = it.dueDate?.let(::lastPeriodFromDueDate),
            editingBabyName = it.babyName.orEmpty(),
            editingPartnerOneName = it.partnerOneName.orEmpty(),
            editingPartnerTwoName = it.partnerTwoName.orEmpty(),
        )
    }

    override fun onDismissSheet() = set { it.copy(sheetVisible = false) }
    override fun onOpenDatePicker() = set { it.copy(datePickerVisible = true) }
    override fun onDismissDatePicker() = set { it.copy(datePickerVisible = false) }
    override fun onLastPeriodChange(value: LocalDate) = set {
        it.copy(editingLastPeriodDate = value, datePickerVisible = false)
    }
    override fun onBabyNameChange(value: String) = set { it.copy(editingBabyName = value) }
    override fun onPartnerOneNameChange(value: String) = set { it.copy(editingPartnerOneName = value) }
    override fun onPartnerTwoNameChange(value: String) = set { it.copy(editingPartnerTwoName = value) }

    override fun onSubmit() {
        val state = _uiState.value
        val workspace = workspaceId() ?: return
        val lastPeriodDate = state.editingLastPeriodDate ?: return

        viewModelScope.launch {
            settingsRepository.save(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                dueDate = dueDateFromLastPeriod(lastPeriodDate),
                babyName = state.editingBabyName.ifBlank { null },
                partnerOneName = state.editingPartnerOneName.ifBlank { null },
                partnerTwoName = state.editingPartnerTwoName.ifBlank { null },
            )
            set { it.copy(sheetVisible = false) }
        }
    }

    /**
     * Additive and re-runnable: tasks/items/dates are skipped when something with the same
     * title (and, for dates, the same day) already exists, same dedup approach as the
     * hospital-bag preset. Settings (due date, baby name) are always overwritten — importing
     * is a deliberate one-shot action, not a background merge.
     */
    override fun onImportJson(json: String) {
        val workspace = workspaceId() ?: return
        if (_uiState.value.importing) return
        set { it.copy(importing = true) }

        viewModelScope.launch {
            val snapshot = parseWebSnapshot(json)
            if (snapshot == null) {
                set { it.copy(importing = false, importResult = ImportResult.InvalidFile) }
                return@launch
            }

            val imported = snapshot.toImportedSnapshot(newId)
            val userId = auth.currentUserId().orEmpty()

            settingsRepository.save(
                workspaceId = workspace,
                userId = userId,
                dueDate = imported.settings.dueDate,
                babyName = imported.settings.babyName,
                partnerOneName = _uiState.value.partnerOneName,
                partnerTwoName = _uiState.value.partnerTwoName,
            )

            val existingTasks = taskTitlesSnapshot(workspace)
            var taskCount = 0
            imported.tasks.filter { it.title.trim().lowercase() !in existingTasks }.forEach { task ->
                taskRepository.create(
                    workspaceId = workspace,
                    userId = userId,
                    title = task.title,
                    category = task.category,
                    priority = task.priority,
                    assignee = task.assignee,
                    note = task.note,
                    done = task.done,
                )
                taskCount++
            }

            val existingItems = shoppingNamesSnapshot(workspace)
            var shoppingCount = 0
            imported.shoppingItems.filter { it.name.trim().lowercase() !in existingItems }.forEach { item ->
                shoppingRepository.create(
                    workspaceId = workspace,
                    userId = userId,
                    name = item.name,
                    category = item.category,
                    estimatedPrice = item.estimatedPrice,
                    priority = item.priority,
                    assignee = item.assignee,
                    note = item.note,
                    link = item.link,
                )
                shoppingCount++
            }

            val existingDates = dateKeysSnapshot(workspace)
            var dateCount = 0
            imported.importantDates.filter { "${it.title.trim().lowercase()}|${it.date}" !in existingDates }.forEach { date ->
                importantDateRepository.create(workspace, userId, date.date, date.title, date.wish)
                dateCount++
            }

            set {
                it.copy(
                    importing = false,
                    importResult = ImportResult.Success(taskCount, shoppingCount, dateCount),
                )
            }
        }
    }

    override fun onDismissImportResult() = set { it.copy(importResult = null) }

    /**
     * Book of Love easter egg: only surfaces when the partner looks "around right now". There's
     * no live presence channel (see `[DAO].lastActivityByOthers` doc comment), so this is a
     * recency proxy — the partner's most recent task/shopping edit within the last few minutes.
     */
    override fun onMoonLongPress() {
        val workspace = workspaceId() ?: return
        val selfUserId = auth.currentUserId() ?: return

        viewModelScope.launch {
            val lastActivity = maxOf(
                taskRepository.lastActivityByOthers(workspace, selfUserId) ?: 0L,
                shoppingRepository.lastActivityByOthers(workspace, selfUserId) ?: 0L,
            )
            val recentlyActive = lastActivity > 0L &&
                System.currentTimeMillis() - lastActivity <= PARTNER_RECENTLY_ACTIVE_WINDOW_MS
            if (recentlyActive) {
                set { it.copy(bookOfLoveVisible = true) }
            }
        }
    }

    override fun onDismissBookOfLove() = set { it.copy(bookOfLoveVisible = false) }

    /**
     * Pull-to-refresh: the app otherwise only syncs on a 6-hour periodic worker or right after
     * a local write, so there was no way to ask "check the partner's changes now" from the UI.
     * Calls SyncEngine directly rather than going through WorkManager/SyncTrigger, since this
     * needs to await completion to know when to stop spinning.
     */
    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }

        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private suspend fun taskTitlesSnapshot(workspace: String): Set<String> =
        taskRepository.observeAll(workspace).first().map { it.title.trim().lowercase() }.toSet()

    private suspend fun shoppingNamesSnapshot(workspace: String): Set<String> =
        shoppingRepository.observeAll(workspace).first().map { it.name.trim().lowercase() }.toSet()

    private suspend fun dateKeysSnapshot(workspace: String): Set<String> =
        importantDateRepository.observeAll(workspace).first()
            .map { "${it.title.trim().lowercase()}|${it.date}" }.toSet()

    private fun set(block: (HomeUiState) -> HomeUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        const val PARTNER_RECENTLY_ACTIVE_WINDOW_MS = 5 * 60 * 1000L
    }
}
