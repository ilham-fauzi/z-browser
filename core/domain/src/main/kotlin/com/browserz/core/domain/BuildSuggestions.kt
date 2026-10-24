package com.browserz.core.domain

/** Local address-bar suggestions from bookmarks and history, plus a search row. */
class BuildSuggestions {
    operator fun invoke(
        query: String,
        bookmarks: List<Bookmark>,
        history: List<HistoryEntry>,
        defaultEngine: SearchEngine,
        limit: Int = 6,
    ): List<Suggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val seen = HashSet<String>()
        val pages = ArrayList<Suggestion>()
        for (b in bookmarks) {
            if (matches(q, b.title, b.url) && seen.add(b.url)) pages += Suggestion.Page(b.title, b.url, true)
        }
        for (h in history) {
            if (pages.size >= limit) break
            if (matches(q, h.title, h.url) && seen.add(h.url)) pages += Suggestion.Page(h.title, h.url, false)
        }
        return listOf<Suggestion>(Suggestion.Search(q, defaultEngine)) + pages.take(limit)
    }

    private fun matches(q: String, title: String, url: String) =
        title.contains(q, ignoreCase = true) || url.contains(q, ignoreCase = true)
}
