package com.abbas.aldaghat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitrateSelectorTest {

    @Test
    fun `preset ranges match the documented defaults`() {
        assertEquals(4000 to 6000, QualityPreset.P1080.minVideoBitrateKbps to QualityPreset.P1080.maxVideoBitrateKbps)
        assertEquals(2000 to 3000, QualityPreset.P720.minVideoBitrateKbps to QualityPreset.P720.maxVideoBitrateKbps)
        assertEquals(800 to 1500, QualityPreset.P480.minVideoBitrateKbps to QualityPreset.P480.maxVideoBitrateKbps)
        assertEquals(400 to 800, QualityPreset.P360.minVideoBitrateKbps to QualityPreset.P360.maxVideoBitrateKbps)
    }

    @Test
    fun `default audio bitrate is 128 kbps`() {
        assertEquals(128, BitrateSelector.DEFAULT_AUDIO_KBPS)
        assertEquals(128, BitrateSelector.audioBitrateKbps(hasAudio = true, requestedKbps = 128))
        assertEquals(0, BitrateSelector.audioBitrateKbps(hasAudio = false, requestedKbps = 128))
        assertEquals(320, BitrateSelector.audioBitrateKbps(hasAudio = true, requestedKbps = 5000))
    }

    @Test
    fun `1080p lands inside the documented range`() {
        val kbps = BitrateSelector.videoBitrateKbps(
            preset = QualityPreset.P1080,
            outputWidth = 1920,
            outputHeight = 1080,
            sourceFrameRate = 30f,
            sourceVideoBitrateKbps = 0,
        )
        assertTrue("was $kbps", kbps in 4000..6000)
    }

    @Test
    fun `720p lands inside the documented range`() {
        val kbps = BitrateSelector.videoBitrateKbps(
            preset = QualityPreset.P720,
            outputWidth = 1280,
            outputHeight = 720,
            sourceFrameRate = 30f,
            sourceVideoBitrateKbps = 0,
        )
        assertTrue("was $kbps", kbps in 2000..3000)
    }

    @Test
    fun `480p lands inside the documented range`() {
        val kbps = BitrateSelector.videoBitrateKbps(
            preset = QualityPreset.P480,
            outputWidth = 854,
            outputHeight = 480,
            sourceFrameRate = 30f,
            sourceVideoBitrateKbps = 0,
        )
        assertTrue("was $kbps", kbps in 800..1500)
    }

    @Test
    fun `360p lands inside the documented range`() {
        val kbps = BitrateSelector.videoBitrateKbps(
            preset = QualityPreset.P360,
            outputWidth = 640,
            outputHeight = 360,
            sourceFrameRate = 30f,
            sourceVideoBitrateKbps = 0,
        )
        assertTrue("was $kbps", kbps in 400..800)
    }

    @Test
    fun `higher frame rate raises the requested bitrate`() {
        val at24 = BitrateSelector.videoBitrateKbps(
            QualityPreset.P480, 854, 480, 24f, 0,
        )
        val at30 = BitrateSelector.videoBitrateKbps(
            QualityPreset.P480, 854, 480, 30f, 0,
        )
        val at60 = BitrateSelector.videoBitrateKbps(
            QualityPreset.P480, 854, 480, 60f, 0,
        )
        assertTrue("$at24 !< $at30", at24 < at30)
        assertTrue("$at30 !< $at60", at30 < at60)
    }

    @Test
    fun `never asks for more than the source bitrate`() {
        val kbps = BitrateSelector.videoBitrateKbps(
            preset = QualityPreset.P720,
            outputWidth = 1280,
            outputHeight = 720,
            sourceFrameRate = 30f,
            sourceVideoBitrateKbps = 2000,
        )
        assertTrue("was $kbps", kbps <= 2000)
    }

    @Test
    fun `manual bitrate is honoured for the custom preset`() {
        val kbps = BitrateSelector.videoBitrateKbps(
            preset = QualityPreset.CUSTOM,
            outputWidth = 1280,
            outputHeight = 720,
            sourceFrameRate = 30f,
            sourceVideoBitrateKbps = 0,
            manualBitrateKbps = 1234,
        )
        assertEquals(1234, kbps)
    }

    @Test
    fun `bitrate is clamped to absolute bounds`() {
        val tooLow = BitrateSelector.videoBitrateKbps(
            QualityPreset.CUSTOM, 320, 240, 30f, 0, manualBitrateKbps = 1,
        )
        assertEquals(BitrateSelector.ABSOLUTE_MIN_VIDEO_KBPS, tooLow)

        val tooHigh = BitrateSelector.videoBitrateKbps(
            QualityPreset.CUSTOM, 3840, 2160, 60f, 0, manualBitrateKbps = 900_000,
        )
        assertEquals(BitrateSelector.ABSOLUTE_MAX_VIDEO_KBPS, tooHigh)
    }

    @Test
    fun `frame rate falls back to 30 when unknown`() {
        assertEquals(30f, BitrateSelector.effectiveFrameRate(0f), 0.001f)
        assertEquals(30f, BitrateSelector.effectiveFrameRate(Float.NaN), 0.001f)
        assertEquals(24f, BitrateSelector.effectiveFrameRate(5f), 0.001f)
        assertEquals(60f, BitrateSelector.effectiveFrameRate(120f), 0.001f)
    }
}
