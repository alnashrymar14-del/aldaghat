package com.abbas.aldaghat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `formats bytes with arabic units`() {
        assertEquals("0 بايت", Formatters.formatBytes(0))
        assertEquals("512 بايت", Formatters.formatBytes(512))
        assertEquals("2 كيلوبايت", Formatters.formatBytes(1536))
        assertEquals("1.0 ميجابايت", Formatters.formatBytes(1024L * 1024L))
        assertEquals("2.50 جيجابايت", Formatters.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formats durations`() {
        assertEquals("00:00", Formatters.formatDuration(0))
        assertEquals("01:05", Formatters.formatDuration(65_000))
        assertEquals("1:01:01", Formatters.formatDuration(3_661_000))
    }

    @Test
    fun `formats percentages`() {
        assertEquals("42.3%", Formatters.formatPercent(42.34, 1))
        assertEquals("0%", Formatters.formatPercent(-5.0))
        assertEquals("100%", Formatters.formatPercent(120.0))
    }

    @Test
    fun `eta falls back to a dash when unknown`() {
        assertEquals(Formatters.UNKNOWN, Formatters.formatEta(null))
        assertEquals(Formatters.UNKNOWN, Formatters.formatEta(0))
        assertEquals("02:00", Formatters.formatEta(120_000))
    }

    @Test
    fun `date contains an arabic month`() {
        val text = Formatters.formatDate(System.currentTimeMillis())
        assertTrue(text, text.any { it.isDigit() })
        assertTrue(
            text,
            listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
                .any { month -> text.contains(month) },
        )
    }
}
