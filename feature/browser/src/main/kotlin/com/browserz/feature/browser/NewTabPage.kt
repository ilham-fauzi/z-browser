package com.browserz.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.IconButton
import com.browserz.core.domain.SearchEngine
import com.browserz.core.domain.Suggestion
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.NtpShortcuts
import com.browserz.core.domain.Shortcut

class NtpActions(
    val onOpen: (String) -> Unit,
    val onOpenInNewTab: (String) -> Unit,
    /** Suggestions for what has been typed so far (bookmarks, history, search with each engine). */
    val suggestionsFor: (String) -> List<Suggestion>,
    /** Free text or an address typed in the search field; the app decides how to open it. */
    val onSubmit: (String) -> Unit,
    val onSearchWith: (SearchEngine, String) -> Unit,
    val onIncognito: (() -> Unit)?,
    val onBookmarks: () -> Unit,
    val onHistory: () -> Unit,
    val onDownloads: () -> Unit,
    val onSaved: () -> Unit,
    val onPin: (Shortcut) -> Unit,
    val onUnpin: (Shortcut) -> Unit,
    /** Returns null on success, otherwise a message to show in the dialog. */
    val onAddShortcut: (name: String, address: String) -> String?,
)

/**
 * Home of a blank tab, laid out like Chrome's: search box, quick links to the library pages, then up to eight
 * shortcut tiles. Pinned sites come first; the rest are the sites visited most. No feed, no ads.
 */
@Composable
fun NewTabPage(
    shortcuts: List<Shortcut>,
    engines: List<SearchEngine>,
    engineName: String,
    blockedTotal: Long,
    recent: List<Suggestion.Page>,
    searching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    actions: NtpActions,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    // Leaving search mode forgets what was typed, like Chrome.
    LaunchedEffect(searching) {
        if (searching) focus.requestFocus() else text = TextFieldValue("")
    }
    val enter = fadeIn(tween(200)) + expandVertically(tween(220))
    val exit = fadeOut(tween(160)) + shrinkVertically(tween(220))

    Column(
        modifier.fillMaxSize().imePadding().padding(horizontal = if (searching) 12.dp else 24.dp, vertical = if (searching) 8.dp else 16.dp).animateContentSize(tween(220)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(!searching, enter = enter, exit = exit) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(28.dp))
                Text("Browser-Z", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (blockedTotal > 0) "${"%,d".format(blockedTotal)} ads and trackers blocked" else "Fast, light, ad-free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // The same pill is the resting search box and, once tapped, the search bar at the top of the page.
        Surface(
            onClick = { if (!searching) onSearchingChange(true) },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(52.dp),
        ) {
            if (searching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSearchingChange(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text("Search with $engineName") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (text.text.isNotBlank()) actions.onSubmit(text.text) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    if (text.text.isNotEmpty()) {
                        IconButton(onClick = { text = TextFieldValue("", TextRange.Zero) }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Search with $engineName", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        AnimatedVisibility(searching, enter = enter, exit = exit, modifier = Modifier.weight(1f, fill = true)) {
            SearchResults(text.text, engines, recent, actions)
        }

        AnimatedVisibility(!searching, enter = enter, exit = exit) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions.onIncognito?.let { QuickLink("Incognito", Icons.Filled.Search, it) }
                    QuickLink("Bookmarks", Icons.Filled.Star, actions.onBookmarks)
                    QuickLink("History", Icons.Filled.DateRange, actions.onHistory)
                    QuickLink("Downloads", Icons.Filled.KeyboardArrowDown, actions.onDownloads)
                    QuickLink("Saved", Icons.Filled.Favorite, actions.onSaved)
                }
                Spacer(Modifier.height(28.dp))
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val showAdd = shortcuts.count { it.pinned } < NtpShortcuts.MAX && shortcuts.size < NtpShortcuts.MAX
                    val cells: List<Shortcut?> = if (showAdd) shortcuts + null else shortcuts
                    cells.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { cell ->
                                if (cell != null) Tile(cell, actions, Modifier.weight(1f)) else AddTile({ adding = true }, Modifier.weight(1f))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (adding) AddShortcutDialog(actions.onAddShortcut) { adding = false }
}

/** What sits under the search bar: engines to search with, then matches while typing or recent pages when empty. */
@Composable
private fun SearchResults(query: String, engines: List<SearchEngine>, recent: List<Suggestion.Page>, actions: NtpActions) {
    val typed = query.isNotBlank()
    val rows: List<Suggestion> = if (typed) actions.suggestionsFor(query) else recent
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(engines, key = { it.id }) { engine ->
                androidx.compose.material3.AssistChip(onClick = { if (typed) actions.onSearchWith(engine, query) }, label = { Text(engine.name) }, enabled = typed)
            }
        }
        if (!typed && rows.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(rows) { suggestion ->
                when (suggestion) {
                    is Suggestion.Search -> SuggestionLine("Search \"${suggestion.query}\" on ${suggestion.engine.name}", null, true) { actions.onSearchWith(suggestion.engine, suggestion.query) }
                    is Suggestion.Page -> SuggestionLine(suggestion.title, displayAddress(suggestion.url), false) { actions.onOpen(suggestion.url) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionLine(title: String, subtitle: String?, search: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(if (search) Icons.Filled.Search else Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickLink(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(shortcut: Shortcut, actions: NtpActions, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(onClick = { actions.onOpen(shortcut.url) }, onLongClick = { menu = true }).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    shortcut.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (shortcut.pinned) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(14.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
            Text(
                shortcut.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Open in new tab") }, onClick = { menu = false; actions.onOpenInNewTab(shortcut.url) })
            if (shortcut.pinned) {
                DropdownMenuItem(text = { Text("Unpin") }, onClick = { menu = false; actions.onUnpin(shortcut) })
            } else {
                DropdownMenuItem(text = { Text("Pin to shortcuts") }, onClick = { menu = false; actions.onPin(shortcut) })
            }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().combinedClickableCompat(onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "Add shortcut", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Add shortcut", maxLines = 1, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

@Composable
private fun AddShortcutDialog(onAdd: (String, String) -> String?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40); error = null }, singleLine = true, label = { Text("Name") })
                OutlinedTextField(address, { address = it; error = null }, singleLine = true, label = { Text("Address") }, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onAdd(name, address)
                if (result == null) onDismiss() else error = result
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
