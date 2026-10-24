package com.browserz.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.HistoryDays
import com.browserz.core.domain.HistoryEntry

/** The Chrome-style history page: grouped by day, searchable, long-press to select, "Clear browsing data" on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searching) { searching = false; query = "" }

    val filtered = remember(history, query, searching) { HistoryDays.filter(history, if (searching) query else "") }
    val days = remember(filtered) { HistoryDays.group(filtered, System.currentTimeMillis()) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = {
                when {
                    selecting -> Text("${selected.size} selected")
                    searching -> SearchField(query, { query = it }, "Search history")
                    else -> Text("History")
                }
            },
            navigationIcon = {
                when {
                    selecting -> IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
                    searching -> IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                    onClose != null -> IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            },
            actions = {
                if (selecting) {
                    TextButton(onClick = { selected.forEach(onOpen) ; selected = emptySet() }, enabled = selected.size == 1) { Text("Open") }
                    IconButton(onClick = { selected.forEach(onRemove); selected = emptySet() }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
                } else if (!searching) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search history") }
                }
            },
        )
        HorizontalDivider()
        if (!selecting && !searching && history.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(start = 8.dp)) { Text("Clear browsing data…") }
            HorizontalDivider()
        }
        if (days.isEmpty()) {
            Empty(if (searching && query.isNotBlank()) "No history matches \"${query.trim()}\"." else "History is empty.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                days.forEach { day ->
                    item(key = "d:" + day.label) {
                        Text(
                            day.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(day.entries, key = { "h:" + it.url }) { entry ->
                        ListRow(
                            title = entry.title,
                            subtitle = entry.url.substringAfter("://").removePrefix("www."),
                            selected = entry.url in selected,
                            onClick = {
                                if (selecting) selected = if (entry.url in selected) selected - entry.url else selected + entry.url
                                else onOpen(entry.url)
                            },
                            onLongClick = { selected = selected + entry.url },
                            leading = { SiteBadge(entry.title, entry.url) },
                            trailing = {
                                if (selecting) {
                                    Column(Modifier.padding(end = 16.dp)) {}
                                } else {
                                    IconButton(onClick = { onRemove(entry.url) }) { Icon(Icons.Filled.Close, contentDescription = "Remove from history") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing history?") },
            text = { Text("This removes all history. Cookies, cache and site data are cleared from Settings.") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
