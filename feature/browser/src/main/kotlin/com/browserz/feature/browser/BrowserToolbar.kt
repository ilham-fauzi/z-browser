package com.browserz.feature.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.SearchEngine
import com.browserz.core.domain.TabInfo
import com.browserz.core.ui.LayoutClass
import com.browserz.core.ui.LocalLayoutClass
import kotlin.math.abs

class BrowserActions(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onSubmit: (String) -> Unit,
    val onToggleBookmark: () -> Unit,
    val onHome: () -> Unit,
    val onNewTab: () -> Unit,
    val onReopenClosed: () -> Unit,
    val onPrint: () -> Unit,
    val onSiteInfo: () -> Unit,
    val onGroup: () -> Unit,
    val onFind: () -> Unit,
    val onReader: () -> Unit,
    val onTranslate: () -> Unit,
    val onShare: () -> Unit,
    val onAddToHome: () -> Unit,
    val onSavePage: () -> Unit,
    val onDownloads: () -> Unit,
    val onNewIncognitoTab: () -> Unit,
    val onShowTabs: () -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenSaved: () -> Unit,
    val onAddShortcut: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onToggleDesktop: () -> Unit,
    val onToggleAdsOnSite: () -> Unit,
    val onSelectEngine: (Long) -> Unit,
    val onToggleToolbarPosition: () -> Unit,
    val onToggleTabsPosition: () -> Unit,
    val onTogglePin: () -> Unit,
    /** Swipe on the address bar: +1 = next tab, -1 = previous tab. */
    val onSwipeTab: (Int) -> Unit = {},
)

/**
 * Chrome-style toolbar: one row with a pill-shaped address field. Phones get the field, the tab
 * counter and the menu (back, forward, bookmark and reload live in the menu's icon row); larger
 * windows add back, forward and reload beside the field and put the star inside it.
 */
@Composable
fun BrowserToolbar(
    tab: TabInfo?,
    tabCount: Int,
    isBookmarked: Boolean,
    adBlockEnabled: Boolean,
    adsAllowedOnSite: Boolean,
    supportsIncognito: Boolean,
    engines: List<SearchEngine>,
    defaultEngine: SearchEngine,
    toolbarAtBottom: Boolean,
    tabsOnSide: Boolean,
    hasClosedTabs: Boolean,
    address: AddressBarState,
    actions: BrowserActions,
    modifier: Modifier = Modifier,
) {
    val compact = LocalLayoutClass.current == LayoutClass.Compact
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            // The progress line hugs the edge that touches the page.
            if (toolbarAtBottom) ProgressLine(tab)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!compact) NavButtons(tab, actions)
                Omnibox(tab, engines, defaultEngine, address, actions, isBookmarked, compact, Modifier.weight(1f))
                if (compact) {
                    IconButton(onClick = actions.onShowTabs) { TabCountBadge(tabCount) }
                }
                OverflowMenu(tab, isBookmarked, adBlockEnabled, adsAllowedOnSite, supportsIncognito, toolbarAtBottom, tabsOnSide, hasClosedTabs, compact, actions)
            }
            if (!toolbarAtBottom) ProgressLine(tab)
        }
    }
}

