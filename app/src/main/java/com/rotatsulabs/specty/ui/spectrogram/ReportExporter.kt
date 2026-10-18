package com.rotatsulabs.specty.ui.spectrogram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.rotatsulabs.specty.BuildConfig
import com.rotatsulabs.specty.audio.AnalysisResult
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

/** Draws the whole analysis result as one portrait image: verdict, file facts, measurements, spectrogram. */
object ReportExporter {
    private const val W = 1440
    private const val PAD = 60f
    private const val BG = 0xFF101014.toInt()
    private const val CARD = 0xFF1B1B21.toInt()
    private const val TEXT = 0xFFF2F0F4.toInt()
    private const val MUTED = 0xFFB4B0BA.toInt()

    fun render(context: Context, result: AnalysisResult): Bitmap {
        val prefs = context.getSharedPreferences("specty", Context.MODE_PRIVATE)
        val palette = SpectrogramPalette.entries[prefs.getInt("spectrogram_palette", 0).coerceIn(0, SpectrogramPalette.entries.lastIndex)]
        val options = SpecOptions.load(prefs)
        val info = result.info
        val report = result.authenticity
        val quality = ReportFacts.quality(info, report.verdict)
        val metrics = ReportFacts.metrics(result.levels)

        // The spectrogram comes from the existing exporter; its title block is cropped off, the subtitle kept.
        val full = SpectrogramExporter.render(result.spectrogram, 0, palette, info.title, options, report.cutoffHz)
        val crop = 108
        val specW = W - 2 * PAD
        val specH = (full.height - crop) * specW / full.width

        val inner = W - 2 * PAD
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 58f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 32f }
        val noteLayout = StaticLayout.Builder.obtain(report.note, 0, report.note.length, body, (inner - 80f).toInt())
            .setLineSpacing(8f, 1f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val tileH = 150f
        val rows = (metrics.size + 2) / 3
        val timeline = result.levels.timeline?.takeIf { it.points >= 2 }
        val graphH = if (timeline != null) 260f else 0f
        val verdictH = 40f + 56f + 20f + noteLayout.height + 40f
        val height = (PAD + 120f + 70f + 40f + verdictH + 40f + rows * (tileH + 20f) + 20f +
            (if (timeline != null) graphH + 60f else 0f) + specH + 40f + 80f + PAD).roundToInt()

        val out = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(BG)
        var y = PAD

        // Header
        val shown = TextUtils.ellipsize(info.title, title, inner, TextUtils.TruncateAt.END).toString()
        c.drawText(shown, PAD, y + 60f, title)
        c.drawText(DateFormat.getDateInstance(DateFormat.LONG).format(Date()), PAD, y + 108f, body)
        y += 120f + 20f

        // File facts and the quality tag
        var x = PAD
        val chip = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 30f }
        val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        for (text in ReportFacts.chips(info)) {
            val w = chip.measureText(text) + 44f
            if (x + w > W - PAD) break
            c.drawRoundRect(RectF(x, y, x + w, y + 60f), 30f, 30f, chipBg)
            c.drawText(text, x + 22f, y + 41f, chip)
            x += w + 14f
        }
        y += 60f + 30f
        if (quality != null) {
            val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); textSize = 30f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.08f
            }
            val w = tag.measureText(quality.label) + 56f
            val colors = if (quality == ReportFacts.Quality.Atmos) intArrayOf(0xFF3B6CF6.toInt(), 0xFFB04DF0.toInt()) else intArrayOf(0xFF1FA67A.toInt(), 0xFF2E8BE6.toInt())
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(PAD, 0f, PAD + w, 0f, colors, null, Shader.TileMode.CLAMP) }
            c.drawRoundRect(RectF(PAD, y, PAD + w, y + 64f), 32f, 32f, fill)
            c.drawText(quality.label, PAD + 28f, y + 43f, tag)
        }
        y += 64f + 40f

        // Verdict
        c.drawRoundRect(RectF(PAD, y, W - PAD, y + verdictH), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headline = TextUtils.ellipsize(ReportFacts.headline(report.verdict), head, inner - 80f, TextUtils.TruncateAt.END).toString()
        c.drawText(headline, PAD + 40f, y + 40f + 44f, head)
        if (report.confidence > 0) {
            val conf = "${report.confidence}% confidence"
            val cp = TextPaint(body).apply { textAlign = Paint.Align.RIGHT; textSize = 28f }
            if (head.measureText(headline) + cp.measureText(conf) + 120f < inner) c.drawText(conf, W - PAD - 40f, y + 40f + 42f, cp)
        }
        c.save(); c.translate(PAD + 40f, y + 40f + 56f + 20f); noteLayout.draw(c); c.restore()
        y += verdictH + 40f

        // Measurements
        val gap = 20f
        val tileW = (inner - 2 * gap) / 3f
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 26f }
        val value = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 52f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val unit = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 26f }
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        metrics.forEachIndexed { i, m ->
            val tx = PAD + (i % 3) * (tileW + gap)
            val ty = y + (i / 3) * (tileH + gap)
            c.drawRoundRect(RectF(tx, ty, tx + tileW, ty + tileH), 32f, 32f, tile)
            c.drawText(TextUtils.ellipsize(m.label, label, tileW - 48f, TextUtils.TruncateAt.END).toString(), tx + 24f, ty + 44f, label)
            c.drawText(m.value, tx + 24f, ty + 108f, value)
            if (m.unit.isNotEmpty()) c.drawText(m.unit, tx + 24f + value.measureText(m.value) + 10f, ty + 108f, unit)
        }
        y += rows * (tileH + gap) + 20f

        // Loudness over time
        if (timeline != null) {
            c.drawText("Loudness over time", PAD, y + 28f, label)
            val box = RectF(PAD, y + 50f, W - PAD, y + 50f + graphH - 20f)
            c.drawRoundRect(box, 28f, 28f, tile)
            val lo = timeline.loudness.min().coerceAtMost(-40f)
            val hi = max(timeline.loudness.max(), lo + 6f)
            val line = Path()
            val inset = 24f
            timeline.loudness.forEachIndexed { i, v ->
                val px = box.left + inset + (box.width() - 2 * inset) * i / (timeline.points - 1)
                val py = box.bottom - inset - (box.height() - 2 * inset) * ((v - lo) / (hi - lo))
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND
                shader = LinearGradient(box.left, 0f, box.right, 0f, SpectrogramRenderer.gradientColors(palette, 4).map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP)
            })
            y += graphH + 60f
        }

        // Spectrogram
        c.drawBitmap(full, Rect(0, crop, full.width, full.height), RectF(PAD, y, PAD + specW, y + specH), Paint(Paint.FILTER_BITMAP_FLAG))
        full.recycle()
        y += specH + 40f

        // Footer
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 28f; textAlign = Paint.Align.CENTER }
        c.drawText("Specty ${BuildConfig.VERSION_NAME}  ·  analysed on this device", W / 2f, y + 40f, foot)
        return out
    }
}
