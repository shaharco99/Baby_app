package com.oryareach.feature.cycle

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.cycle.CyclePrediction
import com.oryareach.core.domain.cycle.CycleStatistics
import com.oryareach.core.model.CycleEntry
import com.oryareach.core.model.Document
import com.oryareach.core.model.FlowLevel
import com.oryareach.core.model.MenstrualCycle
import com.oryareach.core.model.Mood
import com.oryareach.core.model.PainLevel
import com.oryareach.core.model.Symptom
import kotlinx.datetime.LocalDate

@Immutable
data class CycleUiState(
    // Persisted snapshot: the live data from Room, already decrypted.
    val ongoing: MenstrualCycle? = null,
    val history: List<MenstrualCycle> = emptyList(),
    val entriesInMonth: List<CycleEntry> = emptyList(),

    // Derived at read time from [history] — never persisted, see [MenstrualCycle]'s doc comment.
    val prediction: CyclePrediction = CyclePrediction(hasSufficientHistory = false),
    val statistics: CycleStatistics = CycleStatistics(cycleCount = 0),

    // Calendar navigation.
    val visibleMonth: LocalDate = LocalDate(2000, 1, 1),

    // The day-detail sheet.
    val selectedDate: LocalDate? = null,
    val formFlow: FlowLevel? = null,
    val formSymptoms: Set<Symptom> = emptySet(),
    val formMood: Set<Mood> = emptySet(),
    val formPain: PainLevel? = null,
    val formNote: String = "",

    // Per-period document attachments, shown for whichever history row is expanded.
    val expandedCycleId: String? = null,
    val cycleAttachments: List<Document> = emptyList(),
    val attaching: Boolean = false,

    // Transient UI-only.
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val startPickerVisible: Boolean = false,
) {
    val isPeriodOngoing: Boolean get() = ongoing != null
    val daySheetVisible: Boolean get() = selectedDate != null
}
