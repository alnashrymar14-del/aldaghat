package com.abbas.aldaghat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoInfoTest {

    private fun info(width: Int, height: Int, rotation: Int = 0, fps: Float = 30f) = VideoInfo(
        uri = "content://video/1",
        displayName = "v.mp4",
        sizeBytes = 1000,
        durationMs = 5000,
        width = width,
        height = height,
        rotationDegrees = rotation,
        frameRate = fps,
    )

    @Test
    fun `landscape metadata is reported as-is`() {
        val video = info(1920, 1080)
        assertEquals(1920, video.displayWidth)
        assertEquals(1080, video.displayHeight)
        assertFalse(video.isPortrait)
        assertEquals(1080, video.shortSide)
        assertEquals(1920, video.longSide)
    }

    @Test
    fun `quarter turn rotation swaps display dimensions`() {
        val video = info(1920, 1080, rotation = 90)
        assertEquals(1080, video.displayWidth)
        assertEquals(1920, video.displayHeight)
        assertTrue(video.isPortrait)
    }

    @Test
    fun `upside down rotation keeps dimensions`() {
        val video = info(1920, 1080, rotation = 180)
        assertEquals(1920, video.displayWidth)
        assertEquals(1080, video.displayHeight)
    }

    @Test
    fun `invalid frame rate falls back to 30`() {
        assertEquals(30f, info(1280, 720, fps = 0f).effectiveFrameRate, 0.001f)
    }

    @Test
    fun `validity requires positive dimensions and duration`() {
        assertTrue(info(1280, 720).isValid)
        assertFalse(info(0, 720).isValid)
        assertFalse(info(1280, 720).copy(durationMs = 0L).isValid)
    }
}
