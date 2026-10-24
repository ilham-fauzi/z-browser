package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TabOrderTest {
    private data class T(val name: String, val pinned: Boolean, val group: String? = null)

    @Test
    fun `given mixed tabs when ordering then pinned come first and keep their order`() {
        val tabs = listOf(T("a", false), T("b", true), T("c", false), T("d", true))
        assertEquals(listOf("b", "d", "a", "c"), TabOrder.pinnedFirst(tabs) { it.pinned }.map { it.name })
    }

    @Test
    fun `given no pinned tabs when ordering then nothing moves`() {
        val tabs = listOf(T("a", false), T("b", false))
        assertEquals(tabs, TabOrder.pinnedFirst(tabs) { it.pinned })
    }

    @Test
    fun `given a pin change when placing the tab then it lands right after the pinned group`() {
        val others = listOf(T("p1", true), T("p2", true), T("x", false), T("y", false))
        assertEquals(2, TabOrder.indexAfterPinChange(others) { it.pinned })
        assertEquals(0, TabOrder.indexAfterPinChange(listOf(T("x", false))) { it.pinned })
    }

    @Test
    fun `given grouped tabs when arranging then members sit together at the first member's place`() {
        val tabs = listOf(T("a", false, "g"), T("b", false), T("c", false, "g"), T("d", false), T("e", true))
        assertEquals(listOf("e", "a", "c", "b", "d"), TabOrder.arrange(tabs, { it.pinned }, { it.group }).map { it.name })
    }

    @Test
    fun `given a pinned tab with a group when arranging then it stays in the pinned block`() {
        val tabs = listOf(T("a", false, "g"), T("p", true, "g"))
        assertEquals(listOf("p", "a"), TabOrder.arrange(tabs, { it.pinned }, { it.group }).map { it.name })
    }
}
