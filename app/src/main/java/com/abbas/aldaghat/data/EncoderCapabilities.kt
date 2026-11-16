package com.abbas.aldaghat.data

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Queries the platform for hardware/software encoders so that the app never asks Media3 for a
 * codec the device cannot instantiate. This is what makes HEVC optional and resolution selection
 * device-aware.
 */
class EncoderCapabilities {

    private val codecList: MediaCodecList by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    fun hasEncoder(mimeType: String): Boolean = findEncoder(mimeType, 1280, 720) != null

    fun supportsSize(mimeType: String, width: Int, height: Int): Boolean =
        findEncoder(mimeType, width, height) != null

    fun isHevcEncoderAvailable(): Boolean = hasEncoder(MIME_HEVC)

    fun encoderName(mimeType: String, width: Int, height: Int): String? =
        findEncoder(mimeType, width, height)?.name

    private fun findEncoder(mimeType: String, width: Int, height: Int): MediaCodecInfo? {
        if (width <= 0 || height <= 0) return null
        return codecList.codecInfos
            .asSequence()
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(mimeType, true) } }
            .firstOrNull { info -> supports(info, mimeType, width, height) }
    }

    private fun supports(
        info: MediaCodecInfo,
        mimeType: String,
        width: Int,
        height: Int,
    ): Boolean = try {
        val videoCapabilities = info.getCapabilitiesForType(mimeType).videoCapabilities
            ?: return false
        val widthAlignment = videoCapabilities.widthAlignment.coerceAtLeast(1)
        val heightAlignment = videoCapabilities.heightAlignment.coerceAtLeast(1)
        val alignedWidth = ((width + widthAlignment - 1) / widthAlignment) * widthAlignment
        val alignedHeight = ((height + heightAlignment - 1) / heightAlignment) * heightAlignment
        videoCapabilities.isSizeSupported(alignedWidth, alignedHeight)
    } catch (error: Throwable) {
        false
    }

    companion object {
        const val MIME_H264 = "video/avc"
        const val MIME_HEVC = "video/hevc"
    }
}
