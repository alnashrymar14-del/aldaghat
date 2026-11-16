package com.abbas.aldaghat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPlannerTest {

    private fun video(
        width: Int,
        height: Int,
        rotation: Int = 0,
        fps: Float = 30f,
        durationMs: Long = 10_000L,
        hasAudio: Boolean = true,
        bitrateKbps: Int = 8_000,
    ) = VideoInfo(
        uri = "content://test/video",
        displayName = "clip.mp4",
        sizeBytes = 20L * 1024 * 1024,
        durationMs = durationMs,
        width = width,
        height = height,
        rotationDegrees = rotation,
        frameRate = fps,
        videoBitrateKbps = bitrateKbps,
        audioBitrateKbps = 128,
        hasAudio = hasAudio,
    )

    private val alwaysSupported: (Int, Int) -> Boolean = { _, _ -> true }

    @Test
    fun `landscape 1080p source is scaled to 720p`() {
        val plan = CompressionPlanner.plan(
            video = video(1920, 1080),
            preset = QualityPreset.P720,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = alwaysSupported,
        )
        assertNotNull(plan)
        assertEquals(1280, plan!!.outputWidth)
        assertEquals(720, plan.outputHeight)
        assertTrue(plan.scaled)
    }

    @Test
    fun `portrait source keeps portrait orientation`() {
        val plan = CompressionPlanner.plan(
            video = video(1080, 1920),
            preset = QualityPreset.P720,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = alwaysSupported,
        )
        assertNotNull(plan)
        assertEquals(720, plan!!.outputWidth)
        assertEquals(1280, plan.outputHeight)
        assertTrue(plan.outputHeight > plan.outputWidth)
    }

    @Test
    fun `rotation metadata is applied to the output orientation`() {
        // Stored as landscape but rotated 90° => portrait playback
        val plan = CompressionPlanner.plan(
            video = video(1920, 1080, rotation = 90),
            preset = QualityPreset.P480,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = alwaysSupported,
        )
        assertNotNull(plan)
        assertEquals(480, plan!!.outputWidth)
        // 1920 * (480/1080) rounded down to an even number
        assertEquals(852, plan.outputHeight)
        assertEquals(0, plan.outputHeight % 2)
    }

    @Test
    fun `resolution is never raised above the source`() {
        val plan = CompressionPlanner.plan(
            video = video(640, 480),
            preset = QualityPreset.P1080,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = alwaysSupported,
        )
        assertNotNull(plan)
        assertEquals(640, plan!!.outputWidth)
        assertEquals(480, plan.outputHeight)
        assertFalse(plan.scaled)
    }

    @Test
    fun `dimensions stay even for encoders`() {
        val dimensions = CompressionPlanner.scaledDimensions(1919, 1079, 720)
        assertNotNull(dimensions)
        assertEquals(0, dimensions!![0] % 2)
        assertEquals(0, dimensions[1] % 2)
    }

    @Test
    fun `aspect ratio is preserved within rounding`() {
        val dimensions = CompressionPlanner.scaledDimensions(3840, 2160, 1080)!!
        val sourceRatio = 3840.0 / 2160.0
        val outputRatio = dimensions[0].toDouble() / dimensions[1].toDouble()
        assertEquals(sourceRatio, outputRatio, 0.01)
    }

    @Test
    fun `falls back to a supported resolution when the encoder cannot handle the target`() {
        val plan = CompressionPlanner.plan(
            video = video(3840, 2160),
            preset = QualityPreset.P1080,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = { width, height -> width <= 1920 && height <= 1080 },
        )
        assertNotNull(plan)
        assertTrue(plan!!.outputWidth <= 1920)
        assertTrue(plan.outputHeight <= 1080)
    }

    @Test
    fun `returns null when no resolution is supported`() {
        val plan = CompressionPlanner.plan(
            video = video(1920, 1080),
            preset = QualityPreset.P720,
            codec = VideoCodec.HEVC,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = { _, _ -> false },
        )
        assertNull(plan)
    }

    @Test
    fun `audio bitrate is zero when the source has no audio track`() {
        val plan = CompressionPlanner.plan(
            video = video(1280, 720, hasAudio = false),
            preset = QualityPreset.P720,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = alwaysSupported,
        )
        assertNotNull(plan)
        assertEquals(0, plan!!.audioBitrateKbps)
        assertFalse(plan.hasAudio)
    }

    @Test
    fun `estimated size uses the chosen bitrates`() {
        val plan = CompressionPlanner.plan(
            video = video(1280, 720, durationMs = 60_000L),
            preset = QualityPreset.P720,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = 2000,
            audioBitrateKbps = 128,
            isSizeSupported = alwaysSupported,
        )!!
        assertEquals(
            60.0 * (plan.videoBitrateKbps + plan.audioBitrateKbps) / 8000.0,
            plan.estimatedMegabytes(60_000L),
            0.0001,
        )
    }

    @Test
    fun `invalid video metadata produces no plan`() {
        val broken = video(0, 0).copy(durationMs = 0L)
        assertNull(
            CompressionPlanner.plan(
                video = broken,
                preset = QualityPreset.P480,
                codec = VideoCodec.H264,
                manualVideoBitrateKbps = null,
                audioBitrateKbps = 128,
                isSizeSupported = alwaysSupported,
            ),
        )
    }
}
