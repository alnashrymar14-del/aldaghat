package com.abbas.aldaghat.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.abbas.aldaghat.domain.VideoInfo
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.withContext

/** Reads metadata for a content:// video. Never assumes a filesystem path exists. */
class VideoInspector(private val context: Context) {

    suspend fun inspect(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            inspectBlocking(uri)
        } catch (error: Throwable) {
            android.util.Log.w(TAG, "could not inspect $uri", error)
            null
        }
    }

    private fun inspectBlocking(uri: Uri): VideoInfo? {
        val resolver = context.contentResolver
        var displayName = "video.mp4"
        var sizeBytes = 0L

        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
        }.getOrNull()?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    val queried = cursor.getString(nameIndex)
                    if (!queried.isNullOrBlank()) displayName = queried
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        if (sizeBytes <= 0L) {
            sizeBytes = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.length ?: 0L }
                .getOrDefault(0L)
        }
        if (sizeBytes <= 0L && uri.scheme == "file") {
            sizeBytes = runCatching { File(uri.path.orEmpty()).length() }.getOrDefault(0L)
        }

        var width = 0
        var height = 0
        var rotation = 0
        var frameRate = 0f
        var videoKbps = 0
        var audioKbps = 0
        var hasAudio = false
        var durationMs = 0L
        var mimeType: String? = null

        val extractor = MediaExtractor()
        try {
            val localPath = uri.path
            if (uri.scheme == "file" && !localPath.isNullOrBlank()) {
                extractor.setDataSource(localPath)
            } else {
                val descriptor = runCatching { resolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
                    ?: return null
                descriptor.use {
                    if (it.declaredLength > 0L) {
                        extractor.setDataSource(it.fileDescriptor, it.startOffset, it.declaredLength)
                    } else {
                        extractor.setDataSource(it.fileDescriptor)
                    }
                }
            }

            var videoTrackIndex = -1
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = index
                    mimeType = mime
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    } else if (format.containsKey("rotation-degrees")) {
                        rotation = format.getInteger("rotation-degrees")
                    }
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) }
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        videoKbps = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = maxOf(durationMs, format.getLong(MediaFormat.KEY_DURATION) / 1000)
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioKbps = maxOf(audioKbps, format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000)
                    }
                }
            }
            if (videoTrackIndex < 0) return null
        } finally {
            runCatching { extractor.release() }
        }

        // Rotation, duration and audio presence are also available from the retriever and are more
        // reliable on some vendors when the extractor reports raw dimensions only.
        val retriever = MediaMetadataRetriever()
        try {
            val localPath = uri.path
            if (uri.scheme == "file" && !localPath.isNullOrBlank()) {
                retriever.setDataSource(localPath)
            } else {
                retriever.setDataSource(context, uri)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.let { rotation = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { if (it > 0) durationMs = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.let { hasAudio = it.equals("yes", ignoreCase = true) }
            if (width <= 0) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()?.let { width = it }
            }
            if (height <= 0) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()?.let { height = it }
            }
            if (mimeType == null) {
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            }
        } catch (ignored: Throwable) {
            // Fall back to the values gathered from the extractor.
        } finally {
            runCatching { retriever.release() }
        }

        if (width <= 0 || height <= 0) return null

        return VideoInfo(
            uri = uri.toString(),
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            rotationDegrees = ((rotation % 360) + 360) % 360,
            frameRate = frameRate,
            videoBitrateKbps = videoKbps,
            audioBitrateKbps = audioKbps,
            hasAudio = hasAudio,
            mimeType = mimeType,
        )
    }

    private companion object {
        const val TAG = "VideoInspector"
    }

    /** Best-effort lookup of a MediaStore video id, used by the folder screen. */
    fun mediaStoreId(uri: Uri): Long? = runCatching {
        if (uri.authority?.contains(MediaStore.AUTHORITY) == true) ContentUris.parseId(uri) else null
    }.getOrNull()
}
