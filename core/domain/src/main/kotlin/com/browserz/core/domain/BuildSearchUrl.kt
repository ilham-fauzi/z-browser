package com.browserz.core.domain

import java.net.URLEncoder

/** Replaces the single %s in an engine template with the URL-encoded query. */
class BuildSearchUrl {
    operator fun invoke(engine: SearchEngine, query: String): String {
        require(engine.urlTemplate.startsWith("https://")) { "Search templates must be https" }
        require(engine.urlTemplate.split("%s").size == 2) { "Template needs exactly one %s" }
        return engine.urlTemplate.replace("%s", URLEncoder.encode(query, Charsets.UTF_8))
    }
}
