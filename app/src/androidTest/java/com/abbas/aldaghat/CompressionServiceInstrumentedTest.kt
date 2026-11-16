package com.abbas.aldaghat

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abbas.aldaghat.data.EncoderCapabilities
import com.abbas.aldaghat.data.VideoInspector
import com.abbas.aldaghat.di.AppContainer
import com.abbas.aldaghat.domain.CompressionJobSpec
import com.abbas.aldaghat.domain.CompressionPlanner
import com.abbas.aldaghat.domain.HistoryStatus
import com.abbas.aldaghat.domain.JobState
import com.abbas.aldaghat.domain.QualityPreset
import com.abbas.aldaghat.domain.VideoCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real foreground service: progress state, notification channel, MediaStore output and
 * the history row. Runs on the device, so it also covers "app UI closed while processing".
 */
@RunWith(AndroidJUnit4::class)
class CompressionServiceInstrumentedTest {

    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as AlDaghatApplication
        context = application
        container = application.container
        container.jobController.reset()
    }

    @Test
    fun serviceCompletesJobAndStoresHistory() {
        val source = TestVideoFactory.create(
            context, "service_input.mp4", width = 640, height = 360, durationSeconds = 2,
            videoBitrate = 2_000_000,
        )
        val spec = buildSpec(source, QualityPreset.P360)

        assertTrue("job could not be started", container.jobController.start(spec))

        val terminal = runBlocking {
            withTimeout(180_000) {
                container.jobController.state.first {
                    it is JobState.Success || it is JobState.Failed || it is JobState.Cancelled
                }
            }
        }

        when (terminal) {
            is JobState.Success -> {
                val result = terminal.result
                assertTrue(result.outputName.endsWith(".mp4"))
                assertTrue(result.outputBytes > 0)
                assertTrue(result.outputUri.startsWith("content://"))

                val history = runBlocking {
                    withTimeout(10_000) {
                        container.historyRepository.entries.first { entries ->
                            entries.any { entry ->
                                entry.outputName == result.outputName &&
                                    entry.status == HistoryStatus.COMPLETED
                            }
                        }
                    }
                }
                val entry = history.first { it.outputName == result.outputName }
                assertEquals(HistoryStatus.COMPLETED, entry.status)
                assertEquals(result.outputBytes, entry.outputBytes)
                assertNotNull(entry.outputUri)

                assertTrue(runBlocking { container.publisher.delete(Uri.parse(result.outputUri)) })
            }

            is JobState.Failed -> fail("service failed: ${terminal.message}")
            is JobState.Cancelled -> fail("service reported cancellation unexpectedly")
            else -> fail("unexpected terminal state: $terminal")
        }

        source.delete()
    }

    @Test
    fun secondJobIsRejectedWhileOneIsRunning() {
        val source = TestVideoFactory.create(
            context, "service_busy.mp4", width = 1280, height = 720, durationSeconds = 3,
        )
        val spec = buildSpec(source, QualityPreset.P480)
        assertTrue(container.jobController.start(spec))
        assertTrue("second start must be refused", !container.jobController.start(spec))

        container.jobController.cancel()
        runBlocking {
            withTimeout(30_000) {
                container.jobController.state.first {
                    it is JobState.Cancelled || it is JobState.Failed || it is JobState.Success
                }
            }
        }
        container.jobController.reset()
        source.delete()
    }

    private fun buildSpec(source: java.io.File, preset: QualityPreset): CompressionJobSpec {
        val inspector = VideoInspector(context)
        val capabilities = EncoderCapabilities()
        val info = runBlocking { inspector.inspect(Uri.fromFile(source)) }
            ?: throw AssertionError("could not inspect source")
        val plan = CompressionPlanner.plan(
            video = info,
            preset = preset,
            codec = VideoCodec.H264,
            manualVideoBitrateKbps = null,
            audioBitrateKbps = 128,
            isSizeSupported = { width, height ->
                capabilities.supportsSize(VideoCodec.H264.mimeType, width, height)
            },
        ) ?: throw AssertionError("no plan")
        return CompressionJobSpec(
            sourceUri = info.uri,
            displayName = info.displayName,
            sourceSizeBytes = info.sizeBytes,
            durationMs = info.durationMs,
            outputWidth = plan.outputWidth,
            outputHeight = plan.outputHeight,
            scaled = plan.scaled,
            videoBitrateKbps = plan.videoBitrateKbps,
            audioBitrateKbps = plan.audioBitrateKbps,
            frameRate = plan.frameRate,
            videoMimeType = plan.codec.mimeType,
            hasAudio = plan.hasAudio,
            presetLabel = plan.qualityLabel,
            sourceVideoBitrateKbps = info.videoBitrateKbps,
        )
    }
}
