package com.abbas.aldaghat.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.abbas.aldaghat.domain.CompressionJobSpec
import com.abbas.aldaghat.domain.CompressionResult
import com.abbas.aldaghat.domain.JobState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide job state shared by the foreground service and the UI.
 *
 * It also enforces the single-export rule: a second export can never start while one is active,
 * which protects the encoder and the temp workspace from concurrent writers.
 */
class CompressionJobController(private val context: Context) {

    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state.asStateFlow()

    @Volatile
    private var busy: Boolean = false

    val isBusy: Boolean get() = busy

    /** Returns false when an export is already running. */
    fun start(spec: CompressionJobSpec): Boolean {
        if (busy) return false
        busy = true
        _state.value = JobState.Preparing(spec.displayName)
        return try {
            ContextCompat.startForegroundService(
                context,
                CompressionService.startIntent(context, spec),
            )
            true
        } catch (error: Throwable) {
            busy = false
            _state.value = JobState.Idle
            false
        }
    }

    fun cancel() {
        if (!busy) return
        runCatching {
            context.startService(CompressionService.cancelIntent(context))
        }
    }

    /** Consumes a terminal state after the completion dialog was dismissed. */
    fun acknowledge() {
        if (!busy) _state.value = JobState.Idle
    }

    fun onPreparing(inputName: String) {
        busy = true
        _state.value = JobState.Preparing(inputName)
    }

    fun onProgress(percent: Int, elapsedMs: Long, etaMs: Long?) {
        val current = _state.value
        val name = when (current) {
            is JobState.Running -> current.inputName
            is JobState.Preparing -> current.inputName
            is JobState.Saving -> current.inputName
            else -> return
        }
        _state.value = JobState.Running(
            inputName = name,
            progressPercent = percent.coerceIn(0, 100),
            elapsedMs = elapsedMs,
            etaMs = etaMs,
        )
    }

    fun onSaving() {
        val name = when (val current = _state.value) {
            is JobState.Running -> current.inputName
            is JobState.Preparing -> current.inputName
            else -> ""
        }
        val elapsed = (state.value as? JobState.Running)?.elapsedMs ?: 0L
        _state.value = JobState.Saving(name, elapsed)
    }

    fun onSuccess(inputName: String, result: CompressionResult) {
        busy = false
        _state.value = JobState.Success(inputName, result)
    }

    fun onFailed(inputName: String, message: String) {
        busy = false
        _state.value = JobState.Failed(inputName, message)
    }

    fun onCancelled(inputName: String) {
        busy = false
        _state.value = JobState.Cancelled(inputName)
    }

    fun reset() {
        busy = false
        _state.value = JobState.Idle
    }
}
