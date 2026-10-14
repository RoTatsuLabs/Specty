package com.rotatsulabs.specty.ui.spectrogram

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
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
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SpectrogramCard(spectrogram: Spectrogram, modifier: Modifier = Modifier, title: String = "Spectrogram") {
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

    val image by produceState<ImageBitmap?>(initialValue = null, spectrogram, selected, palette) {
        value = withContext(Dispatchers.Default) {
            SpectrogramRenderer.render(spectrogram, selected, palette).asImageBitmap()
        }
    }

    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val gridColor = Color.White.copy(alpha = 0.14f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                spectrogram.viewLabels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selected == index,
                        onClick = { selected = index },
                        shape = SegmentedButtonDefaults.itemShape(index, spectrogram.viewLabels.size),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }

            Canvas(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val left = 40.dp.toPx()
                val right = 6.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = 20.dp.toPx()
                val plotWidth = size.width - left - right
                val plotHeight = size.height - top - bottom

                drawRect(Color.Black, topLeft = Offset(left, top), size = Size(plotWidth, plotHeight))
                image?.let { img ->
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(plotWidth.roundToInt(), plotHeight.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }

                // Frequency axis
                val nyquistK = spectrogram.sampleRate / 2000f
                val freqStep = when {
                    nyquistK > 30f -> 10
                    nyquistK > 12f -> 5
                    nyquistK > 6f -> 2
                    else -> 1
                }
                var f = 0
                while (f <= nyquistK) {
                    val y = top + plotHeight * (1f - f / nyquistK)
                    drawLine(gridColor, Offset(left, y), Offset(left + plotWidth, y), strokeWidth = 1f)
                    val layout = measurer.measure(if (f == 0) "0" else "${f}k", labelStyle)
                    val labelY = (y - layout.size.height / 2f).coerceIn(0f, size.height - bottom - layout.size.height + 4.dp.toPx())
                    drawText(layout, topLeft = Offset(left - 6.dp.toPx() - layout.size.width, labelY))
                    f += freqStep
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

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpectrogramPalette.entries.forEachIndexed { index, p ->
                    FilterChip(
                        selected = paletteIndex == index,
                        onClick = {
                            paletteIndex = index
                            prefs.edit().putInt("spectrogram_palette", index).apply()
                        },
                        label = { Text(p.label) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("-120 dBFS", style = MaterialTheme.typography.labelMedium, color = axisColor)
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(SpectrogramRenderer.gradientColors(palette))),
                )
                Text("0 dBFS", style = MaterialTheme.typography.labelMedium, color = axisColor)
            }

            FilledTonalButton(
                onClick = {
                    if (saving) return@FilledTonalButton
                    saving = true
                    val view = selected
                    val chosen = palette
                    scope.launch {
                        val uri = withContext(Dispatchers.Default) {
                            val bmp = SpectrogramExporter.render(spectrogram, view, chosen, title)
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
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(if (saving) "Saving…" else "Save image to gallery", modifier = Modifier.padding(start = 8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sample Rate: ${spectrogram.sampleRate} Hz", style = MaterialTheme.typography.bodySmall, color = axisColor)
                Text("Nyquist: ${Fmt.kHz(spectrogram.sampleRate / 2.0)}", style = MaterialTheme.typography.bodySmall, color = axisColor)
            }
        }
    }
}
