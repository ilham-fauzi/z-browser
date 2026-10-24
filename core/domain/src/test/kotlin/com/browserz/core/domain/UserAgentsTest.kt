package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class UserAgentsTest {
    private val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UP1A.231105.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.6478.71 Mobile Safari/537.36"

    @Test
    fun `given a WebView user agent when converting then the WebView tokens are gone and the rest is kept`() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UP1A.231105.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36",
            UserAgents.asChrome(webView),
        )
    }

    @Test
    fun `given a Chrome user agent when converting then it is unchanged`() {
        val chrome = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        assertEquals(chrome, UserAgents.asChrome(chrome))
    }

    @Test
    fun `given a mobile user agent when asking for desktop then platform and mobile token change`() {
        val ua = UserAgents.desktop(UserAgents.asChrome(webView))
        assertEquals(true, ua.contains("(X11; Linux x86_64)") && !ua.contains("Mobile") && !ua.contains("Android"))
    }
}
