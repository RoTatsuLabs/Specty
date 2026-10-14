package com.rotatsulabs.specty.ui.spectrogram

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.ui.components.Fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Draws a shareable spectrogram image (axes, legend, title) and saves it to Pictures/Specty. */
object SpectrogramExporter {
    private const val WIDTH = 1920
    private const val LEFT = 150f
    private const val RIGHT = 60f
    private const val TOP = 190f
    private const val BOTTOM = 250f

    private const val BACKGROUND = 0xFF101014.toInt()
    private const val TEXT = 0xFFF2F0F4.toInt()
    private const val MUTED = 0xFFB4B0BA.toInt()

    fun render(spectrogram: Spectrogram, view: Int, palette: SpectrogramPalette, title: String): Bitmap {
        val plotW = WIDTH - LEFT - RIGHT
        val plotH = plotW * 9f / 16f
        val height = (TOP + plotH + BOTTOM).roundToInt()
        val out = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(BACKGROUND)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT; textSize = 56f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 34f }
        val axisPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 30f }

        val shownTitle = TextUtils.ellipsize(title, titlePaint, WIDTH - 120f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(shownTitle, 60f, 90f, titlePaint)
        val subtitle = "${spectrogram.viewLabels[view]}  ·  ${spectrogram.sampleRate / 1000.0} kHz  ·  " +
            "FFT ${spectrogram.fftSize}  ·  ${Fmt.duration(spectrogram.durationSec)}  ·  ${palette.label}"
        canvas.drawText(subtitle, 60f, 145f, subtitlePaint)

        // Plot
        val plot = RectF(LEFT, TOP, LEFT + plotW, TOP + plotH)
        canvas.drawRect(plot, Paint().apply { color = 0xFF000000.toInt() })
        val image = SpectrogramRenderer.render(spectrogram, view, palette)
        canvas.drawBitmap(image, null, plot, Paint(Paint.FILTER_BITMAP_FLAG))
        image.recycle()

        val grid = Paint().apply { color = 0x24FFFFFF; strokeWidth = 2f }

        // Frequency axis
        val nyquistK = spectrogram.sampleRate / 2000f
        val freqStep = when {
            nyquistK > 30f -> 10
            nyquistK > 12f -> 5
            nyquistK > 6f -> 2
            else -> 1
        }
        axisPaint.textAlign = Paint.Align.RIGHT
        var f = 0
        while (f <= nyquistK) {
            val y = plot.bottom - plotH * (f / nyquistK)
            canvas.drawLine(plot.left, y, plot.right, y, grid)
            canvas.drawText(if (f == 0) "0" else "${f}k", plot.left - 14f, y + 10f, axisPaint)
            f += freqStep
        }

        // Time axis
        val duration = spectrogram.durationSec
        val timeStep = listOf(5, 10, 15, 30, 60, 120, 300, 600).firstOrNull { duration / it <= 8.0 } ?: 600
        axisPaint.textAlign = Paint.Align.CENTER
        var t = 0
        while (t <= duration) {
            val x = plot.left + plotW * (t / duration).toFloat()
            canvas.drawLine(x, plot.top, x, plot.bottom, grid)
            canvas.drawText(Fmt.duration(t.toDouble()), x, plot.bottom + 44f, axisPaint)
            t += timeStep
        }

        // Colour legend
        val legendTop = plot.bottom + 90f
        val colors = SpectrogramRenderer.gradientColors(palette, 16).map { it.toArgb() }.toIntArray()
        val legend = RectF(plot.left, legendTop, plot.right, legendTop + 28f)
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(legend.left, 0f, legend.right, 0f, colors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(legend, 14f, 14f, gradient)
        axisPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${Spectrogram.MIN_DB.toInt()} dBFS", legend.left, legend.bottom + 44f, axisPaint)
        axisPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${Spectrogram.MAX_DB.toInt()} dBFS", legend.right, legend.bottom + 44f, axisPaint)
        axisPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Specty", (legend.left + legend.right) / 2f, legend.bottom + 44f, axisPaint)
        return out
    }

    /** Saves [bitmap] to Pictures/Specty through MediaStore (no storage permission needed). */
    fun saveToGallery(context: Context, bitmap: Bitmap, baseName: String): Uri? {
        val safe = baseName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "spectrogram" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-spectrogram-$stamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Specty")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("No output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
