package com.abbas.aldaghat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.media3.transformer.ExportException
import com.abbas.aldaghat.AlDaghatApplication
import com.abbas.aldaghat.R
import com.abbas.aldaghat.di.AppContainer
import com.abbas.aldaghat.domain.CompressionJobSpec
import com.abbas.aldaghat.domain.CompressionResult
import com.abbas.aldaghat.domain.HistoryStatus
import com.abbas.aldaghat.domain.OutputNaming
import com.abbas.aldaghat.media.CompressionOutcome
import com.abbas.aldaghat.media.Media3CompressionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs exactly one export at a time as a foreground service, so processing survives the user
 * leaving the app. Progress, elapsed time and the ETA (derived from the measured processing
 * speed, not a guess) are pushed to both the UI state and the notification.
 */
class CompressionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private lateinit var engine: Media3CompressionEngine
    private lateinit var notifications: CompressionNotifications

    private var job: Job? = null
    private var currentSpec: CompressionJobSpec? = null
    private var startedAtElapsed: Long = 0L
    private var cancelRequested: Boolean = false
    private var lastNotifiedPercent: Int = -1
    private var lastNotifiedAtElapsed: Long = 0L

    override fun onCreate() {
        super.onCreate()
        container = (application as AlDaghatApplication).container
        engine = Media3CompressionEngine(this)
        notifications = CompressionNotifications(this)
        notifications.ensureChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                requestCancel()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val spec = intent.toSpec()
                if (spec == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (job?.isActive == true) {
                    // A second export is refused; the controller already reports "busy".
                    return START_NOT_STICKY
                }
                startForegroundProgress(spec)
                startJob(spec)
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundProgress(spec: CompressionJobSpec) {
        val notification = notifications.progressNotification(
            spec = spec,
            percent = 0,
            etaMs = null,
            elapsedMs = 0L,
        )
        // Use the complete FGS type declared for this service in AndroidManifest.xml.
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        ServiceCompat.startForeground(
            this,
            CompressionNotifications.PROGRESS_NOTIFICATION_ID,
            notification,
            type,
        )
    }

    private fun startJob(spec: CompressionJobSpec) {
        currentSpec = spec
        startedAtElapsed = SystemClock.elapsedRealtime()
        cancelRequested = false
        lastNotifiedPercent = -1
        lastNotifiedAtElapsed = 0L
        container.jobController.onPreparing(spec.displayName)

        job = serviceScope.launch {
            val tempDirectory = File(cacheDir, TEMP_DIRECTORY).apply { mkdirs() }
            val tempFile = File(tempDirectory, "temp_${System.currentTimeMillis()}.mp4")
            var historyId = -1L
            try {
                if (!container.publisher.hasEnoughSpace(spec.estimatedOutputBytes)) {
                    throw NotEnoughSpaceException()
                }

                historyId = container.historyRepository.startJob(
                    sourceName = spec.displayName,
                    sourceUri = spec.sourceUri,
                    originalBytes = spec.sourceSizeBytes,
                    quality = spec.presetLabel,
                    codec = if (spec.videoMimeType == MIME_HEVC) "H.265" else "H.264",
                    width = spec.outputWidth,
                    height = spec.outputHeight,
                    thumbPath = null,
                )

                val outcome = engine.compress(spec, tempFile) { percent ->
                    onProgress(spec, percent)
                }

                when (outcome) {
                    is CompressionOutcome.Success -> handleSuccess(spec, tempFile, historyId)
                    is CompressionOutcome.Failure -> {
                        if (cancelRequested) {
                            throw CancellationException("cancelled")
                        }
                        throw CompressionFailedException(outcome.message)
                    }

                    CompressionOutcome.Cancelled -> throw CancellationException("cancelled")
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    handleFailure(spec, tempFile, historyId, error)
                }
            } finally {
                tempFile.delete()
                withContext(NonCancellable) { finishService() }
            }
        }
    }

    private fun onProgress(spec: CompressionJobSpec, percent: Int) {
        val elapsed = SystemClock.elapsedRealtime() - startedAtElapsed
        val eta = if (percent in 1..99 && elapsed > 1500L) {
            (elapsed.toDouble() / percent.toDouble() * (100 - percent).toDouble()).toLong()
        } else {
            null
        }
        container.jobController.onProgress(percent, elapsed, eta)

        val now = SystemClock.elapsedRealtime()
        val shouldNotify = percent != lastNotifiedPercent &&
            (percent - lastNotifiedPercent >= NOTIFICATION_PROGRESS_STEP ||
                now - lastNotifiedAtElapsed >= NOTIFICATION_MIN_INTERVAL_MS ||
                percent >= 100)
        if (shouldNotify) {
            lastNotifiedPercent = percent
            lastNotifiedAtElapsed = now
            notifications.show(
                CompressionNotifications.PROGRESS_NOTIFICATION_ID,
                notifications.progressNotification(spec, percent, eta, elapsed),
            )
        }
    }

    private suspend fun handleSuccess(
        spec: CompressionJobSpec,
        tempFile: File,
        historyId: Long,
    ) {
        container.jobController.onSaving()
        val elapsedSoFar = SystemClock.elapsedRealtime() - startedAtElapsed
        notifications.show(
            CompressionNotifications.PROGRESS_NOTIFICATION_ID,
            notifications.progressNotification(spec, 100, 0L, elapsedSoFar, saving = true),
        )

        val baseName = OutputNaming.compressedBaseName(spec.displayName)
        val published = container.publisher.publish(tempFile, baseName)

        val elapsed = SystemClock.elapsedRealtime() - startedAtElapsed
        val thumbnailPath = container.thumbnailStore.createThumbnail(
            published.uri,
            "job_$historyId",
        )

        val result = CompressionResult(
            outputName = published.displayName,
            outputUri = published.uri.toString(),
            sourceBytes = spec.sourceSizeBytes,
            outputBytes = published.sizeBytes,
            elapsedMs = elapsed,
            presetLabel = spec.presetLabel,
            outputWidth = spec.outputWidth,
            outputHeight = spec.outputHeight,
            videoBitrateKbps = spec.videoBitrateKbps,
            audioBitrateKbps = spec.audioBitrateKbps,
        )

        if (historyId > 0) {
            container.historyRepository.completeJob(
                id = historyId,
                outputName = published.displayName,
                outputUri = published.uri.toString(),
                outputBytes = published.sizeBytes,
                width = spec.outputWidth,
                height = spec.outputHeight,
                thumbPath = thumbnailPath,
            )
        }

        container.jobController.onSuccess(spec.displayName, result)
        notifications.cancel(CompressionNotifications.PROGRESS_NOTIFICATION_ID)
        notifications.show(
            CompressionNotifications.RESULT_NOTIFICATION_ID,
            notifications.successNotification(result),
        )
    }

    private suspend fun handleFailure(
        spec: CompressionJobSpec,
        tempFile: File,
        historyId: Long,
        error: Throwable,
    ) {
        tempFile.delete()
        val cancelled = cancelRequested || error is CancellationException
        val message = when {
            cancelled -> null
            error is NotEnoughSpaceException -> getString(R.string.error_storage_full)
            error is CompressionFailedException ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.error_compression_failed, "")

            else -> getString(
                R.string.error_compression_failed,
                error.message ?: error.javaClass.simpleName,
            )
        }

        if (historyId > 0) {
            container.historyRepository.markStatus(
                id = historyId,
                status = if (cancelled) HistoryStatus.CANCELLED else HistoryStatus.FAILED,
                error = message,
            )
        }

        notifications.cancel(CompressionNotifications.PROGRESS_NOTIFICATION_ID)

        if (cancelled) {
            container.jobController.onCancelled(spec.displayName)
            notifications.show(
                CompressionNotifications.RESULT_NOTIFICATION_ID,
                notifications.cancelledNotification(spec.displayName),
            )
        } else {
            val failureMessage = message ?: getString(R.string.error_compression_failed, "")
            container.jobController.onFailed(spec.displayName, failureMessage)
            notifications.show(
                CompressionNotifications.RESULT_NOTIFICATION_ID,
                notifications.failureNotification(spec.displayName, failureMessage),
            )
        }
    }

    private fun finishService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        File(cacheDir, TEMP_DIRECTORY).listFiles()?.forEach { file ->
            if (!file.name.endsWith(".keep")) file.delete()
        }
        currentSpec = null
        job = null
        stopSelf()
    }

    private fun requestCancel() {
        if (job?.isActive != true) {
            stopSelf()
            return
        }
        cancelRequested = true
        engine.cancel()
        job?.cancel()
    }

    override fun onDestroy() {
        engine.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private class NotEnoughSpaceException : Exception("not enough storage")

    private class CompressionFailedException(message: String?) : Exception(message)

    companion object {
        const val ACTION_START = "com.abbas.aldaghat.action.START_COMPRESSION"
        const val ACTION_CANCEL = "com.abbas.aldaghat.action.CANCEL_COMPRESSION"

        private const val TEMP_DIRECTORY = "compression"
        private const val MIME_HEVC = "video/hevc"
        private const val NOTIFICATION_PROGRESS_STEP = 2
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1000L

        private const val EXTRA_SOURCE_URI = "source_uri"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_SOURCE_SIZE = "source_size"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_OUTPUT_WIDTH = "output_width"
        private const val EXTRA_OUTPUT_HEIGHT = "output_height"
        private const val EXTRA_SCALED = "scaled"
        private const val EXTRA_VIDEO_KBPS = "video_kbps"
        private const val EXTRA_AUDIO_KBPS = "audio_kbps"
        private const val EXTRA_FRAME_RATE = "frame_rate"
        private const val EXTRA_VIDEO_MIME = "video_mime"
        private const val EXTRA_HAS_AUDIO = "has_audio"
        private const val EXTRA_PRESET_LABEL = "preset_label"
        private const val EXTRA_SOURCE_KBPS = "source_kbps"

        fun startIntent(context: Context, spec: CompressionJobSpec): Intent =
            Intent(context, CompressionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SOURCE_URI, spec.sourceUri)
                putExtra(EXTRA_DISPLAY_NAME, spec.displayName)
                putExtra(EXTRA_SOURCE_SIZE, spec.sourceSizeBytes)
                putExtra(EXTRA_DURATION, spec.durationMs)
                putExtra(EXTRA_OUTPUT_WIDTH, spec.outputWidth)
                putExtra(EXTRA_OUTPUT_HEIGHT, spec.outputHeight)
                putExtra(EXTRA_SCALED, spec.scaled)
                putExtra(EXTRA_VIDEO_KBPS, spec.videoBitrateKbps)
                putExtra(EXTRA_AUDIO_KBPS, spec.audioBitrateKbps)
                putExtra(EXTRA_FRAME_RATE, spec.frameRate)
                putExtra(EXTRA_VIDEO_MIME, spec.videoMimeType)
                putExtra(EXTRA_HAS_AUDIO, spec.hasAudio)
                putExtra(EXTRA_PRESET_LABEL, spec.presetLabel)
                putExtra(EXTRA_SOURCE_KBPS, spec.sourceVideoBitrateKbps)
            }

        fun cancelIntent(context: Context): Intent =
            Intent(context, CompressionService::class.java).apply {
                action = ACTION_CANCEL
            }
    }
}

private fun Intent.toSpec(): CompressionJobSpec? {
    val sourceUri = getStringExtra("source_uri") ?: return null
    val displayName = getStringExtra("display_name") ?: return null
    val videoMime = getStringExtra("video_mime") ?: return null
    return CompressionJobSpec(
        sourceUri = sourceUri,
        displayName = displayName,
        sourceSizeBytes = getLongExtra("source_size", 0L),
        durationMs = getLongExtra("duration", 0L),
        outputWidth = getIntExtra("output_width", 0),
        outputHeight = getIntExtra("output_height", 0),
        scaled = getBooleanExtra("scaled", false),
        videoBitrateKbps = getIntExtra("video_kbps", 0),
        audioBitrateKbps = getIntExtra("audio_kbps", 0),
        frameRate = getIntExtra("frame_rate", 30),
        videoMimeType = videoMime,
        hasAudio = getBooleanExtra("has_audio", false),
        presetLabel = getStringExtra("preset_label") ?: "",
        sourceVideoBitrateKbps = getIntExtra("source_kbps", 0),
    )
}
