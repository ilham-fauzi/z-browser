package com.browserz.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.LinkTarget

/** What the long-press menu can do; each callback gets the already-validated URL or text. */
class LinkMenuActions(
    val onOpenInNewTab: (String) -> Unit,
    val onOpenInIncognito: ((String) -> Unit)?,
    val onCopy: (label: String, text: String) -> Unit,
    val onDownload: (String) -> Unit,
    val onShare: (String) -> Unit,
)

/** Chrome-style context menu for a long-pressed link or image, as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMenuSheet(target: LinkTarget, actions: LinkMenuActions, onDismiss: () -> Unit) {
    val link = target.openableUrl
    val image = target.openableImage
    fun act(block: () -> Unit): () -> Unit = { block(); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                target.url ?: target.imageUrl.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            if (link != null) {
                Row("Open in new tab", act { actions.onOpenInNewTab(link) })
                actions.onOpenInIncognito?.let { open -> Row("Open in incognito tab", act { open(link) }) }
            }
            if (image != null && link == null) Row("Open image in new tab", act { actions.onOpenInNewTab(image) })
            if (target.url != null) {
                Row("Copy link address", act { actions.onCopy("Link", target.url!!) })
                target.text?.let { text -> Row("Copy link text", act { actions.onCopy("Link text", text) }) }
            }
            if (link != null) Row("Download link", act { actions.onDownload(link) })
            if (image != null) {
                if (link != null) Row("Open image in new tab", act { actions.onOpenInNewTab(image) })
                Row("Download image", act { actions.onDownload(image) })
                Row("Copy image address", act { actions.onCopy("Image", image) })
            }
            val shareable = link ?: image
            if (shareable != null) Row(if (link != null) "Share link" else "Share image", act { actions.onShare(shareable) })
        }
    }
}

@Composable
private fun Row(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
