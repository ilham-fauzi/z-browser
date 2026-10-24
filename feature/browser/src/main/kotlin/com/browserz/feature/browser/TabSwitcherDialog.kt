package com.browserz.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.browserz.core.domain.TabInfo

/**
 * Tab switcher for compact windows (phones in portrait): a two-column grid of cards, each with the site's icon,
 * title and a picture of the page as it was last seen. Tap opens, X closes, long-press pins or unpins.
 * [thumbnail] and [favicon] return null when there is nothing to show yet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSwitcherDialog(
    tabs: List<TabInfo>,
    selectedId: Long?,
    supportsIncognito: Boolean,
    thumbnail: (Long) -> ImageBitmap?,
    favicon: (Long) -> ImageBitmap?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(tabs, query) {
        val q = query.trim()
        if (q.isEmpty()) tabs else tabs.filter { it.title.contains(q, true) || it.url.contains(q, true) || it.group?.contains(q, true) == true }
    }
    val grid = rememberLazyGridState()
    // Open with the current tab in view, like Chrome.
    LaunchedEffect(Unit) {
        val index = tabs.indexOfFirst { it.id == selectedId }
        if (index > 0) grid.scrollToItem(index)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${tabs.size} tabs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (supportsIncognito) TextButton(onClick = onNewIncognitoTab) { Text("Incognito") }
                    TextButton(onClick = onNewTab) { Text("New tab") }
                }
                if (tabs.size >= SEARCH_FROM) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search tabs") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { tab ->
                        TabCard(
                            tab = tab,
                            selected = tab.id == selectedId,
                            thumbnail = thumbnail(tab.id),
                            favicon = favicon(tab.id),
                            onSelect = { onSelect(tab.id) },
                            onClose = { onClose(tab.id) },
                            onTogglePin = { onTogglePin(tab.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: TabInfo,
    selected: Boolean,
    thumbnail: ImageBitmap?,
    favicon: ImageBitmap?,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (tab.isIncognito) scheme.inverseSurface else scheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(3.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onSelect, onLongClick = onTogglePin),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().groupAccent(tab.group, underline = true).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabBadge(tab, favicon = favicon)
                Spacer(Modifier.width(8.dp))
                Text(
                    tabTitle(tab),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tab.isIncognito) scheme.inverseOnSurface else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (tab.pinned) {
                    // Pinned tabs cannot be closed from here; long-press unpins.
                    Text("Pinned", style = MaterialTheme.typography.labelSmall, color = scheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp))
                } else {
                    IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(18.dp),
                            tint = if (tab.isIncognito) scheme.inverseOnSurface else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TabBadge(tab, favicon = favicon, modifier = Modifier.size(36.dp))
                        if (!tab.isBlank) {
                            Text(
                                displayAddress(tab.url).substringBefore('/'),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tab search appears once there are enough tabs to need it. */
private const val SEARCH_FROM = 5
