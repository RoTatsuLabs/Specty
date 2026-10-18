package com.rotatsulabs.specty.ui.spectrogram

import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.Pill
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.TransformOrigin
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Segment
import com.rotatsulabs.specty.ui.design.components.SegmentedControl
import com.rotatsulabs.specty.ui.theme.Motion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rotatsulabs.specty.audio.FreqScale
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Rounds [range] / [target] up to 1, 2 or 5 times a power of ten. */
internal fun niceStep(range: Double, target: Int = 5): Double {
    if (range <= 0.0) return 1.0
    val raw = range / target
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val m = when {
        n <= 1.0 -> 1.0
        n <= 2.0 -> 2.0
        n <= 5.0 -> 5.0
        else -> 10.0
    }
    return m * mag
}

/**
 * Full-screen spectrogram. One finger reads out frequency, time and level under the touch; two fingers
 * zoom (each axis follows the finger spread) and pan; double tap resets the view.
 */
@Composable
fun SpectrogramViewer(spectrogram: Spectrogram, title: String, initialView: Int, onClose: () -> Unit, cutoffHz: Double? = null) {
    val context = LocalContext.current
    var landscape by remember { mutableStateOf(true) }

    DisposableEffect(landscape) {
        val activity = context.findActivity()
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {}
    }
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val prefs = remember { context.getSharedPreferences("specty", Context.MODE_PRIVATE) }
    var view by remember { mutableIntStateOf(initialView.coerceIn(0, spectrogram.views.lastIndex)) }
    var paletteIndex by remember {
        mutableIntStateOf(prefs.getInt("spectrogram_palette", 0).coerceIn(0, SpectrogramPalette.entries.lastIndex))
    }
    val palette = SpectrogramPalette.entries[paletteIndex]
    var options by remember { mutableStateOf(SpecOptions.load(prefs)) }
    val image by produceState<ImageBitmap?>(null, spectrogram, view, palette, options.log, options.range) {
        value = withContext(Dispatchers.Default) { SpectrogramRenderer.render(spectrogram, view, palette, options).asImageBitmap() }
    }

    // Visible window as fractions: time left to right, frequency bottom (0) to top (1).
    var x0 by remember { mutableFloatStateOf(0f) }
    var x1 by remember { mutableFloatStateOf(1f) }
    var f0 by remember { mutableFloatStateOf(0f) }
    var f1 by remember { mutableFloatStateOf(1f) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val minX = (10f / spectrogram.columns).coerceIn(0.005f, 1f)
    val minF = (16f / spectrogram.bins).coerceIn(0.005f, 1f)

    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
    val grid = Color.White.copy(alpha = 0.16f)
    val nyquist = spectrogram.sampleRate / 2.0
    val duration = spectrogram.durationSec

    Box(Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding()) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(spectrogram) { detectTapGestures(onDoubleTap = { x0 = 0f; x1 = 1f; f0 = 0f; f1 = 1f; touch = null }) }
                .pointerInput(spectrogram) {
                    awaitEachGesture {
                        // Measured per gesture so a rotation mid-session is picked up.
                        val left = 52.dp.toPx(); val right = 12.dp.toPx(); val top = BAR_HEIGHT.toPx(); val bottom = 28.dp.toPx()
                        val plotW = (size.width - left - right).coerceAtLeast(1f)
                        val plotH = (size.height - top - bottom).coerceAtLeast(1f)
                        val first = awaitFirstDown(requireUnconsumed = false)
                        touch = first.position
                        var multi = false
                        do {
                            val event = awaitPointerEvent()
                            val down = event.changes.filter { it.pressed }
                            if (down.size >= 2) {
                                multi = true
                                touch = null
                                val a = down[0]; val b = down[1]
                                val prevC = (a.previousPosition + b.previousPosition) / 2f
                                val c = (a.position + b.position) / 2f
                                val pdx = abs(a.previousPosition.x - b.previousPosition.x)
                                val pdy = abs(a.previousPosition.y - b.previousPosition.y)
                                val ndx = abs(a.position.x - b.position.x)
                                val ndy = abs(a.position.y - b.position.y)
                                val pd = hypot(pdx, pdy)
                                val nd = hypot(ndx, ndy)
                                val uniform = if (pd > 1f) nd / pd else 1f
                                val zx = if (pdx > 60f && ndx > 60f) ndx / pdx else uniform
                                val zy = if (pdy > 60f && ndy > 60f) ndy / pdy else uniform
                                val span = x1 - x0
                                val newSpan = (span / zx).coerceIn(minX, 1f)
                                val anchorT = x0 + ((prevC.x - left) / plotW) * span
                                var nx0 = anchorT - ((c.x - left) / plotW) * newSpan
                                nx0 = nx0.coerceIn(0f, 1f - newSpan)
                                val fspan = f1 - f0
                                val newF = (fspan / zy).coerceIn(minF, 1f)
                                val anchorF = f1 - ((prevC.y - top) / plotH) * fspan
                                var nf1 = anchorF + ((c.y - top) / plotH) * newF
                                nf1 = nf1.coerceIn(newF, 1f)
                                x0 = nx0; x1 = nx0 + newSpan
                                f1 = nf1; f0 = nf1 - newF
                                event.changes.forEach { it.consume() }
                            } else if (down.size == 1 && !multi) {
                                touch = down[0].position
                                down[0].consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val left = 52.dp.toPx(); val right = 12.dp.toPx(); val top = BAR_HEIGHT.toPx(); val bottom = 28.dp.toPx()
            val plotW = size.width - left - right
            val plotH = size.height - top - bottom
            image?.let { img ->
                val sx = (x0 * img.width).roundToInt().coerceIn(0, img.width - 1)
                val sw = ((x1 - x0) * img.width).roundToInt().coerceIn(1, img.width - sx)
                val sy = ((1f - f1) * img.height).roundToInt().coerceIn(0, img.height - 1)
                val sh = ((f1 - f0) * img.height).roundToInt().coerceIn(1, img.height - sy)
                clipRect(left, top, left + plotW, top + plotH) {
                    drawImage(
                        img, srcOffset = IntOffset(sx, sy), srcSize = IntSize(sw, sh),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(plotW.roundToInt(), plotH.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }

            // Frequency axis. The image rows already follow the chosen scale, so a row fraction maps straight to y.
            fun yOfFrac(frac: Double) = top + plotH * (1f - ((frac - f0) / (f1 - f0)).toFloat())
            fun axisLine(hz: Double, label: String?) {
                val y = yOfFrac(FreqScale.hzToFrac(hz, nyquist, options.log))
                if (y < top - 1f || y > top + plotH + 1f) return
                drawLine(grid, Offset(left, y), Offset(left + plotW, y), 1f)
                if (label != null) {
                    val layout = measurer.measure(label, axisStyle)
                    drawText(layout, topLeft = Offset(left - 6.dp.toPx() - layout.size.width, (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
                }
            }
            if (options.log) {
                val loHz = FreqScale.fracToHz(f0.toDouble(), nyquist, true)
                val hiHz = FreqScale.fracToHz(f1.toDouble(), nyquist, true)
                val narrow = hiHz / loHz < 4.0
                var decade = 10.0.pow(floor(log10(max(loHz, 1.0))))
                while (decade <= hiHz) {
                    for (m in 1..9) {
                        val hz = m * decade
                        if (hz < loHz || hz > hiHz || hz < FreqScale.LOG_MIN_HZ) continue
                        axisLine(hz, if (m == 1 || m == 2 || m == 5 || narrow) FreqScale.label(hz) else null)
                    }
                    decade *= 10.0
                }
            } else {
                val fLoK = f0 * nyquist / 1000.0
                val fHiK = f1 * nyquist / 1000.0
                val fStep = niceStep(fHiK - fLoK)
                var fk = ceilTo(fLoK, fStep)
                while (fk <= fHiK + 1e-9) {
                    val text = if (fStep < 1.0) String.format(Locale.US, "%.1fk", fk) else "${fk.roundToInt()}k"
                    axisLine(fk * 1000.0, if (fk == 0.0) "0" else text)
                    fk += fStep
                }
            }
            if (options.cutoffLine && cutoffHz != null && cutoffHz < nyquist * 0.99) {
                val y = yOfFrac(FreqScale.hzToFrac(cutoffHz, nyquist, options.log))
                if (y in top..(top + plotH)) {
                    drawLine(
                        Color.White.copy(alpha = 0.9f), Offset(left, y), Offset(left + plotW, y), 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f, 10f)),
                    )
                    val tag = measurer.measure("cutoff ${Fmt.kHz(cutoffHz)}", axisStyle)
                    drawText(tag, topLeft = Offset(left + plotW - tag.size.width - 6.dp.toPx(), (y - tag.size.height - 2.dp.toPx()).coerceAtLeast(top)))
                }
            }
            // Time axis
            val tLo = x0 * duration
            val tHi = x1 * duration
            val tStep = niceStep(tHi - tLo, 6)
            var t = ceilTo(tLo, tStep)
            while (t <= tHi + 1e-9) {
                val x = left + plotW * ((t - tLo) / (tHi - tLo)).toFloat()
                drawLine(grid, Offset(x, top), Offset(x, top + plotH), 1f)
                val label = if (tStep < 1.0) String.format(Locale.US, "%d:%04.1f", (t / 60).toInt(), t % 60) else Fmt.duration(t)
                val layout = measurer.measure(label, axisStyle)
                drawText(layout, topLeft = Offset((x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), top + plotH + 6.dp.toPx()))
                t += tStep
            }

            touch?.let { p ->
                if (p.x in left..(left + plotW) && p.y in top..(top + plotH)) {
                    drawLine(Color.White, Offset(p.x, top), Offset(p.x, top + plotH), 1.5f)
                    drawLine(Color.White, Offset(left, p.y), Offset(left + plotW, p.y), 1.5f)
                    drawCircle(Color.White, 6.dp.toPx(), p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
        }

        // Readout
        val readout = touch?.let { p ->
            val density = context.resources.displayMetrics.density
            val left = 52 * density; val right = 12 * density; val top = BAR_HEIGHT.value * density; val bottom = 28 * density
            val w = canvasSize.width - left - right
            val h = canvasSize.height - top - bottom
            if (w <= 0f || h <= 0f || p.x < left || p.x > left + w || p.y < top || p.y > top + h) null else {
                val tFrac = x0 + (p.x - left) / w * (x1 - x0)
                val fFrac = f1 - (p.y - top) / h * (f1 - f0)
                val col = (tFrac * (spectrogram.columns - 1)).roundToInt().coerceIn(0, spectrogram.columns - 1)
                val bin = (FreqScale.fracToHz(fFrac.toDouble(), nyquist, options.log) / nyquist * (spectrogram.bins - 1)).roundToInt().coerceIn(0, spectrogram.bins - 1)
                val q = spectrogram.views[view][col * spectrogram.bins + bin].toInt() and 0xFF
                val db = Spectrogram.MIN_DB + q / 255.0 * (Spectrogram.MAX_DB - Spectrogram.MIN_DB)
                val hz = FreqScale.fracToHz(fFrac.toDouble(), nyquist, options.log)
                val seconds = tFrac * duration
                String.format(Locale.US, "%.2f kHz   %s   %.0f dB", hz / 1000.0, formatTime(seconds), db)
            }
        }

        var showOptions by remember { mutableStateOf(false) }
        fun resetZoom() { x0 = 0f; x1 = 1f; f0 = 0f; f1 = 1f; touch = null }

        // The bar owns the top strip; the plot starts below it, so nothing is covered.
        Row(
            Modifier.fillMaxWidth().height(BAR_HEIGHT).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveIconButton(Icons.Rounded.Close, "Close", onClose)
            Box(Modifier.weight(1f)) {
                Pill(
                    readout ?: title,
                    container = Color.Black.copy(alpha = 0.55f), content = Color.White,
                )
            }
            if (spectrogram.views.size > 1) {
                ExpressiveButton(
                    spectrogram.displayLabels[view], { view = (view + 1) % spectrogram.views.size },
                    kind = ButtonKind.Tonal, modifier = Modifier.widthIn(min = 72.dp),
                )
            }
            ExpressiveIconButton(Icons.Rounded.Tune, "Display options", { showOptions = !showOptions }, toggled = showOptions)
            ExpressiveIconButton(Icons.Rounded.CropRotate, "Toggle landscape lock", { landscape = !landscape }, toggled = landscape)
            ExpressiveIconButton(Icons.Rounded.ZoomOutMap, "Reset zoom", { resetZoom() })
        }

        AnimatedVisibility(
            visible = showOptions,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = BAR_HEIGHT, end = 12.dp),
            enter = fadeIn(Motion.effects()) + scaleIn(Motion.spatial(), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(Motion.effects()) + scaleOut(Motion.spatial(), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            MorphSurface(
                Modifier.width(380.dp).heightIn(max = 280.dp),
                Corners(24.dp), Color(0xFF1B171A).copy(alpha = 0.96f), Color.White, morphOnPress = false,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val caption = MaterialTheme.typography.labelMedium
                    Text("Colour palette", style = caption, color = Color.White.copy(alpha = 0.7f))
                    SegmentedControl(
                        segments = SpectrogramPalette.entries.mapIndexed { i, p -> Segment(i, p.label) },
                        selected = paletteIndex,
                        onSelect = { paletteIndex = it; prefs.edit().putInt("spectrogram_palette", it).apply() },
                        scrollable = true,
                        modifier = Modifier.fillMaxWidth(),
                        indicator = MaterialTheme.colorScheme.secondaryContainer,
                        onIndicator = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text("Frequency scale", style = caption, color = Color.White.copy(alpha = 0.7f))
                    SegmentedControl(
                        segments = listOf(Segment(false, "Linear"), Segment(true, "Log")),
                        selected = options.log,
                        onSelect = { options = options.copy(log = it).also { o -> o.save(prefs) }; resetZoom() },
                        modifier = Modifier.fillMaxWidth(),
                        indicator = MaterialTheme.colorScheme.secondaryContainer,
                        onIndicator = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text("Level range", style = caption, color = Color.White.copy(alpha = 0.7f))
                    SegmentedControl(
                        segments = DbRange.entries.map { Segment(it, it.label) },
                        selected = options.range,
                        onSelect = { options = options.copy(range = it).also { o -> o.save(prefs) } },
                        modifier = Modifier.fillMaxWidth(),
                        indicator = MaterialTheme.colorScheme.secondaryContainer,
                        onIndicator = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/** Height of the control strip above the plot. */
private val BAR_HEIGHT = 64.dp

private fun ceilTo(value: Double, step: Double): Double = kotlin.math.ceil(value / step - 1e-9) * step

private fun formatTime(seconds: Double): String {
    val m = (seconds / 60).toInt()
    val s = seconds - m * 60
    return String.format(Locale.US, "%d:%04.1f", m, s)
}
