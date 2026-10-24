package com.browserz.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.SearchEngine
import com.browserz.core.ui.SectionTitle

/** Settings block: choose the default search engine, add or remove custom ones. */
@Composable
fun SearchEnginesSection(
    engines: List<SearchEngine>,
    defaultId: Long,
    onSetDefault: (Long) -> Unit,
    /** Returns null on success, otherwise an error message to show. */
    onAdd: (name: String, urlTemplate: String, keyword: String?) -> String?,
    onRemove: (Long) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    SectionTitle("Search engine")
    engines.forEach { engine ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = engine.id == defaultId, role = Role.RadioButton, onClick = { onSetDefault(engine.id) })
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioButton(selected = engine.id == defaultId, onClick = null)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(engine.name, style = MaterialTheme.typography.bodyLarge)
                if (engine.keyword != null) {
                    Text("Shortcut: ${engine.keyword}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!engine.isBuiltIn) {
                IconButton(onClick = { onRemove(engine.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${engine.name}") }
            }
        }
    }
    TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Add search engine") }
    if (adding) {
        AddEngineDialog(onAdd = onAdd, onDismiss = { adding = false })
    }
}

@Composable
private fun AddEngineDialog(onAdd: (String, String, String?) -> String?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var keyword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add search engine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("URL with %s for the query") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(keyword, { keyword = it }, label = { Text("Shortcut (optional), e.g. !w") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onAdd(name, url, keyword.ifBlank { null })
                if (result == null) onDismiss() else error = result
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
