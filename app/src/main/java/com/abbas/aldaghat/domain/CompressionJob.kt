package com.abbas.aldaghat.domain

/** Everything the foreground service needs in order to run one export. */
data class CompressionJobSpec(
    val sourceUri: String,
    val displayName: String,
    val sourceSizeBytes: Long,
    val durationMs: Long,
    val outputWidth: Int,
    val outputHeight: Int,
    val scaled: Boolean,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
    val frameRate: Int,
    val videoMimeType: String,
    val hasAudio: Boolean,
    val presetLabel: String,
    val sourceVideoBitrateKbps: Int,
) {
    val estimatedOutputBytes: Long
        get() = SizeEstimator.estimateBytes(durationMs, videoBitrateKbps, audioBitrateKbps)
}

/** Terminal/intermediate states surfaced to the UI and the notification. */
sealed interface JobState {
    data object Idle : JobState

    data class Preparing(val inputName: String) : JobState

    data class Running(
        val inputName: String,
        val progressPercent: Int,
        val elapsedMs: Long,
        val etaMs: Long?,
    ) : JobState

    data class Saving(val inputName: String, val elapsedMs: Long) : JobState

    data class Success(
        val inputName: String,
        val result: CompressionResult,
    ) : JobState

    data class Failed(val inputName: String, val message: String) : JobState

    data class Cancelled(val inputName: String) : JobState
}

/** Facts about a finished export, with the real output size (never an estimate). */
data class CompressionResult(
    val outputName: String,
    val outputUri: String,
    val sourceBytes: Long,
    val outputBytes: Long,
    val elapsedMs: Long,
    val presetLabel: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
) {
    val savedBytes: Long
        get() = (sourceBytes - outputBytes).coerceAtLeast(0L)

    val savingPercent: Double
        get() = SizeEstimator.reductionPercent(sourceBytes, outputBytes)
}

enum class HistoryStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ;

    companion object {
        fun fromStorage(value: String?): HistoryStatus =
            entries.firstOrNull { it.name == value } ?: INTERRUPTED
    }
}
