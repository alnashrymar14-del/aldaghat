package com.abbas.aldaghat.domain

/**
 * The compression presets offered in the UI.
 *
 * [targetShortSide] always refers to the *shorter* edge of the video, which makes the preset
 * behave identically for landscape and portrait recordings (a 1080x1920 portrait clip at 720p
 * becomes 720x1280, exactly like a 1920x1080 landscape clip).
 *
 * Bitrate ranges are expressed in kbps for H.264 at ~30 fps and are the single source of truth
 * used by both the estimator and the encoder settings. They are intentionally centralised here:
 * adjust them in one place and the whole app (estimates, plan, encoder) follows.
 */
enum class QualityPreset(
    val id: String,
    val label: String,
    val description: String,
    val targetShortSide: Int,
    val minVideoBitrateKbps: Int,
    val maxVideoBitrateKbps: Int,
    /** Bits per pixel per frame, used to derive a bitrate for the real resolution/frame rate. */
    val bitsPerPixelPerFrame: Double,
) {
    P1080(
        id = "1080p",
        label = "1080p",
        description = "جودة عالية",
        targetShortSide = 1080,
        minVideoBitrateKbps = 4000,
        maxVideoBitrateKbps = 6000,
        bitsPerPixelPerFrame = 0.09,
    ),
    P720(
        id = "720p",
        label = "720p",
        description = "جودة متوسطة مرتفعة",
        targetShortSide = 720,
        minVideoBitrateKbps = 2000,
        maxVideoBitrateKbps = 3000,
        bitsPerPixelPerFrame = 0.10,
    ),
    P480(
        id = "480p",
        label = "480p",
        description = "جودة متوسطة",
        targetShortSide = 480,
        minVideoBitrateKbps = 800,
        maxVideoBitrateKbps = 1500,
        bitsPerPixelPerFrame = 0.11,
    ),
    P360(
        id = "360p",
        label = "360p",
        description = "حجم صغير",
        targetShortSide = 360,
        minVideoBitrateKbps = 400,
        maxVideoBitrateKbps = 800,
        bitsPerPixelPerFrame = 0.13,
    ),
    CUSTOM(
        id = "custom",
        label = "حجم مخصص",
        description = "تحديد معدل البت يدويًا",
        targetShortSide = 720,
        minVideoBitrateKbps = BitrateSelector.ABSOLUTE_MIN_VIDEO_KBPS,
        maxVideoBitrateKbps = BitrateSelector.CUSTOM_MAX_VIDEO_KBPS,
        bitsPerPixelPerFrame = 0.10,
    ),
    ;

    val isCustom: Boolean get() = this == CUSTOM

    companion object {
        fun fromId(id: String?): QualityPreset =
            entries.firstOrNull { it.id == id } ?: P720
    }
}

/** Video codec choices exposed to the user. HEVC is only offered when an encoder exists. */
enum class VideoCodec(val mimeType: String, val label: String) {
    H264("video/avc", "H.264"),
    HEVC("video/hevc", "H.265"),
}
