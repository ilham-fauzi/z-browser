package com.browserz.core.domain

/** A site the user pinned to the new-tab page. */
data class PinnedSite(val url: String, val title: String)

/** One tile on the new-tab page. */
data class Shortcut(val url: String, val title: String, val pinned: Boolean)

object NtpShortcuts {
    const val MAX = 8

    /**
     * Pinned sites first (in the order they were pinned), then the sites visited most, up to [max] tiles.
     * History keeps one entry per page, so "most visited" counts how many pages of a site were opened, newest breaking ties.
     */
    fun build(pinned: List<PinnedSite>, history: List<HistoryEntry>, max: Int = MAX): List<Shortcut> {
        val tiles = pinned.take(max).map { Shortcut(it.url, it.title, pinned = true) }.toMutableList()
        if (tiles.size >= max) return tiles
        val pinnedHosts = pinned.mapTo(HashSet()) { Hosts.hostOf(it.url) }
        val frequent = history
            .filter { (it.url.startsWith("http://") || it.url.startsWith("https://")) && Hosts.hostOf(it.url).isNotEmpty() }
            .groupBy { Hosts.hostOf(it.url) }
            .filterKeys { it !in pinnedHosts }
            .map { (host, entries) -> Triple(host, entries.size, entries.maxOf { it.visitedAt }) }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
        for ((host, _, _) in frequent) {
            if (tiles.size >= max) break
            val scheme = history.first { Hosts.hostOf(it.url) == host }.url.substringBefore("://")
            tiles += Shortcut("$scheme://$host/", host.removePrefix("www."), pinned = false)
        }
        return tiles
    }

    /** Adds `https://` when the user typed a bare domain; null when it cannot be a web address. */
    fun normalizeUrl(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val url = if (text.contains("://")) text else "https://$text"
        if (!LinkTargets.isWeb(url)) return null
        val host = Hosts.hostOf(url)
        return if (host.contains('.') || host == "localhost") url else null
    }
}
