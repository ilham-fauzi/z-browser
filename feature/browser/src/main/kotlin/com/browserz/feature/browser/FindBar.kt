package com.browserz.feature.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.FindResult

/** Find-in-page strip: type to highlight, arrows jump between matches. */
@Composable
fun FindBar(result: FindResult, onQuery: (String) -> Unit, onStep: (forward: Boolean) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it; onQuery(it) },
                singleLine = true,
                placeholder = { Text("Find in page") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onStep(true) }),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            val label = when {
                result.query.isBlank() -> ""
                result.matches == 0 -> "None"
                else -> "${result.index}/${result.matches}"
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = { onStep(false) }, enabled = result.matches > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match") }
            IconButton(onClick = { onStep(true) }, enabled = result.matches > 0) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close find") }
        }
    }
}
