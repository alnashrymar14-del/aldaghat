package com.abbas.aldaghat.data

import com.abbas.aldaghat.data.model.HistoryEntry
import com.abbas.aldaghat.domain.HistoryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Repository over the SQLite log. Exposes an observable list for Compose. */
class HistoryRepository(private val database: HistoryDatabase) {

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _entries.value = runCatching { database.queryAll() }.getOrDefault(emptyList())
    }

    suspend fun startJob(
        sourceName: String,
        sourceUri: String,
        originalBytes: Long,
        quality: String,
        codec: String,
        width: Int,
        height: Int,
        thumbPath: String?,
    ): Long = withContext(Dispatchers.IO) {
        val id = database.insertRunning(
            sourceName = sourceName,
            sourceUri = sourceUri,
            originalBytes = originalBytes,
            quality = quality,
            codec = codec,
            width = width,
            height = height,
            thumbPath = thumbPath,
        )
        refreshInternal()
        id
    }

    suspend fun completeJob(
        id: Long,
        outputName: String,
        outputUri: String,
        outputBytes: Long,
        width: Int,
        height: Int,
        thumbPath: String?,
    ) = withContext(Dispatchers.IO) {
        database.complete(id, outputName, outputUri, outputBytes, width, height, thumbPath)
        refreshInternal()
    }

    suspend fun markStatus(id: Long, status: HistoryStatus, error: String? = null) =
        withContext(Dispatchers.IO) {
            database.updateStatus(id, status, error)
            refreshInternal()
        }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        database.delete(id)
        refreshInternal()
    }

    /** Called on cold start: any row still marked RUNNING belongs to a dead process. */
    suspend fun markInterrupted(): Int = withContext(Dispatchers.IO) {
        val updated = database.markAllRunningAsInterrupted()
        refreshInternal()
        updated
    }

    private fun refreshInternal() {
        _entries.value = runCatching { database.queryAll() }.getOrDefault(emptyList())
    }
}
