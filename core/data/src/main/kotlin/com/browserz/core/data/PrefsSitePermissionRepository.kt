package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.PermissionDecision
import com.browserz.core.domain.SitePermission
import com.browserz.core.domain.SitePermissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One preference key per site and permission, so a decision is a single small write. Ask is the default and is not stored. */
class PrefsSitePermissionRepository(private val prefs: SharedPreferences) : SitePermissionRepository {
    private val _version = MutableStateFlow(0)
    override val version: StateFlow<Int> = _version.asStateFlow()

    override fun decision(host: String, permission: SitePermission): PermissionDecision =
        when (prefs.getString(key(host, permission), null)) {
            PermissionDecision.Allow.name -> PermissionDecision.Allow
            PermissionDecision.Block.name -> PermissionDecision.Block
            else -> PermissionDecision.Ask
        }

    @Synchronized
    override fun set(host: String, permission: SitePermission, decision: PermissionDecision) {
        val edit = prefs.edit()
        if (decision == PermissionDecision.Ask) edit.remove(key(host, permission)) else edit.putString(key(host, permission), decision.name)
        edit.apply()
        _version.value += 1
    }

    @Synchronized
    override fun reset(host: String) {
        val edit = prefs.edit()
        SitePermission.entries.forEach { edit.remove(key(host, it)) }
        edit.apply()
        _version.value += 1
    }

    private fun key(host: String, permission: SitePermission) = "perm:${host.lowercase()}:${permission.name}"
}
