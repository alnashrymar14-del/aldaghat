package com.abbas.aldaghat.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.abbas.aldaghat.domain.CompressionJobSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Real transcoding through Media3 Transformer.
 *
 * - Video is always re-encoded as H.264 (or HEVC when the user opted in and the device supports it)
 *   at the planned resolution and bitrate; audio is re-encoded as AAC at the planned bitrate.
 * - Scaling uses `Presentation` with exact even dimensions, so the aspect ratio is preserved and
 *   portrait videos stay portrait.
 * - Progress comes from `Transformer.getProgress(ProgressHolder)`, the official API. No log
 *   parsing and no synthetic progress.
 * - Cancellation calls `Transformer.cancel()` and removes the partial output.
 */
class Media3CompressionEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var transformer: Transformer? = null

    @Volatile
    private var progressJob: Job? = null

    suspend fun compress(
        spec: CompressionJobSpec,
        outputFile: File,
        onProgress: (percent: Int) -> Unit,
    ): CompressionOutcome = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            if (!continuation.isActive) return@post
            try {
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(spec.sourceUri))
                    .build()

                val videoEffects: List<Effect> = if (spec.scaled) {
                    listOf(
                        Presentation.createForWidthAndHeight(
                            spec.outputWidth,
                            spec.outputHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT,
                        ),
                    )
                } else {
                    emptyList()
                }

                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(!spec.hasAudio)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()

                val videoEncoderSettings = VideoEncoderSettings.Builder()
                    .setBitrate(spec.videoBitrateKbps * 1000)
                    .setiFrameIntervalSeconds(1f)
                    .build()

                val audioEncoderSettings = AudioEncoderSettings.Builder()
                    .setBitrate(spec.audioBitrateKbps.coerceAtLeast(64) * 1000)
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(videoEncoderSettings)
                    .setRequestedAudioEncoderSettings(audioEncoderSettings)
                    .setEnableFallback(true)
                    .build()

                val built = Transformer.Builder(context)
                    .setVideoMimeType(spec.videoMimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .setPortraitEncodingEnabled(true)
                    .setLooper(Looper.getMainLooper())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult,
                        ) {
                            finishProgress()
                            transformer = null
                            if (!continuation.isActive) {
                                outputFile.delete()
                                return
                            }
                            if (!outputFile.exists() || outputFile.length() <= 0L) {
                                continuation.resume(
                                    CompressionOutcome.Failure(
                                        message = "output file is empty",
                                        errorCode = ExportException.ERROR_CODE_UNSPECIFIED,
                                    ),
                                )
                            } else {
                                continuation.resume(CompressionOutcome.Success(exportResult))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            finishProgress()
                            transformer = null
                            outputFile.delete()
                            if (!continuation.isActive) return
                            continuation.resume(
                                CompressionOutcome.Failure(
                                    message = exportException.message,
                                    errorCode = exportException.errorCode,
                                ),
                            )
                        }
                    })
                    .build()

                transformer = built
                built.start(editedMediaItem, outputFile.absolutePath)

                progressJob = scope.launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        val state = built.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 100))
                        }
                        delay(PROGRESS_POLL_INTERVAL_MS)
                    }
                }
            } catch (error: Throwable) {
                finishProgress()
                transformer = null
                if (continuation.isActive) {
                    continuation.resume(
                        CompressionOutcome.Failure(
                            message = error.message ?: error.javaClass.simpleName,
                            errorCode = ExportException.ERROR_CODE_UNSPECIFIED,
                        ),
                    )
                }
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post {
                finishProgress()
                transformer?.cancel()
                transformer = null
                outputFile.delete()
            }
        }
    }

    /** Cancels the running export, if any. Safe to call from any thread. */
    fun cancel() {
        mainHandler.post {
            finishProgress()
            transformer?.cancel()
            transformer = null
        }
    }

    private fun finishProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 400L
    }
}

sealed interface CompressionOutcome {
    data class Success(val exportResult: ExportResult) : CompressionOutcome
    data class Failure(val message: String?, val errorCode: Int) : CompressionOutcome
    data object Cancelled : CompressionOutcome
}
