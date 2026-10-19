package com.rotatsulabs.specty.ui.spectrogram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rotatsulabs.specty.BuildConfig
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.ui.components.Fmt
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

/** The colours of the app's current theme, so the exported image looks like the screen it came from. */
class ReportTheme(
    val surface: Int, val card: Int, val onSurface: Int, val onMuted: Int, val outline: Int,
    val primary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiaryContainer: Int, val onTertiaryContainer: Int,
    val verdict: Int, val onVerdict: Int,
    val tag: Int, val onTag: Int,
) {
    companion object {
        fun of(s: ColorScheme, verdict: Pair<Color, Color>, tag: Pair<Color, Color>) = ReportTheme(
            s.surface.toArgb(), s.surfaceContainer.toArgb(), s.onSurface.toArgb(), s.onSurfaceVariant.toArgb(), s.outlineVariant.toArgb(),
            s.primary.toArgb(), s.primaryContainer.toArgb(), s.onPrimaryContainer.toArgb(),
            s.secondaryContainer.toArgb(), s.onSecondaryContainer.toArgb(),
            s.tertiaryContainer.toArgb(), s.onTertiaryContainer.toArgb(),
            verdict.first.toArgb(), verdict.second.toArgb(), tag.first.toArgb(), tag.second.toArgb(),
        )
    }
}

/**
 * Draws the whole analysis result as one portrait image in the app's own Material 3 Expressive language: tonal
 * containers, the large asymmetric corners, segmented tiles and solid tags. Everything is recorded first and the
 * bitmap is cut to the measured height, so nothing can be clipped at the bottom.
 */
object ReportExporter {
    private const val W = 1440
    private const val DP = W / 411f
    private fun dp(v: Float) = v * DP

    // The app's corner vocabulary (SpectyShapes), clockwise from top-left.
    private val hero = floatArrayOf(56f, 56f, 56f, 20f)
    private val card = floatArrayOf(36f, 36f, 36f, 14f)
    private val cardMirrored = floatArrayOf(14f, 36f, 36f, 36f)
    private val tileStart = floatArrayOf(32f, 10f, 10f, 32f)
    private val tileMiddle = floatArrayOf(10f, 10f, 10f, 10f)
    private val tileEnd = floatArrayOf(10f, 32f, 32f, 10f)

    fun render(context: Context, result: AnalysisResult, theme: ReportTheme): Bitmap {
        val prefs = context.getSharedPreferences("specty", Context.MODE_PRIVATE)
        val palette = SpectrogramPalette.entries[prefs.getInt("spectrogram_palette", 0).coerceIn(0, SpectrogramPalette.entries.lastIndex)]
        val options = SpecOptions.load(prefs)
        val info = result.info
        val report = result.authenticity
        val spec = result.spectrogram
        val quality = ReportFacts.quality(info, report.verdict)
        val metrics = ReportFacts.metrics(result.levels)

        val shot = SpectrogramExporter.render(spec, 0, palette, info.title, options, report.cutoffHz)
        val crop = SpectrogramExporter.plotCrop()

        fun weight(w: Int) = Typeface.create(Typeface.DEFAULT, w, false)
        fun text(sp: Float, color: Int, w: Int = 400, tracking: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = dp(sp); typeface = weight(w); letterSpacing = tracking
        }
        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun shape(c: Canvas, r: RectF, radii: FloatArray, paint: Paint) {
            val px = FloatArray(8) { dp(radii[it / 2]) }
            c.drawPath(Path().apply { addRoundRect(r, px, Path.Direction.CW) }, paint)
        }

        val pad = dp(20f)
        val inner = W - 2 * pad
        val picture = Picture()
        val c = picture.beginRecording(W, 12_000)
        c.drawColor(theme.surface)
        var y = dp(28f)

        // Title and date
        val titlePaint = text(30f, theme.onSurface, 800, -0.015f)
        val title = StaticLayout.Builder.obtain(info.title, 0, info.title.length, titlePaint, inner.toInt())
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.05f).build()
        c.save(); c.translate(pad, y); title.draw(c); c.restore()
        y += title.height + dp(4f)
        val dateText = "Analysed " + DateFormat.getDateInstance(DateFormat.LONG).format(Date())
        c.drawText(dateText, pad, y + dp(15f), text(14f, theme.onMuted, 600, 0.005f))
        y += dp(15f) + dp(18f)

