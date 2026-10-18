package com.rotatsulabs.specty.batch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The rules that keep a scan of a large library safe: one file at a time, nothing left open, failures contained,
 * cancellation prompt, and a repeated or resumed scan never redoing finished work.
 */
class BatchEngineTest {
    /** Counts what the scan does so a test can check it against the library it was given. */
    private class Probe(val saved: MutableSet<Int> = HashSet()) : BatchListener<Int> {
        val inFlight = AtomicInteger()
        var maxInFlight = 0
        val open = AtomicInteger()
        val started = ArrayList<Int>()
        val processed = ArrayList<Int>()
        var done = 0
        var skipped = 0
        var failed = 0
        var onOutcomeHook: (Int) -> Unit = {}

        override fun onStart(index: Int, item: Int) { started += item }
        override fun onOutcome(index: Int, item: Int, outcome: BatchOutcome) {
            when (outcome) {
                BatchOutcome.Skipped -> skipped++
                is BatchOutcome.Done -> done++
                is BatchOutcome.Failed -> failed++
            }
            onOutcomeHook(item)
        }

        /** One file's worth of work: holds a handle while it runs, as a decoder and temp file would. */
        suspend fun work(item: Int, body: suspend (Int) -> Unit = {}): Boolean {
            val now = inFlight.incrementAndGet()
            maxInFlight = maxOf(maxInFlight, now)
            open.incrementAndGet()
            try {
                processed += item
                body(item)
                saved += item
                return item % 5 == 0
            } finally {
                open.decrementAndGet()
                inFlight.decrementAndGet()
            }
        }
    }

    private fun engine(
        probe: Probe,
        gate: suspend () -> Unit = {},
        maxFailures: Int = 25,
        body: suspend (Int) -> Unit = {},
    ) = BatchEngine<Int>(
        isSaved = { it in probe.saved },
        process = { item, _ -> probe.work(item, body) },
        gate = gate,
        maxConsecutiveFailures = maxFailures,
    )

    private fun library(n: Int) = (0 until n).toList()

    @Test
    fun aLibraryOf1726SongsIsAnalysedOneAtATimeAndLeavesNothingOpen() = runBlocking {
        val probe = Probe()
        val stop = engine(probe).run(library(1726), probe)
        assertEquals(BatchStop.Finished, stop)
        assertEquals(1726, probe.done)
        assertEquals(0, probe.failed + probe.skipped)
        assertEquals("never more than one file in flight", 1, probe.maxInFlight)
        assertEquals("handles are released", 0, probe.open.get())
        assertEquals("in order, each once", library(1726), probe.started)
    }

    @Test
    fun aLargerLibraryWithSavedAndUnreadableFilesAddsUp() = runBlocking {
        val n = 2_500
        val probe = Probe(saved = (0 until n step 11).toMutableSet())
        val unreadable = (0 until n).filter { it % 7 == 3 && it % 11 != 0 }.toSet()
        val stop = engine(probe) { if (it in unreadable) throw IOException("corrupt") }.run(library(n), probe)
        assertEquals(BatchStop.Finished, stop)
        assertEquals(n, probe.done + probe.skipped + probe.failed)
        assertEquals((0 until n step 11).count(), probe.skipped)
        assertEquals(unreadable.size, probe.failed)
        assertEquals(0, probe.open.get())
    }

    @Test
    fun repeatedScansDoNotRepeatFinishedWork() = runBlocking {
        val probe = Probe()
        val items = library(1726)
        engine(probe).run(items, probe)
        val firstPass = probe.processed.size
        repeat(3) { engine(probe).run(items, probe) }
        assertEquals(1726, firstPass)
        assertEquals("later scans analyse nothing", 1726, probe.processed.size)
        assertEquals(3 * 1726, probe.skipped)
        assertEquals(1, probe.maxInFlight)
    }

    @Test
    fun repeatedScansAfterFailuresOnlyRetryTheFailedFiles() = runBlocking {
        val probe = Probe()
        val broken = (0 until 1726 step 100).toSet()
        val items = library(1726)
        engine(probe) { if (it in broken) throw IOException("corrupt") }.run(items, probe)
        val retried = ArrayList<Int>()
        engine(probe) { retried += it }.run(items, probe)
        assertEquals(broken.sorted(), retried)
    }

