package com.abbas.aldaghat.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeEstimatorTest {

    @Test
    fun `uses the documented megabytes formula`() {
        // 60 s at 2500 kbps video + 128 kbps audio => 60 * 2628 / 8000
        val megabytes = SizeEstimator.estimateMegabytes(
            durationMs = 60_000L,
            videoBitrateKbps = 2500,
            audioBitrateKbps = 128,
        )
        assertEquals(19.71, megabytes, 0.001)
    }

    @Test
    fun `returns zero for unknown duration`() {
        assertEquals(0.0, SizeEstimator.estimateMegabytes(0L, 3000, 128), 0.0)
        assertEquals(0L, SizeEstimator.estimateBytes(-5L, 3000, 128))
    }

    @Test
    fun `bytes follow megabytes`() {
        val megabytes = SizeEstimator.estimateMegabytes(10_000L, 2000, 128)
        val bytes = SizeEstimator.estimateBytes(10_000L, 2000, 128)
        assertEquals((megabytes * 1024.0 * 1024.0).toLong(), bytes)
    }

    @Test
    fun `saved bytes never go negative`() {
        assertEquals(0L, SizeEstimator.savedBytes(originalBytes = 1_000L, estimatedBytes = 2_000L))
        assertEquals(500L, SizeEstimator.savedBytes(originalBytes = 1_500L, estimatedBytes = 1_000L))
    }

    @Test
    fun `reduction percent is clamped between 0 and 100`() {
        assertEquals(50.0, SizeEstimator.reductionPercent(1000L, 500L), 0.001)
        assertEquals(0.0, SizeEstimator.reductionPercent(500L, 1000L), 0.001)
        assertEquals(100.0, SizeEstimator.reductionPercent(1000L, 0L), 0.001)
        assertEquals(0.0, SizeEstimator.reductionPercent(0L, 0L), 0.001)
    }
}
