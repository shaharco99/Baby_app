package com.oryareach.feature.search

import androidx.compose.runtime.Immutable
import com.oryareach.core.database.repository.SearchResult

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val refreshing: Boolean = false,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
}

sealed interface SearchEffect {
    /** Handled in `:app`: switches to the tab that owns [result], since navigating to the
     * exact record would need per-screen "scroll to id" support this pass didn't build —
     * documented gap, not an oversight. */
    data class OpenResult(val result: SearchResult) : SearchEffect
}
