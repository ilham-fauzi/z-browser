package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveInputTest {
    private val engines = DefaultSearchEngines.presets
    private val google = engines.first { it.name == "Google" }
    private val resolve = ResolveInput()

    private fun go(input: String) = resolve(input, google, engines)

    @Test
    fun `given a full url when resolving then it is unchanged`() {
        assertEquals("https://example.com/a?b=1", go("https://example.com/a?b=1"))
    }

    @Test
    fun `given a bare domain when resolving then https is added`() {
        assertEquals("https://example.com", go("example.com"))
        assertEquals("https://sub.example.co.id/path", go("sub.example.co.id/path"))
    }

    @Test
    fun `given localhost or an ip when resolving then http is used`() {
        assertEquals("http://localhost:8080", go("localhost:8080"))
        assertEquals("http://10.0.2.2:8000/test.html", go("10.0.2.2:8000/test.html"))
    }

    @Test
    fun `given plain words when resolving then the default engine searches`() {
        assertEquals("https://www.google.com/search?q=cats+and+dogs", go("cats and dogs"))
        assertEquals("https://www.google.com/search?q=cats", go("cats"))
    }

    @Test
    fun `given a keyword shortcut when resolving then that engine searches once`() {
        assertEquals("https://www.bing.com/search?q=cats", go("!b cats"))
        assertEquals("https://www.bing.com/search?q=cats", go("!B cats"))
    }

    @Test
    fun `given the hidden duckduckgo shortcut when resolving then it is a normal search`() {
        assertEquals("https://www.google.com/search?q=%21d+cats", go("!d cats"))
    }

    @Test
    fun `given a dangerous scheme when resolving then it becomes a search and never loads`() {
        val result = go("javascript:alert(1)")
        assertEquals("https://www.google.com/search?q=javascript%3Aalert%281%29", result)
        assertEquals(true, go("file:///etc/hosts").startsWith("https://www.google.com/search"))
    }

    @Test
    fun `given blank input when resolving then nothing loads`() {
        assertEquals("", go("   "))
    }
}
