package com.browserz.core.webview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.browserz.core.domain.PermissionDecision
import com.browserz.core.domain.PermissionRules
import com.browserz.core.domain.SitePermission
import com.browserz.core.domain.SitePermissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A page is asking for something; the app shows a dialog and calls one of the three answers. */
class PermissionPrompt(
    val host: String,
    val kinds: List<SitePermission>,
    val allow: () -> Unit,
    val block: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * Decides what happens when a page asks for the camera, microphone or location: the site's standing answer first,
 * then a prompt when there is none, then the Android runtime permission if the app does not hold it yet.
 */
internal class PermissionBroker(context: Context, private val repo: SitePermissionRepository) {
    private val appContext = context.applicationContext
    private val _prompt = MutableStateFlow<PermissionPrompt?>(null)
    val prompt: StateFlow<PermissionPrompt?> = _prompt.asStateFlow()

    /** Set by the activity: asks Android for runtime permissions and reports whether all were granted. */
    @Volatile var runtimeRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null

    fun request(host: String, kinds: List<SitePermission>, grant: () -> Unit, deny: () -> Unit) {
        when (PermissionRules.combine(kinds.map { repo.decision(host, it) })) {
            PermissionDecision.Allow -> withRuntime(kinds, grant, deny)
            PermissionDecision.Block -> deny()
            PermissionDecision.Ask -> ask(host, kinds, grant, deny)
        }
    }

    fun dismissPrompt() {
        _prompt.value?.dismiss?.invoke()
    }

    private fun ask(host: String, kinds: List<SitePermission>, grant: () -> Unit, deny: () -> Unit) {
        if (_prompt.value != null) return deny() // one question at a time
        _prompt.value = PermissionPrompt(
            host = host,
            kinds = kinds,
            allow = {
                _prompt.value = null
                kinds.forEach { repo.set(host, it, PermissionDecision.Allow) }
                withRuntime(kinds, grant, deny)
            },
            block = {
                _prompt.value = null
                kinds.forEach { repo.set(host, it, PermissionDecision.Block) }
                deny()
            },
            dismiss = {
                _prompt.value = null
                deny()
            },
        )
    }

    private fun withRuntime(kinds: List<SitePermission>, grant: () -> Unit, deny: () -> Unit) {
        if (kinds.all { isGranted(it) }) return grant()
        val needed = kinds.flatMap { runtimePermissions(it) }.distinct().filter {
            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) return grant()
        val requester = runtimeRequester ?: return deny()
        requester(needed.toTypedArray()) { _ -> if (kinds.all { isGranted(it) }) grant() else deny() }
    }

    /** Approximate location is enough for a page; the other kinds need exactly their one permission. */
    private fun isGranted(kind: SitePermission): Boolean = when (kind) {
        SitePermission.Location -> runtimePermissions(kind).any { has(it) }
        else -> runtimePermissions(kind).all { has(it) }
    }

    private fun has(permission: String) = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun runtimePermissions(kind: SitePermission): List<String> = when (kind) {
        SitePermission.Camera -> listOf(Manifest.permission.CAMERA)
        SitePermission.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
        SitePermission.Location -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
