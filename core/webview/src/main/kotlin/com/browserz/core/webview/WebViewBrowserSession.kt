package com.browserz.core.webview

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.content.MutableContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.webkit.ProfileStore
import androidx.webkit.WebMessageCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.browserz.core.domain.BlockDecision
import com.browserz.core.domain.BlockStats
import com.browserz.core.domain.ClosedTab
import com.browserz.core.domain.ClosedTabs
import com.browserz.core.domain.HttpsOnly
import com.browserz.core.domain.SitePermission
import com.browserz.core.domain.SitePermissionRepository
import com.browserz.core.domain.BrowserSession
import com.browserz.core.domain.ContentBlocker
import com.browserz.core.domain.CookieMode
import com.browserz.core.domain.DesktopMode
import com.browserz.core.domain.FindResult
import com.browserz.core.domain.HistoryRepository
import com.browserz.core.domain.LinkTargets
import com.browserz.core.domain.LinkTarget
import com.browserz.core.domain.Hosts
import com.browserz.core.domain.PopupPolicy
import com.browserz.core.domain.PopupVerdict
import com.browserz.core.domain.RequestType
import com.browserz.core.domain.SavedPage
import com.browserz.core.domain.SettingsRepository
import com.browserz.core.domain.SiteSettingsRepository
import com.browserz.core.domain.TabGroups
import com.browserz.core.domain.TabInfo
import com.browserz.core.domain.TabOrder
import com.browserz.core.domain.UserAgents
import com.browserz.core.domain.VideoShape
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A page asked for full screen (video or the Fullscreen API): [view] is what WebView wants shown edge to edge. */
data class FullscreenVideo(val view: View, val tabId: Long, val shape: VideoShape = VideoShape.Unknown)

/** A pop-up the browser held back; the UI offers to open it anyway. */
data class PopupNotice(val host: String, val url: String)

/**
 * Owns every tab and its WebView. All public methods must be called on the main thread, except the
 * WebViewClient callbacks that WebView itself delivers on its IO thread (shouldInterceptRequest).
 *
 * Tabs lifecycle: Active (attached, resumed) -> Background (kept in RAM, paused) -> Suspended
 * (state bundle in memory, WebView destroyed). The number of live WebViews is capped.
 */
