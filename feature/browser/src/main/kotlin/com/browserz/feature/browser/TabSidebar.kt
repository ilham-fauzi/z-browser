package com.browserz.feature.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.TabInfo

/** Vertical tab list shown beside the page when tabs are set to "side". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSidebar(
    tabs: List<TabInfo>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${tabs.size} tabs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onNewTab) { Icon(Icons.Filled.Add, contentDescription = "New tab") }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val selected = tab.id == selectedId
                    val container by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tween(160),
                        label = "sideTabFill",
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = container,
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onSelect(tab.id) }, onLongClick = { onTogglePin(tab.id) }),
                    ) {
                        Row(Modifier.groupAccent(tab.group, underline = false).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TabBadge(tab)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(
                                    tabTitle(tab),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (tab.pinned) {
                                    Text("Pinned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else if (!tab.isBlank) {
                                    Text(
                                        (tab.group?.let { "$it · " } ?: "") + displayAddress(tab.url),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (tab.pinned) {
                                Spacer(Modifier.width(12.dp))
                            } else {
                                IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
