package com.browserz.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.Bookmark
import com.browserz.core.domain.BookmarkEdit
import com.browserz.core.domain.BookmarkFolders

/** Asks for a folder name; [onConfirm] returns an error message to show, or null when it worked. */
@Composable
fun FolderNameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> String?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(BookmarkFolders.MAX_NAME_LENGTH + 5); error = null },
                    singleLine = true,
                    label = { Text("Folder name") },
                    isError = error != null,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onConfirm(name)
                if (result == null) onDismiss() else error = result
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick a folder for a bookmark (or none); "New folder" creates one and puts the bookmark in it. */
@Composable
fun MoveToFolderDialog(
    folders: List<String>,
    current: String?,
    onPick: (String?) -> Unit,
    onNewFolder: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                FolderChoice("No folder", current == null) { onPick(null); onDismiss() }
                folders.sortedBy { it.lowercase() }.forEach { FolderChoice(it, current == it) { onPick(it); onDismiss() } }
                TextButton(onClick = { creating = true }) { Text("New folder…") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (creating) {
        FolderNameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create and move",
            onConfirm = { name ->
                val error = onNewFolder(name)
                if (error == null) {
                    onPick(name.trim())
                    onDismiss()
                }
                error
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun FolderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Edit a bookmark's name and address; [onSave] returns an error message to show, or null when it worked. */
@Composable
fun EditBookmarkDialog(bookmark: Bookmark, onSave: (title: String, address: String) -> String?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(bookmark.title) }
    var address by remember { mutableStateOf(bookmark.url) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit bookmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(BookmarkEdit.MAX_TITLE_LENGTH); error = null }, singleLine = true, label = { Text("Name") })
                OutlinedTextField(address, { address = it; error = null }, singleLine = true, label = { Text("URL") }, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onSave(title, address)
                if (result == null) onDismiss() else error = result
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