class WebViewBrowserSession(
    context: Context,
    private val blocker: ContentBlocker,
    private val settingsRepo: SettingsRepository,
    private val siteSettings: SiteSettingsRepository,
    sitePermissions: SitePermissionRepository,
    private val history: HistoryRepository,
    private val stats: BlockStats,
    private val prefs: SharedPreferences,
) : BrowserSession {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val runtimes = ArrayList<TabRuntime>()
    private val saved = SavedPageStore(appContext, prefs)
    private val permissionBroker = PermissionBroker(appContext, sitePermissions)

    /** A page is asking for camera, microphone or location and the user has not answered for this site yet. */
    val permissionPrompt: StateFlow<PermissionPrompt?> = permissionBroker.prompt

    /** Lets the activity ask Android for runtime permissions on behalf of a page the user allowed. */
    fun setRuntimePermissionRequester(requester: ((Array<String>, (Boolean) -> Unit) -> Unit)?) {
        permissionBroker.runtimeRequester = requester
    }

    fun dismissPermissionPrompt() = permissionBroker.dismissPrompt()

    private val _jsDialog = MutableStateFlow<JsDialog?>(null)

    /** A page of the selected tab is waiting for an answer to alert(), confirm(), prompt() or "leave site?". */
    val jsDialog: StateFlow<JsDialog?> = _jsDialog.asStateFlow()

    /** Set by the activity: opens the system file picker for a page's file input and reports the chosen files. */
    private var fileChooser: ((Intent, (Array<Uri>?) -> Unit) -> Unit)? = null

    fun setFileChooser(chooser: ((Intent, (Array<Uri>?) -> Unit) -> Unit)?) {
        fileChooser = chooser
    }

    private val _closed = MutableStateFlow<List<ClosedTab>>(emptyList())
    override val recentlyClosed: StateFlow<List<ClosedTab>> = _closed.asStateFlow()

    /** Hosts where the user chose to continue over plain http after the secure address failed. */
    private val insecureHosts = HashSet<String>()

    private val _find = MutableStateFlow(FindResult())
    override val findResult: StateFlow<FindResult> = _find.asStateFlow()
    override val savedPages: StateFlow<List<SavedPage>> = saved.pages

    /** Windows a page opened that are waiting for their first URL before we decide to show or drop them. */
    private val pendingPopups = ArrayList<TabRuntime>()
    private var selectedId: Long? = null
    private var nextId = 1L

    private val _tabs = MutableStateFlow<List<TabInfo>>(emptyList())
    private val _selected = MutableStateFlow<Long?>(null)
    private val _hostVersion = MutableStateFlow(0)

    override val tabs: StateFlow<List<TabInfo>> = _tabs.asStateFlow()
    override val selectedTabId: StateFlow<Long?> = _selected.asStateFlow()
    val hostVersion: StateFlow<Int> = _hostVersion.asStateFlow()

    private val _fullscreen = MutableStateFlow<FullscreenVideo?>(null)
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** Non-null while a page is in full screen; the app shows [FullscreenVideo.view] over everything else. */
    val fullscreen: StateFlow<FullscreenVideo?> = _fullscreen.asStateFlow()

    private val _popupNotice = MutableStateFlow<PopupNotice?>(null)

    /** Set when a pop-up was held back and the user may want it; cleared by [dismissPopupNotice]. */
    val popupNotice: StateFlow<PopupNotice?> = _popupNotice.asStateFlow()

    private val _linkMenu = MutableStateFlow<LinkTarget?>(null)

    /** Set when the user long-presses a link or image; the UI shows the Chrome-style menu and calls [dismissLinkMenu]. */
    val linkMenu: StateFlow<LinkTarget?> = _linkMenu.asStateFlow()

    private val _barsVisible = MutableStateFlow(true)

    /** False while the user is scrolling a page down; the UI slides the address bar away. */
    val barsVisible: StateFlow<Boolean> = _barsVisible.asStateFlow()
    private var scrollAccum = 0
    private var lastBarChange = 0L

    private val _pull = MutableStateFlow(0f)

    /** How far the user has pulled the page down, as a share of the distance that reloads it (0 = not pulling). */
    val pull: StateFlow<Float> = _pull.asStateFlow()

    private val _refreshing = MutableStateFlow(false)

    /** True from a pull-to-refresh until the reloaded page has finished loading. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val endRefresh = Runnable { _refreshing.value = false }

    private val _media = MutableStateFlow<MediaPlayback?>(null)

    /** The tab whose video should be started again after the engine paused it in the background (see [resumeHiddenVideo]). */
    private var resumeVideoFor = -1L

    /** The audible media a tab plays or last played (paused); null when none. Drives the media notification and PiP. */
    val media: StateFlow<MediaPlayback?> = _media.asStateFlow()

    /** Plays, pauses or skips the media in [media]: play, pause, next, previous; stop pauses and forgets it. */
    fun mediaControl(action: String) {
        if (action == "pause" || action == "stop") resumeVideoFor = -1L // the user (or the notification) asked: do not resume
        val current = _media.value ?: return
        val wv = find(current.tabId)?.webView
        if (wv == null) {
            _media.value = null
            return
        }
        wv.evaluateJavascript(PlaybackScript.control(if (action == "stop") "pause" else action), null)
        if (action == "stop") _media.value = null
    }

    /** True while the app's window is a picture-in-picture window: the playing video is laid over its page. */
    fun setPictureInPicture(on: Boolean) {
        val tab = find(_media.value?.tabId) ?: find(selectedId) ?: return
        tab.webView?.evaluateJavascript(PlaybackScript.pictureInPicture(on), null)
    }

    private fun onMediaMessage(rt: TabRuntime, data: String) {
        val o = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return
        }
        val current = _media.value
        when (o.optString("s")) {
            "play" -> _media.value = MediaPlayback(
                tabId = rt.id,
                title = o.optString("t").ifBlank { rt.title }.take(200),
                artist = o.optString("a").take(200),
                host = hostOf(rt.pageUrl).removePrefix("www."),
                playing = true,
                hasVideo = o.optInt("v") == 1,
                videoWidth = o.optInt("w").coerceIn(0, 16384),
                videoHeight = o.optInt("h").coerceIn(0, 16384),
                canNext = o.optInt("n") == 1,
                canPrevious = o.optInt("p") == 1,
            )
            "pause", "ended" -> if (current != null && current.tabId == rt.id) _media.value = current.copy(playing = false)
        }
    }

    /** The tab's page went away (navigation, close, crash): its media is gone with it. */
    private fun forgetMedia(rt: TabRuntime) {
        if (_media.value?.tabId == rt.id) _media.value = null
    }

    /**
     * The page changed its address without loading a new document: media it no longer shows is paused. Sites swap the
     * page a moment after the address, so the check runs again a little later.
     */
    private fun pauseHiddenMedia(rt: TabRuntime, view: WebView) {
        view.evaluateJavascript(PlaybackScript.HIDDEN_CHECK, null)
        for (delay in HIDDEN_CHECK_DELAYS_MS) {
            main.postDelayed({
                if (rt.webView === view && isPlayingMedia(rt)) view.evaluateJavascript(PlaybackScript.HIDDEN_CHECK, null)
            }, delay)
        }
    }

    private fun isPlayingMedia(rt: TabRuntime): Boolean = _media.value?.let { it.tabId == rt.id && it.playing } == true

    private val _previewVersion = MutableStateFlow(0)

    /** Changes whenever a tab's [thumbnail] or [favicon] changes, so the tab switcher can redraw. */
    val previewVersion: StateFlow<Int> = _previewVersion.asStateFlow()

    /** A small picture of the tab as it was last seen on screen; null until it has been shown (not kept across restarts). */
    fun thumbnail(tabId: Long): Bitmap? = find(tabId)?.thumbnail

    fun favicon(tabId: Long): Bitmap? = find(tabId)?.favicon

    /** Takes a fresh picture of the tab on screen, e.g. right before the tab switcher opens. */
    fun captureSelectedThumbnail() {
        find(selectedId)?.let(::captureThumbnail)
    }

    /** True when this device's WebView can hand passkey (WebAuthn) requests to the system's Credential Manager. */
    val supportsPasskeys: Boolean by lazy {
        try {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Asking WebView about features loads the WebView provider (slow), so it is done lazily, not at startup. */
    override val supportsIncognito: Boolean by lazy {
        try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (_: RuntimeException) {
            false
        }
    }

    private var wideScreen = false
    private var darkTheme = false
    private var hostBase: Context = appContext
    private var hostActive = true

    /** Live WebViews are the biggest memory cost, so the cap follows the device's RAM (totalMem is a bit under nominal). */
    private val maxLiveWebViews: Int = run {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val gigabytes = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem / (1024.0 * 1024.0 * 1024.0)
        when {
            am.isLowRamDevice || gigabytes <= 2.5 -> 2
            gigabytes <= 4.5 -> 3
            gigabytes <= 6.5 -> 4
            else -> 6
        }
    }

    private val publishPending = AtomicBoolean(false)
    private val publishRunnable = Runnable {
        publishPending.set(false)
        publish()
    }
    private val saveRunnable = Runnable { saveTabs() }

    init {
        restoreTabs()
    }

    // ---------------------------------------------------------------- BrowserSession

    override fun openTab(url: String, incognito: Boolean, select: Boolean): Long {
        if (incognito && !supportsIncognito) return -1L
        val rt = TabRuntime(nextId++, incognito, appContext)
        val target = upgradeFor(rt, url)
        rt.url = target
        rt.pageUrl = target
        rt.lastActive = System.currentTimeMillis()
        runtimes.add(rt)
        if (select) find(selectedId)?.let(::captureThumbnail)
        if (select || selectedId == null) selectedId = rt.id
        publish()
        bumpHost()
        return rt.id
    }

    override fun load(tabId: Long, url: String) {
        val rt = find(tabId) ?: return
        val target = upgradeFor(rt, url)
        rt.url = target
        rt.pageUrl = target
        rt.title = ""
        rt.errorUrl = null
        rt.blocked.set(0)
        showBars()
        rt.webView?.loadUrl(target)
        publish()
        bumpHost()
    }

    override fun selectTab(tabId: Long) {
        if (tabId != selectedId) _find.value = FindResult()
        val rt = find(tabId) ?: return
        if (selectedId == tabId) return
        stopRefreshIndicator()
        cancelJsDialog()
        find(selectedId)?.let(::captureThumbnail)
        find(selectedId)?.webView?.onPause()
        _fullscreen.value?.let { exitFullscreen() }
        showBars()
        selectedId = tabId
        rt.lastActive = System.currentTimeMillis()
        publish()
        bumpHost()
    }

    override fun closeTab(tabId: Long) {
        val index = runtimes.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val rt = runtimes.removeAt(index)
        if (tabId == selectedId) cancelJsDialog()
        if (!rt.incognito && rt.url.isNotBlank() && !rt.url.startsWith("file:") && !rt.pendingPopup) {
            _closed.value = ClosedTabs.push(_closed.value, ClosedTab(rt.url, rt.title, rt.pinned, rt.group))
        }
        leaveFullscreenOf(rt.id)
        destroyWebView(rt)
        if (selectedId == tabId) {
            // Like Chrome: closing a tab that a page opened goes back to the page.
            val opener = runtimes.firstOrNull { it.id == rt.openerId }
            selectedId = opener?.id ?: runtimes.getOrNull(index.coerceAtMost(runtimes.lastIndex))?.id
        }
        if (runtimes.isEmpty()) {
            val blank = TabRuntime(nextId++, false, appContext)
            runtimes.add(blank)
            selectedId = blank.id
        }
        if (rt.incognito && runtimes.none { it.incognito }) deleteIncognitoProfile()
        publish()
        bumpHost()
    }

    override fun goBack(tabId: Long) {
        val rt = find(tabId) ?: return
        val wv = rt.webView
        if (wv != null && wv.canGoBack()) wv.goBack()
    }

    override fun goForward(tabId: Long) {
        val rt = find(tabId) ?: return
        val wv = rt.webView
        if (wv != null && wv.canGoForward()) wv.goForward()
    }

    override fun reload(tabId: Long) {
        val rt = find(tabId) ?: return
        val failed = rt.errorUrl
        if (failed != null) {
            rt.errorUrl = null
            rt.webView?.loadUrl(failed)
        } else {
            rt.webView?.reload()
        }
    }

    /**
     * Reloads after a blocking rule changed. Resources already in the renderer's memory cache are served
     * without going through request interception, so they would bypass a rule that was just switched on.
     */
    fun reloadFresh(tabId: Long) {
        val rt = find(tabId) ?: return
        rt.errorUrl = null
        rt.webView?.let {
            it.clearCache(false)
            it.reload()
        }
    }

    override fun stop(tabId: Long) {
        find(tabId)?.webView?.stopLoading()
    }

    override fun reopenClosedTab(): Long? {
        val (tab, rest) = ClosedTabs.pop(_closed.value)
        tab ?: return null
        _closed.value = rest
        val id = openTab(tab.url, incognito = false, select = true)
        if (id >= 0) {
            if (tab.pinned) setPinned(id, true) else tab.group?.let { setGroup(id, it) }
        }
        return id.takeIf { it >= 0 }
    }

    override fun setPinned(tabId: Long, pinned: Boolean) {
        val rt = find(tabId) ?: return
        if (rt.pinned == pinned) return
        rt.pinned = pinned
        runtimes.remove(rt)
        runtimes.add(TabOrder.indexAfterPinChange(runtimes) { it.pinned }, rt)
        if (pinned) rt.group = null
        arrangeTabs()
        publish()
    }

    override fun setGroup(tabId: Long, group: String?) {
        val rt = find(tabId) ?: return
        val name = if (rt.pinned) null else TabGroups.normalize(group)
        if (rt.group == name) return
        rt.group = name
        arrangeTabs()
        publish()
    }

    private fun arrangeTabs() {
        val arranged = TabOrder.arrange(runtimes.toList(), { it.pinned }, { it.group })
        runtimes.clear()
        runtimes.addAll(arranged)
    }

    override fun findInPage(tabId: Long, query: String) {
        val wv = find(tabId)?.webView ?: return
        if (query.isBlank()) {
            wv.clearMatches()
            _find.value = FindResult()
        } else {
            _find.value = FindResult(query, 0, 0)
            wv.findAllAsync(query)
        }
    }

    override fun findNext(tabId: Long, forward: Boolean) {
        find(tabId)?.webView?.findNext(forward)
    }

    override fun clearFind(tabId: Long) {
        find(tabId)?.webView?.clearMatches()
        _find.value = FindResult()
    }

    override fun readerMode(tabId: Long) {
        val wv = find(tabId)?.webView ?: return
        wv.evaluateJavascript(READER_JS) { result ->
            if (result?.contains("ok") != true) toast("No article found on this page")
        }
    }

    override fun savePage(tabId: Long, onDone: (Boolean) -> Unit) {
        val rt = find(tabId)
        val wv = rt?.webView
        if (rt == null || wv == null || rt.incognito || rt.url.isBlank() || !saved.prepare()) return onDone(false)
        val id = System.currentTimeMillis().toString()
        val title = rt.title.ifBlank { rt.url }
        val url = rt.url
        wv.saveWebArchive(saved.fileFor(id).absolutePath, false) { path ->
            if (path == null) {
                saved.fileFor(id).delete()
            } else {
                saved.add(SavedPage(id, title, url, System.currentTimeMillis()))
            }
            onDone(path != null)
        }
    }

    override fun openSavedPage(tabId: Long, id: String) {
        val rt = find(tabId) ?: return
        val file = saved.fileFor(id).takeIf { it.exists() } ?: return
        val wv = ensureWebView(rt) ?: return
        selectedId = tabId
        wv.settings.allowFileAccess = true // only for our own archives; turned off again when the page is left
        wv.loadUrl("file://" + file.absolutePath)
        publish()
    }

    override fun deleteSavedPage(id: String) = saved.remove(id)

    /** HTTPS-first: a public http address becomes https unless the user already chose to continue over http for that site. */
    private fun upgradeFor(rt: TabRuntime, url: String): String {
        if (!settingsRepo.settings.value.httpsOnly) return url
        val upgraded = HttpsOnly.upgrade(url) ?: return url
        if (Hosts.registrableDomain(hostOf(url)) in insecureHosts) return url
        rt.upgradedTo = upgraded
        return upgraded
    }

    /** Who vouched for the page's certificate and until when; null parts when the page is not https. */
    @Suppress("DEPRECATION")
    fun siteCertificate(tabId: Long): Pair<String?, String?> {
        val cert = find(tabId)?.webView?.certificate ?: return null to null
        val issuer = cert.issuedBy?.oName?.takeIf { it.isNotBlank() } ?: cert.issuedBy?.cName?.takeIf { it.isNotBlank() }
        return issuer to cert.validNotAfter
    }

    /** Opens Android's print dialog for the page; it offers "Save as PDF" among the printers. */
    fun print(tabId: Long) {
        val rt = find(tabId) ?: return
        val wv = rt.webView ?: return
        val activity = generateSequence(hostBase) { (it as? android.content.ContextWrapper)?.baseContext }.firstOrNull { it is android.app.Activity }
        val printManager = (activity ?: return) .getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
        val name = rt.title.ifBlank { "Browser-Z page" }
        printManager.print(name, wv.createPrintDocumentAdapter(name), null)
    }

    private fun toast(message: String) {
        main.post { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
    }

    override fun setDesktopMode(tabId: Long, enabled: Boolean) {
        val rt = find(tabId) ?: return
        rt.desktopOverride = enabled
        rt.webView?.let {
            applyUserAgent(it, rt)
            it.reload()
        }
        publish()
    }

    // ---------------------------------------------------------------- host integration

    /** Called by [WebViewHost]; inputs are only there so the caller re-runs this when they change. */
    @Suppress("UNUSED_PARAMETER")
    fun attach(container: FrameLayout, selected: Long?, version: Int, url: String?) {
        val rt = find(selectedId)
        hostBase = container.context
        for (tab in runtimes) tab.context.baseContext = themed(hostBase)
        val wv = if (rt != null && rt.url.isNotBlank()) ensureWebView(rt) else null
        if (wv == null) {
            container.removeAllViews()
            return
        }
        if (container.childCount == 1 && container.getChildAt(0) === wv) {
            if (hostActive) wv.onResume()
            return
        }
        container.removeAllViews()
        (wv.parent as? ViewGroup)?.removeView(wv)
        container.addView(
            wv,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        if (hostActive) wv.onResume()
        for (tab in runtimes) if (tab !== rt) tab.webView?.onPause()
    }

    /** The host view is going away (activity destroyed): free the Activity reference held by WebViews. */
    fun detach(container: FrameLayout) {
        container.removeAllViews()
        hostBase = appContext
        for (tab in runtimes) tab.context.baseContext = themed(hostBase)
    }

    fun onHostResumed() {
        hostActive = true
        for (tab in runtimes) tab.webView?.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        find(selectedId)?.webView?.onResume()
    }

    /**
     * The app's window left the screen or shrank to picture-in-picture. With [keepPlaying] (background play or
     * PiP) the tab that plays audible media keeps running, with its renderer kept at the importance of a visible
     * page; every other tab is paused as usual.
     */
    fun onHostPaused(keepPlaying: Boolean = false) {
        hostActive = false
        for (tab in runtimes) {
            val wv = tab.webView ?: continue
            if (keepPlaying && isPlayingMedia(tab)) {
                wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                if (_media.value?.hasVideo == true) resumeHiddenVideo(tab.id)
            } else {
                wv.onPause()
            }
        }
    }

    /**
     * The web engine pauses a <video> when its window is hidden (screen off), even with sound; audio-only players are
     * left alone. A video that was playing when the app went away is started again, unless the user paused it.
     */
    private fun resumeHiddenVideo(tabId: Long) {
        resumeVideoFor = tabId
        for (delay in RESUME_DELAYS_MS) {
            main.postDelayed({
                val media = _media.value
                if (!hostActive && resumeVideoFor == tabId && media != null && media.tabId == tabId && !media.playing) mediaControl("play")
            }, delay)
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            for (tab in runtimes.toList()) if (tab.id != selectedId && !isPlayingMedia(tab)) suspend(tab)
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            for (tab in runtimes) if (tab.id != selectedId) tab.thumbnail = null
            _previewVersion.value++
        }
    }

    /**
     * Draws the visible part of the tab's page into a small RGB_565 bitmap (about 60 KB). Only a tab that is on
     * screen can be drawn; a page drawn by the GPU only (video, WebGL) may come out blank, like in other browsers.
     */
    private fun captureThumbnail(rt: TabRuntime) {
        val wv = rt.webView ?: return
        if (wv.width <= 0 || wv.height <= 0 || wv.parent == null || rt.url.isBlank()) return
        try {
            val scale = THUMBNAIL_WIDTH.toFloat() / wv.width
            val height = (minOf(wv.height, (wv.width * THUMBNAIL_ASPECT).toInt()) * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, height, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.scale(scale, scale)
            canvas.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(canvas)
            rt.thumbnail = bitmap
            _previewVersion.value++
        } catch (_: RuntimeException) {
            // Out of memory for the bitmap or the WebView refused to draw: the card shows the site letter instead.
        } catch (_: OutOfMemoryError) {
        }
    }

    fun setWideScreen(wide: Boolean) {
        wideScreen = wide
    }

    fun setDarkTheme(dark: Boolean) {
        if (darkTheme == dark) return
        darkTheme = dark
        // A page decides "dark" from the context its WebView was built with, so the WebViews are rebuilt on the new
        // theme (their state is saved and restored); tabs that are not on screen are rebuilt when they are selected.
        var rebuilt = false
        for (tab in runtimes) {
            tab.context.baseContext = themed(hostBase)
            if (tab.webView != null) {
                suspend(tab)
                rebuilt = true
            }
        }
        if (rebuilt) bumpHost()
        applySettingsToAll()
    }

    /** What the pages should believe about night mode: the in-app theme choice, whatever the system says. */
    private fun themed(base: Context): Context = NightContext(base, darkTheme)

    private class NightContext(base: Context, night: Boolean) : ContextThemeWrapper(
        base,
        if (night) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light,
    ) {
        init {
            val config = Configuration(base.resources.configuration)
            val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            applyOverrideConfiguration(config)
        }
    }

    /** Re-applies cookie, theme and user-agent settings; call when [SettingsRepository] changes. */
    fun applySettingsToAll() {
        if (!settingsRepo.settings.value.hideBarOnScroll) showBars()
        for (tab in runtimes) {
            tab.cssHost = null // the lists may have changed: the page's next injection asks the engine again
            tab.webView?.let {
                (it as? TabWebView)?.pullEnabled = settingsRepo.settings.value.pullToRefresh
                (it as? TabWebView)?.keepPlayingHidden = settingsRepo.settings.value.let { s -> s.backgroundPlay || s.pictureInPicture }
                applyCookiePolicy(it, tab)
                applyDarkening(it)
                it.settings.textZoom = settingsRepo.settings.value.textZoom
                installStartScript(tab, it)
                installBackgroundScript(tab, it)
                if (!tab.loading && !isInternalUrl(tab.pageUrl)) injectCosmetic(tab, it, tab.pageUrl)
            }
        }
        publish()
    }

    fun selectedTab(): TabInfo? = _tabs.value.firstOrNull { it.id == _selected.value }

    // ---------------------------------------------------------------- internals

    private fun find(id: Long?): TabRuntime? = if (id == null) null else runtimes.firstOrNull { it.id == id }

    private fun bumpHost() {
        _hostVersion.value = _hostVersion.value + 1
    }

    private fun isDesktop(rt: TabRuntime): Boolean = rt.desktopOverride ?: when (settingsRepo.settings.value.desktopMode) {
        DesktopMode.Always -> true
        DesktopMode.Never -> false
        DesktopMode.Auto -> wideScreen
    }

    private fun publish() {
        _tabs.value = runtimes.map { it.toInfo(isDesktop(it)) }
        _selected.value = selectedId
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    private fun schedulePublish() {
        if (publishPending.compareAndSet(false, true)) main.postDelayed(publishRunnable, PUBLISH_DELAY_MS)
    }

    private fun ensureWebView(rt: TabRuntime): WebView? {
        rt.webView?.let { return it }
        enforceLiveCap(rt)
        val wv = try {
            createWebView(rt)
        } catch (_: RuntimeException) {
            return null // WebView provider unavailable (e.g. being updated)
        }
        rt.webView = wv
        val saved = rt.savedState
        rt.savedState = null
        if (saved == null || wv.restoreState(saved) == null) wv.loadUrl(rt.url)
        return wv
    }

    /** Leaves full screen (back button or the page's own exit button). Safe to call at any time. */
    fun exitFullscreen() {
        val callback = fullscreenCallback
        fullscreenCallback = null
        _fullscreen.value = null
        callback?.onCustomViewHidden()
    }

    /** Asks the page for the size of the fullscreen (or playing) video, for players the media hook did not see. */
    private fun probeVideoShape(rt: TabRuntime) {
        val wv = rt.webView ?: return
        wv.evaluateJavascript(PROBE_SCRIPT) { result ->
            val size = result?.trim('"')?.takeIf { it.isNotEmpty() && it != "null" }
            if (size != null && _fullscreen.value?.tabId == rt.id) onVideoSize(rt, size)
        }
    }

    private fun leaveFullscreenOf(tabId: Long) {
        if (_fullscreen.value?.tabId == tabId) exitFullscreen()
    }

    fun dismissLinkMenu() {
        _linkMenu.value = null
    }

    /** Downloads [url] with the cookies and user agent of the tab it came from. */
    fun download(tabId: Long, url: String) {
        val rt = find(tabId) ?: return
        startDownload(rt, url, rt.webView?.settings?.userAgentString, null, null)
    }

    private fun onLongPress(rt: TabRuntime, wv: WebView): Boolean {
        val hit = wv.hitTestResult
        val extra = hit.extra
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val imageLink = hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                val reply = Handler(Looper.getMainLooper()) { m ->
                    val data = m.data
                    val url = data.getString("url") ?: extra
                    if (url != null) {
                        _linkMenu.value = LinkTarget(url, data.getString("title")?.trim()?.ifEmpty { null }, if (imageLink) data.getString("src") else null, rt.incognito)
                    }
                    true
                }.obtainMessage()
                wv.requestFocusNodeHref(reply)
                return true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                if (extra == null) return false
                _linkMenu.value = LinkTarget(null, null, extra, rt.incognito)
                return true
            }
        }
        return false // text and empty space: leave the default selection behaviour alone
    }

    fun dismissPopupNotice() {
        _popupNotice.value = null
    }

    fun openBlockedPopup(notice: PopupNotice) {
        _popupNotice.value = null
        openTab(notice.url, incognito = supportsIncognito && find(selectedId)?.incognito == true)
    }

    /** Same decision as [resolvePopup] for callers already on the main thread. True = let the window load. */
    private fun resolvePopupOnMain(popup: TabRuntime, url: String): Boolean {
        val ad = isAdNavigation(url, popup.openerUrl)
        return when (val verdict = PopupPolicy.forWindow(popup.openerUrl, url, userGesture = true, adMatch = ad, clickedLinkUrl = popup.clickedLinkUrl)) {
            PopupVerdict.Allow -> {
                if (pendingPopups.remove(popup)) showPopupTab(popup)
                true
            }
            is PopupVerdict.Block -> {
                rejectPopup(popup, url, verdict.silent)
                false
            }
        }
    }

    private fun isAdNavigation(url: String, sourceUrl: String): Boolean = try {
        blocker.check(url, sourceUrl, RequestType.Popup) == BlockDecision.Block
    } catch (_: RuntimeException) {
        false
    }

    private fun countBlocked(opener: TabRuntime?) {
        opener?.blocked?.incrementAndGet()
        stats.add(1)
        schedulePublish()
    }

    /** A page tried to navigate itself somewhere: ad hosts and unrequested cross-site jumps are cancelled. */
    private fun redirectBlocked(rt: TabRuntime, url: String, request: WebResourceRequest): Boolean {
        if (!settingsRepo.settings.value.blockPopups || !blockingActiveFor(rt)) return false
        val ad = isAdNavigation(url, rt.pageUrl)
        val block = PopupPolicy.blockRedirect(rt.pageUrl, url, request.hasGesture(), request.isRedirect, ad)
        if (block) {
            countBlocked(rt)
            if (!ad && rt.id == selectedId) _popupNotice.value = PopupNotice(Hosts.hostOf(url).ifEmpty { url }, url)
        }
        return block
    }

    /**
     * Creates the WebView for a window a page opened. With [pending] the window stays invisible until its
     * first URL is known (see [resolvePopup]); otherwise it opens as a tab straight away.
     */
    private fun openPopupWindow(opener: TabRuntime, transport: WebView.WebViewTransport, message: Message, pending: Boolean, clickedLinkUrl: String? = null): Boolean {
        val popup = TabRuntime(nextId++, opener.incognito, appContext)
        popup.clickedLinkUrl = clickedLinkUrl
        // A window opened by a site the user allowed ads on belongs to that choice (the ad a site asks for before it plays).
        popup.allowAds = !blockingActiveFor(opener)
        popup.context.baseContext = opener.context.baseContext
        popup.url = "about:blank"
        popup.openerId = opener.id
        popup.openerUrl = opener.pageUrl
        popup.lastActive = System.currentTimeMillis()
        enforceLiveCap(popup)
        val webView = try {
            createWebView(popup)
        } catch (_: RuntimeException) {
            return false
        }
        popup.webView = webView
        transport.webView = webView
        message.sendToTarget()
        if (pending) {
            popup.pendingPopup = true
            pendingPopups.add(popup)
            main.postDelayed({ if (popup.pendingPopup) rejectPopup(popup, null, silent = true) }, POPUP_RESOLVE_MS)
        } else {
            showPopupTab(popup)
        }
        return true
    }

    private fun showPopupTab(popup: TabRuntime) {
        popup.pendingPopup = false
        find(selectedId)?.let(::captureThumbnail)
        find(selectedId)?.webView?.onPause()
        runtimes.add(popup)
        selectedId = popup.id
        showBars()
        publish()
        bumpHost()
    }

    /** Decides a pending window once its first URL is known. Called on the IO thread; the result is applied on main. */
    private fun resolvePopup(popup: TabRuntime, url: String): Boolean {
        val ad = isAdNavigation(url, popup.openerUrl)
        return when (val verdict = PopupPolicy.forWindow(popup.openerUrl, url, userGesture = true, adMatch = ad, clickedLinkUrl = popup.clickedLinkUrl)) {
            PopupVerdict.Allow -> {
                main.post { if (pendingPopups.remove(popup)) showPopupTab(popup) }
                true
            }
            is PopupVerdict.Block -> {
                main.post { rejectPopup(popup, url, verdict.silent) }
                false
            }
        }
    }

    private fun rejectPopup(popup: TabRuntime, url: String?, silent: Boolean) {
        if (!pendingPopups.remove(popup)) return
        popup.pendingPopup = false
        destroyWebView(popup)
        val opener = find(popup.openerId)
        countBlocked(opener)
        if (!silent && url != null && opener != null && opener.id == selectedId) {
            _popupNotice.value = PopupNotice(Hosts.hostOf(url).ifEmpty { url }, url)
        }
    }

    private fun cancelJsDialog() {
        _jsDialog.value?.answer(confirmed = false)
        _jsDialog.value = null
    }

    /** Shows a page's dialog if it belongs to the tab on screen and the user has not silenced the page. */
    private fun showJsDialog(rt: TabRuntime, kind: JsDialog.Kind, url: String, message: String, defaultText: String?, result: JsResult): Boolean {
        if (rt.id != selectedId || rt.suppressDialogs || _jsDialog.value != null || !hostActive) {
            result.cancel()
            return true
        }
        rt.dialogCount++
        lateinit var dialog: JsDialog
        dialog = JsDialog(
            kind = kind,
            host = hostOf(url).ifEmpty { "This page" },
            message = message.take(MAX_DIALOG_TEXT),
            defaultText = defaultText,
            canSuppress = rt.dialogCount > 1,
            result = result,
        ) { suppress ->
            if (suppress) rt.suppressDialogs = true
            if (_jsDialog.value === dialog) _jsDialog.value = null
        }
        _jsDialog.value = dialog
        return true
    }

    /** Brings the address bar back; also called whenever the user navigates or switches tab. */
    fun showBars() {
        scrollAccum = 0
        if (!_barsVisible.value) {
            _barsVisible.value = true
            lastBarChange = System.currentTimeMillis()
        }
    }

    private fun onPageScrolled(rt: TabRuntime, scrollY: Int, oldScrollY: Int) {
        if (rt.id != selectedId) return
        if (!settingsRepo.settings.value.hideBarOnScroll || scrollY <= BAR_TOP_ZONE_PX) {
            showBars()
            return
        }
        // Hiding the bar resizes the page, which can itself nudge the scroll position: ignore that for a moment.
        if (System.currentTimeMillis() - lastBarChange < BAR_SETTLE_MS) return
        val dy = scrollY - oldScrollY
        if (dy == 0) return
        if ((dy > 0) != (scrollAccum > 0)) scrollAccum = 0
        scrollAccum += dy
        if (scrollAccum > HIDE_AFTER_PX && _barsVisible.value) {
            _barsVisible.value = false
            lastBarChange = System.currentTimeMillis()
            scrollAccum = 0
        } else if (scrollAccum < -SHOW_AFTER_PX) {
            showBars()
        }
    }

    private fun enforceLiveCap(keep: TabRuntime) {
        // The tab playing audio in the background is never the one that makes room.
        val others = runtimes.filter { it !== keep && it.webView != null && !isPlayingMedia(it) }.sortedBy { it.lastActive }.toMutableList()
        while (others.size + 1 > maxLiveWebViews && others.isNotEmpty()) suspend(others.removeAt(0))
    }

    private fun suspend(rt: TabRuntime) {
        val wv = rt.webView ?: return
        leaveFullscreenOf(rt.id)
        forgetMedia(rt)
        val bundle = Bundle()
        wv.saveState(bundle)
        rt.savedState = bundle
        forgetStartScript(rt)
        detachAndDestroy(wv)
        rt.webView = null
    }

    private fun destroyWebView(rt: TabRuntime) {
        forgetMedia(rt)
        forgetStartScript(rt)
        rt.webView?.let { detachAndDestroy(it) }
        rt.webView = null
        rt.savedState = null
    }

    /**
     * Drops the document-start script handle without calling remove(): the handle belongs to the WebView
     * that is being destroyed, and calling remove() on it afterwards is a native null dereference.
     */
    private fun forgetStartScript(rt: TabRuntime) {
        rt.startScript = null
        rt.youTubeScript = null
        rt.startScriptOwner = null
    }

    /**
     * Registers the generic ad-hiding CSS to run at document start, before any page script, so ad
     * slots are already hidden when ad scripts measure the layout. Removed when blocking is off.
     */
    private fun installStartScript(rt: TabRuntime, wv: WebView) {
        if (rt.startScriptOwner === wv) {
            rt.startScript?.let { runCatching { it.remove() } }
            rt.youTubeScript?.let { runCatching { it.remove() } }
        }
        forgetStartScript(rt)
        if (!settingsRepo.settings.value.adBlockEnabled) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        installYouTubeScript(rt, wv)
        val css = blocker.genericCss()
        if (css.isEmpty()) return
        // Sites with hiding exceptions get their (filtered) CSS from injectCosmetic instead.
        val skipped = blocker.genericSkipHosts()
        val allowed = JSONArray((siteSettings.adsAllowedHosts.value + skipped).toList()).toString()
        rt.genericSkipHosts = skipped
        val script = "(function(){try{if(window.top!==window)return;var h=location.hostname,a=$allowed;" +
            "for(var k=0;k<a.length;k++){if(h===a[k]||h.endsWith('.'+a[k]))return;}" +
            "var go=function(){var t=document.head||document.documentElement;if(!t)return false;" +
            "if(document.getElementById('__bz_css'))return true;" +
            "var s=document.createElement('style');s.id='__bz_css';s.textContent=" + JSONObject.quote(css) + ";" +
            "t.appendChild(s);return true;};" +
            "if(!go()){new MutationObserver(function(m,o){if(go())o.disconnect();}).observe(document,{childList:true,subtree:true});}" +
            OVERLAY_GUARD_JS +
            "}catch(e){}})();"
        rt.startScript = try {
            WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
        } catch (_: RuntimeException) {
            null
        }
        if (rt.startScript != null) rt.startScriptOwner = wv
    }

    /** While background play is on, pages are told they stay visible (see [PlaybackScript.STAY_VISIBLE]). */
    private fun installBackgroundScript(rt: TabRuntime, wv: WebView) {
        val wanted = settingsRepo.settings.value.backgroundPlay
        if (wanted == (rt.backgroundScript != null) && rt.backgroundScriptOwner === wv) return
        if (rt.backgroundScriptOwner === wv) rt.backgroundScript?.let { runCatching { it.remove() } }
        rt.backgroundScript = null
        rt.backgroundScriptOwner = null
        if (!wanted || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        rt.backgroundScript = try {
            WebViewCompat.addDocumentStartJavaScript(wv, PlaybackScript.STAY_VISIBLE, setOf("*"))
        } catch (_: RuntimeException) {
            null
        }
        if (rt.backgroundScript != null) rt.backgroundScriptOwner = wv
    }

    /** Removes YouTube's video ads (see [YouTubeAdScript]) unless the user allowed ads on YouTube. */
    private fun installYouTubeScript(rt: TabRuntime, wv: WebView) {
        if (rt.allowAds || siteSettings.adsAllowedHosts.value.any { it == "youtube.com" || it.endsWith(".youtube.com") }) return
        rt.youTubeScript = try {
            WebViewCompat.addDocumentStartJavaScript(wv, YouTubeAdScript.SCRIPT, YouTubeAdScript.ORIGINS)
        } catch (_: RuntimeException) {
            null
        }
        if (rt.youTubeScript != null) rt.startScriptOwner = wv
    }

    /**
     * Lets every frame (including cross-origin player iframes) tell us the size of the video that plays,
     * so full screen can pick portrait or landscape. The page can only send "WIDTHxHEIGHT" text.
     */
    private fun installMediaHook(rt: TabRuntime, wv: WebView) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
            WebViewCompat.addWebMessageListener(wv, MEDIA_BRIDGE, setOf("*")) { _, message: WebMessageCompat, _, _, _ ->
                val size = message.data
                if (size != null && size.length < 16) onVideoSize(rt, size)
            }
            WebViewCompat.addWebMessageListener(wv, GUARD_BRIDGE, setOf("*")) { _, message: WebMessageCompat, _, _, _ ->
                if (message.data == "overlay") countBlocked(rt)
            }
            WebViewCompat.addWebMessageListener(wv, PlaybackScript.BRIDGE, setOf("*")) { view, message: WebMessageCompat, _, _, _ ->
                val data = message.data
                if (data != null && data.length < 1_000 && rt.webView === view) onMediaMessage(rt, data)
            }
            WebViewCompat.addDocumentStartJavaScript(wv, MEDIA_HOOK_SCRIPT, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(wv, PlaybackScript.HOOK, setOf("*"))
        } catch (_: RuntimeException) {
            // Without the hook full screen simply follows the device sensor.
        }
    }

    private fun onVideoSize(rt: TabRuntime, size: String) {
        val shape = VideoShape.fromSize(size)
        if (shape == VideoShape.Unknown) return
        rt.videoShape = shape
        val current = _fullscreen.value
        if (current != null && current.tabId == rt.id && current.shape != shape) _fullscreen.value = current.copy(shape = shape)
    }

    private fun detachAndDestroy(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.webChromeClient = null
        wv.webViewClient = WebViewClient()
        wv.destroy()
    }

    private fun onPullEnd(rt: TabRuntime, refresh: Boolean) {
        _pull.value = 0f
        if (!refresh || rt.id != selectedId) return
        _refreshing.value = true
        main.removeCallbacks(endRefresh)
        main.postDelayed(endRefresh, REFRESH_TIMEOUT_MS)
        reload(rt.id)
    }

    private fun stopRefreshIndicator() {
        _pull.value = 0f
        main.removeCallbacks(endRefresh)
        _refreshing.value = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(rt: TabRuntime): WebView {
        val wv = TabWebView(rt.context)
        wv.pullEnabled = settingsRepo.settings.value.pullToRefresh
        wv.keepPlayingHidden = settingsRepo.settings.value.let { it.backgroundPlay || it.pictureInPicture }
        wv.pullListener = object : TabWebView.PullListener {
            override fun onPull(distancePx: Float) {
                if (rt.id == selectedId) _pull.value = distancePx / (TabWebView.TRIGGER_DP * wv.resources.displayMetrics.density)
            }

            override fun onPullEnd(refresh: Boolean) = onPullEnd(rt, refresh)
        }
        if (rt.incognito) {
            // Must happen before the WebView is used for anything else.
            ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE)
            WebViewCompat.setProfile(wv, INCOGNITO_PROFILE)
        }
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = settingsRepo.settings.value.textZoom
        }
        applyUserAgent(wv, rt)
        applyPasskeys(wv, rt)
        applyCookiePolicy(wv, rt)
        applyDarkening(wv)
        installStartScript(rt, wv)
        installBackgroundScript(rt, wv)
        installMediaHook(rt, wv)
        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY -> onPageScrolled(rt, scrollY, oldScrollY) }
        wv.webViewClient = TabClient(rt)
        wv.webChromeClient = TabChromeClient(rt)
        wv.importantForAutofill = if (rt.incognito) View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS else View.IMPORTANT_FOR_AUTOFILL_YES
        wv.setOnLongClickListener { onLongPress(rt, wv) }
        wv.setFindListener { index, matches, done ->
            if (rt.id == selectedId && done) _find.value = _find.value.copy(matches = matches, index = if (matches == 0) 0 else index + 1)
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(rt, url, userAgent, contentDisposition, mimeType)
        }
        return wv
    }

    /**
     * Lets pages use passkeys: the WebView passes WebAuthn requests to the system Credential Manager (Google Password
     * Manager or another provider), acting as a browser so the page's own site is the relying party. Left off in
     * incognito tabs so a passkey is never created from a private window. Whether the provider accepts a browser
     * that is not on its allow-list is the provider's decision.
     */
    private fun applyPasskeys(wv: WebView, rt: TabRuntime) {
        if (rt.incognito || !supportsPasskeys) return
        try {
            WebSettingsCompat.setWebAuthenticationSupport(wv.settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
        } catch (_: RuntimeException) {
            // Older WebView without the setting: pages simply get no passkey support.
        }
    }

    private fun applyUserAgent(wv: WebView, rt: TabRuntime) {
        val base = rt.defaultUserAgent ?: UserAgents.asChrome(wv.settings.userAgentString).also { rt.defaultUserAgent = it }
        wv.settings.userAgentString = if (isDesktop(rt)) UserAgents.desktop(base) else base
    }

    private fun cookieManagerFor(rt: TabRuntime): CookieManager =
        if (rt.incognito) {
            ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE).cookieManager
        } else {
            CookieManager.getInstance()
        }

    private fun applyCookiePolicy(wv: WebView, rt: TabRuntime) {
        val mode = settingsRepo.settings.value.cookieMode
        val cm = cookieManagerFor(rt)
        cm.setAcceptCookie(mode != CookieMode.BlockAll)
        cm.setAcceptThirdPartyCookies(wv, mode == CookieMode.AllowAll)
    }

    @Suppress("DEPRECATION")
    private fun applyDarkening(wv: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, darkTheme)
        }
    }

    private fun deleteIncognitoProfile() {
        try {
            ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE)
        } catch (_: RuntimeException) {
            // Profile still in use or provider unavailable; it is retried when the last incognito tab closes again.
        }
    }

    private fun blockingActiveFor(rt: TabRuntime): Boolean {
        if (rt.allowAds) return false
        if (!settingsRepo.settings.value.adBlockEnabled) return false
        val host = hostOf(rt.pageUrl)
        if (host.isEmpty()) return true
        return siteSettings.adsAllowedHosts.value.none { host == it || host.endsWith(".$it") }
    }

    private fun injectCosmetic(rt: TabRuntime, view: WebView, url: String) {
        if (!blockingActiveFor(rt)) return
        val host = hostOf(url)
        if (host.isEmpty()) return
        if (rt.cssHost != host) {
            // The start script already put the generic CSS on the page, unless this site has hiding exceptions.
            val genericApplied = rt.startScript != null && rt.genericSkipHosts.none { host == it || host.endsWith(".$it") }
            rt.css = blocker.siteCss(host, genericApplied)
            rt.procedural = blocker.proceduralRules(host)
            rt.cssHost = host
        }
        val css = rt.css
        if (css.isNotEmpty()) {
            // Updates the style in place, so a later injection (lists finished loading) replaces older rules.
            val script = "(function(){var t=document.head||document.documentElement;if(!t)return;" +
                "var s=document.getElementById('__bz_site');if(!s){s=document.createElement('style');s.id='__bz_site';t.appendChild(s);}" +
                "var c=" + JSONObject.quote(css) + ";if(s.textContent!==c)s.textContent=c;})();"
            view.evaluateJavascript(script, null)
        }
        if (rt.procedural.isNotEmpty()) view.evaluateJavascript(ProceduralScript.forRules(rt.procedural), null)
    }

    /**
     * Chrome-style handling of `intent:` links: sites (Google sign-up among them) use them to hand a step over to an
     * installed app. Only after a tap, only as a browsable activity, never with a component, selector or grants;
     * when no app answers, the link's own `browser_fallback_url` is opened instead.
     */
    private fun openIntentLink(view: WebView, url: String) {
        val intent = try {
            Intent.parseUri(url, if (url.startsWith("intent:")) Intent.URI_INTENT_SCHEME else Intent.URI_ANDROID_APP_SCHEME)
        } catch (_: java.net.URISyntaxException) {
            return
        }
        val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { LinkTargets.isWeb(it) }
        val dataScheme = intent.data?.scheme?.lowercase()
        val unsafe = dataScheme == "file" || dataScheme == "content" || intent.`package` == appContext.packageName
        if (unsafe) return
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            appContext.startActivity(intent)
        } catch (_: RuntimeException) {
            fallback?.let { view.loadUrl(it) }
        }
    }

    private fun startDownload(rt: TabRuntime, url: String, userAgent: String?, disposition: String?, mime: String?) {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return
        try {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val cookie = cookieManagerFor(rt).getCookie(url)
            val request = DownloadManager.Request(uri).apply {
                if (!mime.isNullOrEmpty()) setMimeType(mime)
                if (!cookie.isNullOrEmpty()) addRequestHeader("Cookie", cookie)
                if (!userAgent.isNullOrEmpty()) addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(appContext, "Downloading $name", Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            Toast.makeText(appContext, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- persistence

    private fun saveTabs() {
        val array = JSONArray()
        runtimes.filter { !it.incognito }.forEach {
            array.put(JSONObject().put("id", it.id).put("url", it.url.takeUnless { u -> u.startsWith("file:") } ?: "").put("title", it.title).put("pinned", it.pinned).put("group", it.group ?: ""))
        }
        val selected = find(selectedId)?.takeIf { !it.incognito }?.id ?: -1L
        prefs.edit().putString(KEY_TABS, array.toString()).putLong(KEY_SELECTED, selected).apply()
    }

    private fun restoreTabs() {
        try {
            val array = JSONArray(prefs.getString(KEY_TABS, "[]"))
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optLong("id", -1L)
                if (id < 0) continue
                val rt = TabRuntime(id, false, appContext)
                rt.url = o.optString("url")
                rt.pageUrl = rt.url
                rt.title = o.optString("title")
                rt.pinned = o.optBoolean("pinned", false)
                rt.group = if (rt.pinned) null else TabGroups.normalize(o.optString("group"))
                runtimes.add(rt)
                nextId = maxOf(nextId, id + 1)
            }
        } catch (_: JSONException) {
            runtimes.clear()
        }
        if (runtimes.isEmpty()) {
            runtimes.add(TabRuntime(nextId++, false, appContext))
        }
        val ordered = TabOrder.arrange(runtimes.toList(), { it.pinned }, { it.group })
        runtimes.clear()
        runtimes.addAll(ordered)
        val wanted = prefs.getLong(KEY_SELECTED, -1L)
        selectedId = runtimes.firstOrNull { it.id == wanted }?.id ?: runtimes.first().id
        publish()
    }

    // ---------------------------------------------------------------- WebView callbacks

    private inner class TabClient(private val rt: TabRuntime) : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) {
                // Runs on the IO thread before any sub-resource of this page, unlike onPageStarted, which
                // is posted to the (possibly busy) UI thread and can arrive after the first blocks.
                val url = request.url.toString()
                val web = url.startsWith("http://") || url.startsWith("https://")
                if (rt.pendingPopup && web) {
                    if (resolvePopup(rt, url)) return null
                    return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                if (web) {
                    rt.pageUrl = url
                    rt.blocked.set(0)
                }
                return null
            }
            if (!blockingActiveFor(rt)) return null
            val decision = try {
                blocker.check(request.url.toString(), rt.pageUrl, requestType(request))
            } catch (_: RuntimeException) {
                BlockDecision.Allow
            }
            if (decision != BlockDecision.Block) return null
            rt.blocked.incrementAndGet()
            stats.add(1)
            schedulePublish()
            return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                val url = request.url.toString()
                if (rt.pendingPopup) return !resolvePopupOnMain(rt, url)
                if (redirectBlocked(rt, url, request)) return true
                if (request.isForMainFrame) {
                    val upgraded = upgradeFor(rt, url)
                    if (upgraded != url) {
                        view.loadUrl(upgraded)
                        return true
                    }
                }
                return false
            }
            if (scheme == "bz-insecure") {
                // "Continue over http" on the secure-connection-failed page.
                val target = request.url.getQueryParameter("u")
                if (target != null && target.startsWith("http://")) {
                    insecureHosts.add(Hosts.registrableDomain(hostOf(target)))
                    view.loadUrl(target)
                }
                return true
            }
            if (scheme == "about") return false
            if (scheme == "file" && saved.owns(request.url.toString())) return false
            if (scheme == "intent" || scheme == "android-app") {
                if (request.hasGesture()) openIntentLink(view, request.url.toString())
                return true
            }
            if (scheme == "tel" || scheme == "mailto" || scheme == "sms") {
                if (request.hasGesture()) {
                    try {
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: RuntimeException) {
                        // No app can handle it.
                    }
                }
            }
            return true // intent://, market:, javascript:, file: ... never leave the browser
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (!url.startsWith("file://")) view.settings.allowFileAccess = false
            if (!isInternalUrl(url)) {
                forgetMedia(rt)
                if (rt.faviconHost != hostOf(url) && rt.favicon != null) {
                    rt.favicon = null // another site: its icon arrives with onReceivedIcon
                    _previewVersion.value++
                }
                rt.dialogCount = 0
                rt.suppressDialogs = false
                rt.errorUrl = null
                rt.url = url
                rt.cssHost = null
                rt.injectStage = 0
                showBars()
            }
            rt.loading = true
            rt.progress = 0
            syncNavigation(rt, view)
            publish()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (!isInternalUrl(url)) injectCosmetic(rt, view, url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            rt.loading = false
            rt.progress = 100
            if (rt.id == selectedId) stopRefreshIndicator()
            syncNavigation(rt, view)
            if (!isInternalUrl(url)) {
                injectCosmetic(rt, view, url)
                if (!rt.incognito && rt.errorUrl == null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    history.record(url, rt.title)
                }
            }
            publish()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (!isInternalUrl(url)) {
                rt.url = url
                rt.pageUrl = url
            }
            // A new document already forgot its media in onPageStarted, so this only runs when the page moved itself.
            if (isPlayingMedia(rt)) pauseHiddenMedia(rt, view)
            syncNavigation(rt, view)
            publish()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val failing = request.url.toString()
            rt.errorUrl = failing
            if (failing == rt.upgradedTo && failing.startsWith("https://")) {
                rt.title = "Secure connection failed"
                view.loadDataWithBaseURL(null, insecurePage("http://" + failing.removePrefix("https://")), "text/html", "utf-8", null)
                return
            }
            rt.title = "Page not available"
            view.loadDataWithBaseURL(null, errorPage(failing, error.description?.toString().orEmpty()), "text/html", "utf-8", null)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Renderer crashed or was killed: drop this WebView; it is recreated and reloaded on demand.
            if (rt.webView === view) {
                leaveFullscreenOf(rt.id)
                forgetMedia(rt)
                forgetStartScript(rt)
                detachAndDestroy(view)
                rt.webView = null
                rt.savedState = null
                rt.loading = false
                publish()
                bumpHost()
            }
            return true
        }
    }

    private inner class TabChromeClient(private val rt: TabRuntime) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Inject the ad-hiding CSS as soon as the document exists (not only when the page finishes),
            // so ad slots do not flash and are already hidden when page scripts measure the layout.
            if (rt.injectStage == 0 && newProgress >= 10 || rt.injectStage == 1 && newProgress >= 70) {
                rt.injectStage++
                if (!isInternalUrl(rt.pageUrl)) injectCosmetic(rt, view, rt.pageUrl)
            }
            rt.progress = newProgress
            rt.loading = newProgress < 100
            schedulePublish()
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            if (icon == null || icon.width <= 0) return
            rt.favicon = if (icon.width > FAVICON_PX) Bitmap.createScaledBitmap(icon, FAVICON_PX, FAVICON_PX * icon.height / icon.width, true) else icon
            rt.faviconHost = hostOf(rt.url)
            _previewVersion.value++
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank() && rt.errorUrl == null && !isInternalUrl(title)) {
                rt.title = title
                schedulePublish()
            }
        }

        // Shown by the app (it owns the Activity window); dialogs from tabs that are not on screen are refused.
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean =
            showJsDialog(rt, JsDialog.Kind.Alert, url, message, null, result)

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean =
            showJsDialog(rt, JsDialog.Kind.Confirm, url, message, null, result)

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean =
            showJsDialog(rt, JsDialog.Kind.Prompt, url, message, defaultValue.orEmpty(), result)

        // Pages cannot word this one any more (browsers ignore their text), so the app uses its own.
        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean =
            showJsDialog(rt, JsDialog.Kind.LeavePage, url, "", null, result)

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            permissionBroker.request(
                host = hostOf(origin),
                kinds = listOf(SitePermission.Location),
                grant = { callback.invoke(origin, true, false) },
                deny = { callback.invoke(origin, false, false) },
            )
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val kinds = request.resources.map {
                when (it) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermission.Camera
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermission.Microphone
                    else -> null
                }
            }
            // Anything else (protected media ids, MIDI) is not offered.
            if (kinds.isEmpty() || kinds.any { it == null }) return request.deny()
            permissionBroker.request(
                host = hostOf(request.origin.toString()),
                kinds = kinds.filterNotNull().distinct(),
                grant = { request.grant(request.resources) },
                deny = { request.deny() },
            )
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val blockingOn = settingsRepo.settings.value.blockPopups && blockingActiveFor(rt)
            // Windows nobody asked for (no click) are never opened.
            if (!isUserGesture) {
                if (blockingOn) countBlocked(rt)
                return false
            }
            if (!blockingOn) return openPopupWindow(rt, transport, resultMsg, pending = false)

            val hit = view.hitTestResult
            val linkUrl = if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) hit.extra else null
            if (linkUrl != null && (linkUrl.startsWith("http://") || linkUrl.startsWith("https://"))) {
                // The user tapped a real link that opens in a new window.
                if (isAdNavigation(linkUrl, rt.pageUrl)) {
                    countBlocked(rt)
                    return false
                }
                // A script can also open a window from the same tap (a blank window that is sent to an ad a moment later),
                // so the window is held until its first URL is known; that URL decides, with the tapped link as a hint.
                return openPopupWindow(rt, transport, resultMsg, pending = true, clickedLinkUrl = linkUrl)
            }
            // A script opened it from a click (the overlay trick): wait for its URL, then decide.
            return openPopupWindow(rt, transport, resultMsg, pending = true)
        }

        override fun onCloseWindow(window: WebView) {
            val popup = pendingPopups.firstOrNull { it.webView === window }
            if (popup != null) {
                rejectPopup(popup, null, silent = true)
                return
            }
            runtimes.firstOrNull { it.webView === window }?.let { closeTab(it.id) }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullscreenCallback != null) {
                callback.onCustomViewHidden() // already in full screen: refuse the second request
                return
            }
            fullscreenCallback = callback
            view.keepScreenOn = true
            // The orientation follows the sensor until the page reports which element is full screen.
            _fullscreen.value = FullscreenVideo(view, rt.id, VideoShape.Unknown)
            main.postDelayed({ if (_fullscreen.value?.tabId == rt.id) probeVideoShape(rt) }, PROBE_DELAY_MS)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) =
            onShowCustomView(view, callback)

        override fun onHideCustomView() {
            // The page left full screen by itself (or we asked it to); either way drop our reference.
            fullscreenCallback = null
            _fullscreen.value = null
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            val chooser = fileChooser
            val intent = try {
                fileChooserParams.createIntent()
            } catch (_: RuntimeException) {
                null
            }
            if (chooser == null || intent == null || rt.id != selectedId) {
                filePathCallback.onReceiveValue(null)
                return true
            }
            if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            // WebView needs exactly one answer, or the page's file input stops working until it reloads.
            val answered = AtomicBoolean(false)
            chooser(intent) { uris -> if (answered.compareAndSet(false, true)) filePathCallback.onReceiveValue(uris) }
            return true
        }
    }

    private fun syncNavigation(rt: TabRuntime, view: WebView) {
        rt.canGoBack = view.canGoBack()
        rt.canGoForward = view.canGoForward()
    }

    private class TabRuntime(val id: Long, val incognito: Boolean, baseContext: Context) {
        var url = ""
        var title = ""
        var loading = false
        var progress = 0
        var canGoBack = false
        var canGoForward = false
        var pinned = false
        var group: String? = null
        var desktopOverride: Boolean? = null
        var webView: WebView? = null
        var savedState: Bundle? = null
        var lastActive = 0L
        var errorUrl: String? = null
        var cssHost: String? = null
        var css: String = ""

        /** Procedural hiding rules for [cssHost] (JSON), run by [ProceduralScript]. */
        var procedural: String = ""

        /** Hosts the current start script leaves the generic CSS off for. */
        var genericSkipHosts: Set<String> = emptySet()
        var defaultUserAgent: String? = null

        /** Windows opened by a page: who opened it, from which page, and whether it still awaits a verdict. */
        var openerId = -1L
        var openerUrl = ""
        @Volatile var pendingPopup = false

        /** Opened by a site that has ads allowed: this window is not filtered either. */
        @Volatile var allowAds = false

        /** The link the user tapped when this window was opened, if any; a window opened to that site is wanted. */
        var clickedLinkUrl: String? = null

        /** The https address this tab was sent to by HTTPS-first, so a failure there can offer http instead. */
        var upgradedTo: String? = null

        var startScript: ScriptHandler? = null

        /** For the tab switcher: what the page looked like when last on screen, and the site's icon. */
        var thumbnail: Bitmap? = null
        var favicon: Bitmap? = null
        var faviconHost: String? = null

        /** Dialogs the current page has shown, and whether the user silenced it ("don't show more dialogs"). */
        var dialogCount = 0
        var suppressDialogs = false
        var youTubeScript: ScriptHandler? = null
        /** Handle of [PlaybackScript.STAY_VISIBLE]; only valid while [backgroundScriptOwner] lives (a new WebView starts over). */
        var backgroundScript: ScriptHandler? = null
        var backgroundScriptOwner: WebView? = null

        /** The WebView [startScript] was registered on; the handle is only valid while that WebView lives. */
        var startScriptOwner: WebView? = null

        /** Shape of the video that played last in this tab (any frame), reported by the media hook. */
        @Volatile var videoShape = VideoShape.Unknown

        /** 0 = nothing injected for this navigation, 1 = early, 2 = late. */
        var injectStage = 0

        /** Read from WebView IO threads as the "source" of sub-resource requests. */
        @Volatile var pageUrl = ""
        val blocked = AtomicInteger(0)
        val context = MutableContextWrapper(baseContext)

        fun toInfo(desktop: Boolean) = TabInfo(
            id = id,
            url = url,
            title = title,
            isIncognito = incognito,
            isLoading = loading,
            progress = progress,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            blockedCount = blocked.get(),
            desktopMode = desktop,
            pinned = pinned,
            group = group,
        )
    }

    private companion object {
        const val KEY_TABS = "tabs"
        const val KEY_SELECTED = "selectedTab"
        const val INCOGNITO_PROFILE = "incognito"
        const val POPUP_RESOLVE_MS = 3_000L
        const val PROBE_DELAY_MS = 400L
        const val REFRESH_TIMEOUT_MS = 15_000L
        val RESUME_DELAYS_MS = longArrayOf(300, 1_000, 2_500)
        val HIDDEN_CHECK_DELAYS_MS = longArrayOf(300, 1_000)
        const val MAX_DIALOG_TEXT = 2_000
        const val THUMBNAIL_WIDTH = 240
        const val THUMBNAIL_ASPECT = 1.3f
        const val FAVICON_PX = 64
        /**
         * Removes ad overlays that imitate system UI or scam prompts: a fixed element added straight to the page that
         * (a) carries an "adsby" link, or (b) sits at the top of the stacking order and is wide and either says
         * "Google Chrome" + "Site settings" / a "you won" style message, or (c) covers the screen with a blank iframe.
         * Domains of these networks rotate daily, their markup does not. Real modals have real content, so they stay.
         */
        const val OVERLAY_GUARD_JS =
            "var bad=/congratulations|withdraw money|transfer money|claim (?:your )?(?:prize|reward|bonus)|you(?:'ve| have) (?:won|been selected)|(?:device|phone|browser) (?:is|has been) (?:infected|hacked)/i," +
            "seen=[],sw=function(){return window.innerWidth||document.documentElement.clientWidth;},sh=function(){return window.innerHeight||document.documentElement.clientHeight;}," +
            "judge=function(e){if(!e||e.nodeType!==1||e.tagName==='SCRIPT'||e.tagName==='STYLE')return;" +
            "var cs=getComputedStyle(e);if(cs.position!=='fixed'&&cs.position!=='absolute')return;" +
            "var z=parseInt(cs.zIndex,10)||0,r=e.getBoundingClientRect(),wide=r.width>=sw()*0.9,tall=r.height>=sh()*0.9," +
            "text=(e.innerText||e.textContent||'')," +
            "adsby=!!e.querySelector('a[id$=\"adsby\"],a[class*=\"adsby\"]')," +
            "fakeNotif=/google chrome/i.test(text)&&/site settings/i.test(text)," +
            "scam=bad.test(text)," +
            "blank=!!e.querySelector('iframe:not([src]),iframe[src=\"\"],iframe[src^=\"about:\"],iframe[src^=\"data:\"],iframe[src^=\"javascript:\"]')," +
            "top=z>=2147483000;" +
            "if(adsby||(top&&wide&&(fakeNotif||scam))||(top&&wide&&tall&&blank)){" +
            "if(seen.indexOf(e)<0){seen.push(e);if(window.__bzGuard)window.__bzGuard.postMessage('overlay');}" +
            "e.style.setProperty('display','none','important');if(e.parentNode)e.parentNode.removeChild(e);}}," +
            "sweep=function(){var b=document.body,d=document.documentElement;" +
            "if(b)for(var i=b.children.length-1;i>=0;i--)judge(b.children[i]);" +
            "if(d)for(var j=d.children.length-1;j>=0;j--)judge(d.children[j]);}," +
            "queued=false,later=function(){if(queued)return;queued=true;setTimeout(function(){queued=false;sweep();},200);}," +
            "begin=function(){sweep();var n=0,iv=setInterval(function(){sweep();if(++n>80)clearInterval(iv);},1500);" +
            "var mo=new MutationObserver(later);mo.observe(document.documentElement,{childList:true});if(document.body)mo.observe(document.body,{childList:true});};" +
            "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',begin);else begin();"
        const val GUARD_BRIDGE = "__bzGuard"
        const val MEDIA_BRIDGE = "__bzMedia"
        const val MEDIA_HOOK_SCRIPT = "(function(){try{if(!window.__bzMedia||window.__bzMediaHooked)return;window.__bzMediaHooked=true;" +
            "var send=function(v){if(v&&v.videoWidth>0&&v.videoHeight>0){window.__bzMedia.postMessage(v.videoWidth+'x'+v.videoHeight);}};" +
            // While something is full screen only that element may report (looping previews must not flip the orientation).
            "var r=function(e){var t=e.target;if(!t||t.tagName!=='VIDEO')return;" +
            "var f=document.fullscreenElement||document.webkitFullscreenElement;if(f&&f!==t&&!f.contains(t))return;send(t);};" +
            "['loadedmetadata','playing','resize'].forEach(function(n){document.addEventListener(n,r,true);});" +
            // The element that actually went full screen wins over whichever video played last.
            "var fs=function(){var e=document.fullscreenElement||document.webkitFullscreenElement;if(!e)return;" +
            "send(e.tagName==='VIDEO'?e:e.querySelector('video'));};" +
            "document.addEventListener('fullscreenchange',fs,true);document.addEventListener('webkitfullscreenchange',fs,true);" +
            "}catch(e){}})();"
        const val PROBE_SCRIPT = "(function(){var e=document.fullscreenElement||document.webkitFullscreenElement;if(!e)return '';" +
            "var v=e.tagName==='VIDEO'?e:e.querySelector('video');return v&&v.videoWidth?v.videoWidth+'x'+v.videoHeight:'';})()"
        const val BAR_TOP_ZONE_PX = 8
        const val HIDE_AFTER_PX = 48
        const val SHOW_AFTER_PX = 24
        const val BAR_SETTLE_MS = 350L
        const val SAVE_DELAY_MS = 500L
        const val PUBLISH_DELAY_MS = 250L

        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp")
        private val MEDIA_EXT = setOf("mp4", "webm", "mp3", "ogg", "m4a", "m3u8", "mpd", "ts")

        fun isInternalUrl(url: String) = url == "about:blank" || url.startsWith("data:") || url.isEmpty()

        fun hostOf(url: String): String =
            url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfter('@').substringBefore(':').lowercase()

        fun requestType(request: WebResourceRequest): RequestType {
            val path = request.url.path?.lowercase().orEmpty()
            val ext = path.substringAfterLast('.', "")
            val accept = request.requestHeaders["Accept"].orEmpty()
            return when {
                ext == "js" || ext == "mjs" -> RequestType.Script
                ext == "css" -> RequestType.Stylesheet
                ext in IMAGE_EXT || accept.startsWith("image/") -> RequestType.Image
                ext in MEDIA_EXT || accept.startsWith("video/") || accept.startsWith("audio/") -> RequestType.Media
                accept.contains("text/html") -> RequestType.SubFrame
                request.requestHeaders["X-Requested-With"] == "XMLHttpRequest" -> RequestType.Xhr
                else -> RequestType.Other
            }
        }

        /** Shown when the https version of an address could not be reached: the user decides whether to use http. */
        fun insecurePage(httpUrl: String): String {
            val safeUrl = TextUtils.htmlEncode(httpUrl)
            val encoded = Uri.encode(httpUrl)
            return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;margin:32px;color:#222}h1{font-size:20px}" +
                "p{color:#555;word-break:break-all}a{display:inline-block;margin-top:16px;padding:10px 18px;" +
                "border:1px solid #888;border-radius:8px;color:#222;text-decoration:none}</style></head><body>" +
                "<h1>This site does not support a secure connection</h1>" +
                "<p>Browser-Z tried https first. Continuing uses plain http, so others on the network could read or change what you send and see.</p>" +
                "<p>$safeUrl</p><a href=\"bz-insecure://continue?u=$encoded\">Continue to the unsecured site</a></body></html>"
        }

        fun errorPage(url: String, reason: String): String {
            val safeUrl = TextUtils.htmlEncode(url)
            val safeReason = TextUtils.htmlEncode(reason)
            return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;margin:32px;color:#222}h1{font-size:20px}" +
                "p{color:#555;word-break:break-all}a{display:inline-block;margin-top:16px;padding:10px 18px;" +
                "border:1px solid #888;border-radius:8px;color:#222;text-decoration:none}</style></head><body>" +
                "<h1>This page can't be reached</h1><p>$safeUrl</p><p>$safeReason</p>" +
                "<a href=\"$safeUrl\">Try again</a></body></html>"
        }
    }
}
