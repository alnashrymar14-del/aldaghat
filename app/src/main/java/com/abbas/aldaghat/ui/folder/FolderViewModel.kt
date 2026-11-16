package com.abbas.aldaghat.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abbas.aldaghat.data.MediaStorePublisher
import com.abbas.aldaghat.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderViewModel(private val container: AppContainer) : ViewModel() {

    private val _videos = MutableStateFlow<List<MediaStorePublisher.SavedVideo>>(emptyList())
    val videos: StateFlow<List<MediaStorePublisher.SavedVideo>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) { container.publisher.listAbbasVideos() }
            }.getOrDefault(emptyList())
            _videos.value = result
            _error.value = false
            _loading.value = false
        }
    }

    fun delete(video: MediaStorePublisher.SavedVideo) {
        viewModelScope.launch {
            container.publisher.delete(video.uri)
            refresh()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FolderViewModel(container) as T
            }
    }
}
