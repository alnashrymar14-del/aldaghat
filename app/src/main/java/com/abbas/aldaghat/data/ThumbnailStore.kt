package com.abbas.aldaghat.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Creates and caches small JPEG thumbnails for the picker and the history list. */
class ThumbnailStore(private val context: Context) {

    private val thumbnailDirectory: File
        get() = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }

    fun pathFor(key: String): String = File(thumbnailDirectory, "$key.jpg").absolutePath

    suspend fun createThumbnail(videoUri: Uri, key: String): String? = withContext(Dispatchers.IO) {
        val target = File(thumbnailDirectory, "$key.jpg")
        if (target.exists() && target.length() > 0) return@withContext target.absolutePath

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val frame = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        THUMBNAIL_WIDTH,
                        THUMBNAIL_HEIGHT,
                    )
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull() ?: return@withContext null

            val scaled = if (frame.width > THUMBNAIL_WIDTH) {
                val ratio = THUMBNAIL_WIDTH.toFloat() / frame.width
                Bitmap.createScaledBitmap(
                    frame,
                    THUMBNAIL_WIDTH,
                    (frame.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                frame
            }

            target.outputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
            target.absolutePath
        } catch (error: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun load(path: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 480
        const val THUMBNAIL_HEIGHT = 270
    }
}