    @Test
    fun aCancelledScanResumesWithoutRedoingFinishedFiles() = runBlocking {
        val probe = Probe()
        val items = library(1726)
        val cutAt = 600
        val job = launch {
            engine(probe) { if (it == cutAt) { currentCoroutineContext()[Job]!!.cancel(); awaitCancellation() } }.run(items, probe)
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals("work before the cut is kept", cutAt, probe.saved.size)
        assertEquals("handle of the interrupted file is released", 0, probe.open.get())
        val before = probe.processed.size
        engine(probe).run(items, probe)
        assertEquals("only the rest is analysed", 1726 - cutAt, probe.processed.size - before)
        assertEquals("no file analysed twice except the interrupted one", 1726, probe.processed.toSet().size)
        assertEquals(1, probe.processed.count { it == cutAt } - 1)
    }

    @Test
    fun cancellingInterruptsAFileThatIsStillRunning() = runBlocking {
        val probe = Probe()
        val running = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            engine(probe) { running.complete(Unit); delay(60_000) }.run(library(10), probe)
        }
        running.await()
        job.cancel()
        withTimeout(2_000) { job.join() }
        assertEquals(0, probe.open.get())
        assertEquals(1, probe.started.size)
    }

    @Test
    fun cancellingBetweenFilesStopsBeforeTheNextOne() = runBlocking {
        val probe = Probe()
        lateinit var job: Job
        probe.onOutcomeHook = { if (it == 10) job.cancel() }
        job = launch { engine(probe).run(library(1726), probe) }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals((0..10).toList(), probe.started)
    }

    @Test
    fun cancellingWhileHeldByTheGateStopsTheScan() = runBlocking {
        val probe = Probe()
        val job = launch { engine(probe, gate = { delay(60_000) }).run(library(5), probe) }
        delay(50)
        job.cancel()
        withTimeout(2_000) { job.join() }
        assertTrue(probe.started.isEmpty())
    }

    @Test
    fun corruptedFilesAreCountedAndTheScanCarriesOn() = runBlocking {
        val probe = Probe()
        val stop = engine(probe) {
            when (it % 4) {
                0 -> throw IOException("This file could not be read as audio")
                1 -> throw IllegalStateException("The decoder produced no audio samples")
                2 -> if (it % 8 == 2) throw UnsatisfiedLinkError("native") else Unit
                else -> Unit
            }
        }.run(library(1726), probe)
        assertEquals(BatchStop.Finished, stop)
        assertEquals(1726, probe.done + probe.failed)
        assertTrue(probe.failed > 800)
        assertEquals(0, probe.open.get())
    }

    @Test
    fun aRunOfFailuresStopsTheScanInsteadOfHammeringOn() = runBlocking {
        val probe = Probe()
        val stop = engine(probe, maxFailures = 25) { throw IOException("storage full") }.run(library(1726), probe)
        assertEquals(BatchStop.TooManyFailures, stop)
        assertEquals(25, probe.failed)
        assertEquals(25, probe.started.size)
    }

    @Test
    fun scatteredFailuresNeverTripTheLimit() = runBlocking {
        val probe = Probe()
        val stop = engine(probe, maxFailures = 25) { if (it % 25 != 24) throw IOException("bad") }.run(library(1726), probe)
        assertEquals("24 failures then a success, over and over", BatchStop.Finished, stop)
        assertEquals(1726, probe.done + probe.failed)
    }

    @Test
    fun savedFilesDoNotCountAsSuccessesForTheFailureLimit() = runBlocking {
        val probe = Probe(saved = (0 until 1000 step 2).toMutableSet())
        val stop = engine(probe, maxFailures = 25) { throw IOException("bad") }.run(library(1000), probe)
        assertEquals(BatchStop.TooManyFailures, stop)
    }

    @Test
    fun runningOutOfMemoryStopsTheScan() = runBlocking {
        val probe = Probe()
        val stop = engine(probe) { if (it == 5) throw OutOfMemoryError("heap") }.run(library(100), probe)
        assertEquals(BatchStop.LowMemory, stop)
        assertEquals(6, probe.started.size)
        assertEquals(1, probe.failed)
        assertEquals(0, probe.open.get())
    }

    @Test
    fun theGateRunsBeforeEveryFileAndCanHoldTheScan() = runBlocking {
        val probe = Probe()
        val release = CompletableDeferred<Unit>()
        var gated = 0
        val job = async {
            engine(probe, gate = { gated++; if (gated == 4) release.await() }).run(library(10), probe)
        }
        delay(100)
        assertEquals("held at the fourth file", 3, probe.started.size)
        release.complete(Unit)
        assertEquals(BatchStop.Finished, job.await())
        assertEquals(10, probe.started.size)
        assertEquals(10, gated)
    }

    @Test
    fun progressAndOutcomeAreReportedForEveryFile() = runBlocking {
        val probe = Probe()
        val progress = ArrayList<Pair<Int, Float>>()
        val listener = object : BatchListener<Int> by probe {
            override fun onProgress(index: Int, fraction: Float) { progress += index to fraction }
        }
        val engine = BatchEngine<Int>(isSaved = { false }, process = { _, p -> p(0.5f); p(1f); false })
        engine.run(library(20), listener)
        assertEquals(40, progress.size)
        assertEquals(20, probe.done)
        assertFalse(progress.any { it.second !in 0f..1f })
    }
}
