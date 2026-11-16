package com.abbas.aldaghat

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abbas.aldaghat.data.EncoderCapabilities
import com.abbas.aldaghat.data.VideoInspector
import com.abbas.aldaghat.domain.CompressionJobSpec
import com.abbas.aldaghat.domain.CompressionPlan
import com.abbas.aldaghat.domain.CompressionPlanner
import com.abbas.aldaghat.domain.QualityPreset
import com.abbas.aldaghat.domain.VideoCodec
import com.abbas.aldaghat.media.CompressionOutcome
import com.abbas.aldaghat.media.Media3CompressionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * End-to-end engine tests on real encoded videos. Each test generates its input with the device
 * encoder, compresses it through Media3 Transformer and inspects the produced MP4.
 */
@RunWith(AndroidJUnit4::class)
class CompressionEngineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var inspector: VideoInspector
    private lateinit var capabilities: EncoderCapabilities
    private lateinit var engine: Media3CompressionEngine
    private lateinit var outputDirectory: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        inspector = VideoInspector(context)
        capabilities = EncoderCapabilities()
        engine = Media3CompressionEngine(context)
        outputDirectory = File(context.cacheDir, "test-outputs").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun compress1080pVideoProducesPlayableSmallerFile() {
        val source = TestVideoFactory.create(
            context, "input_1080p.mp4", width = 1920, height = 1080, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P1080)

        assertTrue("output must exist", result.outputFile.exists())
        assertTrue("output must not be empty", result.outputFile.length() > 0)
        assertTrue(
            "compressed (${result.outputFile.length()}) should be smaller than source (${source.length()})",
            result.outputFile.length() < source.length(),
        )
        val meta = readMetadata(result.outputFile)
        assertTrue("video track missing", meta.hasVideo)
        assertEquals(1080.0, meta.shortSide.toDouble(), 2.0)
        assertTrue("duration must be positive", meta.durationMs > 0)
        // The encoder must honour the planned bitrate (synthetic bars compress differently than
        // real footage, so file-size comparison is only asserted for the low-bitrate presets).
        assertBitrateHonoured(result)
    }

    @Test
    fun compress720pVideoScalesDown() {
        val source = TestVideoFactory.create(
            context, "input_720p.mp4", width = 1920, height = 1080, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P720)
        val meta = readMetadata(result.outputFile)
        assertEquals(720.0, meta.shortSide.toDouble(), 2.0)
        assertTrue(meta.hasVideo)
        assertBitrateHonoured(result)
    }

    @Test
    fun compress480pVideoScalesDown() {
        val source = TestVideoFactory.create(
            context, "input_480p.mp4", width = 1280, height = 720, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P480)
        val meta = readMetadata(result.outputFile)
        assertEquals(480.0, meta.shortSide.toDouble(), 2.0)
        assertTrue(result.outputFile.length() < source.length())
    }

    @Test
    fun compress360pVideoScalesDown() {
        val source = TestVideoFactory.create(
            context, "input_360p.mp4", width = 1280, height = 720, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P360)
        val meta = readMetadata(result.outputFile)
        assertEquals(360.0, meta.shortSide.toDouble(), 2.0)
        assertTrue(result.outputFile.length() < source.length())
    }

    @Test
    fun portraitVideoStaysPortrait() {
        val source = TestVideoFactory.create(
            context, "input_portrait.mp4", width = 720, height = 1280, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P480)
        val meta = readMetadata(result.outputFile)
        assertTrue("expected portrait output, got ${meta.width}x${meta.height}", meta.height > meta.width)
        assertEquals(480.0, meta.shortSide.toDouble(), 2.0)
    }

    @Test
    fun landscapeVideoStaysLandscape() {
        val source = TestVideoFactory.create(
            context, "input_landscape.mp4", width = 1280, height = 720, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P480)
        val meta = readMetadata(result.outputFile)
        assertTrue("expected landscape output, got ${meta.width}x${meta.height}", meta.width > meta.height)
    }

    @Test
    fun audioTrackIsPreserved() {
        val source = TestVideoFactory.create(
            context, "input_audio.mp4", width = 1280, height = 720, durationSeconds = 2,
            withAudio = true,
        )
        val result = compress(source, QualityPreset.P480)
        val meta = readMetadata(result.outputFile)
        assertTrue("audio track must be preserved", meta.hasAudio)
    }

    @Test
    fun videoWithoutAudioProducesNoAudioTrack() {
        val source = TestVideoFactory.create(
            context, "input_silent.mp4", width = 1280, height = 720, durationSeconds = 2,
            withAudio = false,
        )
        val result = compress(source, QualityPreset.P480)
        val meta = readMetadata(result.outputFile)
        assertFalse("unexpected audio track", meta.hasAudio)
        assertTrue(meta.hasVideo)
    }

    @Test
    fun customBitrateIsApplied() {
        val source = TestVideoFactory.create(
            context, "input_custom.mp4", width = 1280, height = 720, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.CUSTOM, manualBitrateKbps = 700)
        assertTrue(result.outputFile.length() > 0)
        assertTrue(result.spec.videoBitrateKbps <= 700)
    }

    @Test
    fun corruptInputFailsGracefully() {
        val broken = File(context.cacheDir, "broken.mp4")
        broken.writeBytes(ByteArray(64 * 1024) { (it * 31 % 251).toByte() })
        assertTrue("inspector should reject corrupt input", runBlocking { inspector.inspect(Uri.fromFile(broken)) } == null)

        val spec = CompressionJobSpec(
            sourceUri = Uri.fromFile(broken).toString(),
            displayName = "broken.mp4",
            sourceSizeBytes = broken.length(),
            durationMs = 2000,
            outputWidth = 1280,
            outputHeight = 720,
            scaled = false,
            videoBitrateKbps = 1500,
            audioBitrateKbps = 128,
            frameRate = 30,
            videoMimeType = VideoCodec.H264.mimeType,
            hasAudio = false,
            presetLabel = "480p",
            sourceVideoBitrateKbps = 0,
        )
        val output = File(outputDirectory, "broken_out.mp4")
        val outcome = runBlocking { engine.compress(spec, output) { } }
        assertTrue("expected failure, got $outcome", outcome is CompressionOutcome.Failure)
        assertFalse(output.exists())
        broken.delete()
    }

    @Test
    fun cancellingDeletesPartialOutput() {
        runBlocking {
        val source = TestVideoFactory.create(
            context, "input_cancel.mp4", width = 1920, height = 1080, durationSeconds = 4,
        )
        val spec = buildSpec(source, QualityPreset.P480)
        val output = File(outputDirectory, "cancel_out.mp4")

        var sawProgress = false
        val job = launch {
            engine.compress(spec, output) { percent -> if (percent > 0) sawProgress = true }
        }
        // Wait for the pipeline to actually start, then cancel.
        var waitedMs = 0
        while (!sawProgress && waitedMs < 15_000) {
            delay(100)
            waitedMs += 100
        }
        job.cancel()
        job.join()

        assertFalse("partial output must be removed", output.exists())
        source.delete()
        }
    }

    @Test
    fun encoderCapabilitiesAreConsistent() {
        assertTrue(capabilities.supportsSize(VideoCodec.H264.mimeType, 1280, 720))
        assertNotNull(capabilities.encoderName(VideoCodec.H264.mimeType, 1280, 720))
        // HEVC must only be advertised when the device really has a working encoder.
        if (capabilities.isHevcEncoderAvailable()) {
            assertNotNull(capabilities.encoderName(VideoCodec.HEVC.mimeType, 1280, 720))
            assertTrue(capabilities.supportsSize(VideoCodec.HEVC.mimeType, 1280, 720))
        }
    }

    @Test
    fun hevcCompressionWorksWhenSupported() {
        if (!capabilities.supportsSize(VideoCodec.HEVC.mimeType, 1280, 720)) return
        val source = TestVideoFactory.create(
            context, "input_hevc.mp4", width = 1280, height = 720, durationSeconds = 2,
        )
        val result = compress(source, QualityPreset.P480, VideoCodec.HEVC)
        assertTrue(result.outputFile.length() > 0)
        assertTrue(readMetadata(result.outputFile).hasVideo)
    }

    private fun describe(file: File): String = try {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val tracks = (0 until extractor.trackCount).mapNotNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
        }
        extractor.release()
        "size=${file.length()} tracks=$tracks"
    } catch (error: Throwable) {
        "unreadable: ${error.message}"
    }

    private fun assertBitrateHonoured(run: CompressionRun) {
        if (run.averageVideoBitrate <= 0) return
        val planned = run.spec.videoBitrateKbps * 1000
        assertTrue(
            "encoder used ${run.averageVideoBitrate} bps, planned $planned bps",
            run.averageVideoBitrate <= planned * 1.25,
        )
    }

    private data class CompressionRun(
        val spec: CompressionJobSpec,
        val outputFile: File,
        val averageVideoBitrate: Int,
    )

    private fun compress(
        source: File,
        preset: QualityPreset,
        codec: VideoCodec = VideoCodec.H264,
        manualBitrateKbps: Int? = null,
    ): CompressionRun {
        val spec = buildSpec(source, preset, codec, manualBitrateKbps)
        val output = File(outputDirectory, "${source.nameWithoutExtension}_${preset.id}_out.mp4")
        if (output.exists()) output.delete()
        val outcome = runBlocking { engine.compress(spec, output) { } }
        val success = outcome as? CompressionOutcome.Success
            ?: throw AssertionError("compression failed: $outcome")
        return CompressionRun(spec, output, success.exportResult.averageVideoBitrate)
    }

    private fun buildSpec(
        source: File,
        preset: QualityPreset,
        codec: VideoCodec = VideoCodec.H264,
        manualBitrateKbps: Int? = null,
    ): CompressionJobSpec {
        if (!source.exists() || source.length() <= 0L) {
            throw AssertionError("generated test input is missing or empty: ${source.name}")
        }
        val info = runBlocking { inspector.inspect(Uri.fromFile(source)) }
            ?: throw AssertionError("could not inspect ${source.name}: ${describe(source)}")
        val plan: CompressionPlan = CompressionPlanner.plan(
            video = info,
            preset = preset,
            codec = codec,
            manualVideoBitrateKbps = manualBitrateKbps,
            audioBitrateKbps = 128,
            isSizeSupported = { width, height ->
                capabilities.supportsSize(codec.mimeType, width, height)
            },
        ) ?: throw AssertionError("no compression plan for ${source.name}")
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

    private data class OutputMetadata(
        val width: Int,
        val height: Int,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val durationMs: Long,
    ) {
        val shortSide: Int get() = minOf(width, height)
    }

    private fun readMetadata(file: File): OutputMetadata {
        assertNotNull(file)
        val extractor = MediaExtractor()
        var width = 0
        var height = 0
        var rotation = 0
        var hasVideo = false
        var hasAudio = false
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && !hasVideo) {
                    hasVideo = true
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                }
            }
        } finally {
            extractor.release()
        }

        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (error: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }

        val rotated = abs(rotation) % 180 == 90
        return OutputMetadata(
            width = if (rotated) height else width,
            height = if (rotated) width else height,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            durationMs = duration,
        )
    }
}
