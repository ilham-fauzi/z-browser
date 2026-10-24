package com.browserz.core.domain

/** User-agent rewriting. The system WebView announces itself ("; wv", "Version/4.0"); sites such as Google sign-up then treat the browser as an embedded app. */
object UserAgents {
    private val webViewMarker = Regex(";\\s*wv\\b")
    private val versionToken = Regex("\\bVersion/\\d+(\\.\\d+)*\\s+")
    private val platformGroup = Regex("\\(.*?\\)")

    /** The same browser as Chrome would announce it: WebView-only tokens removed, everything else (Android and Chrome versions) kept. */
    fun asChrome(webViewUa: String): String = webViewUa.replace(webViewMarker, "").replace(versionToken, "")

    /** Desktop Linux Chrome: platform group swapped and the mobile token dropped. */
    fun desktop(ua: String): String = ua.replace(platformGroup, "(X11; Linux x86_64)").replace(" Mobile", "")
}
