package com.browserz.core.webview

/**
 * The audible media a page plays (or last played): what the media notification, the lock screen and
 * picture-in-picture show. [videoWidth] x [videoHeight] is 0 x 0 for audio.
 */
data class MediaPlayback(
    val tabId: Long,
    val title: String,
    val artist: String,
    val host: String,
    val playing: Boolean,
    val hasVideo: Boolean,
    val videoWidth: Int,
    val videoHeight: Int,
    val canNext: Boolean,
    val canPrevious: Boolean,
)
