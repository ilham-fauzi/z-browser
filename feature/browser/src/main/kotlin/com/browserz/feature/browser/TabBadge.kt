package com.browserz.feature.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserz.core.domain.TabInfo

/** The site's icon when known; otherwise its first letter, a spinner while loading, a search icon on a new tab. */
@Composable
internal fun TabBadge(tab: TabInfo, modifier: Modifier = Modifier, favicon: ImageBitmap? = null) {
    val scheme = MaterialTheme.colorScheme
    if (tab.isLoading && !tab.isBlank) {
        CircularProgressIndicator(modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.primary)
        return
    }
    if (favicon != null && !tab.isBlank) {
        Image(favicon, contentDescription = null, modifier = modifier.size(20.dp).clip(RoundedCornerShape(4.dp)))
        return
    }
    val background = if (tab.isIncognito) scheme.inverseSurface else scheme.primaryContainer
    val foreground = if (tab.isIncognito) scheme.inverseOnSurface else scheme.onPrimaryContainer
    Box(modifier.size(20.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        if (tab.isBlank) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = foreground, modifier = Modifier.size(12.dp))
        } else {
            val letter = displayAddress(tab.url).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            Text(letter, fontSize = 11.sp, color = foreground)
        }
    }
}
