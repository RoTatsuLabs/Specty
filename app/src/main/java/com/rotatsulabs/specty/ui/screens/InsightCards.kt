package com.rotatsulabs.specty.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.LevelStats
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.components.Stat
import com.rotatsulabs.specty.ui.components.StatGrid
import com.rotatsulabs.specty.ui.components.rememberGrow
import com.rotatsulabs.specty.ui.theme.Motion
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

/** How sure the verdict is, where in the track the cutoff was found, and what encoder it resembles. */
@Composable
internal fun ConfidenceCard(report: AuthenticityReport, info: AudioInfo) {
    if (report.verdict == Verdict.INCONCLUSIVE) return
    val scheme = MaterialTheme.colorScheme
    val target = report.confidence / 100f
    val grow by rememberGrow(450)
    val animated = (target * grow).coerceIn(0f, 1f)
    val label = when {
        report.confidence >= 80 -> "High confidence"
        report.confidence >= 55 -> "Medium confidence"
        else -> "Low confidence"
    }
    SectionCard("Verdict confidence") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${(animated * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            val track = scheme.surfaceContainerHighest
            val fill = scheme.primary
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                drawRoundRect(track, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                drawRoundRect(fill, size = Size(size.width * animated, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            }
            if (report.segmentCutoffs.size >= 2) CutoffTimeline(report, info)
            if (report.sectionsJudged > 0 && report.verdict != Verdict.LOSSY_CODEC && report.verdict != Verdict.DOLBY_ATMOS) {
                InsightLine("Matches the overall reading in ${report.sectionsAgreeing} of ${report.sectionsJudged} sections that could be read clearly.")
            }
            report.encoderGuess?.let { InsightLine("Resembles: $it.") }
            report.emptyAboveHz?.let { InsightLine("Nothing above ${f1(it / 1000.0)} kHz; the band up to ${f1(info.nyquistHz / 1000.0)} kHz is empty.") }
        }
    }
}

@Composable
private fun InsightLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One bar per slice of the track; bar height is the cutoff as a share of Nyquist. */
@Composable
private fun CutoffTimeline(report: AuthenticityReport, info: AudioInfo) {
    val scheme = MaterialTheme.colorScheme
    val bar = scheme.primary
    val none = scheme.surfaceContainerHighest
    val line = scheme.tertiary
    val cuts = report.segmentCutoffs
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Cutoff across the track", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        val reveal by rememberGrow(600)
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (cuts.size - 1)) / cuts.size
            cuts.forEachIndexed { i, hz ->
                val x = i * (w + gap)
                if (hz <= 0f) {
                    drawRoundRect(none, Offset(x, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(w / 2))
                } else {
                    val h = size.height * (hz / info.nyquistHz.toFloat()).coerceIn(0.05f, 1f) * reveal.coerceIn(0f, 1f)
                    drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
                }
            }
            report.cutoffHz?.let { hz ->
                val y = size.height * (1f - (hz / info.nyquistHz).toFloat().coerceIn(0f, 1f))
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        val found = cuts.count { it > 0f }
        Text(
            if (found == 0) "No cutoff found in any part of the track."
            else "Cutoff found in $found of ${cuts.size} sections" + (report.cutoffHz?.let { ", line marks ${f1(it / 1000.0)} kHz." } ?: "."),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Loudness and peak over time with clip markers; touch or drag to read a point. */
@Composable
internal fun TimelineCard(levels: LevelStats) {
    val t = levels.timeline ?: return
    val scheme = MaterialTheme.colorScheme
    val loudColor = scheme.primary
    val peakColor = scheme.tertiary.copy(alpha = 0.28f)
    val clipColor = scheme.error
    val grid = scheme.outlineVariant
    var sel by remember { mutableStateOf(-1) }
    val reveal by rememberGrow(500)

    val audible = t.loudness.filter { it > -69.9f }
    val lo = if (audible.isEmpty()) -60f else (((audible.min() - 6f) / 10f).toInt() * 10f - 10f).coerceAtLeast(-70f)
    val hi = 0f

    SectionCard("Loudness over time") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(150.dp)
                    .pointerInput(t) {
                        detectTapGestures { p -> sel = (p.x / size.width * t.points).toInt().coerceIn(0, t.points - 1) }
                    }
                    .pointerInput(t) {
                        detectDragGestures(
                            onDragStart = { p -> sel = (p.x / size.width * t.points).toInt().coerceIn(0, t.points - 1) },
                            onDrag = { change, _ -> sel = (change.position.x / size.width * t.points).toInt().coerceIn(0, t.points - 1) },
                        )
                    },
            ) {
                fun yOf(db: Float) = size.height * (1f - ((db - lo) / (hi - lo)).coerceIn(0f, 1f) * reveal.coerceIn(0f, 1f))
                fun xOf(i: Int) = (i + 0.5f) / t.points * size.width
                // Grid every 10 dB.
                var g = hi
                while (g >= lo) {
                    drawLine(grid, Offset(0f, yOf(g)), Offset(size.width, yOf(g)), strokeWidth = 1f)
                    g -= 10f
                }
                val area = Path().apply {
                    moveTo(0f, size.height)
                    for (i in 0 until t.points) lineTo(xOf(i), yOf(t.peakDb[i]))
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(area, peakColor)
                val line = Path()
                for (i in 0 until t.points) if (i == 0) line.moveTo(xOf(i), yOf(t.loudness[i])) else line.lineTo(xOf(i), yOf(t.loudness[i]))
                drawPath(line, loudColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                for (i in 0 until t.points) {
                    if (t.clips[i] > 0) drawLine(clipColor, Offset(xOf(i), 0f), Offset(xOf(i), 10.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
                if (sel >= 0) {
                    drawLine(scheme.onSurface, Offset(xOf(sel), 0f), Offset(xOf(sel), size.height), strokeWidth = 1.5.dp.toPx())
                    drawCircle(loudColor, 5.dp.toPx(), Offset(xOf(sel), yOf(t.loudness[sel])))
                }
            }
            val readout = if (sel >= 0) {
                val time = (sel + 0.5) / t.points * t.durationSec
                "${Fmt.duration(time)}  ·  ${if (t.loudness[sel] <= -69.9f) "silence" else f1(t.loudness[sel].toDouble()) + " LUFS"}  ·  peak ${f1(t.peakDb[sel].toDouble())} dBFS" +
                    if (t.clips[sel] > 0) "  ·  ${t.clips[sel]} clipped" else ""
            } else {
                "Touch the graph to read a point. Line: loudness, shaded: peak, red ticks: clipping."
            }
            Text(readout, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

            val stats = buildList {
                levels.drTt?.let { add(Stat(Icons.Rounded.Equalizer, "DR meter", "DR${it.roundToInt()}")) }
                val loudest = t.loudness.withIndex().maxByOrNull { it.value }
                if (loudest != null && loudest.value > -69.9f) {
                    add(Stat(Icons.AutoMirrored.Rounded.ShowChart, "Loudest part", Fmt.duration((loudest.index + 0.5) / t.points * t.durationSec)))
                }
                val clipSlices = t.clips.count { it > 0 }
                add(Stat(Icons.Rounded.Tune, "Clipped sections", if (clipSlices == 0) "None" else "$clipSlices of ${t.points}"))
            }
            StatGrid(stats)
        }
    }
}

/** Stereo image: correlation, left/right balance and DC offset. */
@Composable
internal fun StereoCard(levels: LevelStats) {
    val corr = levels.correlation
    val balance = levels.balanceDb
    val dc = levels.dcOffsets
    val worstDc = dc.maxOfOrNull { abs(it) } ?: 0.0
    if (corr == null && balance == null && worstDc < 1e-6) return
    val scheme = MaterialTheme.colorScheme
    SectionCard("Stereo and offset") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (corr != null) {
                MeterRow(
                    title = "Correlation",
                    value = String.format(Locale.US, "%+.2f", corr),
                    position = ((corr + 1.0) / 2.0).toFloat(),
                    left = "-1 out of phase", right = "+1 mono",
                    accent = if (corr < 0.0) scheme.error else scheme.primary,
                )
                Text(
                    when {
                        corr < -0.3 -> "Strong phase cancellation: this can lose level or vanish when summed to mono."
                        corr < 0.2 -> "Very wide image with little in common between the channels."
                        corr < 0.9 -> "Normal stereo width."
                        else -> "Nearly identical channels; effectively mono."
                    },
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            if (balance != null) {
                MeterRow(
                    title = "Balance",
                    value = if (abs(balance) < 0.05) "Centred" else "${f1(abs(balance))} dB ${if (balance > 0) "left" else "right"}",
                    position = ((balance.coerceIn(-12.0, 12.0) + 12.0) / 24.0).toFloat(),
                    left = "L", right = "R",
                    accent = if (abs(balance) > 3.0) scheme.tertiary else scheme.primary,
                    invert = true,
                )
            }
            val dcText = dc.withIndex().joinToString("  ") { (i, v) -> "Ch${i + 1} ${String.format(Locale.US, "%+.4f", v * 100)}%" }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("DC offset", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Text(dcText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            if (worstDc > 0.005) {
                Text("A DC offset above 0.5% wastes headroom and can cause clicks; a high-pass filter removes it.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** A horizontal scale with a marker; [invert] puts "left" louder on the left side of the scale. */
@Composable
private fun MeterRow(title: String, value: String, position: Float, left: String, right: String, accent: Color, invert: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(if (invert) 1f - position else position, Motion.effects(), label = "meter")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        val track = scheme.surfaceContainerHighest
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRoundRect(track, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            drawLine(scheme.outline, Offset(size.width / 2, 2f), Offset(size.width / 2, size.height - 2f), strokeWidth = 1.5f)
            drawCircle(accent, size.height / 2, Offset(size.height / 2 + (size.width - size.height) * animated, size.height / 2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(right, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}