@Composable
private fun ProgressLine(tab: TabInfo?) {
    if (tab?.isLoading == true) {
        LinearProgressIndicator(
            progress = { tab.progress.coerceAtLeast(6) / 100f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            trackColor = Color.Transparent,
        )
    } else {
        Spacer(Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
private fun NavButtons(tab: TabInfo?, actions: BrowserActions) {
    IconButton(onClick = actions.onBack, enabled = tab?.canGoBack == true) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
    IconButton(onClick = actions.onForward, enabled = tab?.canGoForward == true) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
    }
    ReloadStopButton(tab, actions, enabledWhenBlank = false)
    IconButton(onClick = actions.onHome) { Icon(Icons.Filled.Home, contentDescription = "Home") }
}

@Composable
private fun ReloadStopButton(tab: TabInfo?, actions: BrowserActions, enabledWhenBlank: Boolean, modifier: Modifier = Modifier) {
    val loading = tab?.isLoading == true
    IconButton(
        onClick = actions.onReloadOrStop,
        enabled = tab != null && (enabledWhenBlank || !tab.isBlank),
        modifier = modifier,
    ) {
        Icon(
            if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
            contentDescription = if (loading) "Stop" else "Reload",
        )
    }
}

@Composable
private fun TabCountBadge(count: Int) {
    Box(
        Modifier.size(26.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.coerceAtMost(99).toString(), style = MaterialTheme.typography.labelMedium)
    }
}

/** The pill: lock or engine picker on the left, the address, and quick actions on the right. */
@Composable
private fun Omnibox(
    tab: TabInfo?,
    engines: List<SearchEngine>,
    defaultEngine: SearchEngine,
    address: AddressBarState,
    actions: BrowserActions,
    isBookmarked: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val focused = address.focused
    val hasPage = tab != null && !tab.isBlank

    // The pill lights up with a primary outline and a lighter fill while you type, and eases back after.
    val outline by animateColorAsState(if (focused) scheme.primary else Color.Transparent, tween(180), label = "omniboxOutline")
    val fill by animateColorAsState(if (focused) scheme.surface else scheme.surfaceContainerHigh, tween(180), label = "omniboxFill")

    val shown = if (focused) address.value else TextFieldValue(displayAddress(tab?.url.orEmpty()))
    val emphasis = remember(scheme.onSurface, scheme.onSurfaceVariant) { HostEmphasis(scheme.onSurface, scheme.onSurfaceVariant) }
    val swipe = remember { mutableFloatStateOf(0f) }
    // The gesture outlives recompositions (tab updates arrive mid-swipe), so it calls the latest action.
    val swipeAction by rememberUpdatedState(actions.onSwipeTab)
    val onSwipe = remember { { step: Int -> swipeAction(step) } }

    Surface(
        modifier.height(42.dp).tabSwipe(enabled = !focused, offset = swipe, onSwipe = onSwipe),
        shape = RoundedCornerShape(21.dp),
        color = fill,
        border = BorderStroke(2.dp, outline),
    ) {
        Row(
            Modifier.graphicsLayer {
                // The contents follow the finger (damped) and fade, a hint that letting go changes tab.
                translationX = swipe.floatValue * 0.5f
                alpha = 1f - (abs(swipe.floatValue) / 400.dp.toPx()).coerceAtMost(0.6f)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingIcon(tab, focused, engines, defaultEngine, actions.onSelectEngine, actions.onSiteInfo)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (shown.text.isEmpty()) {
                    Text(
                        "Search with ${defaultEngine.name}",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = shown,
                    onValueChange = address::onEdit,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    visualTransformation = if (focused) VisualTransformation.None else emphasis,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        actions.onSubmit(address.value.text)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(address.focusRequester)
                        .onFocusChanged { if (it.isFocused) address.onFocusGained(tab) else address.onFocusLost() },
                )
            }
            when {
                focused -> if (address.value.text.isNotEmpty()) {
                    IconButton(onClick = { address.value = TextFieldValue("") }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
                hasPage -> {
                    BlockedChip(tab?.blockedCount ?: 0)
                    if (compact) {
                        ReloadStopButton(tab, actions, enabledWhenBlank = false, modifier = Modifier.size(38.dp))
                    } else {
                        IconButton(onClick = actions.onToggleBookmark, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isBookmarked) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                else -> Spacer(Modifier.size(8.dp))
            }
        }
    }
}

/** Gives the host the strong colour and the path the quiet one, like Chrome does. */
private class HostEmphasis(private val strong: Color, private val quiet: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val value = text.text
        val end = value.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) value.length else it }
        val styled = buildAnnotatedString {
            withStyle(SpanStyle(color = strong)) { append(value.substring(0, end)) }
            withStyle(SpanStyle(color = quiet)) { append(value.substring(end)) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Lock for https, warning for http; the search icon (engine picker) while typing or on a blank tab. */
@Composable
private fun LeadingIcon(
    tab: TabInfo?,
    focused: Boolean,
    engines: List<SearchEngine>,
    current: SearchEngine,
    onSelect: (Long) -> Unit,
    onSiteInfo: () -> Unit,
) {
    if (focused || tab == null || tab.isBlank) {
        EnginePicker(engines, current, onSelect)
        return
    }
    val secure = tab.url.startsWith("https://")
    IconButton(onClick = onSiteInfo, modifier = Modifier.size(40.dp)) {
        Icon(
            if (secure) Icons.Filled.Lock else Icons.Filled.Warning,
            contentDescription = if (secure) "Secure connection" else "Not secure",
            tint = if (secure) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Browser-Z's own touch: how many ads and trackers this page lost, right inside the address field. */
@Composable
private fun BlockedChip(count: Int) {
    if (count <= 0) return
    Box(
        Modifier
            .padding(end = 2.dp)
            .widthIn(min = 24.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(11.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.coerceAtMost(99).toString() + if (count > 99) "+" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun OverflowMenu(
    tab: TabInfo?,
    isBookmarked: Boolean,
    adBlockEnabled: Boolean,
    adsAllowedOnSite: Boolean,
    supportsIncognito: Boolean,
    toolbarAtBottom: Boolean,
    tabsOnSide: Boolean,
    hasClosedTabs: Boolean,
    compact: Boolean,
    actions: BrowserActions,
) {
    var expanded by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    val hasPage = tab != null && !tab.isBlank
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (compact) {
                // Chrome keeps the page actions in a row at the top of its phone menu; so do we.
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { expanded = false; actions.onBack() }, enabled = tab?.canGoBack == true) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { expanded = false; actions.onForward() }, enabled = tab?.canGoForward == true) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { expanded = false; actions.onHome() }) {
                        Icon(Icons.Filled.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = { expanded = false; actions.onToggleBookmark() }, enabled = hasPage) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                            tint = when {
                                !hasPage -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                isBookmarked -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { expanded = false; actions.onReloadOrStop() }, enabled = hasPage) {
                        Icon(
                            if (tab?.isLoading == true) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = if (tab?.isLoading == true) "Stop" else "Reload",
                        )
                    }
                }
                HorizontalDivider()
            }
            DropdownMenuItem(text = { Text("New tab") }, onClick = { expanded = false; actions.onNewTab() })
            if (supportsIncognito) {
                DropdownMenuItem(text = { Text("New incognito tab") }, onClick = { expanded = false; actions.onNewIncognitoTab() })
            }
            if (hasPage) {
                DropdownMenuItem(
                    text = { Text(if (tab?.pinned == true) "Unpin tab" else "Pin tab") },
                    onClick = { expanded = false; actions.onTogglePin() },
                )
                if (tab?.pinned != true) {
                    DropdownMenuItem(
                        text = { Text(if (tab?.group != null) "Group: ${tab.group}" else "Add to group…") },
                        onClick = { expanded = false; actions.onGroup() },
                    )
                }
            }
            if (hasClosedTabs) {
                DropdownMenuItem(text = { Text("Reopen closed tab") }, onClick = { expanded = false; actions.onReopenClosed() })
            }
            DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { expanded = false; actions.onOpenBookmarks() })
            DropdownMenuItem(text = { Text("History") }, onClick = { expanded = false; actions.onOpenHistory() })
            DropdownMenuItem(text = { Text("Saved pages") }, onClick = { expanded = false; actions.onOpenSaved() })
            DropdownMenuItem(text = { Text("Downloads") }, onClick = { expanded = false; actions.onDownloads() })
            HorizontalDivider()
            if (hasPage) {
                DropdownMenuItem(text = { Text("Share") }, onClick = { expanded = false; actions.onShare() })
                DropdownMenuItem(text = { Text("Find in page") }, onClick = { expanded = false; actions.onFind() })
                DropdownMenuItem(
                    text = { Text("Desktop site") },
                    trailingIcon = { Checkbox(checked = tab?.desktopMode == true, onCheckedChange = null) },
                    onClick = { expanded = false; actions.onToggleDesktop() },
                )
                if (adBlockEnabled) {
                    DropdownMenuItem(
                        text = { Text("Allow ads on this site") },
                        trailingIcon = { Checkbox(checked = adsAllowedOnSite, onCheckedChange = null) },
                        onClick = { expanded = false; actions.onToggleAdsOnSite() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (tools) "Fewer tools" else "More tools") },
                    trailingIcon = { Icon(if (tools) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                    onClick = { tools = !tools },
                )
                if (tools) {
                    DropdownMenuItem(text = { Text("Reader mode") }, onClick = { expanded = false; actions.onReader() })
                    DropdownMenuItem(text = { Text("Translate (Google)") }, onClick = { expanded = false; actions.onTranslate() })
                    DropdownMenuItem(text = { Text("Print or save as PDF") }, onClick = { expanded = false; actions.onPrint() })
                    DropdownMenuItem(text = { Text("Save for offline") }, onClick = { expanded = false; actions.onSavePage() })
                    DropdownMenuItem(text = { Text("Add to shortcuts") }, onClick = { expanded = false; actions.onAddShortcut() })
                    DropdownMenuItem(text = { Text("Add to Home screen") }, onClick = { expanded = false; actions.onAddToHome() })
                    if (adBlockEnabled) {
                        DropdownMenuItem(text = { Text("Blocked on this page: ${tab?.blockedCount ?: 0}") }, enabled = false, onClick = {})
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (toolbarAtBottom) "Address bar on top" else "Address bar on bottom") },
                onClick = { expanded = false; actions.onToggleToolbarPosition() },
            )
            if (!compact) {
                DropdownMenuItem(
                    text = { Text(if (tabsOnSide) "Tabs on top" else "Tabs on side") },
                    onClick = { expanded = false; actions.onToggleTabsPosition() },
                )
            }
            DropdownMenuItem(text = { Text("Settings") }, onClick = { expanded = false; actions.onOpenSettings() })
        }
    }
}

/** Search icon at the start of the address field; opens the list of engines with a check on the current one. */
@Composable
private fun EnginePicker(engines: List<SearchEngine>, current: SearchEngine, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Search, contentDescription = "Search engine: ${current.name}", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "Search engine",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            engines.forEach { engine ->
                DropdownMenuItem(
                    text = { Text(engine.name) },
                    leadingIcon = {
                        if (engine.id == current.id) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected")
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(engine.id)
                    },
                )
            }
        }
    }
}
