package com.abbas.aldaghat.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.abbas.aldaghat.domain.OutputNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Publishes the finished temp file into `Movies/ABBAS` using MediaStore + scoped storage.
 *
 * - API 29+: insert with `IS_PENDING=1`, stream the bytes, then clear the pending flag. The row is
 *   removed again if anything fails, so a half-written file never becomes visible.
 * - API 23-28: MediaStore has no RELATIVE_PATH, so the file is written into the public Movies
 *   directory directly and then indexed with MediaScannerConnection.
 *
 * MANAGE_EXTERNAL_STORAGE is never requested.
 */
class MediaStorePublisher(private val context: Context) {

    fun abbasRelativePath(): String = "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME"

    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        return try {
            val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.cacheDir
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }
            val stat = StatFs(target.absolutePath)
            val available = stat.availableBytes
            // The pipeline needs the temp file plus the final copy.
            available > requiredBytes * 2 + MIN_HEADROOM_BYTES
        } catch (error: Throwable) {
            true
        }
    }

    fun availableBytes(): Long = try {
        StatFs(context.cacheDir.absolutePath).availableBytes
    } catch (error: Throwable) {
        -1L
    }

    /** Finds a free name inside ABBAS, appending " (n)" until it is unique. */
    fun uniqueDisplayName(base: String): String {
        var index = 0
        while (index < 1000) {
            val candidate = OutputNaming.fileName(base, index)
            if (!nameExists(candidate)) return candidate
            index++
        }
        return OutputNaming.fileName("$base-${System.currentTimeMillis()}", 0)
    }

    suspend fun publish(tempFile: File, baseName: String): PublishedFile =
        withContext(Dispatchers.IO) {
            require(tempFile.exists()) { "temp file missing" }
            val displayName = uniqueDisplayName(baseName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishScoped(tempFile, displayName)
            } else {
                publishLegacy(tempFile, displayName)
            }
        }

    private fun publishScoped(tempFile: File, displayName: String): PublishedFile {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Video.Media.RELATIVE_PATH, abbasRelativePath())
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed")

        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("MediaStore openOutputStream failed")
            output.use { stream ->
                tempFile.inputStream().use { input -> input.copyTo(stream, DEFAULT_BUFFER_SIZE) }
            }

            val size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
                .getOrNull()
                ?.takeIf { it > 0L }
                ?: tempFile.length()

            val finish = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
                put(MediaStore.Video.Media.SIZE, size)
            }
            resolver.update(uri, finish, null, null)

            return PublishedFile(uri = uri, displayName = displayName, sizeBytes = size)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private suspend fun publishLegacy(tempFile: File, displayName: String): PublishedFile {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            FOLDER_NAME,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("cannot create ${directory.absolutePath}")
        }
        val target = File(directory, displayName)
        tempFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        val uri = scanFile(target) ?: Uri.fromFile(target)
        return PublishedFile(uri = uri, displayName = displayName, sizeBytes = target.length())
    }

    private suspend fun scanFile(file: File): Uri? = suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(MIME_TYPE),
        ) { _, uri ->
            if (continuation.isActive) continuation.resume(uri)
        }
    }

    private fun nameExists(displayName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf(abbasRelativePath() + "/", displayName),
                null,
            )?.use { cursor -> if (cursor.count > 0) return true }
            return false
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            FOLDER_NAME,
        )
        return File(directory, displayName).exists()
    }

    fun listAbbasVideos(): List<SavedVideo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listScoped()
        } else {
            listLegacy()
        }
    }

    private fun listScoped(): List<SavedVideo> {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
        )
        val result = mutableListOf<SavedVideo>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("${abbasRelativePath()}%"),
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                result += SavedVideo(
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(1) ?: "video.mp4",
                    sizeBytes = cursor.getLong(2),
                    dateAddedMs = cursor.getLong(3) * 1000L,
                    durationMs = cursor.getLong(4),
                )
            }
        }
        return result
    }

    private fun listLegacy(): List<SavedVideo> {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            FOLDER_NAME,
        )
        val files = directory.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            SavedVideo(
                uri = Uri.fromFile(file),
                displayName = file.name,
                sizeBytes = file.length(),
                dateAddedMs = file.lastModified(),
                durationMs = 0L,
            )
        }
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (error: Throwable) {
            if (uri.scheme == "file") {
                runCatching { uri.path?.let { File(it).delete() } }.getOrNull() ?: false
            } else {
                false
            }
        }
    }

    data class PublishedFile(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
    )

    data class SavedVideo(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val dateAddedMs: Long,
        val durationMs: Long,
    )

    companion object {
        const val FOLDER_NAME = "ABBAS"
        const val MIME_TYPE = "video/mp4"
        private const val MIN_HEADROOM_BYTES = 64L * 1024L * 1024L
    }
}
