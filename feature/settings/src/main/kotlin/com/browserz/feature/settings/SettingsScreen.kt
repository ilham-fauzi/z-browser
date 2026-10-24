package com.browserz.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browserz.core.domain.CookieMode
import com.browserz.core.domain.DesktopMode
import com.browserz.core.domain.Settings
import com.browserz.core.domain.TabsPosition
import com.browserz.core.domain.ThemeMode
import com.browserz.core.domain.ToolbarPosition
import com.browserz.core.ui.CheckRow
import com.browserz.core.ui.ClickRow
import com.browserz.core.ui.RadioRow
import com.browserz.core.ui.SectionTitle
import com.browserz.core.ui.SwitchRow

/**
 * Settings page. The search-engine and ad-block blocks come from their own feature modules and
 * are passed in as slots, so feature modules never depend on each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onClearData: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit,
    onBack: () -> Unit,
    passkeysSupported: Boolean,
    isDefaultBrowser: Boolean,
    onMakeDefault: () -> Unit,
    searchSection: @Composable () -> Unit,
    filtersSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var clearing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            filtersSection()
            HorizontalDivider(Modifier.padding(top = 8.dp))
            searchSection()
            HorizontalDivider(Modifier.padding(top = 8.dp))

            SectionTitle("Cookies")
            RadioRow("Allow all cookies", null, settings.cookieMode == CookieMode.AllowAll) {
                onUpdate { it.copy(cookieMode = CookieMode.AllowAll) }
            }
            RadioRow("Block third-party cookies", "Recommended. Logins on the site you visit keep working.", settings.cookieMode == CookieMode.BlockThirdParty) {
                onUpdate { it.copy(cookieMode = CookieMode.BlockThirdParty) }
            }
            RadioRow("Block all cookies", "Most sites that need a login will not work.", settings.cookieMode == CookieMode.BlockAll) {
                onUpdate { it.copy(cookieMode = CookieMode.BlockAll) }
            }

            SectionTitle("Desktop site")
            RadioRow("Automatic", "Desktop sites on tablets and large windows", settings.desktopMode == DesktopMode.Auto) {
                onUpdate { it.copy(desktopMode = DesktopMode.Auto) }
            }
            RadioRow("Always", null, settings.desktopMode == DesktopMode.Always) { onUpdate { it.copy(desktopMode = DesktopMode.Always) } }
            RadioRow("Never", null, settings.desktopMode == DesktopMode.Never) { onUpdate { it.copy(desktopMode = DesktopMode.Never) } }

            SectionTitle("Address bar position")
            RadioRow("Automatic", "Bottom on phones, top on tablets and large windows", settings.toolbarPosition == ToolbarPosition.Auto) {
                onUpdate { it.copy(toolbarPosition = ToolbarPosition.Auto) }
            }
            RadioRow("Top", null, settings.toolbarPosition == ToolbarPosition.Top) { onUpdate { it.copy(toolbarPosition = ToolbarPosition.Top) } }
            RadioRow("Bottom", null, settings.toolbarPosition == ToolbarPosition.Bottom) { onUpdate { it.copy(toolbarPosition = ToolbarPosition.Bottom) } }

            SwitchRow("Hide address bar when scrolling", "Frees space while you read; scroll up to bring it back", settings.hideBarOnScroll) { v ->
                onUpdate { it.copy(hideBarOnScroll = v) }
            }
            SwitchRow("Pull to refresh", "Pull down at the top of a page to reload it", settings.pullToRefresh) { v ->
                onUpdate { it.copy(pullToRefresh = v) }
            }

            SectionTitle("Tabs")
            RadioRow("Strip on top", null, settings.tabsPosition == TabsPosition.Top) { onUpdate { it.copy(tabsPosition = TabsPosition.Top) } }
            RadioRow("List at the side", "Tablets and large windows. Phones always use the tab switcher.", settings.tabsPosition == TabsPosition.Side) {
                onUpdate { it.copy(tabsPosition = TabsPosition.Side) }
            }

            SectionTitle("Video and audio")
            SwitchRow(
                "Background play",
                "Audio and video keep playing when you leave the app or turn the screen off, with controls in the notification and on the lock screen",
                settings.backgroundPlay,
            ) { v -> onUpdate { it.copy(backgroundPlay = v) } }
            SwitchRow(
                "Picture-in-picture",
                "Leaving the app while a video plays keeps it going in a small floating window",
                settings.pictureInPicture,
            ) { v -> onUpdate { it.copy(pictureInPicture = v) } }

            SectionTitle("Home page")
            SwitchRow(
                "Show visited sites on the home page",
                "Tiles for the sites you visit most, and recent pages when you search. Shortcuts you pinned always stay.",
                settings.showHistoryOnHome,
            ) { v -> onUpdate { it.copy(showHistoryOnHome = v) } }
            SectionTitle("Passkeys")
            Text(
                if (passkeysSupported) {
                    "Passkeys are turned on: sites can ask your phone's password manager to sign you in. " +
                        "Whether a site accepts them here depends on the password manager."
                } else {
                    "This device's web engine (Android System WebView) is too old for passkeys. Update it in the Play Store."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SectionTitle("Security")
            SwitchRow(
                "Always use secure connections",
                "Opens public http addresses over https. If a site has no secure version you are asked before using http.",
                settings.httpsOnly,
            ) { v -> onUpdate { it.copy(httpsOnly = v) } }
            SectionTitle("Page text size")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("${settings.textZoom}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.textZoom.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(textZoom = (Math.round(v / 10f) * 10).coerceIn(50, 200)) } },
                    valueRange = 50f..200f,
                    steps = 14,
                )
                Text(
                    "The quick brown fox jumps over the lazy dog.",
                    fontSize = (16f * settings.textZoom / 100f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionTitle("Default browser")
            Text(
                if (isDefaultBrowser) "Browser-Z opens the links you tap in other apps." else "Open links from other apps in Browser-Z.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (!isDefaultBrowser) {
                TextButton(onClick = onMakeDefault, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Make Browser-Z the default browser") }
            }
            SectionTitle("Theme")
            RadioRow("Follow system", null, settings.themeMode == ThemeMode.System) { onUpdate { it.copy(themeMode = ThemeMode.System) } }
            RadioRow("Light", null, settings.themeMode == ThemeMode.Light) { onUpdate { it.copy(themeMode = ThemeMode.Light) } }
            RadioRow("Dark", "Also darkens web pages", settings.themeMode == ThemeMode.Dark) { onUpdate { it.copy(themeMode = ThemeMode.Dark) } }

            SectionTitle("Privacy")
            ClickRow("Clear browsing data", "History, cookies and site data, cached files") { clearing = true }
            SwitchRow("Clear history on exit", null, settings.clearHistoryOnExit) { v -> onUpdate { it.copy(clearHistoryOnExit = v) } }
            SwitchRow("Clear cookies and site data on exit", null, settings.clearCookiesOnExit) { v -> onUpdate { it.copy(clearCookiesOnExit = v) } }
            SwitchRow("Clear cached files on exit", null, settings.clearCacheOnExit) { v -> onUpdate { it.copy(clearCacheOnExit = v) } }
        }
    }
    if (clearing) {
        ClearDataDialog(onClear = { h, c, f -> onClearData(h, c, f) }, onDismiss = { clearing = false })
    }
}

@Composable
private fun ClearDataDialog(onClear: (Boolean, Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column {
                Text("This clears all data of the types you tick, for all time.")
                CheckRow("Browsing history", history) { history = it }
                CheckRow("Cookies and site data", cookies) { cookies = it }
                CheckRow("Cached images and files", cache) { cache = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = history || cookies || cache,
                onClick = { onClear(history, cookies, cache); onDismiss() },
            ) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
