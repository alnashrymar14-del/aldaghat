package com.abbas.aldaghat.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.abbas.aldaghat.data.MediaStorePublisher

/** External intents: open the produced video, share it, or try to open the ABBAS folder. */
object VideoIntents {

    fun open(context: Context, uri: Uri, mimeType: String = MediaStorePublisher.MIME_TYPE): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(context, intent)
    }

    fun share(context: Context, uri: Uri, title: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MediaStorePublisher.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, chooser)
    }

    /**
     * Attempts to open `Movies/ABBAS` in an external document browser. Many devices have no
     * activity that accepts a directory URI, so callers must fall back to the in-app folder view.
     */
    fun openAbbasFolder(context: Context): Boolean {
        val documentUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3AMovies%2F" +
                Uri.encode(MediaStorePublisher.FOLDER_NAME),
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentUri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    } catch (error: SecurityException) {
        false
    }
}
