package com.browserz.core.webview

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.browserz.core.domain.SiteDataManager

/** Clears data of the default WebView profile. Main thread only. Incognito data is deleted with its profile. */
class WebViewSiteDataManager(private val context: Context) : SiteDataManager {

    override fun clearCookiesAndSiteData() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        } catch (_: RuntimeException) {
            // WebView provider missing or being updated: nothing to clear.
        }
    }

    override fun clearCache() {
        try {
            val webView = WebView(context.applicationContext)
            webView.clearCache(true)
            webView.destroy()
        } catch (_: RuntimeException) {
            // WebView provider missing or being updated: nothing to clear.
        }
    }
}
