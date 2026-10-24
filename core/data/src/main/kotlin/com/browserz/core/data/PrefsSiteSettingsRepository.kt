package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.SiteSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrefsSiteSettingsRepository(private val prefs: SharedPreferences) : SiteSettingsRepository {

    private val _hosts = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    override val adsAllowedHosts: StateFlow<Set<String>> = _hosts.asStateFlow()

    @Synchronized
    override fun setAdsAllowed(host: String, allowed: Boolean) {
        val h = host.lowercase()
        if (h.isEmpty()) return
        _hosts.value = if (allowed) _hosts.value + h else _hosts.value - h
        // putStringSet needs a fresh set instance: SharedPreferences ignores in-place mutation.
        prefs.edit().putStringSet(KEY, HashSet(_hosts.value)).apply()
    }

    private companion object {
        const val KEY = "adsAllowedHosts"
    }
}
