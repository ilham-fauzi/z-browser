package com.browserz.feature.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.FilterStatus
import com.browserz.core.ui.SectionTitle
import com.browserz.core.ui.SwitchRow
import java.text.DateFormat
import java.util.Date

/** Settings block: ad blocking switch, filter list status and the per-site allow list. */
@Composable
fun FiltersSection(
    adBlockEnabled: Boolean,
    onAdBlockChange: (Boolean) -> Unit,
    blockPopups: Boolean,
    onBlockPopupsChange: (Boolean) -> Unit,
    status: FilterStatus,
    onUpdate: () -> Unit,
    allowedHosts: Set<String>,
    onRemoveHost: (String) -> Unit,
) {
    SectionTitle("Ad blocking")
    SwitchRow(
        title = "Block ads and trackers",
        subtitle = "Blocks AdSense and other ad networks on every site",
        checked = adBlockEnabled,
        onCheckedChange = onAdBlockChange,
    )
    SwitchRow(
        title = "Block pop-ups and ad redirects",
        subtitle = "Holds back new windows you did not ask for and automatic jumps to other sites",
        checked = blockPopups,
        onCheckedChange = onBlockPopupsChange,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text("Filter lists", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${status.ruleCount} blocking rules, ${status.cosmeticCount} hiding rules\n" + updatedText(status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (status.updating) {
            CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onUpdate) { Text("Update now") }
        }
    }
    if (allowedHosts.isNotEmpty()) {
        SectionTitle("Sites where ads are allowed")
        allowedHosts.sorted().forEach { host ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(host, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onRemoveHost(host) }) { Icon(Icons.Filled.Close, contentDescription = "Block ads on $host again") }
            }
        }
    }
}

private fun updatedText(status: FilterStatus): String =
    if (status.lastUpdatedAt == 0L) {
        "Built-in list only. Tap Update now to download EasyList and EasyPrivacy."
    } else {
        "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(status.lastUpdatedAt))
    }