        // File facts: the quality tag first, in the same solid colours the library uses
        val chipText = text(13f, theme.onSurface, 600, 0.004f)
        val tagText = text(13f, theme.onTag, 700, 0.004f)
        val chipH = dp(34f)
        val chipGap = dp(8f)
        var x = pad
        fun chip(label: String, bg: Int, paint: TextPaint) {
            val w = paint.measureText(label) + dp(28f)
            if (x + w > W - pad && x > pad) { x = pad; y += chipH + chipGap }
            c.drawRoundRect(RectF(x, y, x + w, y + chipH), chipH / 2, chipH / 2, fill(bg))
            c.drawText(label, x + dp(14f), y + chipH / 2 - (paint.ascent() + paint.descent()) / 2, paint)
            x += w + chipGap
        }
        quality?.let { chip(it.label, theme.tag, tagText) }
        ReportFacts.chips(info).forEach { chip(it, theme.card, chipText) }
        y += chipH + dp(20f)

        // Verdict
        val headPaint = text(22f, theme.onVerdict, 700)
        val notePaint = text(15f, theme.onVerdict, 400)
        notePaint.alpha = 215
        val padCard = dp(24f)
        val headline = ReportFacts.headline(report.verdict)
        val noteLayout = StaticLayout.Builder.obtain(report.note, 0, report.note.length, notePaint, (inner - 2 * padCard).toInt())
            .setLineSpacing(0f, 1.3f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val verdictH = padCard + dp(28f) + dp(10f) + noteLayout.height + padCard
        shape(c, RectF(pad, y, W - pad, y + verdictH), hero, fill(theme.verdict))
        val confidence = if (report.confidence > 0) "${report.confidence}% confidence" else null
        val confPaint = text(13f, theme.onVerdict, 600, 0.004f).apply { textAlign = Paint.Align.RIGHT; alpha = 200 }
        val room = inner - 2 * padCard - (confidence?.let { confPaint.measureText(it) + dp(16f) } ?: 0f)
        c.drawText(TextUtils.ellipsize(headline, headPaint, room, TextUtils.TruncateAt.END).toString(), pad + padCard, y + padCard + dp(22f), headPaint)
        confidence?.let { c.drawText(it, W - pad - padCard, y + padCard + dp(21f), confPaint) }
        c.save(); c.translate(pad + padCard, y + padCard + dp(38f)); noteLayout.draw(c); c.restore()
        y += verdictH + dp(14f)

        // Measurements: three tonal headline tiles, then rows of quiet ones, each row a segmented group
        val gap = dp(3f)
        val tileW = (inner - 2 * gap) / 3f
        val tileH = dp(104f)
        val labelPaint = text(12f, 0, 600, 0.04f)
        val valuePaint = text(26f, 0, 700)
        val unitPaint = text(12f, 0, 600, 0.04f)
        val tonal = listOf(
            theme.primaryContainer to theme.onPrimaryContainer,
            theme.secondaryContainer to theme.onSecondaryContainer,
            theme.tertiaryContainer to theme.onTertiaryContainer,
        )
        metrics.forEachIndexed { i, m ->
            val col = i % 3
            val row = i / 3
            val bg = if (i < 3) tonal[i].first else theme.card
            val fg = if (i < 3) tonal[i].second else theme.onSurface
            val tx = pad + col * (tileW + gap)
            val ty = y + row * (tileH + gap)
            shape(c, RectF(tx, ty, tx + tileW, ty + tileH), when (col) { 0 -> tileStart; 1 -> tileMiddle; else -> tileEnd }, fill(bg))
            labelPaint.color = fg; labelPaint.alpha = 185
            valuePaint.color = fg; unitPaint.color = fg; unitPaint.alpha = 185
            val left = tx + dp(16f)
            c.drawText(TextUtils.ellipsize(m.label, labelPaint, tileW - dp(32f), TextUtils.TruncateAt.END).toString(), left, ty + dp(30f), labelPaint)
            c.drawText(m.value, left, ty + dp(64f), valuePaint)
            if (m.unit.isNotEmpty()) c.drawText(m.unit, left, ty + dp(88f), unitPaint)
        }
        y += ((metrics.size + 2) / 3) * (tileH + gap) - gap + dp(14f)

        // Loudness over time
        val timeline = result.levels.timeline?.takeIf { it.points >= 2 }
        if (timeline != null) {
            val h = dp(190f)
            shape(c, RectF(pad, y, W - pad, y + h), card, fill(theme.card))
            c.drawText("Loudness over time", pad + padCard, y + dp(36f), text(16f, theme.onSurface, 600, 0.002f))
            val small = text(11f, theme.onMuted, 600, 0.03f)
            val box = RectF(pad + padCard + dp(34f), y + dp(52f), W - pad - padCard, y + h - dp(34f))
            val lo = timeline.loudness.min().coerceAtMost(-40f)
            val hi = max(timeline.loudness.max(), lo + 6f)
            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.outline; strokeWidth = dp(0.8f); style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f) }
            for (v in listOf(hi, (hi + lo) / 2f, lo)) {
                val gy = box.bottom - box.height() * ((v - lo) / (hi - lo))
                c.drawLine(box.left, gy, box.right, gy, grid)
                small.textAlign = Paint.Align.RIGHT
                c.drawText("%.0f".format(v), box.left - dp(8f), gy + dp(4f), small)
            }
            val line = Path()
            timeline.loudness.forEachIndexed { i, v ->
                val px = box.left + box.width() * i / (timeline.points - 1)
                val py = box.bottom - box.height() * ((v - lo) / (hi - lo))
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            val area = Path(line).apply { lineTo(box.right, box.bottom); lineTo(box.left, box.bottom); close() }
            c.drawPath(area, fill(theme.primary).apply { alpha = 40 })
            c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.primary; style = Paint.Style.STROKE; strokeWidth = dp(2.4f); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            })
            small.textAlign = Paint.Align.LEFT
            c.drawText("0:00", box.left, y + h - dp(12f), small)
            small.textAlign = Paint.Align.RIGHT
            c.drawText(Fmt.duration(timeline.durationSec), box.right, y + h - dp(12f), small)
            small.textAlign = Paint.Align.RIGHT
            c.drawText("LUFS", pad + padCard + dp(30f), y + dp(36f), small)
            y += h + dp(14f)
        }

        // Spectrogram on its own dark panel, so it reads the same in a light theme
        val padSpec = dp(16f)
        val panelW = inner - 2 * padSpec
        val panelH = crop.height() * panelW / crop.width()
        val captionPaint = text(12.5f, theme.onMuted, 500, 0.004f)
        val caption = "${spec.displayLabels[0]}  ·  ${Fmt.kHz(spec.sampleRate.toDouble())}  ·  FFT ${spec.fftSize}  ·  ${Fmt.duration(spec.durationSec)}  ·  ${palette.label}" +
            if (options.log) "  ·  log scale" else ""
        val specH = dp(20f) + dp(18f) + dp(10f) + dp(20f) + panelH + padSpec
        shape(c, RectF(pad, y, W - pad, y + specH), cardMirrored, fill(theme.card))
        c.drawText("Spectrogram", pad + padSpec + dp(8f), y + dp(38f), text(16f, theme.onSurface, 600, 0.002f))
        c.drawText(TextUtils.ellipsize(caption, captionPaint, panelW - dp(8f), TextUtils.TruncateAt.END).toString(), pad + padSpec + dp(8f), y + dp(60f), captionPaint)
        val panel = RectF(pad + padSpec, y + dp(72f), W - pad - padSpec, y + dp(72f) + panelH)
        c.save()
        c.clipPath(Path().apply { addRoundRect(panel, dp(22f), dp(22f), Path.Direction.CW) })
        c.drawColor(0xFF101014.toInt())
        c.drawBitmap(shot, crop, panel, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        y += dp(72f) + panelH + padSpec + dp(18f)

        // Footer
        val foot = text(12f, theme.onMuted, 600, 0.02f).apply { textAlign = Paint.Align.CENTER }
        c.drawText("Specty ${BuildConfig.VERSION_NAME}  ·  analysed on this device", W / 2f, y + dp(12f), foot)
        y += dp(12f) + dp(28f)
        picture.endRecording()

        val out = Bitmap.createBitmap(W, y.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(out).drawPicture(picture)
        shot.recycle()
        return out
    }
}
