package com.rotatsulabs.specty.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Runs the full pipeline: probe, decode to a temp PCM file, measure levels, build the spectrogram. */
class AudioAnalyzer(private val context: Context) {

    suspend fun analyze(
        uri: Uri,
        fftSize: Int,
        windowFn: WindowFn = WindowFn.Hann,
        onStage: (stage: String, progress: Float) -> Unit,
    ): AnalysisResult = withContext(Dispatchers.Default) {
        fun report(stage: String, progress: Float) {
            ensureActive()
            onStage(stage, progress.coerceIn(0f, 1f))
        }

        report("Reading file", 0f)
        val info = MediaProbe.probe(context, uri)

        val estimatedBytes = (info.durationSec * info.sampleRate * info.channels * 4).toLong()
        if (estimatedBytes > 0 && context.cacheDir.usableSpace < estimatedBytes + 64L * 1024 * 1024) {
            throw IOException("Not enough free storage to decode this file (about ${estimatedBytes / 1_000_000} MB needed)")
        }

        // A header can understate the length; cap the decode so a lying file cannot fill the storage.
        val limit = if (estimatedBytes > 0) estimatedBytes * 2 + 256L * 1024 * 1024 else 0L
        val temp = File.createTempFile("pcm_", ".f32", context.cacheDir)
        try {
            report("Decoding", 0.05f)
            AudioDecoder.decode(context, uri, info.durationSec, temp, limit) { p -> report("Decoding", 0.05f + 0.5f * p) }
            if (limit > 0 && temp.length() >= limit) throw IOException("This file holds far more audio than its header declares")

            PcmFile(temp, info.channels, info.sampleRate).use { pcm ->
                if (pcm.frames <= 0) throw IOException("The decoder produced no audio")

                // Level measurement (multi-threaded inside) and the spectrogram read the same file independently,
                // so they run side by side.
                val stage = "Analysing"
                val specProgress = java.util.concurrent.atomic.AtomicInteger(0)
                report(stage, 0.55f)
                coroutineScope {
                    val spectrum = async(Dispatchers.Default) {
                        PcmFile(temp, info.channels, info.sampleRate).use { own ->
                            SpectrogramAnalyzer.analyze(own, fftSize, windowFn = windowFn) { p ->
                                ensureActive()
                                specProgress.set((p * 1000).toInt())
                            }
                        }
                    }
                    val levels = LevelAnalyzer.analyze(pcm) { p ->
                        report(stage, 0.55f + 0.45f * (0.85f * p + 0.15f * specProgress.get() / 1000f))
                    }
                    val out = spectrum.await()
                    report(stage, 1f)
                    val base = AuthenticityAnalyzer.analyze(info, out.averageDb, fftSize, levels.effectiveBitDepth)
                    val authenticity = AuthenticityAnalyzer.withInsights(
                        base, info, out.averageDb, fftSize, AuthenticityAnalyzer.segmentSpectra(out.spectrogram),
                    )
                    AnalysisResult(uri, info, levels, authenticity, out.spectrogram)
                }
            }
        } finally {
            temp.delete()
        }
    }

    companion object {
        /** Deletes decode files left behind when the app was killed mid-analysis. */
        fun sweepStale(context: Context, maxAgeMs: Long = 60 * 60 * 1000L) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            context.cacheDir.listFiles { f -> f.name.startsWith("pcm_") && f.name.endsWith(".f32") && f.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }
}
