package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClosedTabsTest {
    private fun tab(url: String) = ClosedTab(url, "t", false, null)

    @Test
    fun `given closed tabs when pushing then newest is first, repeats collapse and the list is capped`() {
        var list = emptyList<ClosedTab>()
        (1..15).forEach { list = ClosedTabs.push(list, tab("https://s$it.com")) }
        assertEquals(ClosedTabs.MAX, list.size)
        assertEquals("https://s15.com", list.first().url)
        list = ClosedTabs.push(list, tab("https://s12.com"))
        assertEquals("https://s12.com", list.first().url)
        assertEquals(1, list.count { it.url == "https://s12.com" })
    }

    @Test
    fun `given blank tabs when pushing then they are not kept`() {
        assertEquals(emptyList(), ClosedTabs.push(emptyList(), tab("")))
    }

    @Test
    fun `given a list when popping then the newest comes out`() {
        val (top, rest) = ClosedTabs.pop(listOf(tab("a"), tab("b")))
        assertEquals("a", top?.url)
        assertEquals(listOf("b"), rest.map { it.url })
        assertNull(ClosedTabs.pop(emptyList()).first)
    }
}
