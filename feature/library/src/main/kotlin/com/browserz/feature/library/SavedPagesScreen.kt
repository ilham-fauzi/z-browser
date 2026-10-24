package com.browserz.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.browserz.core.domain.SavedPage

/** Pages saved for offline reading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPagesScreen(
    pages: List<SavedPage>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text("Saved pages") },
            navigationIcon = {
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        HorizontalDivider()
        if (pages.isEmpty()) {
            Empty("No saved pages. Use \"Save for offline\" in the menu to read a page without a connection.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(pages, key = { it.id }) {
                    ListRow(
                        title = it.title,
                        subtitle = it.url.substringAfter("://").removePrefix("www."),
                        selected = false,
                        onClick = { onOpen(it.id) },
                        onLongClick = {},
                        leading = { SiteBadge(it.title, it.url) },
                        trailing = { IconButton(onClick = { onDelete(it.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete saved page") } },
                    )
                }
            }
        }
    }
}
