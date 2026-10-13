package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.cos

class AudioDspTest {

    // ---- helpers -------------------------------------------------------------------------

    private fun writePcm(channels: Int, frames: Int, gen: (Int, Int) -> Float): File {
        val file = File.createTempFile("specty", ".f32")
        file.deleteOnExit()
        val bb = ByteBuffer.allocate(frames * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) for (c in 0 until channels) bb.putFloat(gen(f, c))
        file.writeBytes(bb.array())
        return file
    }

    private fun lufsOf(rate: Int, channels: Int, seconds: Int, gen: (Int, Int) -> Float): Double? {
        val file = writePcm(channels, rate * seconds, gen)
        PcmFile(file, channels, rate).use { pcm ->
            val meter = LoudnessMeter(rate, channels)
            pcm.forEachChunk { buf, n -> meter.process(buf, n) }
            return meter.integratedLufs()
        }
    }

    private fun info(codec: String, rate: Int, depth: Int? = 24) =
        AudioInfo("test", codec, null, rate, 2, depth, "s32p", null, 10.0, 1000)

    /** Equal-level sines every [stepHz] from 200 Hz up to [maxHz]; a band-limited, music-like signal. */
    private fun multitone(rate: Int, seconds: Int, maxHz: Double, stepHz: Double): File {
        val n = rate * seconds
        val freqs = generateSequence(200.0) { it + stepHz }.takeWhile { it <= maxHz }.toList()
        val rnd = Random(7)
        val phases = freqs.map { rnd.nextDouble() * 2 * PI }
        val gain = 0.3 / sqrt(freqs.size.toDouble())
        val data = FloatArray(n)
        for (i in 0 until n) {
            var s = 0.0
            for (j in freqs.indices) s += sin(2 * PI * freqs[j] * i / rate + phases[j])
            data[i] = (s * gain).toFloat()
        }
        return writePcm(2, n) { f, _ -> data[f] }
    }

