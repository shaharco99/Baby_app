package com.oryareach.feature.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.SearchRepository
import com.oryareach.core.database.repository.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Stable
interface SearchActions {
    fun onQueryChange(value: String)
    fun onResultClick(result: SearchResult)
    fun onRefresh()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val workspaceId: () -> String?,
    private val syncEngine: com.oryareach.core.sync.SyncEngine,
) : ViewModel(), SearchActions {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.debounce(DEBOUNCE_MILLIS).flatMapLatest { text ->
                val workspace = workspaceId()
                if (text.isBlank() || workspace == null) emptyFlow() else repository.search(text, workspace)
            }.collect { results -> set { it.copy(results = results) } }
        }
    }

    override fun onQueryChange(value: String) {
        query.value = value
        set { it.copy(query = value, results = if (value.isBlank()) emptyList() else it.results) }
    }

    override fun onResultClick(result: SearchResult) {
        _effects.trySend(SearchEffect.OpenResult(result))
    }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }

        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (SearchUiState) -> SearchUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}
