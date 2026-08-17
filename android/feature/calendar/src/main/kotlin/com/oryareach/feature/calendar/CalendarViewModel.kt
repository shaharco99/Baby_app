package com.oryareach.feature.calendar

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.calendar.GoogleCalendarSyncRepository
import com.oryareach.core.database.repository.CycleRepository
import com.oryareach.core.database.repository.ImportantDateRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.domain.cycle.calculateCycleStatistics
import com.oryareach.core.domain.cycle.predictNextCycle
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.ImportantDate
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.settings.SettingsPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Stable
interface CalendarActions {
    fun onPreviousMonth()
    fun onNextMonth()
    fun onSelectDate(date: LocalDate)
    fun onDismissDaySheet()
    fun onEventClick(event: CalendarEvent)
    fun onRefresh()

    // Important-date add/edit/delete — Calendar's own local records, unrelated to the
    // read-only Google Calendar integration below.
    fun onAddClick()
    fun onEditClick(date: ImportantDate)
    fun onDismissSheet()
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onDateChange(value: LocalDate)
    fun onTitleChange(value: String)
    fun onWishChange(value: String)
    fun onSubmit()
    fun onDelete(id: String)
}

/**
 * Merges tasks (by due date), important dates, and logged/predicted periods into one set of
 * [CalendarEvent]s. Deliberately not a separate persisted table — like [CalendarEffect], this
 * is a read-side view over three existing repositories, recomputed from their live flows via
 * `combine` rather than written anywhere, so it can never go stale independently of them.
 */
