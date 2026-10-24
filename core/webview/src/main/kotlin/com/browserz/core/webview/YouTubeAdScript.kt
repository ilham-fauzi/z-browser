package com.browserz.core.webview

/**
 * Also runs on music.youtube.com (same player data; extra rules hide its promo banners).
 *
 * YouTube serves its video ads from its own servers, in the same responses as the video, so request blocking
 * cannot reach them. This document-start script works on the data instead (the approach of uBlock Origin's
 * json-prune): before the player reads a player response, the ad entries are deleted from it, so no ad is
 * scheduled. Three ways the data arrives are covered: `ytInitialPlayerResponse` set by the page, JSON.parse,
 * and fetch responses read with `.json()`. An ad that still starts (a format this does not know yet) is muted,
 * skipped to its end and its skip button pressed; feed and banner ad slots are hidden.
 *
 * When YouTube changes its formats this needs upkeep; server-side inserted ads (stitched into the video
 * stream itself) are not removed.
 */
internal object YouTubeAdScript {

    /** WebView origin rules: every YouTube site, in every frame (embedded players included). */
    val ORIGINS = setOf("https://*.youtube.com", "https://youtube.com", "https://*.youtube-nocookie.com")

    val SCRIPT: String =
        "(function(){try{if(window.__bzYt)return;window.__bzYt=true;" +
            "var KEYS=['adPlacements','adSlots','playerAds','adBreakHeartbeatParams'];" +
            "var prune=function(o){if(!o||typeof o!=='object')return o;" +
            "var t=[o,o.playerResponse,o.response,o.playerResponse&&o.playerResponse.response];" +
            "for(var i=0;i<t.length;i++){var x=t[i];if(!x||typeof x!=='object')continue;" +
            "for(var k=0;k<KEYS.length;k++){if(KEYS[k] in x){try{delete x[KEYS[k]];}catch(e){}}}}" +
            "if(Array.isArray(o.onResponseReceivedEndpoints))o.onResponseReceivedEndpoints.forEach(prune);" +
            "return o;};" +
            // JSON.parse: player responses parsed from text.
            "var P=JSON.parse;JSON.parse=function(){var r=P.apply(this,arguments);try{prune(r);}catch(e){}return r;};" +
            // fetch(...).json(): /youtubei/v1/player and /next.
            "if(window.Response){var J=Response.prototype.json;Response.prototype.json=function(){" +
            "var u=this.url||'';var p=J.apply(this,arguments);" +
            "return /\\/youtubei\\/v1\\/(player|next|reel)/.test(u)?p.then(function(d){try{prune(d);}catch(e){}return d;}):p;};}" +
            // ytInitialPlayerResponse assigned by an inline script.
            "var v;try{Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true," +
            "get:function(){return v;},set:function(n){v=prune(n);}});}catch(e){}" +
            // Leftover ads: mute, jump to the end, press skip.
            "var tick=function(){var p=document.querySelector('.ad-showing,.ad-interrupting');" +
            "if(p){var vid=p.querySelector('video');if(vid){vid.muted=true;if(isFinite(vid.duration)&&vid.duration>0)vid.currentTime=vid.duration;}" +
            "var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytm-skip-ad-button');if(b)b.click();}};" +
            "setInterval(tick,300);" +
            "var css='ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-promoted-sparkles-web-renderer,ytd-promoted-video-renderer," +
            "ytd-display-ad-renderer,ytd-banner-promo-renderer,ytd-statement-banner-renderer,ytd-player-legacy-desktop-watch-ads-renderer," +
            "ytd-companion-slot-renderer,ytd-action-companion-ad-renderer,ytd-rich-item-renderer:has(ytd-ad-slot-renderer)," +
            "ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,ytm-companion-slot," +
            "ad-slot-renderer,ytm-rich-item-renderer:has(ad-slot-renderer),#player-ads,#masthead-ad,.ytp-ad-overlay-container," +
            ".ytp-ad-message-container,.ytd-merch-shelf-renderer," +
            // YouTube Music: upgrade banners, promos and sponsored shelves.
            "ytmusic-mealbar-promo-renderer,ytmusic-statement-banner-renderer,ytmusic-popup-container ytmusic-mealbar-promo-renderer," +
            "ytmusic-player-bar .ad-badge,ytmusic-companion-ad-renderer,ytmusic-brand-promo-renderer,ytmusic-ad-slot-renderer{display:none!important}';" +
            "var add=function(){var h=document.head||document.documentElement;if(!h)return false;" +
            "var s=document.createElement('style');s.id='__bz_yt';s.textContent=css;h.appendChild(s);return true;};" +
            "if(!add())document.addEventListener('DOMContentLoaded',add);" +
            "}catch(e){}})();"
}
