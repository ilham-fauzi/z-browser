package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoShapeTest {
    @Test
    fun `given a wide video size when classifying then it is landscape`() {
        assertEquals(VideoShape.Landscape, VideoShape.fromSize("1920x1080"))
        assertEquals(VideoShape.Landscape, VideoShape.fromSize("640x360"))
        assertEquals(VideoShape.Landscape, VideoShape.fromSize("1280x720"))
    }

    @Test
    fun `given a tall video size when classifying then it is portrait`() {
        assertEquals(VideoShape.Portrait, VideoShape.fromSize("1080x1920"))
        assertEquals(VideoShape.Portrait, VideoShape.fromSize("360x640"))
    }

    @Test
    fun `given a near square or unreadable size when classifying then the sensor decides`() {
        assertEquals(VideoShape.Unknown, VideoShape.fromSize("500x500"))
        assertEquals(VideoShape.Unknown, VideoShape.fromSize("520x500"))
        assertEquals(VideoShape.Unknown, VideoShape.fromSize("0x0"))
        assertEquals(VideoShape.Unknown, VideoShape.fromSize("abc"))
        assertEquals(VideoShape.Unknown, VideoShape.fromSize(null))
        assertEquals(VideoShape.Unknown, VideoShape.fromSize(""))
    }
}
