package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildSearchUrlTest {
    private val engine = SearchEngine(1, "DDG", "https://duckduckgo.com/?q=%s", "!d", true)

    @Test
    fun `given a query when building then it is url encoded into the template`() {
        assertEquals("https://duckduckgo.com/?q=cats+%26+dogs", BuildSearchUrl()(engine, "cats & dogs"))
    }

    @Test
    fun `given a non https template when building then it is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BuildSearchUrl()(engine.copy(urlTemplate = "http://x.test/?q=%s"), "a")
        }
    }
}
