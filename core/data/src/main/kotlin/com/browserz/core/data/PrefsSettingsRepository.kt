package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.CookieMode
import com.browserz.core.domain.DesktopMode
import com.browserz.core.domain.Settings
import com.browserz.core.domain.SettingsRepository
import com.browserz.core.domain.TabsPosition
import com.browserz.core.domain.ThemeMode
import com.browserz.core.domain.ToolbarPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrefsSettingsRepository(private val prefs: SharedPreferences) : SettingsRepository {

    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    override fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit()
            .putBoolean(AD_BLOCK, next.adBlockEnabled)
            .putString(COOKIES, next.cookieMode.name)
            .putString(DESKTOP, next.desktopMode.name)
            .putString(THEME, next.themeMode.name)
            .putString(TOOLBAR, next.toolbarPosition.name)
            .putString(TABS, next.tabsPosition.name)
            .putBoolean(HIDE_BAR, next.hideBarOnScroll)
            .putBoolean(PULL_REFRESH, next.pullToRefresh)
            .putBoolean(BACKGROUND_PLAY, next.backgroundPlay)
            .putBoolean(PIP, next.pictureInPicture)
            .putBoolean(POPUPS, next.blockPopups)
            .putBoolean(HOME_HISTORY, next.showHistoryOnHome)
            .putBoolean(HTTPS_ONLY, next.httpsOnly)
            .putInt(TEXT_ZOOM, next.textZoom)
            .putBoolean(CLEAR_HISTORY, next.clearHistoryOnExit)
            .putBoolean(CLEAR_COOKIES, next.clearCookiesOnExit)
            .putBoolean(CLEAR_CACHE, next.clearCacheOnExit)
            .apply()
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            adBlockEnabled = prefs.getBoolean(AD_BLOCK, d.adBlockEnabled),
            cookieMode = enumOf(prefs.getString(COOKIES, null), d.cookieMode),
            desktopMode = enumOf(prefs.getString(DESKTOP, null), d.desktopMode),
            themeMode = enumOf(prefs.getString(THEME, null), d.themeMode),
            toolbarPosition = enumOf(prefs.getString(TOOLBAR, null), d.toolbarPosition),
            tabsPosition = enumOf(prefs.getString(TABS, null), d.tabsPosition),
            hideBarOnScroll = prefs.getBoolean(HIDE_BAR, d.hideBarOnScroll),
            pullToRefresh = prefs.getBoolean(PULL_REFRESH, d.pullToRefresh),
            backgroundPlay = prefs.getBoolean(BACKGROUND_PLAY, d.backgroundPlay),
            pictureInPicture = prefs.getBoolean(PIP, d.pictureInPicture),
            blockPopups = prefs.getBoolean(POPUPS, d.blockPopups),
            showHistoryOnHome = prefs.getBoolean(HOME_HISTORY, d.showHistoryOnHome),
            httpsOnly = prefs.getBoolean(HTTPS_ONLY, d.httpsOnly),
            textZoom = prefs.getInt(TEXT_ZOOM, d.textZoom).coerceIn(50, 200),
            clearHistoryOnExit = prefs.getBoolean(CLEAR_HISTORY, d.clearHistoryOnExit),
            clearCookiesOnExit = prefs.getBoolean(CLEAR_COOKIES, d.clearCookiesOnExit),
            clearCacheOnExit = prefs.getBoolean(CLEAR_CACHE, d.clearCacheOnExit),
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val AD_BLOCK = "adBlockEnabled"
        const val COOKIES = "cookieMode"
        const val DESKTOP = "desktopMode"
        const val THEME = "themeMode"
        const val TOOLBAR = "toolbarPosition"
        const val TABS = "tabsPosition"
        const val HIDE_BAR = "hideBarOnScroll"
        const val PULL_REFRESH = "pullToRefresh"
        const val BACKGROUND_PLAY = "backgroundPlay"
        const val PIP = "pictureInPicture"
        const val POPUPS = "blockPopups"
        const val HOME_HISTORY = "showHistoryOnHome"
        const val HTTPS_ONLY = "httpsOnly"
        const val TEXT_ZOOM = "textZoom"
        const val CLEAR_HISTORY = "clearHistoryOnExit"
        const val CLEAR_COOKIES = "clearCookiesOnExit"
        const val CLEAR_CACHE = "clearCacheOnExit"
    }
}
