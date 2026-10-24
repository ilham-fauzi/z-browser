package com.browserz.core.domain

/** What a page can ask the device for. */
enum class SitePermission { Camera, Microphone, Location }

/** The user's standing answer for one site and one permission. */
enum class PermissionDecision { Ask, Allow, Block }

object PermissionRules {
    /** One verdict for a request that needs several permissions at once: any Block wins, all Allow passes, otherwise ask. */
    fun combine(decisions: List<PermissionDecision>): PermissionDecision = when {
        decisions.isEmpty() -> PermissionDecision.Block
        decisions.any { it == PermissionDecision.Block } -> PermissionDecision.Block
        decisions.all { it == PermissionDecision.Allow } -> PermissionDecision.Allow
        else -> PermissionDecision.Ask
    }
}
