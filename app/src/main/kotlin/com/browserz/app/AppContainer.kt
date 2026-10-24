package com.browserz.app

import android.app.Application
import android.content.Context
import com.browserz.core.data.PrefsBlockStats
import com.browserz.core.data.PrefsBookmarkRepository
import com.browserz.core.data.PrefsHistoryRepository
import com.browserz.core.data.PrefsSearchEngineRepository
import com.browserz.core.data.PrefsSettingsRepository
import com.browserz.core.data.PrefsShortcutRepository
import com.browserz.core.data.PrefsSitePermissionRepository
import com.browserz.core.data.PrefsSiteSettingsRepository
import com.browserz.core.domain.BuildSuggestions
import com.browserz.core.domain.ResolveInput
import com.browserz.core.webview.WebViewBrowserSession
import com.browserz.core.webview.WebViewSiteDataManager
import com.browserz.engine.adblock.FilterListUpdater

/** Wires ports to adapters. The only place that knows every module. */
class AppContainer(app: Application) {
    private val prefs = app.getSharedPreferences("browserz", Context.MODE_PRIVATE)

    val settings = PrefsSettingsRepository(prefs)
    val engines = PrefsSearchEngineRepository(prefs)
    val bookmarks = PrefsBookmarkRepository(prefs)
    val history = PrefsHistoryRepository(prefs)
    val siteSettings = PrefsSiteSettingsRepository(prefs)
    val shortcuts = PrefsShortcutRepository(prefs)
    val sitePermissions = PrefsSitePermissionRepository(prefs)
    val stats = PrefsBlockStats(prefs)

    val filters = FilterListUpdater(app).also { it.start() }
    val siteData = WebViewSiteDataManager(app)
    val session = WebViewBrowserSession(
        context = app,
        blocker = filters.blocker,
        settingsRepo = settings,
        siteSettings = siteSettings,
        sitePermissions = sitePermissions,
        history = history,
        stats = stats,
        prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE),
    )

    /** True while the window is a picture-in-picture window; the root then shows only the page. */
    val pictureInPicture = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Size of the page area in pixels outside picture-in-picture; PiP keeps the page at this size (see the root). */
    var pageSizePx = androidx.compose.ui.unit.IntSize.Zero

    init {
        MediaPlaybackService.watch(app, this)
    }

    /** The "bring your bookmarks" offer is shown once, on the first launch. */
    var importOfferSeen: Boolean
        get() = prefs.getBoolean("importOfferSeen", false)
        set(value) = prefs.edit().putBoolean("importOfferSeen", value).apply()

    val resolveInput = ResolveInput()
    val buildSuggestions = BuildSuggestions()

    /** Set by the root composable; receives (keyCode, shift) for Ctrl+key shortcuts. */
    var shortcutHandler: ((Int, Boolean) -> Boolean)? = null

    /** Applies the "clear on exit" choices. Called when the activity is really finishing. */
    fun clearOnExit() {
        val s = settings.settings.value
        if (s.clearHistoryOnExit) history.clear()
        if (s.clearCookiesOnExit) siteData.clearCookiesAndSiteData()
        if (s.clearCacheOnExit) siteData.clearCache()
    }
}
