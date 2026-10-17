package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue("cutoff ${r.cutoffHz}", abs(r.cutoffHz!! - 16_000) < 700)
    }

    @Test
    fun contentUpToTheTopIsGenuineWithoutAnyCutoff() {
        val r = report("flac", 44100, 21_900.0)
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(SpectrumShape.FULL_BAND, r.shape)
        // Nyquist is the end of the band, never a detected cutoff.
        assertNull(r.cutoffHz)
        assertFalse(r.hasCliff)
    }

    @Test
    fun lossyCodecIsReportedAsLossy() {
        assertEquals(Verdict.LOSSY_CODEC, report("mp3", 44100, 16_000.0).verdict)
    }

    @Test
    fun highRateFileThatStopsAt22kHzIsUpsampled() {
        val r = report("flac", 96000, 21_900.0)
        assertEquals(Verdict.UPSAMPLED, r.verdict)
        assertTrue("cutoff ${r.cutoffHz}", abs(r.cutoffHz!! - 22_000) < 900)
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
            val full = AuthenticityAnalyzer.assess(info("flac", rate), out.auth, null)
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
            val full = AuthenticityAnalyzer.assess(info("flac", rate), out.auth, null)
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

    // ---- compare -------------------------------------------------------------------------

    private fun spectrogramOf(rate: Int, maxHz: Double, gainDb: Double = 0.0): Spectrogram {
        val file = multitone(rate, 6, maxHz, 60.0)
        PcmFile(file, 2, rate).use { pcm ->
            val s = SpectrogramAnalyzer.analyze(pcm, 2048).spectrogram
            if (gainDb == 0.0) return s
            val shift = (gainDb / (Spectrogram.MAX_DB - Spectrogram.MIN_DB) * 255.0).toInt()
            val views = s.views.map { v -> ByteArray(v.size) { i -> ((v[i].toInt() and 0xFF) + shift).coerceIn(0, 255).toByte() } }
            return Spectrogram(s.columns, s.bins, s.sampleRate, s.fftSize, s.durationSec, views, s.viewLabels)
        }
    }

    @Test
    fun differenceOfIdenticalSpectrogramsIsZero() {
        val s = spectrogramOf(44100, 20_000.0)
        val d = CompareMath.difference(s, s, 64, 48)
        assertEquals(0.0, CompareMath.meanAbsolute(d), 1e-9)
    }

    @Test
    fun differenceShowsMissingTopEnd() {
        val full = spectrogramOf(44100, 21_000.0)
        val lossy = spectrogramOf(44100, 16_000.0)
        val d = CompareMath.difference(full, lossy, 32, 100)
        // Top rows (above the 16 kHz cutoff) are strongly negative for B; bottom rows are about equal.
        val top = d.copyOfRange(0, 32 * 10).average()
        val bottom = d.copyOfRange(32 * 80, 32 * 100).average()
        assertTrue("top $top", top < -30.0)
        assertTrue("bottom $bottom", abs(bottom) < 6.0)
    }

    @Test
    fun differenceMeasuresGain() {
        val s = spectrogramOf(44100, 20_000.0)
        val louder = spectrogramOf(44100, 20_000.0, gainDb = 6.0)
        val d = CompareMath.difference(s, louder, 32, 32)
        assertTrue("mean ${d.average()}", abs(d.average() - 6.0) < 1.0)
    }

    @Test
    fun averageSpectrumFollowsContent() {
        val s = spectrogramOf(44100, 16_000.0)
        val avg = CompareMath.averageSpectrum(s)
        val binHz = 44100.0 / 2048
        assertTrue(avg[(10_000 / binHz).toInt()] > avg[(19_000 / binHz).toInt()] + 40f)
    }

    @Test
    fun highlightsMentionTheRealDifferences() {
        val rate = 44100
        val full = spectrogramOf(rate, 21_000.0)
        val lossy = spectrogramOf(rate, 16_000.0)
        fun rep(s: Spectrogram, codec: String): AuthenticityReport {
            val avg = CompareMath.averageSpectrum(s)
            return AuthenticityAnalyzer.analyze(info(codec, rate), avg, 2048, null)
        }
        val lv = LevelStats(-1.0, -18.0, -14.0, -0.5, 0, 1000, 24, emptyList(), drTt = 12.0)
        val lv2 = LevelStats(-1.0, -12.0, -9.0, -0.2, 500, 1000, 24, emptyList(), drTt = 6.0)
        val lines = CompareMath.highlights(rep(full, "flac"), lv, rep(lossy, "flac"), lv2)
        val text = lines.joinToString(" ")
        assertTrue(text, text.contains("more dynamic") && text.contains("louder") && text.contains("clipping"))
    }

    // ---- window functions and frequency scale -------------------------------------------

    @Test
    fun windowsAreWellFormed() {
        for (w in WindowFn.entries) {
            val c = w.coefficients(1024)
            assertTrue("$w start", c[0] < 0.1)
            assertTrue("$w peak", abs(c[512] - 1.0) < 1e-9)
            assertTrue("$w range", c.all { it in -1e-9..1.0 + 1e-9 })
        }
    }

    @Test
    fun blackmanHarrisLeaksLessThanHannNearATone() {
        val rate = 44100
        val file = writePcm(1, rate * 3) { f, _ -> (0.5 * sin(2 * PI * 1000.5 * f / rate)).toFloat() }
        fun farLevel(w: WindowFn): Float = PcmFile(file, 1, rate).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048, windowFn = w)
            val binHz = rate / 2048.0
            out.averageDb[((1000.5 / binHz) + 12).toInt()]
        }
        val hann = farLevel(WindowFn.Hann)
        val bh = farLevel(WindowFn.BlackmanHarris)
        assertTrue("hann $hann bh $bh", bh < hann - 10f)
    }

    @Test
    fun frequencyScaleRoundTrips() {
        for (log in listOf(false, true)) {
            for (hz in listOf(100.0, 1000.0, 8000.0, 20000.0)) {
                val back = FreqScale.fracToHz(FreqScale.hzToFrac(hz, 22050.0, log), 22050.0, log)
                assertEquals("$log $hz", hz, back, 1e-6 * hz)
            }
        }
        assertEquals(0.0, FreqScale.hzToFrac(0.0, 22050.0, true), 0.0)
        assertEquals(1.0, FreqScale.hzToFrac(22050.0, 22050.0, true), 1e-12)
    }

    @Test
    fun frequencyTicksAndLabels() {
        assertEquals(listOf(0.0, 5000.0, 10000.0, 15000.0, 20000.0), FreqScale.ticks(22050.0, false))
        assertEquals(listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0), FreqScale.ticks(22050.0, true))
        assertEquals(listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0), FreqScale.ticks(8000.0, true))
        assertEquals("1k", FreqScale.label(1000.0))
        assertEquals("1.5k", FreqScale.label(1500.0))
        assertEquals("200", FreqScale.label(200.0))
        assertEquals("0", FreqScale.label(0.0))
    }

    // ---- tonal palettes ------------------------------------------------------------------

    @Test
    fun oklchRoundTripsThroughArgb() {
        for (argb in intArrayOf(0xFF3366CC.toInt(), 0xFFE8A33D.toInt(), 0xFF808080.toInt(), 0xFF101010.toInt())) {
            val (l, c, h) = com.rotatsulabs.specty.ui.design.tonal.Oklch.fromArgb(argb)
            val back = com.rotatsulabs.specty.ui.design.tonal.Oklch.toArgb(
                com.rotatsulabs.specty.ui.design.tonal.Oklch.toLinearInGamut(l, c, h),
            )
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue("$argb vs $back", abs(((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
    }

    @Test
    fun toneMatchesLuminanceForEveryHue() {
        for (hue in 0 until 360 step 30) {
            val p = com.rotatsulabs.specty.ui.design.tonal.TonalPalette(hue.toDouble(), 0.15)
            var previous = -1.0
            for (tone in intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 95)) {
                val argb = p.tone(tone)
                val lin = doubleArrayOf(
                    srgbLinear((argb shr 16 and 0xFF) / 255.0), srgbLinear((argb shr 8 and 0xFF) / 255.0), srgbLinear((argb and 0xFF) / 255.0),
                )
                val y = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
                val measured = com.rotatsulabs.specty.ui.design.tonal.luminanceToTone(y)
                assertTrue("hue $hue tone $tone measured $measured", abs(measured - tone) < 1.5)
                assertTrue("tones must rise", measured > previous)
                previous = measured
            }
            assertEquals(0xFF000000.toInt(), p.tone(0))
            assertEquals(0xFFFFFFFF.toInt(), p.tone(100))
        }
    }

    private fun srgbLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    @Test
    fun schemesMeetContrastForEverySeed() {
        for (seed in com.rotatsulabs.specty.ui.design.tonal.SeedPalette.entries) {
            val scheme = com.rotatsulabs.specty.ui.design.tonal.SeedScheme(seed)
            for (dark in listOf(false, true)) {
                val r = scheme.roles(dark)
                fun check(name: String, fg: Int, bg: Int, min: Double) {
                    val c = com.rotatsulabs.specty.ui.design.tonal.contrastRatio(fg, bg)
                    assertTrue("$seed dark=$dark $name contrast $c", c >= min)
                }
                check("primary", r.onPrimary, r.primary, 4.5)
                check("primaryContainer", r.onPrimaryContainer, r.primaryContainer, 4.5)
                check("secondaryContainer", r.onSecondaryContainer, r.secondaryContainer, 4.5)
                check("tertiaryContainer", r.onTertiaryContainer, r.tertiaryContainer, 4.5)
                check("errorContainer", r.onErrorContainer, r.errorContainer, 4.5)
                check("surface", r.onSurface, r.surface, 7.0)
                check("surfaceVariant", r.onSurfaceVariant, r.surfaceVariant, 4.5)
                check("surfaceContainerHighest", r.onSurface, r.surfaceContainerHighest, 7.0)
            }
        }
    }
}
