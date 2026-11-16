package com.abbas.aldaghat.domain

/**
 * Metadata for the selected source video.
 *
 * [width]/[height] are the raw decoded dimensions; [rotationDegrees] is the container rotation
 * metadata, and [displayWidth]/[displayHeight] apply it. Never assume a file path: everything is
 * addressed through a content URI string.
 */
data class VideoInfo(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val frameRate: Float = 30f,
    val videoBitrateKbps: Int = 0,
    val audioBitrateKbps: Int = 0,
    val hasAudio: Boolean = false,
    val mimeType: String? = null,
) {
    private val isRotatedQuarterTurn: Boolean
        get() = rotationDegrees == 90 || rotationDegrees == 270

    val displayWidth: Int
        get() = if (isRotatedQuarterTurn) height else width

    val displayHeight: Int
        get() = if (isRotatedQuarterTurn) width else height

    val isPortrait: Boolean
        get() = displayHeight > displayWidth

    val shortSide: Int
        get() = minOf(displayWidth, displayHeight)

    val longSide: Int
        get() = maxOf(displayWidth, displayHeight)

    val aspectRatio: Float
        get() = if (displayHeight <= 0) 0f else displayWidth.toFloat() / displayHeight

    val effectiveFrameRate: Float
        get() = if (frameRate.isFinite() && frameRate in 1f..240f) frameRate else 30f

    val isValid: Boolean
        get() = width > 0 && height > 0 && durationMs > 0
}
