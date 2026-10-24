package com.browserz.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.PermissionDecision
import com.browserz.core.domain.SitePermission

/** What the lock icon shows: the connection, the site's permissions and its ad setting. */
class SiteInfo(
    val host: String,
    val secure: Boolean,
    val issuer: String?,
    val validUntil: String?,
    val decisions: Map<SitePermission, PermissionDecision>,
    val adBlockEnabled: Boolean,
    val adsAllowed: Boolean,
)

class SiteInfoActions(
    val onPermission: (SitePermission, PermissionDecision) -> Unit,
    val onResetPermissions: () -> Unit,
    val onToggleAds: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(info: SiteInfo, actions: SiteInfoActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(info.host, style = MaterialTheme.typography.titleLarge)
            Text(
                if (info.secure) "Connection is secure" else "Connection is not secure",
                style = MaterialTheme.typography.titleSmall,
                color = if (info.secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            val detail = listOfNotNull(info.issuer?.let { "Certificate from $it" }, info.validUntil?.let { "valid until $it" }).joinToString(", ")
            Text(
                if (info.secure) detail.ifEmpty { "Information you send to this site is encrypted." }
                else "Do not enter passwords or card numbers on this page; others on the network could read them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("Permissions", style = MaterialTheme.typography.titleSmall)
            SitePermission.entries.forEach { permission ->
                val current = info.decisions[permission] ?: PermissionDecision.Ask
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label(permission), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionDecision.entries.forEach { d ->
                            FilterChip(selected = current == d, onClick = { actions.onPermission(permission, d) }, label = { Text(decisionLabel(d)) })
                        }
                    }
                }
            }
            TextButton(onClick = actions.onResetPermissions) { Text("Reset permissions for this site") }
            if (info.adBlockEnabled) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow ads on this site", style = MaterialTheme.typography.bodyLarge)
                        Text("Turns Browser-Z's blocking off for this site only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = info.adsAllowed, onCheckedChange = { actions.onToggleAds() })
                }
            }
            Text("", Modifier.padding(bottom = 8.dp))
        }
    }
}

private fun label(permission: SitePermission) = when (permission) {
    SitePermission.Camera -> "Camera"
    SitePermission.Microphone -> "Microphone"
    SitePermission.Location -> "Location"
}

private fun decisionLabel(d: PermissionDecision) = when (d) {
    PermissionDecision.Ask -> "Ask"
    PermissionDecision.Allow -> "Allow"
    PermissionDecision.Block -> "Block"
}

/** A page wants the camera, microphone or location. */
@Composable
fun PermissionDialog(host: String, kinds: List<SitePermission>, onAllow: () -> Unit, onBlock: () -> Unit, onDismiss: () -> Unit) {
    val what = kinds.joinToString(" and ") { label(it).lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$host wants to use your $what") },
        text = { Text("Your answer is remembered for this site. You can change it from the lock icon in the address bar.") },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onBlock) { Text("Block") } },
    )
}
