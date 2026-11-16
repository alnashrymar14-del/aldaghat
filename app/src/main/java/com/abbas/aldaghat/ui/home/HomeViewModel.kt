package com.abbas.aldaghat.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abbas.aldaghat.R
import com.abbas.aldaghat.di.AppContainer
import com.abbas.aldaghat.domain.BitrateSelector
import com.abbas.aldaghat.domain.CompressionJobSpec
import com.abbas.aldaghat.domain.CompressionPlan
import com.abbas.aldaghat.domain.CompressionPlanner
import com.abbas.aldaghat.domain.QualityPreset
import com.abbas.aldaghat.domain.SizeEstimator
import com.abbas.aldaghat.domain.VideoCodec
import com.abbas.aldaghat.domain.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val video: VideoInfo? = null,
    val thumbnailPath: String? = null,
    val loadingVideo: Boolean = false,
    val selectedPreset: QualityPreset = QualityPreset.P720,
    val customVideoBitrateKbps: Int = 2500,
    val customAudioBitrateKbps: Int = BitrateSelector.DEFAULT_AUDIO_KBPS,
    val useHevc: Boolean = false,
    val hevcSupported: Boolean = false,
    val plan: CompressionPlan? = null,
    val errorMessage: String? = null,
) {
    val estimatedMegabytes: Double
        get() {
            val source = video ?: return 0.0
            val compressionPlan = plan ?: return 0.0
            return compressionPlan.estimatedMegabytes(source.durationMs)
        }

    val estimatedBytes: Long
        get() = (estimatedMegabytes * 1024.0 * 1024.0).toLong()

    val savedBytes: Long
        get() {
            val source = video ?: return 0L
            return SizeEstimator.savedBytes(source.sizeBytes, estimatedBytes)
        }

    val savingPercent: Double
        get() {
            val source = video ?: return 0.0
            return SizeEstimator.reductionPercent(source.sizeBytes, estimatedBytes)
        }

    val canStart: Boolean
        get() = video != null && plan != null && !loadingVideo
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(hevcSupported = container.encoderCapabilities.isHevcEncoderAvailable()),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val jobState: StateFlow<com.abbas.aldaghat.domain.JobState> = container.jobController.state

    private var planJob: Job? = null
    private var selectedThumbnailPath: String? = null

    fun selectVideo(uri: Uri) {
        selectedThumbnailPath?.let { container.thumbnailStore.delete(it) }
        selectedThumbnailPath = null
        _state.update {
            it.copy(loadingVideo = true, errorMessage = null, plan = null, video = null)
        }
        viewModelScope.launch {
            val info = container.videoInspector.inspect(uri)
            if (info == null) {
                _state.update {
                    it.copy(
                        loadingVideo = false,
                        video = null,
                        plan = null,
                        thumbnailPath = null,
                        errorMessage = string(R.string.error_pick_video),
                    )
                }
                return@launch
            }
            val key = "src_${System.currentTimeMillis()}"
            val thumbnail = container.thumbnailStore.createThumbnail(uri, key)
            selectedThumbnailPath = thumbnail
            _state.update {
                it.copy(
                    video = info,
                    thumbnailPath = thumbnail,
                    loadingVideo = false,
                    errorMessage = null,
                )
            }
            recomputePlan()
        }
    }

    fun selectPreset(preset: QualityPreset) {
        _state.update { it.copy(selectedPreset = preset) }
        recomputePlan()
    }

    fun setCustomVideoBitrate(kbps: Int) {
        val rounded = ((kbps / 50) * 50).coerceIn(200, BitrateSelector.CUSTOM_MAX_VIDEO_KBPS)
        _state.update { it.copy(customVideoBitrateKbps = rounded) }
        if (_state.value.selectedPreset.isCustom) recomputePlan()
    }

    fun setCustomAudioBitrate(kbps: Int) {
        _state.update {
            it.copy(customAudioBitrateKbps = kbps.coerceIn(BitrateSelector.MIN_AUDIO_KBPS, 256))
        }
        if (_state.value.selectedPreset.isCustom) recomputePlan()
    }

    fun setUseHevc(enabled: Boolean) {
        if (enabled && !_state.value.hevcSupported) return
        _state.update { it.copy(useHevc = enabled) }
        recomputePlan()
    }

    fun startCompression() {
        val current = _state.value
        val video = current.video ?: return
        val plan = current.plan ?: return
        if (container.jobController.isBusy) {
            _state.update { it.copy(errorMessage = string(R.string.error_busy)) }
            return
        }
        val spec = CompressionJobSpec(
            sourceUri = video.uri,
            displayName = video.displayName,
            sourceSizeBytes = video.sizeBytes,
            durationMs = video.durationMs,
            outputWidth = plan.outputWidth,
            outputHeight = plan.outputHeight,
            scaled = plan.scaled,
            videoBitrateKbps = plan.videoBitrateKbps,
            audioBitrateKbps = plan.audioBitrateKbps,
            frameRate = plan.frameRate,
            videoMimeType = plan.codec.mimeType,
            hasAudio = plan.hasAudio,
            presetLabel = plan.qualityLabel,
            sourceVideoBitrateKbps = video.videoBitrateKbps,
        )
        if (!container.jobController.start(spec)) {
            _state.update { it.copy(errorMessage = string(R.string.error_busy)) }
        }
    }

    fun cancelJob() {
        container.jobController.cancel()
    }

    fun onStoragePermissionDenied() {
        _state.update { it.copy(errorMessage = string(R.string.error_storage_permission)) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun acknowledgeJobResult() {
        container.jobController.acknowledge()
    }

    fun clearSelection() {
        selectedThumbnailPath?.let { container.thumbnailStore.delete(it) }
        selectedThumbnailPath = null
        planJob?.cancel()
        _state.update { HomeUiState(hevcSupported = it.hevcSupported) }
    }

    private fun recomputePlan() {
        planJob?.cancel()
        val current = _state.value
        val video = current.video ?: return
        val codec = if (current.useHevc && current.hevcSupported) VideoCodec.HEVC else VideoCodec.H264
        planJob = viewModelScope.launch {
            val plan = withContext(Dispatchers.Default) {
                CompressionPlanner.plan(
                    video = video,
                    preset = current.selectedPreset,
                    codec = codec,
                    manualVideoBitrateKbps = if (current.selectedPreset.isCustom) {
                        current.customVideoBitrateKbps
                    } else {
                        null
                    },
                    audioBitrateKbps = if (current.selectedPreset.isCustom) {
                        current.customAudioBitrateKbps
                    } else {
                        BitrateSelector.DEFAULT_AUDIO_KBPS
                    },
                    isSizeSupported = { width, height ->
                        container.encoderCapabilities.supportsSize(codec.mimeType, width, height)
                    },
                )
            }
            _state.update {
                it.copy(
                    plan = plan,
                    errorMessage = if (plan == null) string(R.string.error_no_encoder) else it.errorMessage,
                )
            }
        }
    }

    private fun string(resourceId: Int): String = container.appContext.getString(resourceId)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(container) as T
            }
    }
}