    private fun report(codec: String, rate: Int, maxHz: Double, effectiveBits: Int? = null): AuthenticityReport {
        val file = multitone(rate, 6, maxHz, 60.0)
        PcmFile(file, 2, rate).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048)
            return AuthenticityAnalyzer.analyze(info(codec, rate), out.averageDb, 2048, effectiveBits)
        }
    }

    // ---- FFT -----------------------------------------------------------------------------

    @Test
    fun fftFindsTheRightBin() {
        val n = 1024
        val re = DoubleArray(n) { cos(2 * PI * 100 * it / n) }
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        var best = 0
        var bestMag = 0.0
        for (k in 0 until n / 2) {
            val m = hypot(re[k], im[k])
            if (m > bestMag) { bestMag = m; best = k }
        }
        assertEquals(100, best)
        assertTrue("magnitude $bestMag", abs(bestMag - n / 2.0) < 1e-6)
    }

    // ---- loudness ------------------------------------------------------------------------

    @Test
    fun stereoSineAtMinus23IsMinus23Lufs() {
        for (rate in intArrayOf(44100, 48000, 96000)) {
            val amp = 10.0.pow(-23.0 / 20.0)
            val lufs = lufsOf(rate, 2, 20) { f, _ -> (amp * sin(2 * PI * 997.0 * f / rate)).toFloat() }
            assertTrue("rate $rate gave $lufs", lufs != null && abs(lufs + 23.0) < 0.1)
        }
    }

    @Test
    fun monoFullScaleSineIsAboutMinus3Lufs() {
        val rate = 48000
        val lufs = lufsOf(rate, 1, 20) { f, _ -> sin(2 * PI * 997.0 * f / rate).toFloat() }
        assertTrue("got $lufs", lufs != null && abs(lufs + 3.01) < 0.1)
    }

    @Test
    fun quietTailIsGatedOut() {
        val rate = 48000
        val loud = 10.0.pow(-20.0 / 20.0)
        val quiet = 10.0.pow(-60.0 / 20.0)
        val lufs = lufsOf(rate, 2, 20) { f, _ ->
            ((if (f < rate * 10) loud else quiet) * sin(2 * PI * 997.0 * f / rate)).toFloat()
        }
        assertTrue("got $lufs", lufs != null && abs(lufs + 20.0) < 0.15)
    }

    @Test
    fun emptyInputHasNoLoudness() {
        assertNull(lufsOf(48000, 2, 0) { _, _ -> 0f })
    }

    // ---- true peak -----------------------------------------------------------------------

    @Test
    fun interSamplePeakIsFoundAboveTheSamplePeak() {
        val rate = 44100
        // A tone at fs/4 phased so every sample is +/-0.7071 while the waveform peaks at 1.0.
        val file = writePcm(1, rate * 2) { i, _ -> sin(PI / 4 + i * PI / 2).toFloat() }
        PcmFile(file, 1, rate).use { pcm ->
            val meter = TruePeakMeter(1, rate)
            pcm.forEachChunk { buf, n -> meter.process(buf, n) }
            val db = LevelAnalyzer.toDb(meter.peak)
            assertTrue("true peak $db dBTP", abs(db) < 0.25 && meter.peak > 0.7071)
        }
    }

    @Test
    fun lowLevelSineKeepsItsTruePeak() {
        val rate = 44100
        val file = writePcm(1, rate * 2) { i, _ -> (0.5 * sin(2 * PI * 997.0 * i / rate)).toFloat() }
        PcmFile(file, 1, rate).use { pcm ->
            val meter = TruePeakMeter(1, rate)
            pcm.forEachChunk { buf, n -> meter.process(buf, n) }
            assertTrue("got ${LevelAnalyzer.toDb(meter.peak)}", abs(LevelAnalyzer.toDb(meter.peak) + 6.02) < 0.1)
        }
    }

    // ---- level statistics ----------------------------------------------------------------

    @Test
    fun peakRmsAndDynamicRange() {
        val rate = 44100
        val file = writePcm(2, rate * 4) { i, c ->
            val amp = if (c == 0) 0.5 else 0.25
            (amp * sin(2 * PI * 440.0 * i / rate)).toFloat()
        }
        PcmFile(file, 2, rate).use { pcm ->
            val stats = LevelAnalyzer.analyze(pcm)
            assertTrue("peak ${stats.peakDb}", abs(stats.peakDb + 6.02) < 0.05)
            assertTrue("rms ${stats.channels[0].rmsDb}", abs(stats.channels[0].rmsDb + 9.03) < 0.05)
            assertTrue("dr ${stats.channels[0].dynamicRangeDb}", abs(stats.channels[0].dynamicRangeDb - 3.01) < 0.05)
            assertEquals(0L, stats.clippedSamples)
            assertEquals(rate * 4L, stats.framesPerChannel)
        }
    }

    @Test
    fun clippingCountsRunsNotIsolatedPeaks() {
        val file = writePcm(1, 10_000) { i, _ -> if (i in 100..104 || i == 500) 1.0f else 0.1f }
        PcmFile(file, 1, 44100).use { pcm ->
            assertEquals(5L, LevelAnalyzer.analyze(pcm).clippedSamples)
        }
    }

    @Test
    fun effectiveBitDepthIsDetected() {
        val rnd = Random(1)
        val sixteen = writePcm(1, 20_000) { _, _ -> ((rnd.nextInt(60_000) - 30_000) / 32768.0).toFloat() }
        PcmFile(sixteen, 1, 44100).use { assertEquals(16, LevelAnalyzer.analyze(it).effectiveBitDepth) }

        val rnd2 = Random(2)
        val twentyFour = writePcm(1, 20_000) { _, _ -> ((rnd2.nextInt(1 shl 24) - (1 shl 23)) / 8388608.0).toFloat() }
        PcmFile(twentyFour, 1, 44100).use { assertEquals(24, LevelAnalyzer.analyze(it).effectiveBitDepth) }
    }

    // ---- spectrogram and authenticity ----------------------------------------------------

    @Test
    fun spectrogramPlacesASineOnTheRightBinAtTheRightLevel() {
        val rate = 44100
        // 1033.59375 Hz is exactly bin 48 for a 2048-point FFT at 44.1 kHz.
        val file = writePcm(1, rate * 3) { i, _ -> (0.5 * sin(2 * PI * 1033.59375 * i / rate)).toFloat() }
        PcmFile(file, 1, rate).use { pcm ->
            val sg = SpectrogramAnalyzer.analyze(pcm, 2048).spectrogram
            val col = sg.columns / 2
            var best = 0
            var bestValue = -1
            for (k in 0 until sg.bins) {
                val v = sg.views[0][col * sg.bins + k].toInt() and 0xFF
                if (v > bestValue) { bestValue = v; best = k }
            }
            val db = bestValue / 255.0 * 120.0 - 120.0
            assertEquals(48, best)
            assertTrue("level $db dBFS", abs(db + 6.02) < 0.6)
        }
    }

    @Test
    fun losslessFileWithA16kHzCliffIsFlaggedAsLossySource() {
        val r = report("alac", 44100, 16_000.0)
        assertEquals(Verdict.LOSSY_SOURCE, r.verdict)
        assertTrue("cutoff ${r.cutoffHz}", abs(r.cutoffHz - 16_000) < 700)
    }

    @Test
    fun fullBandFileIsGenuine() {
        val r = report("flac", 44100, 21_900.0)
        assertEquals(Verdict.GENUINE, r.verdict)
        assertTrue(r.cutoffHz > 22_000)
    }

    @Test
    fun lossyCodecIsReportedAsLossy() {
        assertEquals(Verdict.LOSSY_CODEC, report("mp3", 44100, 16_000.0).verdict)
    }

    @Test
    fun highRateFileThatStopsAt22kHzIsUpsampled() {
        val r = report("flac", 96000, 21_900.0)
        assertEquals(Verdict.UPSAMPLED, r.verdict)
        assertTrue("cutoff ${r.cutoffHz}", abs(r.cutoffHz - 22_000) < 900)
    }

    @Test
    fun cliffNear20kHzIsOnlyPossiblyLossy() {
        assertEquals(Verdict.POSSIBLE_LOSSY_SOURCE, report("flac", 44100, 19_800.0).verdict)
    }

    @Test
    fun sixteenBitContentInA24BitFileIsCalledOut() {
        val r = report("flac", 44100, 21_900.0, effectiveBits = 16)
        assertEquals(Verdict.GENUINE, r.verdict)
        assertTrue(r.note, r.note.contains("16-bit"))
    }

    @Test
    fun silenceIsInconclusive() {
        val file = writePcm(2, 44100 * 3) { _, _ -> 0f }
        PcmFile(file, 2, 44100).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048)
            assertEquals(Verdict.INCONCLUSIVE, AuthenticityAnalyzer.analyze(info("flac", 44100), out.averageDb, 2048, null).verdict)
        }
    }
}
