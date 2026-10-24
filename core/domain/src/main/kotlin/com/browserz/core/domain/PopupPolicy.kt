package com.browserz.core.domain

sealed interface PopupVerdict {
    data object Allow : PopupVerdict

    /** [silent] = a known ad or an unrequested popup: drop it quietly; otherwise tell the user and offer "Open". */
    data class Block(val silent: Boolean) : PopupVerdict
}

/**
 * Decides which new windows and automatic redirects are allowed. Ad networks rotate domains faster
 * than lists can follow, so besides the filter lists this looks at behaviour: did the user cause it,
 * and does it leave the site they are on?
 */
object PopupPolicy {

    /** Sign-in and payment pop-ups that real sites open on purpose. Matched on the host and its subdomains. */
    private val TRUSTED = listOf(
        "accounts.google.com", "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
        "facebook.com", "github.com", "gitlab.com", "paypal.com", "stripe.com", "twitter.com", "x.com",
        "discord.com", "login.yahoo.com", "auth0.com", "okta.com", "line.me", "telegram.org", "midtrans.com",
    )

    /** A script called window.open (or the page used target=_blank). [adMatch] = the filter lists block the target. */
    fun forWindow(openerUrl: String, targetUrl: String, userGesture: Boolean, adMatch: Boolean, clickedLinkUrl: String? = null): PopupVerdict {
        if (adMatch) return PopupVerdict.Block(silent = true)
        if (!userGesture) return PopupVerdict.Block(silent = true)
        val target = Hosts.hostOf(targetUrl)
        // The window opened on the very site of the link the user tapped (a real target=_blank link): that is what they asked for.
        if (clickedLinkUrl != null && Hosts.sameSite(Hosts.hostOf(clickedLinkUrl), target)) return PopupVerdict.Allow
        if (Hosts.sameSite(Hosts.hostOf(openerUrl), target) || isTrusted(target)) return PopupVerdict.Allow
        // A click-triggered window to some other site is the classic overlay pop-up: hold it back, offer to open.
        return PopupVerdict.Block(silent = false)
    }

    /** The current page is navigating itself to [targetUrl]. True = cancel the navigation. */
    fun blockRedirect(pageUrl: String, targetUrl: String, hasGesture: Boolean, isServerRedirect: Boolean, adMatch: Boolean): Boolean {
        if (adMatch) return true
        if (hasGesture || isServerRedirect) return false
        val target = Hosts.hostOf(targetUrl)
        return !Hosts.sameSite(Hosts.hostOf(pageUrl), target) && !isTrusted(target)
    }

    fun isTrusted(host: String): Boolean = TRUSTED.any { host == it || host.endsWith(".$it") }
}
