package com.browserz.feature.browser

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.TabGroups

private val GROUP_COLORS = listOf(
    Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFF9AB00), Color(0xFF34A853),
    Color(0xFFA142F4), Color(0xFF12B5CB), Color(0xFFFF6D01), Color(0xFFE52592),
)

internal fun groupColor(name: String): Color = GROUP_COLORS[TabGroups.colorIndex(name)]

/** Coloured edge on a tab that belongs to a group: underline for strip chips, left bar for list rows. */
internal fun Modifier.groupAccent(group: String?, underline: Boolean): Modifier {
    if (group == null) return this
    val color = groupColor(group)
    return drawBehind {
        if (underline) {
            val h = 3.dp.toPx()
            drawRect(color, Offset(0f, size.height - h), Size(size.width, h))
        } else {
            drawRect(color, Offset.Zero, Size(4.dp.toPx(), size.height))
        }
    }
}

/** Choose a group for the tab: none, one that exists, or a new one by name. */
@Composable
fun TabGroupDialog(existing: List<String>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tab group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GroupChoice("No group", null, current == null) { onPick(null); onDismiss() }
                existing.forEach { GroupChoice(it, groupColor(it), current == it) { onPick(it); onDismiss() } }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(TabGroups.MAX_NAME_LENGTH) },
                    singleLine = true,
                    label = { Text("New group") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = TabGroups.normalize(newName) != null,
                onClick = { onPick(TabGroups.normalize(newName)); onDismiss() },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupChoice(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
