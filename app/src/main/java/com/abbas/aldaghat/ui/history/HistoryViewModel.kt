package com.abbas.aldaghat.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abbas.aldaghat.data.model.HistoryEntry
import com.abbas.aldaghat.di.AppContainer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val entries: StateFlow<List<HistoryEntry>> = container.historyRepository.entries

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { container.historyRepository.refresh() }
    }

    fun delete(entry: HistoryEntry, deleteFile: Boolean) {
        viewModelScope.launch {
            if (deleteFile) {
                entry.outputUri?.let { uri ->
                    container.publisher.delete(Uri.parse(uri))
                }
                container.thumbnailStore.delete(entry.thumbPath)
            }
            container.historyRepository.delete(entry.id)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HistoryViewModel(container) as T
            }
    }
}
