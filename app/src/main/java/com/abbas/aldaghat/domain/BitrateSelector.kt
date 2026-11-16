package com.abbas.aldaghat.domain

/**
 * Chooses encoder bitrates.
 *
 * Video: a bits-per-pixel-per-frame model derived from the actual output resolution and the
 * source frame rate, clamped to the preset's documented kbps range. It is additionally capped
 * below the source bitrate so that re-encoding a already heavily compressed clip cannot grow it.
 * Audio: fixed AAC bitrate from the user's choice (128 kbps by default).
 */
object BitrateSelector {
    const val DEFAULT_AUDIO_KBPS = 128
    const val MIN_AUDIO_KBPS = 48
    const val MAX_AUDIO_KBPS = 320
    const val ABSOLUTE_MIN_VIDEO_KBPS = 150
    const val CUSTOM_MAX_VIDEO_KBPS = 20000
    const val ABSOLUTE_MAX_VIDEO_KBPS = 50000

    private const val MIN_EFFECTIVE_FPS = 24f
    private const val MAX_EFFECTIVE_FPS = 60f

    /** Never ask for more than ~92% of the source bitrate: anything else can increase the size. */
    private const val SOURCE_BITRATE_CAP = 0.92

    fun audioBitrateKbps(hasAudio: Boolean, requestedKbps: Int): Int {
        if (!hasAudio) return 0
        return requestedKbps.coerceIn(MIN_AUDIO_KBPS, MAX_AUDIO_KBPS)
    }

    fun videoBitrateKbps(
        preset: QualityPreset,
        outputWidth: Int,
        outputHeight: Int,
        sourceFrameRate: Float,
        sourceVideoBitrateKbps: Int,
        manualBitrateKbps: Int? = null,
    ): Int {
        val fps = effectiveFrameRate(sourceFrameRate)
        val pixels = outputWidth.toLong().coerceAtLeast(1L) * outputHeight.toLong().coerceAtLeast(1L)

        val computed = if (preset.isCustom && manualBitrateKbps != null) {
            manualBitrateKbps
        } else {
            val raw = pixels * fps * preset.bitsPerPixelPerFrame / 1000.0
            raw.toInt().coerceIn(preset.minVideoBitrateKbps, preset.maxVideoBitrateKbps)
        }

        val cappedBySource = if (sourceVideoBitrateKbps > 0) {
            minOf(computed, (sourceVideoBitrateKbps * SOURCE_BITRATE_CAP).toInt())
        } else {
            computed
        }

        return cappedBySource.coerceIn(ABSOLUTE_MIN_VIDEO_KBPS, ABSOLUTE_MAX_VIDEO_KBPS)
    }

    fun effectiveFrameRate(sourceFrameRate: Float): Float {
        if (!sourceFrameRate.isFinite() || sourceFrameRate <= 0f) return 30f
        return sourceFrameRate.coerceIn(MIN_EFFECTIVE_FPS, MAX_EFFECTIVE_FPS)
    }
}
