package com.rotatsulabs.specty.batch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** What happened to one item of a batch. */
sealed interface BatchOutcome {
    data object Skipped : BatchOutcome
    data class Done(val flagged: Boolean) : BatchOutcome
    data class Failed(val reason: String) : BatchOutcome
}

/** Why a batch ended without being cancelled. */
enum class BatchStop(val note: String) {
    Finished(""),
    TooManyFailures("Stopped after many files in a row failed. Check free storage and try again."),
    LowMemory("Stopped because the phone ran low on memory. Try again."),
}

interface BatchListener<T> {
    fun onStart(index: Int, item: T) {}
    fun onProgress(index: Int, fraction: Float) {}
    fun onOutcome(index: Int, item: T, outcome: BatchOutcome) {}
}

/**
 * Works through [items] one at a time. It holds no Android types, so the rules that keep a large scan safe can be
 * tested: one item in flight, a failed item never ends the run by itself, a run of failures or a memory error stops it
 * instead of hammering on, and cancellation is honoured between items and inside a running one.
 */
class BatchEngine<T>(
    /** True when the item already has a saved result, so there is nothing to do. */
    private val isSaved: (T) -> Boolean,
    /** Analyses the item and returns whether it should be flagged. Throws when the item is unreadable. */
    private val process: suspend (item: T, onProgress: (Float) -> Unit) -> Boolean,
    /** Runs before each item; may suspend to wait out heat or a low battery. */
    private val gate: suspend () -> Unit = {},
    private val maxConsecutiveFailures: Int = 25,
    /** Pause after each analysed item so a long run lets the CPU rest. */
    private val cooldownMs: Long = 0L,
) {
    /** Returns why the run ended; a cancelled run throws [CancellationException] as usual. */
    suspend fun run(items: List<T>, listener: BatchListener<T>): BatchStop {
        var failuresInARow = 0
        for ((index, item) in items.withIndex()) {
            currentCoroutineContext().ensureActive()
            gate()
            listener.onStart(index, item)
            if (isSaved(item)) {
                listener.onOutcome(index, item, BatchOutcome.Skipped)
                continue
            }
            try {
                val flagged = process(item) { listener.onProgress(index, it) }
                failuresInARow = 0
                listener.onOutcome(index, item, BatchOutcome.Done(flagged))
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                listener.onOutcome(index, item, BatchOutcome.Failed("Out of memory"))
                return BatchStop.LowMemory
            } catch (e: Throwable) {
                listener.onOutcome(index, item, BatchOutcome.Failed(e.message ?: e.javaClass.simpleName))
                if (++failuresInARow >= maxConsecutiveFailures) return BatchStop.TooManyFailures
            }
            if (cooldownMs > 0) delay(cooldownMs) else yield()
        }
        return BatchStop.Finished
    }
}
