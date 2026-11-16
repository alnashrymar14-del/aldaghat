package com.abbas.aldaghat

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Creates real MP4 inputs for instrumentation tests using the device's own encoders, so the
 * compression tests do not depend on any bundled asset and work on every architecture.
 */
object TestVideoFactory {

    data class Sample(val data: ByteArray, val presentationTimeUs: Long, val flags: Int)

    class EncodedTrack(var format: MediaFormat) {
        val samples = mutableListOf<Sample>()
    }

    fun create(
        context: Context,
        fileName: String,
        width: Int,
        height: Int,
        fps: Int = 24,
        durationSeconds: Int = 2,
        withAudio: Boolean = true,
        videoBitrate: Int = 4_000_000,
    ): File {
        val directory = File(context.cacheDir, "test-inputs").apply { mkdirs() }
        val target = File(directory, fileName)
        if (target.exists()) target.delete()

        val videoTrack = encodeVideo(width, height, fps, durationSeconds, videoBitrate)
        val audioTrack = if (withAudio) encodeAudio(durationSeconds) else null
        mux(target, videoTrack, audioTrack)
        return target
    }

    private fun encodeVideo(
        width: Int,
        height: Int,
        fps: Int,
        durationSeconds: Int,
        bitrate: Int,
    ): EncodedTrack {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val track = EncodedTrack(format)
        val info = MediaCodec.BufferInfo()
        val totalFrames = fps * durationSeconds
        var frame = 0
        var inputDone = false
        var eos = false
        val frameDurationUs = 1_000_000L / fps

        while (!eos) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(20_000)
                if (inputIndex >= 0) {
                    if (frame < totalFrames) {
                        fillFrame(codec, inputIndex, width, height, frame)
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            width * height * 3 / 2,
                            frame * frameDurationUs,
                            0,
                        )
                        frame++
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            frame * frameDurationUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    }
                }
            }
            eos = drain(codec, track, info, timeoutUs = 20_000)
        }

        codec.stop()
        codec.release()
        return track
    }

    private fun fillFrame(codec: MediaCodec, index: Int, width: Int, height: Int, frameIndex: Int) {
        val image = codec.getInputImage(index) ?: return
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        for (row in 0 until height) {
            val rowOffset = row * yPlane.rowStride
            for (column in 0 until width) {
                // Moving vertical bars so temporal compression has real work to do.
                val value = ((column + frameIndex * 6) % width) * 255 / width
                yBuffer.put(rowOffset + column * yPlane.pixelStride, value.toByte())
            }
        }

        val chromaWidth = width / 2
        val chromaHeight = height / 2
        for (row in 0 until chromaHeight) {
            val uOffset = row * uPlane.rowStride
            val vOffset = row * vPlane.rowStride
            for (column in 0 until chromaWidth) {
                uPlane.buffer.put(uOffset + column * uPlane.pixelStride, 120.toByte())
                vPlane.buffer.put(vOffset + column * vPlane.pixelStride, 130.toByte())
            }
        }
    }

    private fun encodeAudio(durationSeconds: Int): EncodedTrack {
        val sampleRate = 44_100
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val track = EncodedTrack(format)
        val info = MediaCodec.BufferInfo()
        val samplesPerBuffer = 1024
        val totalSamples = sampleRate * durationSeconds
        var samplesWritten = 0
        var inputDone = false
        var eos = false

        while (!eos) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(20_000)
                if (inputIndex >= 0) {
                    if (samplesWritten < totalSamples) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        buffer.clear()
                        val pcm = ByteBuffer.allocate(samplesPerBuffer * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (sample in 0 until samplesPerBuffer) {
                            val position = samplesWritten + sample
                            val value = if (position < totalSamples) {
                                (Math.sin(2.0 * Math.PI * 440.0 * position / sampleRate) * 8000).toInt().toShort()
                            } else {
                                0
                            }
                            pcm.putShort(value)
                        }
                        buffer.put(pcm.array())
                        val presentationTimeUs = samplesWritten * 1_000_000L / sampleRate
                        codec.queueInputBuffer(inputIndex, 0, samplesPerBuffer * 2, presentationTimeUs, 0)
                        samplesWritten += samplesPerBuffer
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            samplesWritten * 1_000_000L / sampleRate,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    }
                }
            }
            eos = drain(codec, track, info, timeoutUs = 20_000)
        }

        codec.stop()
        codec.release()
        return track
    }

    private fun drain(
        codec: MediaCodec,
        track: EncodedTrack,
        info: MediaCodec.BufferInfo,
        timeoutUs: Long,
    ): Boolean {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track.format = codec.outputFormat
                    continue
                }

                index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> continue
                index < 0 -> return false
                else -> {
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && info.size > 0) {
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val data = ByteArray(info.size)
                            buffer.get(data)
                            track.samples += Sample(data, info.presentationTimeUs, info.flags)
                        }
                    }
                    val endOfStream = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(index, false)
                    if (endOfStream) return true
                }
            }
        }
    }

    private fun mux(target: File, video: EncodedTrack, audio: EncodedTrack?) {
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoTrackIndex = muxer.addTrack(video.format)
        val audioTrackIndex = audio?.let { muxer.addTrack(it.format) } ?: -1
        muxer.start()

        val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
        val merged = mutableListOf<Pair<Int, Sample>>()
        video.samples.forEach { merged += videoTrackIndex to it }
        audio?.samples?.forEach { merged += audioTrackIndex to it }
        merged.sortBy { it.second.presentationTimeUs }

        merged.forEach { (trackIndex, sample) ->
            if (sample.data.size <= buffer.capacity()) {
                buffer.clear()
                buffer.put(sample.data)
                buffer.position(0)
                buffer.limit(sample.data.size)
                val info = MediaCodec.BufferInfo().apply {
                    set(
                        0,
                        sample.data.size,
                        sample.presentationTimeUs,
                        sample.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME,
                    )
                }
                muxer.writeSampleData(trackIndex, buffer, info)
            }
        }

        muxer.stop()
        muxer.release()
    }
}
