package com.oryareach.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.database.repository.SearchResult
import com.oryareach.core.model.EntityType
import com.oryareach.core.ui.theme.OrYareachTheme

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = actions::onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = actions::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }

            when {
                !uiState.hasQuery -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.results.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.results, key = { it.recordId }) { result ->
                        ResultRow(result = result, onClick = { actions.onResultClick(result) })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = stringResource(result.entityType.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = result.title.ifBlank { result.snippet },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (result.title.isNotBlank() && result.snippet.isNotBlank()) {
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchPreview() {
    OrYareachTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "milk",
                results = listOf(
                    SearchResult(EntityType.SHOPPING_ITEM, "1", "Almond milk", "buy the unsweetened one"),
                    SearchResult(EntityType.TASK, "2", "Call the pediatrician", ""),
                ),
            ),
            actions = NoopSearchActions,
        )
    }
}

private object NoopSearchActions : SearchActions {
    override fun onQueryChange(value: String) = Unit
    override fun onResultClick(result: SearchResult) = Unit
    override fun onRefresh() = Unit
}
