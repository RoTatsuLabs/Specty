package com.rotatsulabs.specty.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Runs the full pipeline: probe, decode to a temp PCM file, measure levels, build the spectrogram. */
class AudioAnalyzer(
    private val context: Context,
    /** Where the CPU work runs; a background scan passes a narrower one than the default. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val peakThreads: Int = 8,
) {

    suspend fun analyze(
        uri: Uri,
        fftSize: Int,
        windowFn: WindowFn = WindowFn.Hann,
        onStage: (stage: String, progress: Float) -> Unit,
    ): AnalysisResult = withContext(dispatcher) {
        fun report(stage: String, progress: Float) {
            ensureActive()
            onStage(stage, progress.coerceIn(0f, 1f))
        }

        report("Reading file", 0f)
        val info = MediaProbe.probe(context, uri)

        // Decoded PCM is far bigger than the file. The limit keeps a long file, or one whose header lies about its
        // length, from filling the storage; it also applies when the header gives no length at all.
        val estimatedBytes = (info.durationSec * info.sampleRate * info.channels * 4).toLong()
        val usable = context.cacheDir.usableSpace
        val limit = StorageGuard.decodeLimit(estimatedBytes, usable)
            ?: throw IOException(StorageGuard.refusal(estimatedBytes, usable))
        val temp = File.createTempFile("pcm_", ".f32", context.cacheDir)
        try {
            report("Decoding", 0.05f)
            val reported = if (Codecs.needsPlatformDecoder(info.codec)) {
                try {
                    PlatformDecoder.decode(context, uri, info.durationSec, temp, limit, { ensureActive() }) { p ->
                        report("Decoding", 0.05f + 0.5f * p)
                    }
                } catch (e: PlatformDecoder.Unavailable) {
                    throw LimitedAnalysisException(info, AuthenticityAnalyzer.unmeasured(info))
                }
            } else {
                AudioDecoder.decode(context, uri, info.durationSec, temp, limit) { p -> report("Decoding", 0.05f + 0.5f * p) }
            }
            if (temp.length() >= limit) throw IOException("This file holds more audio than expected, or storage is too low to decode it")

            // Some codecs decode to another rate or layout than the container declares (HE-AAC, parametric stereo),
            // so the PCM is read as the decoder wrote it.
            val decoded = reported ?: DecodedFormat(info.sampleRate, info.channels)
            PcmFile(temp, decoded.channels, decoded.sampleRate).use { pcm ->
                if (pcm.frames <= 0) throw IOException("The decoder produced no audio")

                // Level measurement (multi-threaded inside) and the spectrogram read the same file independently,
                // so they run side by side.
                val stage = "Analysing"
                val specProgress = java.util.concurrent.atomic.AtomicInteger(0)
                report(stage, 0.55f)
                coroutineScope {
                    val spectrum = async(dispatcher) {
                        PcmFile(temp, decoded.channels, decoded.sampleRate).use { own ->
                            SpectrogramAnalyzer.analyze(own, fftSize, windowFn = windowFn) { p ->
                                ensureActive()
                                specProgress.set((p * 1000).toInt())
                            }
                        }
                    }
                    val levels = LevelAnalyzer.analyze(pcm, peakThreads) { p ->
                        report(stage, 0.55f + 0.45f * (0.85f * p + 0.15f * specProgress.get() / 1000f))
                    }
                    val out = spectrum.await()
                    report(stage, 1f)
                    val authenticity = AuthenticityAnalyzer.assess(info, out.auth, levels.effectiveBitDepth)
                    AnalysisResult(uri, info, levels, authenticity, out.spectrogram, decoded)
                }
            }
        } finally {
            temp.delete()
        }
    }

    companion object {
        private val swept = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * Deletes decode files left behind when the app was killed or the phone rebooted mid-analysis. A file from
         * an earlier process is never in use, so the first call after launch removes every one; they can be GBs.
         */
        fun sweepStale(context: Context) {
            if (!swept.compareAndSet(false, true)) return
            context.cacheDir.listFiles { f -> f.name.startsWith("pcm_") && f.name.endsWith(".f32") }
                ?.forEach { it.delete() }
        }
    }
}
