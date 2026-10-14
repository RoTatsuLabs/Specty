package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    // ---- dynamics, stereo, timeline, confidence ------------------------------------------

    private fun levelsOf(channels: Int, rate: Int, seconds: Int, gen: (Int, Int) -> Float): LevelStats {
        val file = writePcm(channels, rate * seconds, gen)
        PcmFile(file, channels, rate).use { return LevelAnalyzer.analyze(it) }
    }

    @Test
    fun fullScaleSineReadsDr0() {
        val rate = 8000
        val m = DynamicsMeter(1, rate)
        val data = FloatArray(rate * 30) { sin(2 * PI * 100.0 * it / rate).toFloat() }
        m.process(data, data.size)
        val dr = m.dynamicRange()!!
        assertTrue("dr $dr", abs(dr) < 0.1)
    }

    @Test
    fun loudBurstsOverQuietBedGiveHigherDr() {
        val rate = 8000
        // 30 s at -30 dBFS with a -10 dBFS section lasting two blocks; peaks stay equal.
        val m = DynamicsMeter(1, rate)
        val data = FloatArray(rate * 30) { i ->
            val loud = i / rate in 12 until 18
            ((if (loud) 0.316 else 0.0316) * sin(2 * PI * 100.0 * i / rate)).toFloat()
        }
        m.process(data, data.size)
        val dr = m.dynamicRange()!!
        // The top 20% of ten blocks are the two loud ones (-10 dBFS RMS+3 dB) against a -10 dBFS peak.
        assertTrue("dr $dr", abs(dr) < 0.5)
        val flat = DynamicsMeter(1, rate)
        val quietBed = FloatArray(rate * 30) { i ->
            val a = if (i / rate in 12 until 18) 0.316 else 0.0316
            val spike = if (i == 5 * rate + 7 || i == 25 * rate + 7) 0.9f else 0f
            (a * sin(2 * PI * 100.0 * i / rate)).toFloat() + spike
        }
        flat.process(quietBed, quietBed.size)
        assertTrue("dr ${flat.dynamicRange()}", flat.dynamicRange()!! > 5.0)
    }

    @Test
    fun silenceHasNoDr() {
        val m = DynamicsMeter(2, 8000)
        val z = FloatArray(8000 * 2 * 10)
        m.process(z, 8000 * 10)
        assertNull(m.dynamicRange())
        assertNull(m.correlation())
    }

    @Test
    fun stereoCorrelationAndBalance() {
        val rate = 8000
        val n = rate * 4
        fun run(gen: (Int, Int) -> Float): DynamicsMeter {
            val m = DynamicsMeter(2, rate)
            val d = FloatArray(n * 2)
            for (f in 0 until n) for (c in 0..1) d[f * 2 + c] = gen(f, c)
            m.process(d, n)
            return m
        }
        val tone = { f: Int -> sin(2 * PI * 440.0 * f / rate).toFloat() }
        val same = run { f, _ -> 0.5f * tone(f) }
        assertTrue(abs(same.correlation()!! - 1.0) < 1e-6)
        assertTrue(abs(same.balanceDb()!!) < 1e-6)
        val inverted = run { f, c -> if (c == 0) 0.5f * tone(f) else -0.5f * tone(f) }
        assertTrue(abs(inverted.correlation()!! + 1.0) < 1e-6)
        val louderLeft = run { f, c -> if (c == 0) 0.5f * tone(f) else 0.25f * tone(f) }
        assertTrue(abs(louderLeft.balanceDb()!! - 6.02) < 0.05)
        val noise = Random(3)
        val independent = run { _, _ -> noise.nextFloat() - 0.5f }
        assertTrue(abs(independent.correlation()!!) < 0.05)
    }

    @Test
    fun dcOffsetIsMeasuredPerChannel() {
        val rate = 8000
        val m = DynamicsMeter(2, rate)
        val d = FloatArray(rate * 2 * 2) { i -> if (i % 2 == 0) 0.1f + 0.2f * sin(i * 0.1).toFloat() else -0.05f }
        m.process(d, rate * 2)
        val dc = m.dcOffsets()
        assertTrue("dc $dc", abs(dc[0] - 0.1) < 0.01 && abs(dc[1] + 0.05) < 1e-6)
    }

    @Test
    fun timelineTracksLoudnessPeaksAndClips() {
        val rate = 8000
        // 20 s: quiet first half, loud second half with a clipped burst at 15 s.
        val stats = levelsOf(1, rate, 20) { f, _ ->
            val sec = f / rate
            val amp = if (sec < 10) 0.05 else 0.5
            if (f in 15 * rate until 15 * rate + 50) 1f else (amp * sin(2 * PI * 440.0 * f / rate)).toFloat()
        }
        val t = stats.timeline!!
        assertEquals(240, t.points)
        assertTrue("first ${t.loudness[10]} last ${t.loudness[200]}", t.loudness[200] - t.loudness[10] > 15f)
        val clipSlice = t.clips.indexOfFirst { it > 0 }
        assertTrue("clip slice $clipSlice", clipSlice in 175..185)
        assertEquals(stats.clippedSamples, t.clips.sum().toLong())
        assertTrue(t.peakDb[200] > t.peakDb[10] + 15f)
        assertNotNull(stats.drTt)
    }

    @Test
    fun segmentCutoffsAndConfidenceForAnMp3Transcode() {
        val rate = 44100
        val file = multitone(rate, 12, 16000.0, 60.0)
        PcmFile(file, 2, rate).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048)
            val base = AuthenticityAnalyzer.analyze(info("flac", rate), out.averageDb, 2048, null)
            val full = AuthenticityAnalyzer.withInsights(
                base, info("flac", rate), out.averageDb, 2048, AuthenticityAnalyzer.segmentSpectra(out.spectrogram),
            )
            assertEquals(Verdict.LOSSY_SOURCE, full.verdict)
            assertTrue("conf ${full.confidence}", full.confidence >= 80)
            assertTrue(full.segmentCutoffs.isNotEmpty() && full.segmentCutoffs.all { abs(it - 16000f) < 700f })
            assertNotNull(full.encoderGuess)
            assertNotNull(full.emptyAboveHz)
            assertTrue(abs(full.emptyAboveHz!! - 16000.0) < 800.0)
        }
    }

    @Test
    fun fullBandSignalHasNoEmptyBandAndGoodConfidence() {
        val rate = 44100
        val file = multitone(rate, 12, 21_900.0, 60.0)
        PcmFile(file, 2, rate).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048)
            val base = AuthenticityAnalyzer.analyze(info("flac", rate), out.averageDb, 2048, null)
            val full = AuthenticityAnalyzer.withInsights(
                base, info("flac", rate), out.averageDb, 2048, AuthenticityAnalyzer.segmentSpectra(out.spectrogram),
            )
            assertEquals(Verdict.GENUINE, full.verdict)
            assertTrue("conf ${full.confidence}", full.confidence >= 80)
            assertNull(full.encoderGuess)
        }
    }

    @Test
    fun encoderGuessTable() {
        assertNull(AuthenticityAnalyzer.encoderGuess(8000.0, "mp3"))
        assertTrue(AuthenticityAnalyzer.encoderGuess(16000.0, "mp3")!!.contains("128"))
        assertTrue(AuthenticityAnalyzer.encoderGuess(20500.0, "mp3")!!.contains("320"))
    }
}
