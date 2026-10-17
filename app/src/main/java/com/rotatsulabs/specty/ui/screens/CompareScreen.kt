package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import com.rotatsulabs.specty.ui.design.components.WavyProgressBar
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.Codecs
import com.rotatsulabs.specty.audio.CompareMath
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.ui.CompareState
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramPalette
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramRenderer
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CompareScreen(state: CompareState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { ExpressiveTopBar("Compare", onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is CompareState.Running -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ShapeLoader(size = 72.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.stage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    WavyProgressBar(state.progress)
                }
                is CompareState.Failed -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    SpectyCard(corners = SpectyShapes.Hero, color = MaterialTheme.colorScheme.errorContainer) {
                        Text("Could not compare", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(12.dp))
                        ExpressiveButton("Back", onBack, kind = ButtonKind.Tonal)
                    }
                }
                is CompareState.Ready -> ReadyContent(state.a, state.b)
                CompareState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ReadyContent(a: AnalysisResult, b: AnalysisResult) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Reveal(0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileTag("A", a, scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f))
                FileTag("B", b, scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f))
            }
        }
        Reveal(1) {
            SectionCard("What differs") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompareMath.highlights(a.authenticity, a.levels, b.authenticity, b.levels).forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Reveal(2) { SpectrogramsCard(a, b) }
        Reveal(3) { DifferenceCard(a.spectrogram, b.spectrogram) }
        Reveal(4) { SpectrumCard(a.spectrogram, b.spectrogram) }
        Reveal(5) { MetricsTable(a, b) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FileTag(letter: String, r: AnalysisResult, container: Color, content: Color, modifier: Modifier) {
    MorphSurface(modifier, SpectyShapes.Tile, container, content, morphOnPress = false) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(letter, container = content.copy(alpha = 0.16f), content = content)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.info.title.withoutExplicitMark(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isExplicit(r.info.tags)) Icon(Icons.Rounded.Explicit, contentDescription = "Explicit", modifier = Modifier.size(18.dp))
                }
                Text(
                    "${Codecs.displayName(r.info.codec)} · ${Fmt.kHz(r.info.sampleRate.toDouble())}",
                    style = MaterialTheme.typography.labelMedium, maxLines = 1,
                )
            }
        }
    }
}

private fun palette(context: android.content.Context): SpectrogramPalette {
    val i = context.getSharedPreferences("specty", android.content.Context.MODE_PRIVATE).getInt("spectrogram_palette", 0)
    return SpectrogramPalette.entries[i.coerceIn(0, SpectrogramPalette.entries.lastIndex)]
}

