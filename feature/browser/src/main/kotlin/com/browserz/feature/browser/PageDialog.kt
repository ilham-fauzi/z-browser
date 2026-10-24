package com.browserz.feature.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What a page asked for: alert (OK only), confirm (OK / Cancel), prompt (with a text field) or "leave site?". */
enum class PageDialogKind { Alert, Confirm, Prompt, LeavePage }

/**
 * Chrome-style dialog for a page's alert(), confirm(), prompt() and beforeunload. The title names the site so a
 * page cannot pass its message off as the browser's. Dismissing counts as Cancel.
 */
@Composable
fun PageDialog(
    kind: PageDialogKind,
    host: String,
    message: String,
    defaultText: String?,
    canSuppress: Boolean,
    onAnswer: (confirmed: Boolean, text: String?, suppress: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(defaultText.orEmpty()) }
    var suppress by remember { mutableStateOf(false) }
    val leaving = kind == PageDialogKind.LeavePage
    AlertDialog(
        onDismissRequest = { onAnswer(false, null, suppress) },
        title = { Text(if (leaving) "Leave site?" else "$host says") },
        text = {
            Column {
                val body = if (leaving) "Changes you made may not be saved." else message
                if (body.isNotEmpty()) {
                    Text(body, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
                }
                if (kind == PageDialogKind.Prompt) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                if (canSuppress && !leaving) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = suppress, onCheckedChange = { suppress = it })
                        Text("Don't let this page show more dialogs", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true, text, suppress) }) { Text(if (leaving) "Leave" else "OK") }
        },
        dismissButton = if (kind == PageDialogKind.Alert) null else {
            { TextButton(onClick = { onAnswer(false, null, suppress) }) { Text(if (leaving) "Stay" else "Cancel") } }
        },
    )
}
