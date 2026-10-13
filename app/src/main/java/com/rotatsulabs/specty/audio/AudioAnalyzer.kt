package com.rotatsulabs.specty.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Runs the full pipeline: probe, decode to a temp PCM file, measure levels, build the spectrogram. */
class AudioAnalyzer(private val context: Context) {

    suspend fun analyze(
        uri: Uri,
        fftSize: Int,
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

        val temp = File.createTempFile("pcm_", ".f32", context.cacheDir)
        try {
            report("Decoding", 0.05f)
            AudioDecoder.decode(context, uri, info.durationSec, temp) { p -> report("Decoding", 0.05f + 0.5f * p) }

            PcmFile(temp, info.channels, info.sampleRate).use { pcm ->
                if (pcm.frames <= 0) throw IOException("The decoder produced no audio")

                report("Measuring levels", 0.55f)
                val levels = LevelAnalyzer.analyze(pcm) { p -> report("Measuring levels", 0.55f + 0.25f * p) }

                report("Building spectrogram", 0.8f)
                val spectrum = SpectrogramAnalyzer.analyze(pcm, fftSize) { p ->
                    report("Building spectrogram", 0.8f + 0.2f * p)
                }

                val authenticity = AuthenticityAnalyzer.analyze(info, spectrum.averageDb, fftSize, levels.effectiveBitDepth)
                AnalysisResult(uri, info, levels, authenticity, spectrum.spectrogram)
            }
        } finally {
            temp.delete()
        }
    }
}
