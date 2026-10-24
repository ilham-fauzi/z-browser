package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineHomeTest {
    private fun engine(template: String) = SearchEngine(1, "e", template, null, false)

    @Test
    fun `given a search template when asking for home then the site root is returned`() {
        assertEquals("https://www.google.com/", EngineHome.url(engine("https://www.google.com/search?q=%s")))
        assertEquals("http://localhost:8080/", EngineHome.url(engine("http://localhost:8080/q/%s")))
    }

    @Test
    fun `given a template without a web origin when asking for home then null`() {
        assertNull(EngineHome.url(engine("search?q=%s")))
        assertNull(EngineHome.url(engine("")))
    }
}
