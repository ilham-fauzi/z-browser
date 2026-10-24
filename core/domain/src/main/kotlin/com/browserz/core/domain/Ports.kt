package com.browserz.core.domain

import kotlinx.coroutines.flow.StateFlow

/** Implemented by :engine:adblock. */
interface ContentBlocker {
    /** Thread-safe; called from WebView IO threads for every sub-resource. */
    fun check(url: String, sourceUrl: String, type: RequestType): BlockDecision

    /** CSS that hides ad slots for [host]; empty when there is nothing to hide. */
    fun cosmeticCss(host: String): String

    /** The host-independent part of the hiding CSS; safe to inject before a page's own scripts run. */
    fun genericCss(): String

    /** Hiding CSS for [host]; with [genericApplied] only the site's own part (the generic part is already on the page). */
    fun siteCss(host: String, genericApplied: Boolean): String

    /** Sites that except some or all of the generic CSS, so it must not be injected there before the page loads. */
    fun genericSkipHosts(): Set<String>

    /** Procedural hiding rules for [host] as a JSON array of programs (see the page script); empty when none. */
    fun proceduralRules(host: String): String
}

/** Implemented by :engine:adblock. */
interface FilterListManager {
    val status: StateFlow<FilterStatus>
    fun updateNow()
}

/** Implemented by :core:data. */
interface SettingsRepository {
    val settings: StateFlow<Settings>
    fun update(transform: (Settings) -> Settings)
}

/** Implemented by :core:data. */
interface SearchEngineRepository {
    val engines: StateFlow<List<SearchEngine>>
    val defaultEngine: StateFlow<SearchEngine>
    fun setDefault(id: Long)

    /** Returns null on success, otherwise a user-facing error message. */
    fun addCustom(name: String, urlTemplate: String, keyword: String?): String?
    fun remove(id: Long)
}

/** Implemented by :core:data. Bookmarks live in one level of folders (a folder name, or none). */
interface BookmarkRepository {
    val bookmarks: StateFlow<List<Bookmark>>
    val folders: StateFlow<List<String>>

    /** Adds the page (no folder) or removes it when it is already saved. */
    fun toggle(url: String, title: String)
    fun remove(url: String)

    /** Changes the title and address of the bookmark at [oldUrl]; returns null on success, otherwise a user-facing error. */
    fun edit(oldUrl: String, title: String, address: String): String?

    /** Moves a bookmark into [folder]; null takes it out of any folder. */
    fun move(url: String, folder: String?)

    /** Each returns null on success, otherwise a user-facing error message. */
    fun addFolder(name: String): String?
    fun renameFolder(old: String, new: String): String?

    /** Deletes the folder; its bookmarks stay, without a folder. */
    fun deleteFolder(name: String)

    /** Adds bookmarks (and the folders they name) that are not saved yet; returns how many were new. */
    fun importAll(items: List<Bookmark>): Int
}

/** Implemented by :core:data. Running total of ads and trackers blocked, across all pages. */
interface BlockStats {
    val totalBlocked: StateFlow<Long>
    fun add(count: Int)
}

/** Implemented by :core:data. */
interface HistoryRepository {
    val entries: StateFlow<List<HistoryEntry>>
    fun record(url: String, title: String)
    fun remove(url: String)
    fun clear()
}

/** Implemented by :core:data. Sites pinned to the new-tab page, in the order they were pinned. */
interface ShortcutRepository {
    val pinned: StateFlow<List<PinnedSite>>

    /** Pins the site (replacing one with the same address); returns false when the new-tab page is already full. */
    fun pin(url: String, title: String): Boolean
    fun unpin(url: String)
}

/** Implemented by :core:data. Standing answers to "may this site use my camera, microphone or location". */
interface SitePermissionRepository {
    /** Changes whenever any answer changes, so a screen can show the current state. */
    val version: StateFlow<Int>
    fun decision(host: String, permission: SitePermission): PermissionDecision
    fun set(host: String, permission: SitePermission, decision: PermissionDecision)
    fun reset(host: String)
}

/** Implemented by :core:data. Hosts for which ad blocking is switched off. */
interface SiteSettingsRepository {
    val adsAllowedHosts: StateFlow<Set<String>>
    fun setAdsAllowed(host: String, allowed: Boolean)
}

/** Implemented by :core:webview (cookies, cache and site storage). */
interface SiteDataManager {
    fun clearCookiesAndSiteData()
    fun clearCache()
}

/** Implemented by :core:webview. */
interface BrowserSession {
    val tabs: StateFlow<List<TabInfo>>
    val selectedTabId: StateFlow<Long?>
    val supportsIncognito: Boolean

    /** [url] empty = new-tab page. Returns the new tab id. */
    fun openTab(url: String, incognito: Boolean = false, select: Boolean = true): Long
    fun load(tabId: Long, url: String)
    fun selectTab(tabId: Long)
    fun closeTab(tabId: Long)
    fun goBack(tabId: Long)
    fun goForward(tabId: Long)
    fun reload(tabId: Long)
    fun stop(tabId: Long)
    fun setDesktopMode(tabId: Long, enabled: Boolean)

    /** Pinned tabs sit first, stay small and cannot be closed from the tab list until unpinned. */
    fun setPinned(tabId: Long, pinned: Boolean)

    /** Puts the tab in the named group (created on first use), or takes it out when [group] is null. */
    fun setGroup(tabId: Long, group: String?)

    /** Tabs closed in this run, newest first (private tabs are never listed). */
    val recentlyClosed: StateFlow<List<ClosedTab>>

    /** Brings back the newest closed tab; returns its id, or null when there is none. */
    fun reopenClosedTab(): Long?

    val findResult: StateFlow<FindResult>
    fun findInPage(tabId: Long, query: String)
    fun findNext(tabId: Long, forward: Boolean)
    fun clearFind(tabId: Long)

    /** Replaces the page with a clean, ad-free reading view of its main text. */
    fun readerMode(tabId: Long)

    val savedPages: StateFlow<List<SavedPage>>

    /** Saves the page for offline reading; [onDone] gets true on success. */
    fun savePage(tabId: Long, onDone: (Boolean) -> Unit)
    fun openSavedPage(tabId: Long, id: String)
    fun deleteSavedPage(id: String)
}
