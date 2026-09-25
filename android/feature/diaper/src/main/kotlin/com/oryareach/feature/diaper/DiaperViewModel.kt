package com.oryareach.feature.diaper

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.DiaperChangeRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.domain.diaper.DiaperDay
import com.oryareach.core.domain.diaper.DiaperEvent
import com.oryareach.core.domain.diaper.diaperDays
import com.oryareach.core.model.Baby
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.sync.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

@Stable
interface DiaperActions {
    fun onLogClick()
    fun onEditClick(event: DiaperEvent)
    fun onDismissSheet()
    fun onToggleUrine()
    fun onToggleStool()
    fun onNoteChange(value: String)
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onDateChange(value: LocalDate)
    fun onOpenTimePicker()
    fun onDismissTimePicker()
    fun onTimeChange(value: LocalTime)
    fun onSave()
    fun onDeleteClick(event: DiaperEvent)
    fun onDismissDelete()
    fun onConfirmDelete()
    fun onUndoDelete()
    fun onUndoDismissed()
    fun onRefresh()
}

/**
 * The nappy log reads two tables at once: feeds (for the urine/stool marked on them) and the
 * changes logged here. Both are live Room flows for the active child, so a feed marked on the
 * feeding screen — on this phone or the partner's — appears here as soon as it lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaperViewModel(
    private val repository: DiaperChangeRepository,
    private val feedingRepository: FeedingEntryRepository,
    private val babyRepository: BabyRepository,
    private val auth: AuthRepository,
    private val syncEngine: SyncEngine,
    private val workspaceId: () -> String?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel(), DiaperActions {

    private val _uiState = MutableStateFlow(DiaperUiState())
    val uiState: StateFlow<DiaperUiState> = _uiState.asStateFlow()

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                babyRepository.observeActive(id)
                    .flatMapLatest { baby -> logFor(id, baby) }
                    .collect { (baby, days) ->
                        set { it.copy(baby = baby, days = days, today = Clock.System.todayIn(timeZone())) }
                    }
            }
        }
    }

    /** No upper bound, so a change logged a second from now still lands in the list. */
    private fun logFor(workspace: String, baby: Baby?): Flow<Pair<Baby?, List<DiaperDay>>> {
        if (baby == null) return flowOf(null to emptyList())
        val from = now() - HISTORY_WINDOW_MILLIS
        return combine(
            feedingRepository.observeInRange(workspace, baby.id, from, Long.MAX_VALUE),
            repository.observeInRange(workspace, baby.id, from, Long.MAX_VALUE),
        ) { feeds, changes -> baby to diaperDays(feeds, changes, timeZone()) }
    }

    override fun onLogClick() = set {
        it.copy(
            sheetVisible = true,
            editingId = null,
            formHadUrine = false,
            formHadStool = false,
            formNote = "",
            formChangedAtEpochMillis = now(),
        )
    }

    /** Only changes logged here open; a feed's marks are edited on the feeding screen. */
    override fun onEditClick(event: DiaperEvent) {
        if (event.fromFeed) return
        set {
            it.copy(
                sheetVisible = true,
                editingId = event.id,
                formHadUrine = event.hadUrine,
                formHadStool = event.hadStool,
                formNote = event.note.orEmpty(),
                formChangedAtEpochMillis = event.atEpochMillis,
            )
        }
    }

    override fun onDismissSheet() = set {
        it.copy(sheetVisible = false, datePickerVisible = false, timePickerVisible = false)
    }

    override fun onToggleUrine() = set { it.copy(formHadUrine = !it.formHadUrine) }
    override fun onToggleStool() = set { it.copy(formHadStool = !it.formHadStool) }
    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }

    override fun onOpenDatePicker() = set { it.copy(datePickerVisible = true) }
    override fun onDismissDatePicker() = set { it.copy(datePickerVisible = false) }
    override fun onOpenTimePicker() = set { it.copy(timePickerVisible = true) }
    override fun onDismissTimePicker() = set { it.copy(timePickerVisible = false) }

    override fun onDateChange(value: LocalDate) = set {
        it.copy(formChangedAtEpochMillis = it.formChangedAtEpochMillis.movedTo(date = value), datePickerVisible = false)
    }

    override fun onTimeChange(value: LocalTime) = set {
        it.copy(formChangedAtEpochMillis = it.formChangedAtEpochMillis.movedTo(time = value), timePickerVisible = false)
    }

    /** Rebuilds the instant with one half replaced, in the device's own zone. */
    private fun Long.movedTo(date: LocalDate? = null, time: LocalTime? = null): Long {
        val zone = timeZone()
        val current = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)
        return LocalDateTime(date ?: current.date, time ?: current.time).toInstant(zone).toEpochMilliseconds()
    }

    override fun onSave() {
        val workspace = workspaceId() ?: return
        val state = _uiState.value
        val baby = state.baby ?: return
        if (state.busy) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
            val editingId = state.editingId
            val note = state.formNote.ifBlank { null }
            if (editingId == null) {
                repository.logChange(
                    workspaceId = workspace,
                    babyId = baby.id,
                    userId = auth.currentUserId().orEmpty(),
                    hadUrine = state.formHadUrine,
                    hadStool = state.formHadStool,
                    note = note,
                    changedAt = state.formChangedAtEpochMillis,
                )
            } else {
                repository.update(
                    id = editingId,
                    hadUrine = state.formHadUrine,
                    hadStool = state.formHadStool,
                    note = note,
                    changedAt = state.formChangedAtEpochMillis,
                )
            }
            set { it.copy(busy = false, sheetVisible = false, editingId = null) }
        }
    }

    override fun onDeleteClick(event: DiaperEvent) {
        if (event.fromFeed) return
        set { it.copy(deleteConfirm = event) }
    }

    override fun onDismissDelete() = set { it.copy(deleteConfirm = null) }

    override fun onConfirmDelete() {
        val id = _uiState.value.deleteConfirm?.id ?: return
        set { it.copy(deleteConfirm = null) }
        viewModelScope.launch {
            repository.delete(id)
            set { it.copy(undoDeleteId = id) }
        }
    }

    override fun onUndoDelete() {
        val id = _uiState.value.undoDeleteId ?: return
        set { it.copy(undoDeleteId = null) }
        viewModelScope.launch { repository.restore(id) }
    }

    override fun onUndoDismissed() = set { it.copy(undoDeleteId = null) }

    /** Same pull-to-refresh contract as the other logs: the spinner stops when the sync does. */
    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }
        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (DiaperUiState) -> DiaperUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        /** A fortnight, the same window as the feeding log it reads from. */
        const val HISTORY_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
