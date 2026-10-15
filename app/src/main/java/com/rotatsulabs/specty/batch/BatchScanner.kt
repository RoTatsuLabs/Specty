package com.rotatsulabs.specty.batch

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rotatsulabs.specty.audio.WindowFn
import com.rotatsulabs.specty.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide handle on the library batch scan: the queue, its progress and start/cancel. */
object BatchScanner {
    data class State(
        val running: Boolean = false,
        val total: Int = 0,
        val done: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val flagged: Int = 0,
        val current: String = "",
        val cancelled: Boolean = false,
        /** True once a run has ended; the UI shows a summary until the next start. */
        val finished: Boolean = false,
    ) {
        val processed: Int get() = done + skipped + failed
        val fraction: Float get() = if (total == 0) 0f else (processed.toFloat() / total).coerceIn(0f, 1f)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile internal var queue: List<Track> = emptyList()
    @Volatile internal var fftSize: Int = 2048
    @Volatile internal var window: WindowFn = WindowFn.Hann

    /** Queues [tracks] for background analysis. Files with a saved result are skipped. */
    fun start(context: Context, tracks: List<Track>, fftSize: Int, window: WindowFn = WindowFn.Hann) {
        if (_state.value.running || tracks.isEmpty()) return
        queue = tracks
        this.fftSize = fftSize
        this.window = window
        _state.value = State(running = true, total = tracks.size)
        ContextCompat.startForegroundService(context, Intent(context, BatchScanService::class.java))
    }

    fun cancel(context: Context) {
        if (!_state.value.running) return
        context.startService(Intent(context, BatchScanService::class.java).setAction(BatchScanService.ACTION_CANCEL))
    }

    internal fun update(block: (State) -> State) = _state.update(block)

    internal fun end(cancelled: Boolean) = _state.update { it.copy(running = false, cancelled = cancelled, finished = true, current = "") }

    fun dismissSummary() = _state.update { if (it.running) it else State() }
}
