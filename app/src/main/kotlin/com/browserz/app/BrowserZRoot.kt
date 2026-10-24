package com.browserz.app

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browserz.core.domain.BookmarkHtml
import com.browserz.core.domain.BookmarkImport
import com.browserz.core.domain.BookmarkImportResult
import com.browserz.core.domain.BuildSearchUrl
import com.browserz.core.domain.SearchEngine
import com.browserz.core.domain.EngineHome
import com.browserz.core.domain.Hosts
import com.browserz.core.domain.NtpShortcuts
import com.browserz.core.domain.ClosedTab
import com.browserz.core.domain.PermissionDecision
import com.browserz.core.domain.SitePermission
import com.browserz.core.domain.Suggestion
import com.browserz.core.domain.TabInfo
import com.browserz.core.domain.TabsPosition
import com.browserz.core.domain.ThemeMode
import com.browserz.core.domain.ToolbarPosition
import com.browserz.core.ui.AdaptiveShell
import com.browserz.core.ui.BarPlacement
import com.browserz.core.ui.BrowserZTheme
import com.browserz.core.ui.LayoutClass
import com.browserz.core.ui.currentLayoutClass
import com.browserz.core.ui.toolbarAtBottom
import com.browserz.core.webview.WebViewHost
import com.browserz.feature.browser.AddressBarState
import com.browserz.feature.browser.BrowserActions
import com.browserz.feature.browser.BrowserToolbar
import com.browserz.feature.browser.BookmarkSavedBanner
import com.browserz.feature.browser.FindBar
import com.browserz.feature.browser.LinkMenuActions
import com.browserz.feature.browser.LinkMenuSheet
import com.browserz.feature.browser.NewTabPage
import com.browserz.feature.browser.NtpActions
import com.browserz.feature.browser.PageDialog
import com.browserz.feature.browser.PageDialogKind
import com.browserz.feature.browser.PopupBlockedBanner
import com.browserz.feature.browser.PullRefreshIndicator
import com.browserz.feature.browser.SuggestionsPanel
import com.browserz.feature.browser.PermissionDialog
import com.browserz.feature.browser.SiteInfo
import com.browserz.feature.browser.SiteInfoActions
import com.browserz.feature.browser.SiteInfoSheet
import com.browserz.feature.browser.TabGroupDialog
import com.browserz.feature.browser.UndoBanner
import com.browserz.feature.browser.TabSidebar
import com.browserz.feature.browser.TabStrip
import com.browserz.feature.browser.TabSwitcherDialog
import com.browserz.feature.filters.FiltersSection
import com.browserz.feature.library.BookmarkActions
import com.browserz.feature.library.BookmarksScreen
import com.browserz.feature.library.HistoryScreen
import com.browserz.feature.library.ImportGuideScreen
import com.browserz.feature.library.SavedPagesScreen
import com.browserz.feature.library.MoveToFolderDialog
import com.browserz.feature.search.SearchEnginesSection
import com.browserz.feature.settings.SettingsScreen

private enum class Screen { Browser, Bookmarks, History, Saved, Settings, ImportGuide }

