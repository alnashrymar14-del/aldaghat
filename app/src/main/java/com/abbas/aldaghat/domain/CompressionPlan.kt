package com.abbas.aldaghat.domain

/** A concrete, device-compatible compression plan derived from a preset and the source video. */
data class CompressionPlan(
    val preset: QualityPreset,
    val outputWidth: Int,
    val outputHeight: Int,
    val scaled: Boolean,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
    val frameRate: Int,
    val codec: VideoCodec,
    val hasAudio: Boolean,
) {
    fun estimatedMegabytes(durationMs: Long): Double =
        SizeEstimator.estimateMegabytes(durationMs, videoBitrateKbps, audioBitrateKbps)

    val qualityLabel: String
        get() = if (preset.isCustom) "${preset.label} • $videoBitrateKbps kbps" else preset.label
}

/**
 * Builds a plan that never upscales, always keeps the aspect ratio, always produces even
 * dimensions (a hard requirement of hardware encoders) and degrades the resolution when the
 * device encoder cannot handle the requested size.
 */
object CompressionPlanner {
    private const val MIN_SHORT_SIDE = 144

    fun plan(
        video: VideoInfo,
        preset: QualityPreset,
        codec: VideoCodec,
        manualVideoBitrateKbps: Int?,
        audioBitrateKbps: Int,
        isSizeSupported: (width: Int, height: Int) -> Boolean,
    ): CompressionPlan? {
        if (!video.isValid) return null

        val sourceShort = video.shortSide.coerceAtLeast(2)
        val requestedShort = minOf(preset.targetShortSide, sourceShort)

        var targetShort = requestedShort
        var dimensions: IntArray? = null

        while (targetShort >= MIN_SHORT_SIDE) {
            val candidate = scaledDimensions(video.displayWidth, video.displayHeight, targetShort)
            if (candidate != null && isSizeSupported(candidate[0], candidate[1])) {
                dimensions = candidate
                break
            }
            targetShort = (targetShort * 0.85f).toInt() / 2 * 2
        }

        if (dimensions == null) return null

        val scaled = dimensions[0] != video.displayWidth || dimensions[1] != video.displayHeight
        val videoKbps = BitrateSelector.videoBitrateKbps(
            preset = preset,
            outputWidth = dimensions[0],
            outputHeight = dimensions[1],
            sourceFrameRate = video.effectiveFrameRate,
            sourceVideoBitrateKbps = video.videoBitrateKbps,
            manualBitrateKbps = manualVideoBitrateKbps,
        )

        return CompressionPlan(
            preset = preset,
            outputWidth = dimensions[0],
            outputHeight = dimensions[1],
            scaled = scaled,
            videoBitrateKbps = videoKbps,
            audioBitrateKbps = BitrateSelector.audioBitrateKbps(video.hasAudio, audioBitrateKbps),
            frameRate = video.effectiveFrameRate.toInt().coerceIn(15, 60),
            codec = codec,
            hasAudio = video.hasAudio,
        )
    }

    /**
     * Scales [width]x[height] so that the shorter edge becomes [targetShort], preserving the
     * aspect ratio and returning even dimensions (rounded down, min 2).
     */
    fun scaledDimensions(width: Int, height: Int, targetShort: Int): IntArray? {
        if (width <= 0 || height <= 0) return null
        val short = minOf(width, height)
        val long = maxOf(width, height)
        if (targetShort >= short) {
            val evenWidth = evenFloor(width)
            val evenHeight = evenFloor(height)
            if (evenWidth < 2 || evenHeight < 2) return null
            return intArrayOf(evenWidth, evenHeight)
        }
        val scale = targetShort.toDouble() / short.toDouble()
        val newShort = evenFloor(targetShort)
        val newLong = evenFloor((long * scale).toInt())
        if (newShort < 2 || newLong < 2) return null
        return if (height >= width) {
            intArrayOf(newShort, newLong) // portrait: width is the short edge
        } else {
            intArrayOf(newLong, newShort) // landscape or square: height is the short edge
        }
    }

    private fun evenFloor(value: Int): Int = (value / 2) * 2
}
