package com.oryareach.feature.cycle

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.CycleEntryRepository
import com.oryareach.core.database.repository.CycleRepository
import com.oryareach.core.database.repository.DocumentRepository
import com.oryareach.core.domain.cycle.calculateCycleStatistics
import com.oryareach.core.domain.cycle.predictNextCycle
import com.oryareach.core.model.Document
import com.oryareach.core.model.FlowLevel
import com.oryareach.core.model.Mood
import com.oryareach.core.model.PainLevel
import com.oryareach.core.model.Symptom
import com.oryareach.core.network.auth.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Stable
interface CycleActions {
    fun onStartPeriod()
    fun onShowStartPicker()
    fun onDismissStartPicker()
    fun onStartPeriodOn(date: LocalDate)
    fun onEndPeriod()
    fun onDelete(id: String)
    fun onPreviousMonth()
    fun onNextMonth()
    fun onSelectDate(date: LocalDate)
    fun onDismissDaySheet()
    fun onFlowChange(value: FlowLevel?)
    fun onToggleSymptom(value: Symptom)
    fun onToggleMood(value: Mood)
    fun onPainChange(value: PainLevel?)
    fun onNoteChange(value: String)
    fun onSaveEntry()
    fun onDeleteEntry()
    fun onToggleAttachments(cycleId: String)
    fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray)
    fun onDeleteAttachment(document: Document)
    fun onRefresh()
}

/**
 * The workspace id is read once, same as [com.oryareach.feature.tasks.TasksViewModel]: the
 * app's routing already guarantees a paired, unlocked device by the time this screen can be
 * reached, and there is no in-app flow that changes the open workspace mid-session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CycleViewModel(
    private val repository: CycleRepository,
    private val entryRepository: CycleEntryRepository,
    private val documents: DocumentRepository,
    private val auth: AuthRepository,
    private val syncEngine: com.oryareach.core.sync.SyncEngine,
    private val workspaceId: () -> String?,
) : ViewModel(), CycleActions {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _uiState = MutableStateFlow(CycleUiState(visibleMonth = today.startOfMonth()))
    val uiState: StateFlow<CycleUiState> = _uiState.asStateFlow()

    private val visibleMonth = MutableStateFlow(today.startOfMonth())
    private val expandedCycleId = MutableStateFlow<String?>(null)

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                combine(repository.observeOngoing(id), repository.observeAll(id)) { ongoing, history ->
                    ongoing to history
                }.collect { (ongoing, history) ->
                    set {
                        it.copy(
                            ongoing = ongoing,
                            history = history,
                            prediction = predictNextCycle(history),
                            statistics = calculateCycleStatistics(history),
                        )
                    }
                }
            }
            viewModelScope.launch {
                visibleMonth.flatMapLatest { month ->
                    entryRepository.observeInRange(id, month, month.endOfMonth())
                }.collect { entries -> set { it.copy(entriesInMonth = entries) } }
            }
            viewModelScope.launch {
                expandedCycleId.flatMapLatest { cycleId ->
                    if (cycleId == null) emptyFlow() else documents.observeForCycle(id, cycleId)
                }.collect { list -> set { it.copy(cycleAttachments = list) } }
            }
        }
    }

    override fun onStartPeriod() = onStartPeriodOn(today)

    override fun onShowStartPicker() = set { it.copy(startPickerVisible = true) }

    override fun onDismissStartPicker() = set { it.copy(startPickerVisible = false) }

    override fun onStartPeriodOn(date: LocalDate) {
        val workspace = workspaceId() ?: return
        set { it.copy(startPickerVisible = false) }
        if (_uiState.value.busy || _uiState.value.isPeriodOngoing) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
            repository.startPeriod(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                startDate = minOf(date, today),
            )
            set { it.copy(busy = false) }
        }
    }

    override fun onEndPeriod() {
        val ongoing = _uiState.value.ongoing ?: return
        if (_uiState.value.busy) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
            repository.endPeriod(id = ongoing.id, endDate = today)
            set { it.copy(busy = false) }
        }
    }

    override fun onDelete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    override fun onPreviousMonth() = shiftMonth(-1)
    override fun onNextMonth() = shiftMonth(1)

    private fun shiftMonth(delta: Int) {
        val next = visibleMonth.value.plus(delta, DateTimeUnit.MONTH)
        visibleMonth.value = next
        set { it.copy(visibleMonth = next) }
    }

    override fun onSelectDate(date: LocalDate) {
        val existing = _uiState.value.entriesInMonth.firstOrNull { it.date == date }
        set {
            it.copy(
                selectedDate = date,
                formFlow = existing?.flow,
                formSymptoms = existing?.symptoms.orEmpty().toSet(),
                formMood = existing?.mood.orEmpty().toSet(),
                formPain = existing?.pain,
                formNote = existing?.note.orEmpty(),
            )
        }
    }

    override fun onDismissDaySheet() = set {
        it.copy(
            selectedDate = null,
            formFlow = null,
            formSymptoms = emptySet(),
            formMood = emptySet(),
            formPain = null,
            formNote = "",
        )
    }

    override fun onFlowChange(value: FlowLevel?) = set {
        it.copy(formFlow = if (it.formFlow == value) null else value)
    }

    override fun onToggleSymptom(value: Symptom) = set {
        it.copy(formSymptoms = it.formSymptoms.toggle(value))
    }

    override fun onToggleMood(value: Mood) = set {
        it.copy(formMood = it.formMood.toggle(value))
    }

    override fun onPainChange(value: PainLevel?) = set {
        it.copy(formPain = if (it.formPain == value) null else value)
    }

    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }

    override fun onSaveEntry() {
        val workspace = workspaceId() ?: return
        val date = _uiState.value.selectedDate ?: return
        val state = _uiState.value

        viewModelScope.launch {
            entryRepository.logEntry(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                date = date,
                flow = state.formFlow,
                symptoms = state.formSymptoms.toList(),
                mood = state.formMood.toList(),
                pain = state.formPain,
                note = state.formNote.ifBlank { null },
            )
            onDismissDaySheet()
        }
    }

    override fun onDeleteEntry() {
        val date = _uiState.value.selectedDate ?: return
        val existing = _uiState.value.entriesInMonth.firstOrNull { it.date == date } ?: run {
            onDismissDaySheet()
            return
        }
        viewModelScope.launch {
            entryRepository.delete(existing.id)
            onDismissDaySheet()
        }
    }

    override fun onToggleAttachments(cycleId: String) {
        val next = if (_uiState.value.expandedCycleId == cycleId) null else cycleId
        expandedCycleId.value = next
        set { it.copy(expandedCycleId = next, cycleAttachments = if (next == null) emptyList() else it.cycleAttachments) }
    }

    override fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray) {
        val workspace = workspaceId() ?: return
        val cycleId = _uiState.value.expandedCycleId ?: return

        viewModelScope.launch {
            set { it.copy(attaching = true) }
            documents.upload(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                cycleId = cycleId,
                name = name,
                mimeType = mimeType,
                bytes = bytes,
            )
            set { it.copy(attaching = false) }
        }
    }

    override fun onDeleteAttachment(document: Document) {
        viewModelScope.launch { documents.delete(document.id) }
    }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }
        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (CycleUiState) -> CycleUiState) {
        _uiState.value = block(_uiState.value)
    }
}

private fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, month, 1)
private fun LocalDate.endOfMonth(): LocalDate = startOfMonth().plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