@Composable
fun BrowserZRoot(c: AppContainer) {
    val session = c.session
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val tabs by session.tabs.collectAsStateWithLifecycle()
    val selectedId by session.selectedTabId.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val engines by c.engines.engines.collectAsStateWithLifecycle()
    val defaultEngine by c.engines.defaultEngine.collectAsStateWithLifecycle()
    val bookmarks by c.bookmarks.bookmarks.collectAsStateWithLifecycle()
    val bookmarkFolders by c.bookmarks.folders.collectAsStateWithLifecycle()
    val history by c.history.entries.collectAsStateWithLifecycle()
    val allowedHosts by c.siteSettings.adsAllowedHosts.collectAsStateWithLifecycle()
    val filterStatus by c.filters.status.collectAsStateWithLifecycle()
    val barsVisible by session.barsVisible.collectAsStateWithLifecycle()
    val popupNotice by session.popupNotice.collectAsStateWithLifecycle()
    val fullscreen by session.fullscreen.collectAsStateWithLifecycle()
    val linkTarget by session.linkMenu.collectAsStateWithLifecycle()
    val pinnedSites by c.shortcuts.pinned.collectAsStateWithLifecycle()
    val findResult by session.findResult.collectAsStateWithLifecycle()
    val savedPages by session.savedPages.collectAsStateWithLifecycle()
    val blockedTotal by c.stats.totalBlocked.collectAsStateWithLifecycle()
    val pull by session.pull.collectAsStateWithLifecycle()
    val refreshing by session.refreshing.collectAsStateWithLifecycle()

    val layout = currentLayoutClass()
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    var screen by rememberSaveable { mutableStateOf(Screen.Browser) }
    var libraryPanel by rememberSaveable { mutableStateOf<Screen?>(null) }
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    val address = remember { AddressBarState() }
    var savedBookmarkUrl by remember { mutableStateOf<String?>(null) }
    var choosingFolderFor by remember { mutableStateOf<String?>(null) }
    var findOpen by remember { mutableStateOf(false) }
    var groupDialogOpen by remember { mutableStateOf(false) }
    var ntpSearching by rememberSaveable { mutableStateOf(false) }
    var siteInfoOpen by remember { mutableStateOf(false) }
    val closedTabs by session.recentlyClosed.collectAsStateWithLifecycle()
    val permissionPrompt by session.permissionPrompt.collectAsStateWithLifecycle()
    val permissionVersion by c.sitePermissions.version.collectAsStateWithLifecycle()

    // Android's own permission dialog, asked only after the user allowed a site to use the camera, microphone or location.
    var pendingRuntime by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val done = pendingRuntime
        pendingRuntime = null
        done?.invoke(result.values.any { it })
    }
    DisposableEffect(session) {
        session.setRuntimePermissionRequester { permissions, done ->
            pendingRuntime = done
            runtimeLauncher.launch(permissions)
        }
        onDispose { session.setRuntimePermissionRequester(null) }
    }

    // A page's file input: the system picker, answered with the chosen files (or none when cancelled).
    var pendingFiles by remember { mutableStateOf<((Array<android.net.Uri>?) -> Unit)?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val done = pendingFiles
        pendingFiles = null
        done?.invoke(PageActions.pickedFiles(result.resultCode, result.data))
    }
    DisposableEffect(session) {
        session.setFileChooser { intent, done ->
            pendingFiles?.invoke(null)
            pendingFiles = done
            try {
                fileLauncher.launch(intent)
            } catch (_: android.content.ActivityNotFoundException) {
                pendingFiles = null
                done(null)
                Toast.makeText(context, "No app can pick files", Toast.LENGTH_SHORT).show()
            }
        }
        onDispose { session.setFileChooser(null) }
    }
    val jsDialog by session.jsDialog.collectAsStateWithLifecycle()
    val inPictureInPicture by c.pictureInPicture.collectAsStateWithLifecycle()

    // Background play shows a media notification; Android 13+ asks before an app may post one.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Without notifications, background play has no controls", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(settings.backgroundPlay) {
        if (settings.backgroundPlay && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // "Tab closed - Undo": shown when a new tab lands at the top of the recently closed list.
    var undoFor by remember { mutableStateOf<ClosedTab?>(null) }
    var lastClosed by remember { mutableStateOf<ClosedTab?>(null) }
    var lastClosedCount by remember { mutableStateOf(0) }
    LaunchedEffect(closedTabs) {
        val top = closedTabs.firstOrNull()
        // A shrinking list is a reopen (the older entry underneath is not news); a same-size or longer list with a new top is a close.
        undoFor = when {
            top == null || closedTabs.size < lastClosedCount -> null
            top != lastClosed -> top
            else -> undoFor
        }
        lastClosed = top
        lastClosedCount = closedTabs.size
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = PageActions.readText(context, uri)
        val message = when (val result = text?.let { BookmarkImport.parse(it) }) {
            null -> "Couldn't read that file"
            is BookmarkImportResult.Unrecognized -> result.message
            is BookmarkImportResult.Found -> "Imported ${c.bookmarks.importAll(result.bookmarks)} new bookmarks from ${result.source}"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        if (text != null) screen = Screen.Bookmarks
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PageActions.writeText(context, uri, BookmarkHtml.export(c.bookmarks.bookmarks.value, c.bookmarks.folders.value))
        Toast.makeText(context, if (ok) "Bookmarks exported" else "Export failed", Toast.LENGTH_SHORT).show()
    }

    val selected: TabInfo? = tabs.firstOrNull { it.id == selectedId }

    // Querying WebView features loads the WebView provider; do it after the first frame so startup stays fast.
    var supportsIncognito by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        supportsIncognito = session.supportsIncognito
    }

    LaunchedEffect(selectedId) { findOpen = false }
    LaunchedEffect(layout) { session.setWideScreen(layout == LayoutClass.Expanded) }
    // Before the page area is composed, so the first WebView is already built on the right theme.
    remember(dark) { session.setDarkTheme(dark); dark }
    // The status and navigation bar icons follow the app's theme, not only the system's.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    LaunchedEffect(settings, allowedHosts, filterStatus.ruleCount) { session.applySettingsToAll() }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) address.requestFocus()
    }

    fun openInCurrentTab(url: String) {
        selected?.let { session.load(it.id, url) }
        focusManager.clearFocus()
        screen = Screen.Browser
    }

    val bookmarkActions = BookmarkActions(
        onOpen = ::openInCurrentTab,
        onOpenInNewTab = { url ->
            session.openTab(url, select = false)
            Toast.makeText(context, "Opened in a new tab", Toast.LENGTH_SHORT).show()
        },
        onRemove = { c.bookmarks.remove(it) },
        onMove = { url, folder -> c.bookmarks.move(url, folder) },
        onEdit = { old, title, address -> c.bookmarks.edit(old, title, address) },
        onAddFolder = { c.bookmarks.addFolder(it) },
        onRenameFolder = { old, new -> c.bookmarks.renameFolder(old, new) },
        onDeleteFolder = { c.bookmarks.deleteFolder(it) },
        onImport = { screen = Screen.ImportGuide },
        onExport = { exportLauncher.launch("browser-z-bookmarks.html") },
    )

    fun navigate(input: String) {
        val url = c.resolveInput(input, defaultEngine, engines)
        if (url.isNotEmpty()) openInCurrentTab(url)
    }

    fun openLibrary(page: Screen) {
        if (layout == LayoutClass.Expanded) libraryPanel = if (libraryPanel == page) null else page else screen = page
    }

    fun pinShortcut(url: String, title: String) {
        val label = title.ifBlank { Hosts.hostOf(url).removePrefix("www.") }
        val ok = c.shortcuts.pin(url, label)
        Toast.makeText(context, if (ok) "Added to shortcuts" else "Shortcuts are full", Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun LibraryPageContent(page: Screen, onClose: (() -> Unit)?, modifier: Modifier) {
        when (page) {
            Screen.Bookmarks -> BookmarksScreen(bookmarks, bookmarkFolders, bookmarkActions, onClose, modifier)
            Screen.History -> HistoryScreen(history, ::openInCurrentTab, { c.history.remove(it) }, { c.history.clear() }, onClose, modifier)
            else -> SavedPagesScreen(
                pages = savedPages,
                onOpen = { id -> selected?.let { session.openSavedPage(it.id, id); screen = Screen.Browser } },
                onDelete = session::deleteSavedPage,
                onClose = onClose,
                modifier = modifier,
            )
        }
    }

    fun searchWith(engine: SearchEngine, query: String) {
        if (query.isNotBlank()) openInCurrentTab(BuildSearchUrl()(engine, query.trim()))
    }

    fun newTab(incognito: Boolean = false) {
        if (session.openTab("", incognito) >= 0) {
            showTabs = false
            screen = Screen.Browser
            focusTick++
        }
    }

    val homeHistory = if (settings.showHistoryOnHome) history else emptyList()
    val ntpShortcuts = remember(pinnedSites, homeHistory) { NtpShortcuts.build(pinnedSites, homeHistory) }
    val ntpRecent = remember(homeHistory) { homeHistory.take(8).map { Suggestion.Page(it.title, it.url, isBookmark = false) } }
    val ntpActions = NtpActions(
        onOpen = { url -> ntpSearching = false; openInCurrentTab(url) },
        onOpenInNewTab = { url -> session.openTab(url, select = false); Toast.makeText(context, "Opened in a new tab", Toast.LENGTH_SHORT).show() },
        suggestionsFor = { q -> c.buildSuggestions(q, bookmarks, history, defaultEngine) },
        onSubmit = { text -> ntpSearching = false; navigate(text) },
        onSearchWith = { engine, q -> ntpSearching = false; searchWith(engine, q) },
        onIncognito = if (supportsIncognito) ({ newTab(incognito = true) }) else null,
        onBookmarks = { openLibrary(Screen.Bookmarks) },
        onHistory = { openLibrary(Screen.History) },
        onDownloads = { PageActions.openDownloads(context) },
        onSaved = { openLibrary(Screen.Saved) },
        onPin = { pinShortcut(it.url, it.title) },
        onUnpin = { c.shortcuts.unpin(it.url) },
        onAddShortcut = { name, addressText ->
            val url = NtpShortcuts.normalizeUrl(addressText)
            when {
                url == null -> "Enter a valid web address"
                !c.shortcuts.pin(url, name.trim().ifBlank { Hosts.hostOf(url).removePrefix("www.") }) -> "Shortcuts are full"
                else -> null
            }
        },
    )

    fun siteHost(tab: TabInfo?): String =
        tab?.url?.substringAfter("://", "")?.substringBefore('/')?.substringBefore('?')?.substringBefore(':')?.lowercase().orEmpty()

    fun cycleTab(step: Int) {
        if (tabs.size < 2) return
        val index = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        session.selectTab(tabs[(index + step + tabs.size) % tabs.size].id)
    }

    val shortcuts = rememberUpdatedState<(Int, Boolean) -> Boolean> { keyCode, shift ->
        when (keyCode) {
            KeyEvent.KEYCODE_T -> { if (shift) session.reopenClosedTab() else newTab(); true }
            KeyEvent.KEYCODE_N -> if (shift) { newTab(incognito = true); true } else false
            KeyEvent.KEYCODE_W -> { selected?.let { session.closeTab(it.id) }; true }
            KeyEvent.KEYCODE_L -> { address.requestFocus(); true }
            KeyEvent.KEYCODE_R -> { selected?.let { session.reload(it.id) }; true }
            KeyEvent.KEYCODE_TAB -> { cycleTab(if (shift) -1 else 1); true }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                tabs.getOrNull(keyCode - KeyEvent.KEYCODE_1)?.let { session.selectTab(it.id) }
                true
            }
            else -> false
        }
    }
    DisposableEffect(c) {
        c.shortcutHandler = { code, shift -> shortcuts.value(code, shift) }
        onDispose { c.shortcutHandler = null }
    }

    BackHandler(enabled = ntpSearching && screen == Screen.Browser) { ntpSearching = false }
    BackHandler(enabled = screen != Screen.Browser) { screen = if (screen == Screen.ImportGuide) Screen.Bookmarks else Screen.Browser }
    BackHandler(enabled = screen == Screen.Browser && selected?.canGoBack == true) { selected?.let { session.goBack(it.id) } }
    BackHandler(enabled = screen == Screen.Browser && address.focused) { focusManager.clearFocus() }

    BrowserZTheme(darkTheme = dark) {
        if (inPictureInPicture) {
            // The floating window shows only the page (its video laid over it) or the page's full-screen view. Sites such
            // as YouTube pause when their page is resized, so the page keeps its size and the window shows it scaled
            // down, cropped to the video: the video is centred in the page, and the window has the video's shape.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                val windowWidth = constraints.maxWidth.toFloat()
                val windowHeight = constraints.maxHeight.toFloat()
                val page = c.pageSizePx.takeIf { it.width > 0 && it.height > 0 } ?: androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight)
                val videoWidth = minOf(page.width.toFloat(), page.height * (windowWidth / windowHeight))
                val scale = windowWidth / videoWidth
                val density = androidx.compose.ui.platform.LocalDensity.current
                val pageSize = with(density) { androidx.compose.ui.unit.DpSize(page.width.toDp(), page.height.toDp()) }
                WebViewHost(
                    session,
                    Modifier
                        .align(androidx.compose.ui.Alignment.Center)
                        .requiredSize(pageSize)
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                )
                FullscreenHost(fullscreen, onExit = { session.exitFullscreen() })
            }
            return@BrowserZTheme
        }
        Surface(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.Bookmarks, Screen.History, Screen.Saved ->
                    LibraryPageContent(screen, { screen = Screen.Browser }, Modifier.safeDrawingPadding())

                Screen.ImportGuide -> ImportGuideScreen(
                    onChooseFile = { importLauncher.launch(arrayOf("*/*")) },
                    onAutofillSettings = { PageActions.openAutofillSettings(context) },
                    onClose = { screen = Screen.Browser },
                    modifier = Modifier.safeDrawingPadding(),
                )

                Screen.Settings -> SettingsScreen(
                    settings = settings,
                    onUpdate = { c.settings.update(it) },
                    onClearData = { clearHistory, clearCookies, clearCache ->
                        if (clearHistory) c.history.clear()
                        if (clearCookies) c.siteData.clearCookiesAndSiteData()
                        if (clearCache) c.siteData.clearCache()
                        Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { screen = Screen.Browser },
                    passkeysSupported = session.supportsPasskeys,
                    isDefaultBrowser = remember(screen) { PageActions.isDefaultBrowser(context) },
                    onMakeDefault = { PageActions.requestDefaultBrowser(context) },
                    searchSection = {
                        SearchEnginesSection(
                            engines = engines,
                            defaultId = defaultEngine.id,
                            onSetDefault = { c.engines.setDefault(it) },
                            onAdd = { name, url, keyword -> c.engines.addCustom(name, url, keyword) },
                            onRemove = { c.engines.remove(it) },
                        )
                    },
                    filtersSection = {
                        FiltersSection(
                            adBlockEnabled = settings.adBlockEnabled,
                            onAdBlockChange = { enabled -> c.settings.update { it.copy(adBlockEnabled = enabled) } },
                            blockPopups = settings.blockPopups,
                            onBlockPopupsChange = { on -> c.settings.update { it.copy(blockPopups = on) } },
                            status = filterStatus,
                            onUpdate = { c.filters.updateNow() },
                            allowedHosts = allowedHosts,
                            onRemoveHost = { c.siteSettings.setAdsAllowed(it, false) },
                        )
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )

                Screen.Browser -> {
                    val host = siteHost(selected)
                    val placement = when (settings.toolbarPosition) {
                        ToolbarPosition.Auto -> BarPlacement.Auto
                        ToolbarPosition.Top -> BarPlacement.Top
                        ToolbarPosition.Bottom -> BarPlacement.Bottom
                    }
                    val barAtBottom = toolbarAtBottom(placement, layout)
                    val tabsOnSide = settings.tabsPosition == TabsPosition.Side
                    val actions = BrowserActions(
                        onBack = { selected?.let { session.goBack(it.id) } },
                        onForward = { selected?.let { session.goForward(it.id) } },
                        onReloadOrStop = { selected?.let { if (it.isLoading) session.stop(it.id) else session.reload(it.id) } },
                        onSubmit = ::navigate,
                        onToggleBookmark = {
                            selected?.takeIf { !it.isBlank }?.let { tab ->
                                val wasSaved = bookmarks.any { it.url == tab.url }
                                c.bookmarks.toggle(tab.url, tab.title)
                                savedBookmarkUrl = if (wasSaved) null else tab.url
                            }
                        },
                        onHome = { EngineHome.url(defaultEngine)?.let(::openInCurrentTab) },
                        onNewTab = { newTab() },
                        onReopenClosed = { session.reopenClosedTab() },
                        onPrint = { selected?.let { session.print(it.id) } },
                        onSiteInfo = { siteInfoOpen = true },
                        onGroup = { groupDialogOpen = true },
                        onFind = { findOpen = true },
                        onReader = { selected?.let { session.readerMode(it.id) } },
                        onTranslate = { selected?.takeIf { !it.isBlank }?.let { openInCurrentTab(PageActions.translateUrl(it.url)) } },
                        onShare = { selected?.takeIf { !it.isBlank }?.let { PageActions.share(context, it) } },
                        onAddToHome = { selected?.takeIf { !it.isBlank }?.let { PageActions.addToHome(context, it) } },
                        onSavePage = {
                            selected?.let { tab ->
                                session.savePage(tab.id) { ok ->
                                    Toast.makeText(context, if (ok) "Saved for offline reading" else "Couldn't save this page", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDownloads = { PageActions.openDownloads(context) },
                        onNewIncognitoTab = { newTab(incognito = true) },
                        onShowTabs = {
                            session.captureSelectedThumbnail()
                            showTabs = true
                        },
                        onOpenBookmarks = { openLibrary(Screen.Bookmarks) },
                        onOpenHistory = { openLibrary(Screen.History) },
                        onOpenSaved = { openLibrary(Screen.Saved) },
                        onAddShortcut = {
                            selected?.takeIf { !it.isBlank }?.let { tab -> pinShortcut(tab.url, tab.title) }
                        },
                        onOpenSettings = { screen = Screen.Settings },
                        onToggleDesktop = { selected?.let { session.setDesktopMode(it.id, !it.desktopMode) } },
                        onToggleAdsOnSite = {
                            if (host.isNotEmpty()) {
                                c.siteSettings.setAdsAllowed(host, host !in allowedHosts)
                                // Apply the new rule to this tab's start script, then reload so the page reflects it.
                                session.applySettingsToAll()
                                selected?.let { session.reloadFresh(it.id) }
                            }
                        },
                        onSelectEngine = { c.engines.setDefault(it) },
                        onToggleToolbarPosition = {
                            c.settings.update {
                                it.copy(toolbarPosition = if (barAtBottom) ToolbarPosition.Top else ToolbarPosition.Bottom)
                            }
                        },
                        onTogglePin = { selected?.let { session.setPinned(it.id, !it.pinned) } },
                        onSwipeTab = { step ->
                            // Like Chrome: stays among normal or incognito tabs, and stops at the ends.
                            val pool = tabs.filter { it.isIncognito == selected?.isIncognito }
                            val index = pool.indexOfFirst { it.id == selectedId }
                            pool.getOrNull(index + step)?.let {
                                session.selectTab(it.id)
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                        onToggleTabsPosition = {
                            c.settings.update {
                                it.copy(tabsPosition = if (tabsOnSide) TabsPosition.Top else TabsPosition.Side)
                            }
                        },
                    )

                    val query = address.value.text
                    val showSuggestions = address.focused && query.isNotBlank() && query != selected?.url

                    AdaptiveShell(
                        toolbarPlacement = placement,
                        toolbarVisible = (barsVisible || address.focused || selected == null || selected.isBlank) && !(ntpSearching && (selected == null || selected.isBlank)),
                        tabsOnSide = tabsOnSide,
                        tabSidebar = {
                            TabSidebar(
                                tabs = tabs,
                                selectedId = selectedId,
                                onSelect = { session.selectTab(it) },
                                onClose = { session.closeTab(it) },
                                onTogglePin = { id -> tabs.firstOrNull { it.id == id }?.let { session.setPinned(id, !it.pinned) } },
                                onNewTab = { newTab() },
                            )
                        },
                        tabStrip = {
                            TabStrip(
                                tabs = tabs,
                                selectedId = selectedId,
                                onSelect = { session.selectTab(it) },
                                onClose = { session.closeTab(it) },
                                onTogglePin = { id -> tabs.firstOrNull { it.id == id }?.let { session.setPinned(id, !it.pinned) } },
                                onNewTab = { newTab() },
                            )
                        },
                        toolbar = {
                            BrowserToolbar(
                                tab = selected,
                                tabCount = tabs.size,
                                isBookmarked = selected != null && bookmarks.any { it.url == selected.url },
                                adBlockEnabled = settings.adBlockEnabled,
                                adsAllowedOnSite = host in allowedHosts,
                                supportsIncognito = supportsIncognito,
                                engines = engines,
                                defaultEngine = defaultEngine,
                                toolbarAtBottom = barAtBottom,
                                tabsOnSide = tabsOnSide,
                                hasClosedTabs = closedTabs.isNotEmpty(),
                                address = address,
                                actions = actions,
                            )
                        },
                        sidePanel = libraryPanel?.let { panel -> { LibraryPageContent(panel, { libraryPanel = null }, Modifier) } },
                        page = {
                            Box(Modifier.fillMaxSize()) {
                                if (selected == null || selected.isBlank) {
                                    NewTabPage(
                                        shortcuts = ntpShortcuts,
                                        engines = engines,
                                        engineName = defaultEngine.name,
                                        blockedTotal = blockedTotal,
                                        recent = ntpRecent,
                                        searching = ntpSearching,
                                        onSearchingChange = { ntpSearching = it },
                                        actions = ntpActions,
                                    )
                                } else {
                                    WebViewHost(session, Modifier.fillMaxSize().onSizeChanged { c.pageSizePx = it })
                                    PullRefreshIndicator(pull, refreshing, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                                }
                                UndoBanner(
                                    message = "Closed ${undoFor?.title?.ifBlank { null } ?: undoFor?.url?.substringAfter("://")?.removePrefix("www.")?.substringBefore('/') ?: "tab"}",
                                    visible = undoFor != null && savedBookmarkUrl == null && popupNotice == null,
                                    onUndo = { undoFor = null; session.reopenClosedTab() },
                                    onDismiss = { undoFor = null },
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                                )
                                BookmarkSavedBanner(
                                    visible = savedBookmarkUrl != null && popupNotice == null,
                                    onChooseFolder = { choosingFolderFor = savedBookmarkUrl; savedBookmarkUrl = null },
                                    onDismiss = { savedBookmarkUrl = null },
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                                )
                                if (findOpen && selected != null) {
                                    FindBar(
                                        result = findResult,
                                        onQuery = { session.findInPage(selected.id, it) },
                                        onStep = { session.findNext(selected.id, it) },
                                        onClose = { session.clearFind(selected.id); findOpen = false },
                                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                                    )
                                }
                                PopupBlockedBanner(
                                    host = popupNotice?.host,
                                    onOpen = { popupNotice?.let { session.openBlockedPopup(it) } },
                                    onDismiss = { session.dismissPopupNotice() },
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                                )
                                if (showSuggestions) {
                                    val suggestions = remember(query, bookmarks, history, defaultEngine) {
                                        c.buildSuggestions(query, bookmarks, history, defaultEngine)
                                    }
                                    SuggestionsPanel(
                                        suggestions = suggestions,
                                        engines = engines,
                                        onSearchWith = { searchWith(it, query) },
                                        onOpenPage = ::openInCurrentTab,
                                    )
                                }
                            }
                        },
                    )

                    if (showTabs && layout == LayoutClass.Compact) {
                        // Read so the cards redraw when a picture or icon arrives.
                        val previewVersion by session.previewVersion.collectAsStateWithLifecycle()
                        TabSwitcherDialog(
                            tabs = tabs,
                            selectedId = selectedId,
                            supportsIncognito = supportsIncognito,
                            thumbnail = { id -> previewVersion.let { session.thumbnail(id)?.asImageBitmap() } },
                            favicon = { id -> previewVersion.let { session.favicon(id)?.asImageBitmap() } },
                            onSelect = { session.selectTab(it); showTabs = false },
                            onClose = { session.closeTab(it) },
                            onTogglePin = { id -> tabs.firstOrNull { it.id == id }?.let { session.setPinned(id, !it.pinned) } },
                            onNewTab = { newTab() },
                            onNewIncognitoTab = { newTab(incognito = true) },
                            onDismiss = { showTabs = false },
                        )
                    }
                }
            }
            jsDialog?.let { dialog ->
                PageDialog(
                    kind = PageDialogKind.valueOf(dialog.kind.name),
                    host = dialog.host,
                    message = dialog.message,
                    defaultText = dialog.defaultText,
                    canSuppress = dialog.canSuppress,
                    onAnswer = dialog::answer,
                )
            }
            permissionPrompt?.let { prompt ->
                PermissionDialog(
                    host = prompt.host,
                    kinds = prompt.kinds,
                    onAllow = prompt.allow,
                    onBlock = prompt.block,
                    onDismiss = prompt.dismiss,
                )
            }
            if (siteInfoOpen && selected != null && !selected.isBlank) {
                val host = siteHost(selected)
                val (issuer, until) = remember(selected.id, selected.url) { session.siteCertificate(selected.id) }
                val decisions = remember(host, permissionVersion) { SitePermission.entries.associateWith { c.sitePermissions.decision(host, it) } }
                SiteInfoSheet(
                    info = SiteInfo(
                        host = host,
                        secure = selected.url.startsWith("https://"),
                        issuer = issuer,
                        validUntil = until,
                        decisions = decisions,
                        adBlockEnabled = settings.adBlockEnabled,
                        adsAllowed = host in allowedHosts,
                    ),
                    actions = SiteInfoActions(
                        onPermission = { permission, decision -> c.sitePermissions.set(host, permission, decision) },
                        onResetPermissions = { c.sitePermissions.reset(host) },
                        onToggleAds = { c.siteSettings.setAdsAllowed(host, host !in allowedHosts) },
                    ),
                    onDismiss = { siteInfoOpen = false },
                )
            }
            linkTarget?.let { target ->
                LinkMenuSheet(
                    target = target,
                    actions = LinkMenuActions(
                        onOpenInNewTab = { url ->
                            session.openTab(url, incognito = target.incognito, select = false)
                            Toast.makeText(context, "Opened in a new tab", Toast.LENGTH_SHORT).show()
                        },
                        onOpenInIncognito = if (supportsIncognito) { url -> session.openTab(url, incognito = true, select = true) } else null,
                        onCopy = { label, text -> PageActions.copy(context, label, text) },
                        onDownload = { url -> selected?.let { session.download(it.id, url) } },
                        onShare = { PageActions.shareText(context, it) },
                    ),
                    onDismiss = { session.dismissLinkMenu() },
                )
            }
            var offerImport by remember { mutableStateOf(!c.importOfferSeen) }
            if (offerImport && screen == Screen.Browser) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { offerImport = false; c.importOfferSeen = true },
                    title = { androidx.compose.material3.Text("Bring your bookmarks?") },
                    text = { androidx.compose.material3.Text("Import bookmarks from Chrome, Firefox, Edge or another browser using a file you export from it.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { offerImport = false; c.importOfferSeen = true; screen = Screen.ImportGuide }) {
                            androidx.compose.material3.Text("Show me how")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { offerImport = false; c.importOfferSeen = true }) {
                            androidx.compose.material3.Text("Not now")
                        }
                    },
                )
            }
            if (groupDialogOpen && selected != null) {
                TabGroupDialog(
                    existing = com.browserz.core.domain.TabGroups.names(tabs.map { it.group }),
                    current = selected.group,
                    onPick = { session.setGroup(selected.id, it) },
                    onDismiss = { groupDialogOpen = false },
                )
            }
            choosingFolderFor?.let { url ->
                MoveToFolderDialog(
                    folders = bookmarkFolders,
                    current = bookmarks.firstOrNull { it.url == url }?.folder,
                    onPick = { c.bookmarks.move(url, it) },
                    onNewFolder = { c.bookmarks.addFolder(it) },
                    onDismiss = { choosingFolderFor = null },
                )
            }
            FullscreenHost(fullscreen, onExit = { session.exitFullscreen() })
          }
        }
    }
}
