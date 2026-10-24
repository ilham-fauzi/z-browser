package com.browserz.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.SearchEngine
import com.browserz.core.domain.Suggestion

/** Shown over the page while the address bar is being edited. */
@Composable
fun SuggestionsPanel(
    suggestions: List<Suggestion>,
    engines: List<SearchEngine>,
    onSearchWith: (SearchEngine) -> Unit,
    onOpenPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(engines, key = { it.id }) { engine ->
                    AssistChip(onClick = { onSearchWith(engine) }, label = { Text(engine.name) })
                }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(suggestions) { suggestion ->
                    when (suggestion) {
                        is Suggestion.Search -> SuggestionRow(
                            title = "Search \"${suggestion.query}\" on ${suggestion.engine.name}",
                            subtitle = null,
                            bookmark = false,
                            search = true,
                            onClick = { onSearchWith(suggestion.engine) },
                        )
                        is Suggestion.Page -> SuggestionRow(
                            title = suggestion.title,
                            subtitle = displayAddress(suggestion.url),
                            bookmark = suggestion.isBookmark,
                            search = false,
                            onClick = { onOpenPage(suggestion.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, subtitle: String?, bookmark: Boolean, search: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (search) Icons.Filled.Search else Icons.Filled.Star,
            contentDescription = null,
            tint = if (bookmark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
