package com.browserz.core.domain

/** The start page of a search engine, derived from its URL template (no extra field to keep in sync). */
object EngineHome {
    private val origin = Regex("^(https?://[^/?#\\s]+)", RegexOption.IGNORE_CASE)

    /** `https://www.google.com/` for `https://www.google.com/search?q=%s`, or null when the template has no web origin. */
    fun url(engine: SearchEngine): String? = origin.find(engine.urlTemplate.trim())?.groupValues?.get(1)?.let { "$it/" }
}
