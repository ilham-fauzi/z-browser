package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabGroupsTest {
    @Test
    fun `given blank or padded names when normalizing then blank means no group`() {
        assertNull(TabGroups.normalize("   "))
        assertNull(TabGroups.normalize(null))
        assertEquals("Work", TabGroups.normalize("  Work "))
        assertEquals(TabGroups.MAX_NAME_LENGTH, TabGroups.normalize("x".repeat(100))!!.length)
    }

    @Test
    fun `given a name when picking a colour then it is stable, case-insensitive and in range`() {
        assertEquals(TabGroups.colorIndex("Work"), TabGroups.colorIndex("work"))
        assertTrue(TabGroups.colorIndex("anything at all") in 0 until TabGroups.COLOR_COUNT)
    }

    @Test
    fun `given tab groups when listing names then repeats and nulls are dropped`() {
        assertEquals(listOf("a", "b"), TabGroups.names(listOf("a", null, "b", "a")))
    }
}
