package com.browserz.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A list row in the Chrome style: leading badge or folder, two text lines, trailing actions; tinted when selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val tint = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(tint).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** Stand-in for a favicon: the first letter of the title or site on a tinted circle. */
@Composable
internal fun SiteBadge(title: String, url: String) {
    val letter = title.ifBlank { url.substringAfter("://") }.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun FolderGlyph(color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(Modifier.size(32.dp).padding(2.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(0f, h * 0.14f), Size(w * 0.44f, h * 0.24f), CornerRadius(w * 0.07f))
        drawRoundRect(color, Offset(0f, h * 0.28f), Size(w, h * 0.62f), CornerRadius(w * 0.1f))
    }
}

/** Search box that lives in the app bar; takes focus when it appears. */
@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(placeholder) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
internal fun Empty(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
