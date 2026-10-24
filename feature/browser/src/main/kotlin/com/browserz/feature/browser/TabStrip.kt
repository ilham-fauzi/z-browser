package com.browserz.feature.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.TabInfo

/** Desktop-style tab strip shown on medium and expanded windows. */
@Composable
fun TabStrip(
    tabs: List<TabInfo>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
            LazyRow(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    TabChip(tab, tab.id == selectedId, { onSelect(tab.id) }, { onClose(tab.id) }, { onTogglePin(tab.id) })
                }
            }
            IconButton(onClick = onNewTab) { Icon(Icons.Filled.Add, contentDescription = "New tab") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(tab: TabInfo, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit, onTogglePin: () -> Unit) {
    // Selected tab shares the toolbar's colour so the two read as one piece, as in Chrome.
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        tween(160),
        label = "tabFill",
    )
    // Pinned tabs shrink to their badge and have no close button; long-press pins or unpins.
    val width = if (tab.pinned) 52.dp else 200.dp
    Surface(
        modifier = Modifier.width(width).height(40.dp).groupAccent(tab.group, underline = true).combinedClickable(onClick = onSelect, onLongClick = onTogglePin),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = container,
    ) {
        if (tab.pinned) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TabBadge(tab) }
            return@Surface
        }
        Row(Modifier.padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TabBadge(tab)
            Spacer(Modifier.width(8.dp))
            Text(
                tabTitle(tab),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
            }
        }
    }
}
