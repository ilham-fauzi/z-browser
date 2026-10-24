package com.browserz.core.domain

data class TabInfo(
    val id: Long,
    val url: String,
    val title: String,
    val isIncognito: Boolean,
    val isLoading: Boolean,
    val progress: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val blockedCount: Int,
    val desktopMode: Boolean,
    val pinned: Boolean = false,
    /** Name of the tab group this tab belongs to; null = ungrouped. Pinned tabs are never grouped. */
    val group: String? = null,
) {
    val isBlank: Boolean get() = url.isBlank()
}

data class Bookmark(val url: String, val title: String, val createdAt: Long, val folder: String? = null)

data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

data class SearchEngine(
    val id: Long,
    val name: String,
    val urlTemplate: String,
    val keyword: String?,
    val isBuiltIn: Boolean,
)

enum class CookieMode { AllowAll, BlockThirdParty, BlockAll }

enum class ThemeMode { System, Light, Dark }

enum class DesktopMode { Auto, Always, Never }

/** Where the address bar and buttons sit. Auto = bottom on phones, top on tablets and large windows. */
enum class ToolbarPosition { Auto, Top, Bottom }

/** Tabs as a strip above the page, or a vertical list beside it (tablets and large windows only). */
enum class TabsPosition { Top, Side }

data class Settings(
    val adBlockEnabled: Boolean = true,
    val cookieMode: CookieMode = CookieMode.BlockThirdParty,
    val desktopMode: DesktopMode = DesktopMode.Auto,
    val themeMode: ThemeMode = ThemeMode.System,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.Auto,
    val tabsPosition: TabsPosition = TabsPosition.Top,
    val hideBarOnScroll: Boolean = true,
    /** Pulling down at the top of a page reloads it. */
    val pullToRefresh: Boolean = true,
    /** Audio and video keep playing when the app is in the background or the screen is off, with a media notification. */
    val backgroundPlay: Boolean = false,
    /** Leaving the app while a video plays shrinks it into a floating window. */
    val pictureInPicture: Boolean = false,
    val blockPopups: Boolean = true,
    /** Shortcut tiles from the most visited sites and the recent list in search; pinned shortcuts are always shown. */
    val showHistoryOnHome: Boolean = true,
    /** Public http addresses are opened over https; if that fails the user is asked before falling back. */
    val httpsOnly: Boolean = true,
    /** Page text size in percent, 100 = normal. */
    val textZoom: Int = 100,
    val clearHistoryOnExit: Boolean = false,
    val clearCookiesOnExit: Boolean = false,
    val clearCacheOnExit: Boolean = false,
)

enum class BlockDecision { Allow, Block }

/** [Popup] = a top-level navigation that the user did not ask for (new window, redirect). */
enum class RequestType { Document, Script, Image, Stylesheet, Xhr, SubFrame, Media, Other, Popup }

data class FilterStatus(
    val ruleCount: Int = 0,
    val cosmeticCount: Int = 0,
    val lastUpdatedAt: Long = 0L,
    val updating: Boolean = false,
    val lastError: String? = null,
)

sealed interface Suggestion {
    data class Page(val title: String, val url: String, val isBookmark: Boolean) : Suggestion
    data class Search(val query: String, val engine: SearchEngine) : Suggestion
}

/** Find-in-page progress for the current tab: [matches] = 0 with a non-blank [query] means "not found". */
data class FindResult(val query: String = "", val matches: Int = 0, val index: Int = 0)

/** A page saved for offline reading. */
data class SavedPage(val id: String, val title: String, val url: String, val savedAt: Long)

/** What was long-pressed on a page: a link ([url], with its [text]), an image ([imageUrl]), or an image that is a link. */
data class LinkTarget(val url: String?, val text: String?, val imageUrl: String?, val incognito: Boolean) {
    /** Only web links can be opened or downloaded; tel:, mailto: and the like can still be copied. */
    val openableUrl: String? get() = url?.takeIf { LinkTargets.isWeb(it) }
    val openableImage: String? get() = imageUrl?.takeIf { LinkTargets.isWeb(it) }
}

object LinkTargets {
    fun isWeb(url: String): Boolean = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
