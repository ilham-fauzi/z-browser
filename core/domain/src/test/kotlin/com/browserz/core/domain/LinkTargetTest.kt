package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkTargetTest {
    @Test
    fun `given a web link when asking what can be opened then it is returned`() {
        val target = LinkTarget("https://a.com/x", "A", null, false)
        assertEquals("https://a.com/x", target.openableUrl)
    }

    @Test
    fun `given javascript or mailto links when asking what can be opened then nothing`() {
        assertNull(LinkTarget("javascript:alert(1)", null, null, false).openableUrl)
        assertNull(LinkTarget("mailto:a@b.c", null, null, false).openableUrl)
        assertNull(LinkTarget(null, null, "data:image/png;base64,AAAA", false).openableImage)
    }
}
