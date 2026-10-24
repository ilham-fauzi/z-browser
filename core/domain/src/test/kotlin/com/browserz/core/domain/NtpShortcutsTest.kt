package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NtpShortcutsTest {
    private fun h(url: String, at: Long) = HistoryEntry(url, "t", at)

    @Test
    fun `given pinned sites and history when building tiles then pinned lead and frequent sites follow`() {
        val history = listOf(
            h("https://a.com/1", 9), h("https://b.com/1", 8), h("https://b.com/2", 7), h("https://c.com/", 6),
        )
        val tiles = NtpShortcuts.build(listOf(PinnedSite("https://p.com/", "P")), history, max = 3)
        assertEquals(listOf("https://p.com/", "https://b.com/", "https://a.com/"), tiles.map { it.url })
        assertEquals(listOf(true, false, false), tiles.map { it.pinned })
    }

    @Test
    fun `given a pinned site that is also in history when building tiles then it appears once`() {
        val tiles = NtpShortcuts.build(listOf(PinnedSite("https://www.a.com/", "A")), listOf(h("https://www.a.com/x", 1)))
        assertEquals(1, tiles.size)
    }

    @Test
    fun `given more pins than room when building tiles then the extra pins are dropped`() {
        val pins = (1..10).map { PinnedSite("https://s$it.com/", "S$it") }
        assertEquals(NtpShortcuts.MAX, NtpShortcuts.build(pins, emptyList()).size)
    }

    @Test
    fun `given typed addresses when normalizing then bare domains get https and junk is rejected`() {
        assertEquals("https://example.com", NtpShortcuts.normalizeUrl(" example.com "))
        assertEquals("http://a.b/x", NtpShortcuts.normalizeUrl("http://a.b/x"))
        assertNull(NtpShortcuts.normalizeUrl("javascript:alert(1)"))
        assertNull(NtpShortcuts.normalizeUrl("not a url"))
        assertNull(NtpShortcuts.normalizeUrl("localhostx"))
    }
}
