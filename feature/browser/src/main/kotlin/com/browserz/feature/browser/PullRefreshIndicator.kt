package com.browserz.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Chrome-style pull-to-refresh circle at the top of the page. [pull] is the pull distance as a share of the
 * distance that reloads (1 = letting go reloads); [refreshing] keeps it spinning until the page has loaded.
 */
@Composable
fun PullRefreshIndicator(pull: Float, refreshing: Boolean, modifier: Modifier = Modifier) {
    if (pull <= 0f && !refreshing) return
    val shown = if (refreshing) 1f else pull.coerceAtMost(1.4f)
    val ready = refreshing || pull >= 1f
    Surface(
        shape = CircleShape,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = modifier
            .padding(top = 8.dp)
            .size(40.dp)
            .graphicsLayer {
                translationY = (shown * 64.dp.toPx()) - 24.dp.toPx()
                alpha = if (ready) 1f else (0.3f + shown * 0.7f).coerceAtMost(1f)
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Pull to refresh",
                    tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = shown * 270f },
                )
            }
        }
    }
}
