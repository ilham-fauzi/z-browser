package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PopupPolicyTest {
    private val page = "https://dramatip.net/series/abc"

    @Test
    fun `given a window without a user gesture when deciding then it is dropped silently`() {
        assertEquals(PopupVerdict.Block(true), PopupPolicy.forWindow(page, "https://ads.example/x", userGesture = false, adMatch = false))
    }

    @Test
    fun `given a known ad target when deciding then it is dropped silently even after a click`() {
        assertEquals(PopupVerdict.Block(true), PopupPolicy.forWindow(page, "https://shameful-farm.com/x", userGesture = true, adMatch = true))
    }

    @Test
    fun `given a click that opens another site when deciding then it is held back and the user is told`() {
        assertEquals(PopupVerdict.Block(false), PopupPolicy.forWindow(page, "https://unknown-ad.example/x", userGesture = true, adMatch = false))
    }

    @Test
    fun `given a click that opens the same site or a sign-in page when deciding then it is allowed`() {
        assertEquals(PopupVerdict.Allow, PopupPolicy.forWindow(page, "https://www.dramatip.net/play", true, false))
        assertEquals(PopupVerdict.Allow, PopupPolicy.forWindow(page, "https://accounts.google.com/o/oauth2/auth", true, false))
        assertEquals(PopupVerdict.Allow, PopupPolicy.forWindow(page, "https://m.facebook.com/dialog/oauth", true, false))
    }

    @Test
    fun `given an automatic cross-site redirect when deciding then it is blocked`() {
        assertTrue(PopupPolicy.blockRedirect(page, "https://scam.example/win", hasGesture = false, isServerRedirect = false, adMatch = false))
    }

    @Test
    fun `given redirects the user or the server started when deciding then they pass`() {
        assertFalse(PopupPolicy.blockRedirect(page, "https://other.example/", hasGesture = true, isServerRedirect = false, adMatch = false))
        assertFalse(PopupPolicy.blockRedirect(page, "https://login.example/", hasGesture = false, isServerRedirect = true, adMatch = false))
        assertFalse(PopupPolicy.blockRedirect(page, "https://dramatip.net/next", hasGesture = false, isServerRedirect = false, adMatch = false))
        assertFalse(PopupPolicy.blockRedirect(page, "https://accounts.google.com/x", hasGesture = false, isServerRedirect = false, adMatch = false))
    }

    @Test
    fun `given a known ad host when redirecting then it is blocked whatever the gesture`() {
        assertTrue(PopupPolicy.blockRedirect(page, "https://shameful-farm.com/", hasGesture = true, isServerRedirect = true, adMatch = true))
    }

    @Test
    fun `given hosts when comparing sites then registrable domains decide`() {
        assertTrue(Hosts.sameSite("a.news.example.co.id", "example.co.id"))
        assertFalse(Hosts.sameSite("example.com", "example.org"))
        assertEquals("example.co.id", Hosts.registrableDomain("x.y.example.co.id"))
    }

    @Test
    fun `given a tapped link and a window opened to that site when deciding then it is allowed even cross-site`() {
        val v = PopupPolicy.forWindow("https://a.com/", "https://other.org/page", userGesture = true, adMatch = false, clickedLinkUrl = "https://other.org/x")
        assertEquals(PopupVerdict.Allow, v)
    }

    @Test
    fun `given a tapped link but a window opened to an unrelated site when deciding then it is held back`() {
        val v = PopupPolicy.forWindow("https://a.com/", "https://ads.example/win", userGesture = true, adMatch = false, clickedLinkUrl = "https://a.com/watch/1")
        assertEquals(PopupVerdict.Block(silent = false), v)
    }

    @Test
    fun `given a tapped link and an ad target when deciding then the ad list still wins`() {
        val v = PopupPolicy.forWindow("https://a.com/", "https://ads.example/win", userGesture = true, adMatch = true, clickedLinkUrl = "https://ads.example/")
        assertEquals(PopupVerdict.Block(silent = true), v)
    }
}
