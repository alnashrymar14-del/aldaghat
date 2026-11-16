package com.abbas.aldaghat.data.model

import com.abbas.aldaghat.domain.HistoryStatus

/** One row of the local (on-device) compression history. */
data class HistoryEntry(
    val id: Long,
    val sourceName: String,
    val sourceUri: String,
    val outputName: String?,
    val outputUri: String?,
    val originalBytes: Long,
    val outputBytes: Long,
    val width: Int,
    val height: Int,
    val quality: String,
    val codec: String,
    val status: HistoryStatus,
    val createdAt: Long,
    val error: String?,
    val thumbPath: String?,
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)

    val savingPercent: Double
        get() = if (originalBytes <= 0L || outputBytes <= 0L) 0.0
        else (((originalBytes - outputBytes).toDouble() / originalBytes) * 100.0).coerceIn(0.0, 100.0)
}
