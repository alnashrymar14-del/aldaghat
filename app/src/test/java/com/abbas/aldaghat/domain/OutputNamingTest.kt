package com.abbas.aldaghat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNamingTest {

    @Test
    fun `adds the compressed marker and mp4 extension`() {
        val base = OutputNaming.compressedBaseName("holiday.MOV")
        assertEquals("holiday_compressed", base)
        assertEquals("holiday_compressed.mp4", OutputNaming.fileName(base))
    }

    @Test
    fun `does not duplicate the marker`() {
        assertEquals("clip_compressed", OutputNaming.compressedBaseName("clip_compressed.mp4"))
        assertEquals("clip_compressed", OutputNaming.compressedBaseName("clip_compressed"))
    }

    @Test
    fun `sanitises illegal filename characters`() {
        val base = OutputNaming.compressedBaseName("my/video:part*1?.mp4")
        assertFalse(base.contains("/"))
        assertFalse(base.contains(":"))
        assertFalse(base.contains("*"))
        assertFalse(base.contains("?"))
        assertTrue(base.endsWith("_compressed"))
    }

    @Test
    fun `falls back for empty names`() {
        assertEquals("video_compressed", OutputNaming.compressedBaseName(""))
        assertEquals("video_compressed", OutputNaming.compressedBaseName(".mp4"))
    }

    @Test
    fun `adds a numeric suffix for duplicates`() {
        assertEquals("clip_compressed.mp4", OutputNaming.fileName("clip_compressed", 0))
        assertEquals("clip_compressed (1).mp4", OutputNaming.fileName("clip_compressed", 1))
        assertEquals("clip_compressed (12).mp4", OutputNaming.fileName("clip_compressed", 12))
    }
}
