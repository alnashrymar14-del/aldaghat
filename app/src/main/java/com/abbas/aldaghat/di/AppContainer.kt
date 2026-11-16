package com.abbas.aldaghat.di

import android.content.Context
import com.abbas.aldaghat.data.EncoderCapabilities
import com.abbas.aldaghat.data.HistoryDatabase
import com.abbas.aldaghat.data.HistoryRepository
import com.abbas.aldaghat.data.MediaStorePublisher
import com.abbas.aldaghat.data.ThumbnailStore
import com.abbas.aldaghat.data.VideoInspector
import com.abbas.aldaghat.service.CompressionJobController
import com.abbas.aldaghat.service.CompressionNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hand-rolled dependency container. Every dependency is app-scoped and created once, which keeps
 * the foreground service and the UI looking at exactly the same state.
 */
class AppContainer(private val context: Context) {

    val appContext: Context get() = context

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val historyDatabase = HistoryDatabase(context)
    val historyRepository = HistoryRepository(historyDatabase)
    val videoInspector = VideoInspector(context)
    val encoderCapabilities = EncoderCapabilities()
    val publisher = MediaStorePublisher(context)
    val thumbnailStore = ThumbnailStore(context)
    val jobController = CompressionJobController(context)

    fun bootstrap() {
        CompressionNotifications(context).ensureChannels()
        applicationScope.launch {
            withContext(Dispatchers.IO) {
                // Temp files from a process that died mid-export are never reusable.
                File(context.cacheDir, "compression").listFiles()?.forEach { it.delete() }
                historyRepository.markInterrupted()
            }
            historyRepository.refresh()
        }
    }
}
