package com.browserz.core.domain

object DefaultSearchEngines {
    /** Presets that exist but are not offered in the UI. Remove an id from here to show that engine again. */
    private val HIDDEN_PRESET_IDS = setOf(2L) // DuckDuckGo

    val presets: List<SearchEngine> = listOf(
        SearchEngine(1, "Google", "https://www.google.com/search?q=%s", "!g", true),
        SearchEngine(2, "DuckDuckGo", "https://duckduckgo.com/?q=%s", "!d", true),
        SearchEngine(3, "Bing", "https://www.bing.com/search?q=%s", "!b", true),
        SearchEngine(4, "Brave Search", "https://search.brave.com/search?q=%s", "!br", true),
        SearchEngine(5, "Yahoo", "https://search.yahoo.com/search?p=%s", "!y", true),
        SearchEngine(6, "Ecosia", "https://www.ecosia.org/search?q=%s", "!e", true),
        SearchEngine(7, "Yandex", "https://yandex.com/search/?text=%s", "!ya", true),
        SearchEngine(8, "Startpage", "https://www.startpage.com/do/search?q=%s", "!sp", true),
    ).filterNot { it.id in HIDDEN_PRESET_IDS }

    /** Null when [template] is acceptable, otherwise a user-facing reason. */
    fun validateTemplate(template: String): String? = when {
        !template.startsWith("https://") -> "The URL must start with https://"
        template.split("%s").size != 2 -> "The URL must contain exactly one %s for the query"
        else -> null
    }
}
