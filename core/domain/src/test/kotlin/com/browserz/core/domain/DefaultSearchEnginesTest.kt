package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSearchEnginesTest {
    @Test
    fun `given the presets when listing then google is first and duckduckgo is hidden`() {
        val names = DefaultSearchEngines.presets.map { it.name }
        assertEquals("Google", names.first())
        assertFalse("DuckDuckGo" in names)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `given the presets when listing then every template is valid and shortcuts are unique`() {
        DefaultSearchEngines.presets.forEach { assertNull(DefaultSearchEngines.validateTemplate(it.urlTemplate), it.name) }
        val keywords = DefaultSearchEngines.presets.mapNotNull { it.keyword }
        assertEquals(keywords.size, keywords.toSet().size)
    }

    @Test
    fun `given bad templates when validating then a reason is returned`() {
        assertTrue(DefaultSearchEngines.validateTemplate("http://x.test/?q=%s") != null)
        assertTrue(DefaultSearchEngines.validateTemplate("https://x.test/?q=") != null)
        assertTrue(DefaultSearchEngines.validateTemplate("https://x.test/?q=%s&r=%s") != null)
    }
}