@Composable
private fun SpectrogramsCard(a: AnalysisResult, b: AnalysisResult) {
    val context = LocalContext.current
    val palette = remember { palette(context) }
    SectionCard("Spectrograms") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StackedImage("A", a.spectrogram, palette)
            StackedImage("B", b.spectrogram, palette)
            Text(
                "Same palette and level scale for both. Each runs from 0 Hz at the bottom to its own Nyquist at the top.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StackedImage(label: String, spec: Spectrogram, palette: SpectrogramPalette) {
    val image by produceState<ImageBitmap?>(null, spec, palette) {
        value = withContext(Dispatchers.Default) { SpectrogramRenderer.render(spec, 0, palette).asImageBitmap() }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 7f).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
        image?.let {
            Image(it, contentDescription = "Spectrogram $label", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
        }
        Pill(
            "$label · ${Fmt.kHz(spec.sampleRate / 2.0)} top",
            Modifier.padding(8.dp).align(Alignment.TopStart),
            container = Color.Black.copy(alpha = 0.6f), content = Color.White,
        )
    }
}

/** Blue where B is quieter than A, red where it is louder, dark where they agree. */
private fun diffBitmap(diff: FloatArray, w: Int, h: Int): Bitmap {
    val pixels = IntArray(w * h)
    for (i in pixels.indices) {
        val t = (diff[i] / 30f).coerceIn(-1f, 1f)
        val base = 26
        pixels[i] = if (t >= 0f) {
            rgb(base + ((255 - base) * t).toInt(), base + ((90 - base) * t).toInt(), base + ((40 - base) * t).toInt())
        } else {
            val u = -t
            rgb(base + ((30 - base) * u).toInt(), base + ((120 - base) * u).toInt(), base + ((255 - base) * u).toInt())
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

@Composable
private fun DifferenceCard(a: Spectrogram, b: Spectrogram) {
    val diff by produceState<Pair<ImageBitmap, Double>?>(null, a, b) {
        value = withContext(Dispatchers.Default) {
            val w = 480; val h = 220
            val d = CompareMath.difference(a, b, w, h)
            diffBitmap(d, w, h).asImageBitmap() to CompareMath.meanAbsolute(d)
        }
    }
    val top = min(a.sampleRate, b.sampleRate) / 2.0
    SectionCard("Difference (B minus A)") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 7f).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                diff?.first?.let {
                    Image(it, contentDescription = "Difference between the two spectrograms", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("B quieter  -30 dB", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5C9BFF))
                Text("same", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+30 dB  B louder", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6A40))
            }
            Text(
                (diff?.second?.let { "Average difference ${String.format(Locale.US, "%.1f", it)} dB. " } ?: "") +
                    "Frequency runs to ${Fmt.kHz(top)}, the lower Nyquist of the two" +
                    if (abs(a.durationSec - b.durationSec) > max(a.durationSec, b.durationSec) * 0.02) ". The files differ in length, so each is stretched to the full width." else ".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpectrumCard(a: Spectrogram, b: Spectrogram) {
    val curves by produceState<Pair<FloatArray, FloatArray>?>(null, a, b) {
        value = withContext(Dispatchers.Default) { CompareMath.averageSpectrum(a) to CompareMath.averageSpectrum(b) }
    }
    val scheme = MaterialTheme.colorScheme
    val colorA = scheme.primary
    val colorB = scheme.tertiary
    val grid = scheme.outlineVariant
    val maxHz = max(a.sampleRate, b.sampleRate) / 2.0
    SectionCard("Average spectrum") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val lo = -110f; val hi = -10f
                fun y(db: Float) = size.height * (1f - ((db - lo) / (hi - lo)).coerceIn(0f, 1f))
                var db = hi
                while (db >= lo) { drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), 1f); db -= 20f }
                var khz = 5
                while (khz * 1000.0 < maxHz) {
                    val x = (khz * 1000.0 / maxHz * size.width).toFloat()
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                    khz += 5
                }
                curves?.let { (ca, cb) ->
                    fun plot(c: FloatArray, sampleRate: Int, color: Color) {
                        val nyq = sampleRate / 2.0
                        val path = Path()
                        val steps = min(c.size, 400)
                        for (s in 0 until steps) {
                            val k = s * (c.size - 1) / max(1, steps - 1)
                            val x = (k.toDouble() / (c.size - 1) * nyq / maxHz * size.width).toFloat()
                            if (s == 0) path.moveTo(x, y(c[k])) else path.lineTo(x, y(c[k]))
                        }
                        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    }
                    plot(ca, a.sampleRate, colorA)
                    plot(cb, b.sampleRate, colorB)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(colorA, "A"); Legend(colorB, "B")
                }
                Text(Fmt.kHz(maxHz), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Text("Mean level of the audible parts, -110 to -10 dB. A shelf that ends early shows where a file was low-passed.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private class Row3(val label: String, val a: String, val b: String, val better: Int = -1)

/** Highest frequency the file keeps, for deciding which side has more top end; null when that is not known. */
private fun topEnd(r: AnalysisResult): Double? {
    val report = r.authenticity
    return report.cutoffHz ?: report.emptyAboveHz ?: r.info.nyquistHz.takeIf { report.shape == com.rotatsulabs.specty.audio.SpectrumShape.FULL_BAND }
}

@Composable
private fun MetricsTable(a: AnalysisResult, b: AnalysisResult) {
    val la = a.levels; val lb = b.levels
    fun pick(x: Double?, y: Double?, higher: Boolean, margin: Double): Int {
        if (x == null || y == null || abs(x - y) < margin) return -1
        return if ((x > y) == higher) 0 else 1
    }
    val rows = listOf(
        Row3("Codec", Codecs.displayName(a.info.codec), Codecs.displayName(b.info.codec)),
        Row3("Sample rate", Fmt.kHz(a.info.sampleRate.toDouble()), Fmt.kHz(b.info.sampleRate.toDouble())),
        Row3("Bit depth", a.info.bitDepth?.let { "$it-bit" } ?: "n/a", b.info.bitDepth?.let { "$it-bit" } ?: "n/a"),
        Row3("Bitrate", Fmt.bitrate(a.info.bitrateBps), Fmt.bitrate(b.info.bitrateBps)),
        Row3("Duration", Fmt.duration(a.info.durationSec), Fmt.duration(b.info.durationSec)),
        Row3("Size", Fmt.size(a.info.sizeBytes), Fmt.size(b.info.sizeBytes)),
        Row3("Loudness", Fmt.lufs(la.lufs), Fmt.lufs(lb.lufs)),
        Row3("Peak", Fmt.db(la.peakDb), Fmt.db(lb.peakDb)),
        Row3("True peak", Fmt.dbtp(la.truePeakDbtp), Fmt.dbtp(lb.truePeakDbtp)),
        Row3("RMS", Fmt.db(la.rmsDb), Fmt.db(lb.rmsDb)),
        Row3("DR meter", la.drTt?.let { "DR${it.roundToInt()}" } ?: "n/a", lb.drTt?.let { "DR${it.roundToInt()}" } ?: "n/a", pick(la.drTt, lb.drTt, true, 1.0)),
        Row3(
            "Clipping",
            if (la.clippedSamples == 0L) "None" else Fmt.count(la.clippedSamples),
            if (lb.clippedSamples == 0L) "None" else Fmt.count(lb.clippedSamples),
            pick(la.clippedSamples.toDouble(), lb.clippedSamples.toDouble(), false, 1.0),
        ),
        Row3("Spectral cutoff", Fmt.spectralEdge(a.authenticity).second, Fmt.spectralEdge(b.authenticity).second, pick(topEnd(a), topEnd(b), true, 1000.0)),
        Row3("Verdict", verdictShort(a), verdictShort(b)),
        Row3(
            "Correlation",
            la.correlation?.let { String.format(Locale.US, "%+.2f", it) } ?: "n/a",
            lb.correlation?.let { String.format(Locale.US, "%+.2f", it) } ?: "n/a",
        ),
    )
    val scheme = MaterialTheme.colorScheme
    SectionCard("Side by side") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Spacer(Modifier.weight(0.34f))
                Text("A", Modifier.weight(0.33f), fontWeight = FontWeight.Bold, color = scheme.primary)
                Text("B", Modifier.weight(0.33f), fontWeight = FontWeight.Bold, color = scheme.tertiary)
            }
            rows.forEachIndexed { i, r ->
                MorphSurface(Modifier.fillMaxWidth(), Corners(14.dp), if (i % 2 == 0) scheme.surfaceContainerHigh else Color.Transparent, morphOnPress = false) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, Modifier.weight(0.34f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Cell(r.a, r.better == 0, Modifier.weight(0.33f))
                        Cell(r.b, r.better == 1, Modifier.weight(0.33f))
                    }
                }
            }
            Text("Highlighted values are the better of the two for dynamics, clipping and top-end extension.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Cell(text: String, better: Boolean, modifier: Modifier) {
    Text(
        text, modifier, style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (better) FontWeight.Bold else FontWeight.Normal,
        color = if (better) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

private fun verdictShort(r: AnalysisResult): String {
    val v = when (r.authenticity.verdict) {
        com.rotatsulabs.specty.audio.Verdict.GENUINE -> "Genuine"
        com.rotatsulabs.specty.audio.Verdict.LOSSY_SOURCE -> "Lossy source"
        com.rotatsulabs.specty.audio.Verdict.POSSIBLE_LOSSY_SOURCE -> "Possibly lossy"
        com.rotatsulabs.specty.audio.Verdict.UPSAMPLED -> "Upsampled"
        com.rotatsulabs.specty.audio.Verdict.BAND_LIMITED -> "Band-limited"
        com.rotatsulabs.specty.audio.Verdict.LOSSY_CODEC -> "Lossy format"
        com.rotatsulabs.specty.audio.Verdict.DOLBY_ATMOS -> "Dolby Atmos"
        com.rotatsulabs.specty.audio.Verdict.INCONCLUSIVE -> "Inconclusive"
    }
    return if (r.authenticity.confidence > 0) "$v (${r.authenticity.confidence}%)" else v
}
