package com.abbas.aldaghat.domain

/**
 * Size estimation.
 *
 * The documented formula is used verbatim:
 *   megabytes = duration_seconds * (video_kbps + audio_kbps) / 8000
 * The result is always presented as an estimate, never as a guaranteed size.
 */
object SizeEstimator {
    fun estimateMegabytes(durationMs: Long, videoBitrateKbps: Int, audioBitrateKbps: Int): Double {
        if (durationMs <= 0L) return 0.0
        val seconds = durationMs / 1000.0
        val totalKbps = (videoBitrateKbps.coerceAtLeast(0) + audioBitrateKbps.coerceAtLeast(0)).toDouble()
        return seconds * totalKbps / 8000.0
    }

    fun estimateBytes(durationMs: Long, videoBitrateKbps: Int, audioBitrateKbps: Int): Long =
        (estimateMegabytes(durationMs, videoBitrateKbps, audioBitrateKbps) * 1024.0 * 1024.0).toLong()

    /** Positive when the estimate is smaller than the source; 0 when it would grow. */
    fun savedBytes(originalBytes: Long, estimatedBytes: Long): Long =
        (originalBytes - estimatedBytes).coerceAtLeast(0L)

    /** Reduction percentage (0..100), clamped to 0 when no reduction is expected. */
    fun reductionPercent(originalBytes: Long, resultBytes: Long): Double {
        if (originalBytes <= 0L) return 0.0
        val reduction = (originalBytes - resultBytes).toDouble() / originalBytes.toDouble() * 100.0
        return reduction.coerceIn(0.0, 100.0)
    }
}
