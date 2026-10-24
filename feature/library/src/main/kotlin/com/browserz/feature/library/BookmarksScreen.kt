package com.browserz.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.Bookmark
import com.browserz.core.domain.BookmarkFolders

/** Everything the bookmarks page can do; folder functions return null on success or an error message. */
class BookmarkActions(
    val onOpen: (String) -> Unit,
    val onOpenInNewTab: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onMove: (url: String, folder: String?) -> Unit,
    val onEdit: (oldUrl: String, title: String, address: String) -> String?,
    val onAddFolder: (String) -> String?,
    val onRenameFolder: (old: String, new: String) -> String?,
    val onDeleteFolder: (String) -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit,
)

/**
 * The Chrome-style bookmarks page: folders open one level at a time, search covers all bookmarks, long-press selects
 * several to move, open or delete. [onClose] is null when shown as a side panel on large windows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    folders: List<String>,
    actions: BookmarkActions,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deletingFolder by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<List<String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }

    // A folder that was deleted or renamed elsewhere must not leave the page showing a ghost.
    if (folder != null && folder !in folders) folder = null

    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searching) { searching = false; query = "" }
    BackHandler(enabled = !selecting && !searching && folder != null) { folder = null }

    val groups = remember(bookmarks, folders) { BookmarkFolders.group(bookmarks, folders) }
    val visible: List<Bookmark> = when {
        searching && query.isNotBlank() -> bookmarks.filter { it.title.contains(query.trim(), true) || it.url.contains(query.trim(), true) }
        folder != null -> groups.firstOrNull { it.folder == folder }?.items.orEmpty()
        else -> groups.firstOrNull { it.folder == null }?.items.orEmpty()
    }
    val showFolders = folder == null && !(searching && query.isNotBlank())

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = {
                when {
                    selecting -> Text("${selected.size} selected")
                    searching -> SearchField(query, { query = it }, "Search bookmarks")
                    else -> Text(folder ?: "Bookmarks")
                }
            },
            navigationIcon = {
                when {
                    selecting -> IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
                    searching -> IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                    folder != null -> IconButton(onClick = { folder = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up") }
                    onClose != null -> IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            },
            actions = {
                if (selecting) {
                    if (selected.size == 1) {
                        TextButton(onClick = { editing = bookmarks.firstOrNull { it.url in selected } }) { Text("Edit") }
                    }
                    TextButton(onClick = { selected.forEach(actions.onOpenInNewTab); selected = emptySet() }) { Text("Open") }
                    TextButton(onClick = { moving = selected.toList() }) { Text("Move") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
                } else if (!searching) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search bookmarks") }
                    var more by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("New folder") }, onClick = { more = false; newFolder = true })
                            DropdownMenuItem(text = { Text("Import bookmarks") }, onClick = { more = false; actions.onImport() })
                            DropdownMenuItem(text = { Text("Export bookmarks") }, enabled = bookmarks.isNotEmpty(), onClick = { more = false; actions.onExport() })
                        }
                    }
                }
            },
        )
        HorizontalDivider()

        val nothing = visible.isEmpty() && (!showFolders || folders.isEmpty())
        if (nothing) {
            Empty(
                when {
                    searching && query.isNotBlank() -> "No bookmarks match \"${query.trim()}\"."
                    folder != null -> "No bookmarks in this folder. Long-press a bookmark, then choose Move."
                    else -> "No bookmarks yet. Tap the star in the menu to add one, or import them from Chrome."
                },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (showFolders) {
                    items(groups.filter { it.folder != null }, key = { "f:" + it.folder }) { group ->
                        val name = group.folder!!
                        var menu by remember { mutableStateOf(false) }
                        ListRow(
                            title = name,
                            subtitle = "${group.items.size} ${if (group.items.size == 1) "bookmark" else "bookmarks"}",
                            selected = false,
                            onClick = { folder = name },
                            onLongClick = {},
                            leading = { FolderGlyph() },
                            trailing = {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Folder options") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = name })
                                        DropdownMenuItem(text = { Text("Delete folder") }, onClick = { menu = false; deletingFolder = name })
                                    }
                                }
                            },
                        )
                    }
                }
                items(visible, key = { "b:" + it.url }) { bookmark ->
                    var menu by remember { mutableStateOf(false) }
                    ListRow(
                        title = bookmark.title,
                        subtitle = bookmark.url,
                        selected = bookmark.url in selected,
                        onClick = {
                            if (selecting) selected = if (bookmark.url in selected) selected - bookmark.url else selected + bookmark.url
                            else actions.onOpen(bookmark.url)
                        },
                        onLongClick = { selected = selected + bookmark.url },
                        leading = { SiteBadge(bookmark.title, bookmark.url) },
                        trailing = {
                            if (selecting) {
                                Box(Modifier.padding(end = 16.dp))
                            } else {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Bookmark options") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Open in new tab") }, onClick = { menu = false; actions.onOpenInNewTab(bookmark.url) })
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = bookmark })
                                        DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menu = false; moving = listOf(bookmark.url) })
                                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; actions.onRemove(bookmark.url) })
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (newFolder) FolderNameDialog("New folder", "", "Create", { actions.onAddFolder(it) }, { newFolder = false })
    renaming?.let { old ->
        FolderNameDialog("Rename folder", old, "Rename", { actions.onRenameFolder(old, it) }, { renaming = null })
    }
    deletingFolder?.let { name ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("The bookmarks inside stay; they just are no longer in a folder.") },
            confirmButton = { TextButton(onClick = { actions.onDeleteFolder(name); deletingFolder = null }) { Text("Delete folder") } },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
        )
    }
    moving?.let { urls ->
        MoveToFolderDialog(
            folders = folders,
            current = urls.singleOrNull()?.let { url -> bookmarks.firstOrNull { it.url == url }?.folder },
            onPick = { target -> urls.forEach { actions.onMove(it, target) }; selected = emptySet() },
            onNewFolder = { actions.onAddFolder(it) },
            onDismiss = { moving = null },
        )
    }
    editing?.let { bookmark ->
        EditBookmarkDialog(
            bookmark = bookmark,
            onSave = { title, address -> actions.onEdit(bookmark.url, title, address) },
            onDismiss = { editing = null; selected = emptySet() },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} ${if (selected.size == 1) "bookmark" else "bookmarks"}?") },
            confirmButton = { TextButton(onClick = { selected.forEach(actions.onRemove); selected = emptySet(); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
