// Compile against :core:audio and run: kotlinc -cp <core-audio-classes> Evaluate.kt -d out && java -cp out:<core-audio-classes>:<kotlin-stdlib> EvaluateKt <corpus-dir> [id-filter] > results.csv
import com.rotatsulabs.specty.audio.*
import java.io.File
import java.util.Locale

fun main(args: Array<String>) {
    val dir = File(args[0])
    val only = args.getOrNull(1)
    println("id,truth,hires_truth,rate,bits,verdict,cutoff_hz,drop_db,shape,confidence,floor_db,floor_level,eff_bits,gap_db,top_margin,top_slope,hires,notes")
    for (line in File(dir, "manifest.csv").readLines().drop(1)) {
        val c = line.split(",", limit = 6)
        val id = c[0]
        if (only != null && !id.contains(only)) continue
        val file = File(dir, "files/$id.flac")
        val probe = run("ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
            "stream=codec_name,sample_rate,channels,bits_per_raw_sample", "-of", "csv=p=0", file.path).trim().split(",")
        val rate = probe[1].toInt()
        val channels = probe[2].toInt()
        val bits = probe[3].toIntOrNull()
        val pcmFile = File.createTempFile("corpus", ".f32")
        try {
            run("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", file.path, "-map", "0:a:0",
                "-vn", "-sn", "-dn", "-f", "f32le", "-acodec", "pcm_f32le", pcmFile.path)
            val info = AudioInfo(id, probe[0], "flac", rate, channels, bits, null, null, 30.0, file.length())
            PcmFile(pcmFile, channels, rate).use { pcm ->
                val levels = LevelAnalyzer.analyze(pcm, 4)
                val auth = AuthSpectrumAnalyzer.compute(pcm)
                val upsample = UpsampleEvidence.scan(pcm)
                val r = AuthenticityAnalyzer.reassure(AuthenticityAnalyzer.assess(info, auth, levels.effectiveBitDepth, upsample), info)
                val d = AuthenticityAnalyzer.detect(auth.whole, auth.sampleRate, auth.fftSize)
                val top = TopContent.marginDb(auth.peak, auth.quietFloorVariance, auth.sampleRate, auth.fftSize)
                val floor = NoiseFloor.classify(auth.quietFloorVariance)
                fun f(v: Double?) = if (v == null) "" else String.format(Locale.US, "%.1f", v)
                println(listOf(
                    id, c[1], c[2], rate, bits ?: "", r.verdict, f(r.cutoffHz), f(r.cliffDropDb), r.shape, r.confidence,
                    f(floor?.vs16BitDb), floor?.level ?: "", levels.effectiveBitDepth ?: "", f(d.gap),
                    f(top), f(d.topSlope), r.hiRes ?: "",
                    r.notes.joinToString("|") { it.key.name },
                ).joinToString(","))
            }
        } finally {
            pcmFile.delete()
        }
    }
}

private fun run(vararg cmd: String): String {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    check(p.waitFor() == 0) { "${cmd[0]} failed: $out" }
    return out
}
