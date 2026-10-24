package com.browserz.core.domain

/**
 * Turns address-bar text into a loadable URL: a typed URL stays a URL, `!d cats` searches
 * DuckDuckGo once, and everything else is a search on the default engine.
 */
class ResolveInput(private val buildSearchUrl: BuildSearchUrl = BuildSearchUrl()) {

    operator fun invoke(input: String, defaultEngine: SearchEngine, engines: List<SearchEngine>): String {
        val text = input.trim()
        if (text.isEmpty()) return ""

        val keyword = text.substringBefore(' ')
        val rest = text.substringAfter(' ', "").trim()
        if (rest.isNotEmpty()) {
            val shortcut = engines.firstOrNull { it.keyword != null && it.keyword.equals(keyword, ignoreCase = true) }
            if (shortcut != null) return buildSearchUrl(shortcut, rest)
        }

        if (!text.any { it.isWhitespace() }) {
            asUrl(text)?.let { return it }
        }
        return buildSearchUrl(defaultEngine, text)
    }

    private fun asUrl(text: String): String? {
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return if (hostOf(text).isNotEmpty()) text else null
        }
        if (lower == "about:blank") return text
        // Anything else with a scheme (javascript:, file:, intent:, data:) is never loaded from the bar.
        if (SCHEME.containsMatchIn(lower) && !HOST_PORT.matches(lower.substringBefore('/'))) return null

        val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringBefore(':').lowercase()
        val isLocal = host == "localhost" || IPV4.matches(host)
        if (isLocal) return "http://$text"
        if (DOMAIN.matches(host)) return "https://$text"
        return null
    }

    companion object {
        private val SCHEME = Regex("^[a-z][a-z0-9+.\\-]*:")
        private val HOST_PORT = Regex("^[^:/]+:\\d{1,5}$")
        private val IPV4 = Regex("^(\\d{1,3})(\\.\\d{1,3}){3}$")
        private val DOMAIN = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$")

        fun hostOf(url: String): String =
            url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfter('@').substringBefore(':').lowercase()
    }
}