class CalendarViewModel(
    private val tasks: TaskRepository,
    private val importantDates: ImportantDateRepository,
    private val cycles: CycleRepository,
    private val syncEngine: com.oryareach.core.sync.SyncEngine,
    private val workspaceId: () -> String?,
    private val googleCalendarSync: GoogleCalendarSyncRepository,
    private val settingsPreferences: SettingsPreferences,
    private val auth: AuthRepository,
) : ViewModel(), CalendarActions {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _uiState = MutableStateFlow(CalendarUiState(visibleMonth = today.startOfMonth()))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CalendarEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val googleEventsFlow = settingsPreferences.selectedGoogleCalendarIds
        .flatMapLatest { ids -> googleCalendarSync.observeCachedEvents(ids.toList()) }

    init {
        // Cached Google events are device-local, not workspace-scoped — always observed
        // regardless of pairing state, unlike the three repositories below. `flatMapLatest`
        // re-subscribes to the cache whenever the selected calendar set changes (e.g. after the
        // user picks calendars in Settings).
        workspaceId()?.let { id ->
            viewModelScope.launch {
                importantDates.observeAll(id).collect { dateList -> set { it.copy(importantDates = dateList) } }
            }
            viewModelScope.launch {
                combine(
                    tasks.observeAll(id),
                    importantDates.observeAll(id),
                    cycles.observeAll(id),
                    googleEventsFlow,
                ) { taskList, dateList, cycleList, googleEventList ->
                    val taskEvents = taskList.mapNotNull { task ->
                        task.dueDate?.let {
                            CalendarEvent(it, EntityType.TASK, task.id, task.title, CalendarEventKind.TASK_DUE)
                        }
                    }
                    val dateEvents = dateList.map { date ->
                        CalendarEvent(date.date, EntityType.IMPORTANT_DATE, date.id, date.title, CalendarEventKind.IMPORTANT_DATE)
                    }
                    val actualEvents = cycleList.flatMap { cycle ->
                        val end = cycle.endDate ?: cycle.startDate
                        daysBetween(cycle.startDate, end).map {
                            CalendarEvent(it, EntityType.CYCLE, cycle.id, "", CalendarEventKind.PERIOD_ACTUAL)
                        }
                    }
                    val prediction = predictNextCycle(cycleList)
                    val predictedEvents = prediction.nextPeriodStart?.let { start ->
                        val periodLength = calculateCycleStatistics(cycleList).averagePeriodLengthDays ?: DEFAULT_PERIOD_LENGTH_DAYS
                        val end = start.plus(periodLength - 1, DateTimeUnit.DAY)
                        daysBetween(start, end).map {
                            CalendarEvent(it, EntityType.CYCLE, "predicted", "", CalendarEventKind.PERIOD_PREDICTED)
                        }
                    }.orEmpty()
                    val remoteEvents = googleEventList.map { event ->
                        CalendarEvent(
                            date = event.startAt.date,
                            entityType = null,
                            recordId = event.id,
                            title = event.title,
                            kind = CalendarEventKind.GOOGLE_EVENT,
                        )
                    }

                    taskEvents + dateEvents + actualEvents + predictedEvents + remoteEvents
                }.collect { events -> set { it.copy(events = events) } }
            }
        }
    }

    override fun onPreviousMonth() = shiftMonth(-1)
    override fun onNextMonth() = shiftMonth(1)

    private fun shiftMonth(delta: Int) = set { it.copy(visibleMonth = it.visibleMonth.plus(delta, DateTimeUnit.MONTH)) }

    override fun onSelectDate(date: LocalDate) = set { it.copy(selectedDate = date) }
    override fun onDismissDaySheet() = set { it.copy(selectedDate = null) }

    override fun onEventClick(event: CalendarEvent) {
        if (event.kind == CalendarEventKind.IMPORTANT_DATE) {
            // Already on the right screen — open the edit sheet in place instead of the
            // tab-switch effect used for every other kind.
            val date = _uiState.value.importantDates.firstOrNull { it.id == event.recordId } ?: return
            onEditClick(date)
            return
        }
        _effects.trySend(CalendarEffect.OpenEvent(event))
    }

    override fun onAddClick() = set {
        it.copy(sheetVisible = true, editingId = null, formDate = it.selectedDate, formTitle = "", formWish = "")
    }

    override fun onEditClick(date: ImportantDate) = set {
        it.copy(
            sheetVisible = true,
            editingId = date.id,
            formDate = date.date,
            formTitle = date.title,
            formWish = date.wish.orEmpty(),
        )
    }

    override fun onDismissSheet() = set { it.copy(sheetVisible = false) }
    override fun onOpenDatePicker() = set { it.copy(datePickerVisible = true) }
    override fun onDismissDatePicker() = set { it.copy(datePickerVisible = false) }
    override fun onDateChange(value: LocalDate) = set { it.copy(formDate = value, datePickerVisible = false) }
    override fun onTitleChange(value: String) = set { it.copy(formTitle = value) }
    override fun onWishChange(value: String) = set { it.copy(formWish = value) }

    override fun onSubmit() {
        val state = _uiState.value
        val workspace = workspaceId() ?: return
        val date = state.formDate ?: return
        if (!state.canSubmitForm) return
        set { it.copy(submitting = true) }

        viewModelScope.launch {
            val editingId = state.editingId
            if (editingId == null) {
                importantDates.create(
                    workspaceId = workspace,
                    userId = auth.currentUserId().orEmpty(),
                    date = date,
                    title = state.formTitle,
                    wish = state.formWish.ifBlank { null },
                )
            } else {
                importantDates.update(editingId, date, state.formTitle, state.formWish.ifBlank { null })
            }
            set { it.copy(submitting = false, sheetVisible = false) }
        }
    }

    override fun onDelete(id: String) {
        viewModelScope.launch { importantDates.delete(id) }
    }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }
        viewModelScope.launch {
            val selectedGoogleCalendarIds = settingsPreferences.selectedGoogleCalendarIds.first()
            // Independent of SyncEngine (that's the couple's Supabase workspace sync) — this is
            // a separate, simpler on-demand fetch against the connected Google account, if any.
            syncEngine.sync()
            googleCalendarSync.refresh(selectedGoogleCalendarIds.toList())
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (CalendarUiState) -> CalendarUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        const val DEFAULT_PERIOD_LENGTH_DAYS = 5
    }
}

private fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, month, 1)

private fun daysBetween(start: LocalDate, end: LocalDate): List<LocalDate> {
    val days = mutableListOf<LocalDate>()
    var d = start
    while (d <= end) {
        days += d
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return days
}
