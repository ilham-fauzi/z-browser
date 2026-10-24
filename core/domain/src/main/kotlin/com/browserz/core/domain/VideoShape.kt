package com.browserz.core.domain

/** Shape of the video being watched, used to pick the screen orientation in full screen. */
enum class VideoShape {
    Unknown,
    Landscape,
    Portrait,
    ;

    companion object {
        /** "1280x720" -> Landscape. Near-square or unreadable sizes stay [Unknown] (the device sensor decides). */
        fun fromSize(size: String?): VideoShape {
            val parts = size?.split('x') ?: return Unknown
            val w = parts.getOrNull(0)?.toIntOrNull() ?: return Unknown
            val h = parts.getOrNull(1)?.toIntOrNull() ?: return Unknown
            if (w <= 0 || h <= 0) return Unknown
            return when {
                w * 100 >= h * 110 -> Landscape
                h * 100 >= w * 110 -> Portrait
                else -> Unknown
            }
        }
    }
}
