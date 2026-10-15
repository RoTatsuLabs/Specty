package com.rotatsulabs.specty.ui.spectrogram

import androidx.compose.material.icons.rounded.Check
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Segment
import com.rotatsulabs.specty.ui.design.components.SegmentedControl
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.drawscope.clipRect
import com.rotatsulabs.specty.ui.components.pressScale
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotatsulabs.specty.audio.FreqScale
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SpectrogramCard(
    spectrogram: Spectrogram,
    modifier: Modifier = Modifier,
    title: String = "Spectrogram",
    onFullscreen: ((view: Int) -> Unit)? = null,
    cutoffHz: Double? = null,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    if (selected >= spectrogram.views.size) selected = 0

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences("specty", android.content.Context.MODE_PRIVATE)
    var paletteIndex by remember {
        mutableIntStateOf(prefs.getInt("spectrogram_palette", 0).coerceIn(0, SpectrogramPalette.entries.lastIndex))
    }
    val palette = SpectrogramPalette.entries[paletteIndex]
    var options by remember { mutableStateOf(SpecOptions.load(prefs)) }

    val image by produceState<ImageBitmap?>(initialValue = null, spectrogram, selected, palette, options.log, options.range) {
        value = withContext(Dispatchers.Default) {
            SpectrogramRenderer.render(spectrogram, selected, palette, options).asImageBitmap()
        }
    }

    // Left-to-right wipe whenever a fresh image arrives (first load, channel or palette change).
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(image) {
        if (image != null) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val gridColor = Color.White.copy(alpha = 0.14f)

    MorphSurface(
        modifier = modifier.fillMaxWidth(),
        corners = SpectyShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        morphOnPress = false,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedControl(
                segments = spectrogram.viewLabels.mapIndexed { i, l -> Segment(i, l) },
                selected = selected,
                onSelect = { selected = it },
                scrollable = spectrogram.viewLabels.size > 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Canvas(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val left = 40.dp.toPx()
                val right = 6.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = 20.dp.toPx()
                val plotWidth = size.width - left - right
                val plotHeight = size.height - top - bottom

                drawRect(Color.Black, topLeft = Offset(left, top), size = Size(plotWidth, plotHeight))
                image?.let { img ->
                    clipRect(left = left, top = top, right = left + plotWidth * reveal.value, bottom = top + plotHeight) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(plotWidth.roundToInt(), plotHeight.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                    }
                }

                // Frequency axis
                val nyquist = spectrogram.sampleRate / 2.0
                for (hz in FreqScale.ticks(nyquist, options.log)) {
                    val y = top + plotHeight * (1f - FreqScale.hzToFrac(hz, nyquist, options.log).toFloat())
                    drawLine(gridColor, Offset(left, y), Offset(left + plotWidth, y), strokeWidth = 1f)
                    val layout = measurer.measure(FreqScale.label(hz), labelStyle)
                    val labelY = (y - layout.size.height / 2f).coerceIn(0f, size.height - bottom - layout.size.height + 4.dp.toPx())
                    drawText(layout, topLeft = Offset(left - 6.dp.toPx() - layout.size.width, labelY))
                }
                if (options.cutoffLine && cutoffHz != null && cutoffHz < nyquist * 0.99) {
                    val y = top + plotHeight * (1f - FreqScale.hzToFrac(cutoffHz, nyquist, options.log).toFloat())
                    drawLine(
                        Color.White.copy(alpha = 0.9f), Offset(left, y), Offset(left + plotWidth, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    )
                    val tag = measurer.measure("cutoff ${Fmt.kHz(cutoffHz)}", TextStyle(fontSize = 10.sp, color = Color.White))
                    drawText(tag, topLeft = Offset(left + plotWidth - tag.size.width - 6.dp.toPx(), (y - tag.size.height - 2.dp.toPx()).coerceAtLeast(top)))
                }

                // Time axis
                val duration = spectrogram.durationSec
                val timeStep = listOf(5, 10, 15, 30, 60, 120, 300, 600).firstOrNull { duration / it <= 6.0 } ?: 600
                var t = 0
                while (t <= duration) {
                    val x = left + plotWidth * (t / duration).toFloat()
                    drawLine(gridColor, Offset(x, top), Offset(x, top + plotHeight), strokeWidth = 1f)
                    val layout = measurer.measure(Fmt.duration(t.toDouble()), labelStyle)
                    val labelX = (x - layout.size.width / 2f).coerceIn(left - 4.dp.toPx(), size.width - layout.size.width.toFloat())
                    drawText(layout, topLeft = Offset(labelX, top + plotHeight + 4.dp.toPx()))
                    t += timeStep
                }
            }

            SegmentedControl(
                segments = SpectrogramPalette.entries.mapIndexed { i, p -> Segment(i, p.label) },
                selected = paletteIndex,
                onSelect = {
                    paletteIndex = it
                    prefs.edit().putInt("spectrogram_palette", it).apply()
                },
                scrollable = true,
                modifier = Modifier.fillMaxWidth(),
            )

            fun set(o: SpecOptions) { options = o; o.save(prefs) }
            SegmentedControl(
                segments = listOf(Segment(false, "Linear"), Segment(true, "Log")),
                selected = options.log,
                onSelect = { set(options.copy(log = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SegmentedControl(
                segments = DbRange.entries.map { Segment(it, it.label) },
                selected = options.range,
                onSelect = { set(options.copy(range = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (cutoffHz != null) {
                ExpressiveButton(
                    "Cutoff line",
                    { set(options.copy(cutoffLine = !options.cutoffLine)) },
                    kind = if (options.cutoffLine) ButtonKind.Filled else ButtonKind.Tonal,
                    icon = if (options.cutoffLine) Icons.Rounded.Check else null,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("${options.range.floor} dBFS", style = MaterialTheme.typography.labelMedium, color = axisColor)
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(SpectrogramRenderer.gradientColors(palette))),
                )
                Text("${options.range.ceil} dBFS", style = MaterialTheme.typography.labelMedium, color = axisColor)
            }

            if (onFullscreen != null) {
                ExpressiveButton(
                    "Full screen, zoom and inspect", { onFullscreen(selected) },
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Fullscreen,
                )
            }

            ExpressiveButton(
                text = if (saving) "Saving…" else "Save image to gallery",
                onClick = {
                    if (saving) return@ExpressiveButton
                    saving = true
                    val view = selected
                    val chosen = palette
                    scope.launch {
                        val uri = withContext(Dispatchers.Default) {
                            val bmp = SpectrogramExporter.render(spectrogram, view, chosen, title, options, cutoffHz)
                            try {
                                SpectrogramExporter.saveToGallery(context, bmp, "$title ${spectrogram.viewLabels[view]}")
                            } finally {
                                bmp.recycle()
                            }
                        }
                        saving = false
                        Toast.makeText(
                            context,
                            if (uri != null) "Saved to Pictures/Specty" else "Could not save the image",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                kind = ButtonKind.Tonal,
                icon = Icons.Rounded.Download,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sample Rate: ${spectrogram.sampleRate} Hz", style = MaterialTheme.typography.bodySmall, color = axisColor)
                Text("Nyquist: ${Fmt.kHz(spectrogram.sampleRate / 2.0)}", style = MaterialTheme.typography.bodySmall, color = axisColor)
            }
        }
    }
}
