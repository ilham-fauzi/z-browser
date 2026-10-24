package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpsOnlyTest {
    @Test
    fun `given a public http address when upgrading then https is used and the rest is kept`() {
        assertEquals("https://example.com/a?b=1#c", HttpsOnly.upgrade("http://example.com/a?b=1#c"))
        assertEquals("https://example.com:8080/x", HttpsOnly.upgrade("HTTP://example.com:8080/x".replaceFirst("HTTP", "http")))
    }

    @Test
    fun `given https or local addresses when upgrading then nothing changes`() {
        assertNull(HttpsOnly.upgrade("https://example.com"))
        assertNull(HttpsOnly.upgrade("http://localhost:8000/x"))
        assertNull(HttpsOnly.upgrade("http://10.0.2.2:8000/x"))
        assertNull(HttpsOnly.upgrade("http://192.168.1.5/"))
        assertNull(HttpsOnly.upgrade("http://router/"))
        assertNull(HttpsOnly.upgrade("http://printer.local/"))
        assertNull(HttpsOnly.upgrade("ftp://example.com"))
    }

    @Test
    fun `given addresses when checking for local then private ranges are local and public ones are not`() {
        assertTrue(HttpsOnly.isLocal("172.16.0.1"))
        assertTrue(HttpsOnly.isLocal("127.0.0.1"))
        assertTrue(HttpsOnly.isLocal("[::1]"))
        assertFalse(HttpsOnly.isLocal("172.32.0.1"))
        assertFalse(HttpsOnly.isLocal("8.8.8.8"))
        assertFalse(HttpsOnly.isLocal("example.co.id"))
    }
}
