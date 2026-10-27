package com.rotatsulabs.specty.ui.spectrogram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
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

/** The spectrogram picture and its caption, drawn once so changing the options re-lays the report out quickly. */
class ReportInput internal constructor(
    internal val shot: Bitmap,
    internal val crop: android.graphics.Rect,
    internal val caption: String,
    internal val cover: Bitmap? = null,
)

/**
 * Draws the analysis result as one image in the app's own Material 3 Expressive language: tonal containers, the large
 * asymmetric corners, segmented tiles and solid tags. The canvas has a fixed shape ([ReportAspect]); the content is
 * scaled until the chosen sections fit it, so nothing is clipped, and each shape has its own arrangement: a single
 * column for 9:16 and 1:1, two columns for 16:9.
 */
object ReportExporter {
    // The app's corner vocabulary (SpectyShapes), clockwise from top-left.
    private val hero = floatArrayOf(56f, 56f, 56f, 20f)
    private val card = floatArrayOf(36f, 36f, 36f, 14f)
    private val cardMirrored = floatArrayOf(14f, 36f, 36f, 36f)
    private val tileStart = floatArrayOf(32f, 10f, 10f, 32f)
    private val tileMiddle = floatArrayOf(10f, 10f, 10f, 10f)
    private val tileEnd = floatArrayOf(10f, 32f, 32f, 10f)
    private val tileSingle = floatArrayOf(32f, 32f, 32f, 32f)

    /** The measurable blocks, in reading order. */
    private enum class Slot { Title, Details, Verdict, Metrics, Loudness, Spectrogram }

    /** A block that has been measured: how tall it is, and how to draw it at a given left and top. */
    private class Laid(val height: Float, val draw: (Canvas, Float, Float) -> Unit)

    /** Everything that depends on the scale: dp to pixels, and the paints in the theme's colours. */
    private class Ink(val u: Float, val theme: ReportTheme) {
        fun dp(v: Float) = v * u

        fun weight(w: Int): Typeface = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(Typeface.DEFAULT, w, false)
            w >= 700 -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            w >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }

        fun text(sp: Float, color: Int, w: Int = 400, tracking: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = dp(sp); typeface = weight(w); letterSpacing = tracking
        }

        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        fun shape(c: Canvas, r: RectF, radii: FloatArray, paint: Paint) {
            val px = FloatArray(8) { dp(radii[it / 2]) }
            c.drawPath(Path().apply { addRoundRect(r, px, Path.Direction.CW) }, paint)
        }
    }

    /** Renders the spectrogram once. The result is reused for every [render] of the same analysis. */
    suspend fun prepare(context: Context, result: AnalysisResult): ReportInput {
        val cover = com.rotatsulabs.specty.data.CoverArt.load(context, result.uri, 512)
        val prefs = context.getSharedPreferences("specty", Context.MODE_PRIVATE)
        val palette = SpectrogramPalette.entries[prefs.getInt("spectrogram_palette", 0).coerceIn(0, SpectrogramPalette.entries.lastIndex)]
        val options = SpecOptions.load(prefs)
        val spec = result.spectrogram
        val shot = SpectrogramExporter.render(spec, 0, palette, result.info.title, options, result.authenticity.cutoffHz)
        val caption = "${spec.displayLabels[0]}  ·  ${Fmt.kHz(spec.sampleRate.toDouble())}  ·  FFT ${spec.fftSize}  ·  ${Fmt.duration(spec.durationSec)}  ·  ${palette.label}" +
            if (options.log) "  ·  log scale" else ""
        return ReportInput(shot, SpectrogramExporter.plotCrop(), caption, cover)
    }

    fun render(input: ReportInput, result: AnalysisResult, theme: ReportTheme, options: ReportOptions): Bitmap {
        val aspect = options.aspect
        val width = aspect.width
        val height = aspect.height
        val base = width / 411f
        val wide = aspect == ReportAspect.Landscape

        fun slots(pane: Int): List<Slot> {
            val column = buildList {
                add(Slot.Title)
                if (options.has(ReportSection.Details)) add(Slot.Details)
                if (options.has(ReportSection.Verdict)) add(Slot.Verdict)
                if (options.has(ReportSection.KeyLevels) || options.has(ReportSection.Measurements)) add(Slot.Metrics)
                if (options.has(ReportSection.Loudness)) add(Slot.Loudness)
                if (options.has(ReportSection.Spectrogram)) add(Slot.Spectrogram)
            }
            if (!wide) return column
            val right = column.filter { it == Slot.Loudness || it == Slot.Spectrogram }
            return if (pane == 0) column - right.toSet() else right
        }

        class Pane(val x: Float, val width: Float, val slots: List<Slot>)

        fun panes(k: Ink): List<Pane> {
            val pad = k.dp(20f)
            if (!wide) return listOf(Pane(pad, width - 2 * pad, slots(0)))
            val right = slots(1)
            if (right.isEmpty()) return listOf(Pane(pad, width - 2 * pad, slots(0)))
            val leftWidth = (width - 3 * pad) * 0.4f
            return listOf(Pane(pad, leftWidth, slots(0)), Pane(pad * 2 + leftWidth, width - 3 * pad - leftWidth, right))
        }

        val footerOn = options.has(ReportSection.Footer)
        fun gap(k: Ink) = k.dp(14f)
        fun top(k: Ink) = k.dp(28f)
        fun bottom(k: Ink) = k.dp(20f) + if (footerOn) k.dp(34f) else 0f
        fun laid(k: Ink, pane: Pane) = pane.slots.mapNotNull { build(it, k, pane.width, input, result, options) }
        fun stackHeight(blocks: List<Laid>, k: Ink) = blocks.sumOf { it.height.toDouble() }.toFloat() + gap(k) * (blocks.size - 1).coerceAtLeast(0)

        val scale = ReportFit.scale(height.toFloat()) { s ->
            val k = Ink(base * s, theme)
            top(k) + bottom(k) + panes(k).maxOf { stackHeight(laid(k, it), k) }
        }
        val k = Ink(base * scale, theme)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(theme.surface)
        val room = height - top(k) - bottom(k)
        for (pane in panes(k)) {
            val blocks = laid(k, pane)
            val extra = ReportFit.extraGap(room - stackHeight(blocks, k), blocks.size, k.dp(26f))
            var y = top(k)
            for (b in blocks) {
                b.draw(c, pane.x, y)
                y += b.height + gap(k) + extra
            }
        }
        if (footerOn) {
            val foot = k.text(12f, theme.onMuted, 600, 0.02f).apply { textAlign = Paint.Align.CENTER }
            c.drawText("Specty ${BuildConfig.VERSION_NAME}  ·  analysed on this device", width / 2f, height - k.dp(20f), foot)
        }
        return out
    }

    private fun build(slot: Slot, k: Ink, w: Float, input: ReportInput, result: AnalysisResult, options: ReportOptions): Laid? = when (slot) {
        Slot.Title -> title(k, w, result, input.cover.takeIf { options.has(ReportSection.Cover) })
        Slot.Details -> details(k, w, result)
        Slot.Verdict -> verdict(k, w, result)
        Slot.Metrics -> metrics(k, w, result, options.has(ReportSection.KeyLevels), options.has(ReportSection.Measurements))
        Slot.Loudness -> loudness(k, w, result)
        Slot.Spectrogram -> spectrogram(k, w, input)
    }

    private fun title(k: Ink, w: Float, result: AnalysisResult, cover: Bitmap?): Laid {
        val name = result.info.title
        val art = if (cover != null) k.dp(76f) else 0f
        val gap = if (cover != null) k.dp(14f) else 0f
        val textW = w - art - gap
        val titlePaint = k.text(30f, k.theme.onSurface, 800, -0.015f)
        val title = StaticLayout.Builder.obtain(name, 0, name.length, titlePaint, textW.toInt())
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.05f).build()
        val datePaint = k.text(14f, k.theme.onMuted, 600, 0.005f)
        val dateText = "Analysed " + DateFormat.getDateInstance(DateFormat.LONG).format(Date())
        val textH = title.height + k.dp(4f) + k.dp(19f)
        return Laid(maxOf(textH, art)) { c, x, y ->
            val tx = x + art + gap
            val ty = y + (maxOf(textH, art) - textH) / 2f
            if (cover != null) {
                val r = RectF(x, y, x + art, y + art)
                c.save()
                c.clipPath(Path().apply { addRoundRect(r, k.dp(16f), k.dp(16f), Path.Direction.CW) })
                c.drawBitmap(cover, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
                c.restore()
            }
            c.save(); c.translate(tx, ty); title.draw(c); c.restore()
            c.drawText(dateText, tx, ty + title.height + k.dp(4f) + k.dp(15f), datePaint)
        }
    }

    /** File facts: the quality tag first, in the same solid colours the library uses. */
    private fun details(k: Ink, w: Float, result: AnalysisResult): Laid {
        val chipText = k.text(13f, k.theme.onSurface, 600, 0.004f)
        val tagText = k.text(13f, k.theme.onTag, 700, 0.004f)
        val chipH = k.dp(34f)
        val chipGap = k.dp(8f)
        class Chip(val label: String, val bg: Int, val paint: TextPaint, val x: Float, val y: Float, val w: Float)
        val chips = ArrayList<Chip>()
        var x = 0f
        var y = 0f
        fun add(label: String, bg: Int, paint: TextPaint) {
            val cw = paint.measureText(label) + k.dp(28f)
            if (x + cw > w && x > 0f) { x = 0f; y += chipH + chipGap }
            chips += Chip(label, bg, paint, x, y, cw)
            x += cw + chipGap
        }
        ReportFacts.quality(result.info, result.authenticity.verdict)?.let { add(it.label, k.theme.tag, tagText) }
        ReportFacts.chips(result.info).forEach { add(it, k.theme.card, chipText) }
        return Laid(y + chipH) { c, left, top ->
            for (chip in chips) {
                val r = RectF(left + chip.x, top + chip.y, left + chip.x + chip.w, top + chip.y + chipH)
                c.drawRoundRect(r, chipH / 2, chipH / 2, k.fill(chip.bg))
                c.drawText(chip.label, r.left + k.dp(14f), r.centerY() - (chip.paint.ascent() + chip.paint.descent()) / 2, chip.paint)
            }
        }
    }

    private fun verdict(k: Ink, w: Float, result: AnalysisResult): Laid {
        val report = result.authenticity
        val headPaint = k.text(22f, k.theme.onVerdict, 700)
        val notePaint = k.text(15f, k.theme.onVerdict, 400).apply { alpha = 215 }
        val pad = k.dp(24f)
        val headline = ReportFacts.headline(report.verdict)
        val note = StaticLayout.Builder.obtain(report.note, 0, report.note.length, notePaint, (w - 2 * pad).toInt())
            .setLineSpacing(0f, 1.3f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val confidence = if (report.confidence > 0) "${report.confidence}% confidence" else null
        val confPaint = k.text(13f, k.theme.onVerdict, 600, 0.004f).apply { textAlign = Paint.Align.RIGHT; alpha = 200 }
        val room = w - 2 * pad - (confidence?.let { confPaint.measureText(it) + k.dp(16f) } ?: 0f)
        val head = TextUtils.ellipsize(headline, headPaint, room, TextUtils.TruncateAt.END).toString()
        val h = pad + k.dp(28f) + k.dp(10f) + note.height + pad
        return Laid(h) { c, x, y ->
            k.shape(c, RectF(x, y, x + w, y + h), hero, k.fill(k.theme.verdict))
            c.drawText(head, x + pad, y + pad + k.dp(22f), headPaint)
            confidence?.let { c.drawText(it, x + w - pad, y + pad + k.dp(21f), confPaint) }
            c.save(); c.translate(x + pad, y + pad + k.dp(38f)); note.draw(c); c.restore()
        }
    }

    /** Three tonal headline tiles, then rows of quiet ones, each row a segmented group. */
    private fun metrics(k: Ink, w: Float, result: AnalysisResult, keyLevels: Boolean, more: Boolean): Laid? {
        val items = buildList {
            if (keyLevels) ReportFacts.keyLevels(result.levels).forEach { add(it to true) }
            if (more) ReportFacts.moreMeasurements(result.levels).forEach { add(it to false) }
        }
        if (items.isEmpty()) return null
        val theme = k.theme
        val gap = k.dp(3f)
        val tileW = (w - 2 * gap) / 3f
        val tileH = k.dp(104f)
        val tonal = listOf(
            theme.primaryContainer to theme.onPrimaryContainer,
            theme.secondaryContainer to theme.onSecondaryContainer,
            theme.tertiaryContainer to theme.onTertiaryContainer,
        )
        val label = k.text(12f, 0, 600, 0.04f)
        val value = k.text(26f, 0, 700)
        val unit = k.text(12f, 0, 600, 0.04f)
        val rows = (items.size + 2) / 3
        return Laid(rows * (tileH + gap) - gap) { c, x, y ->
            items.forEachIndexed { i, (m, headline) ->
                val col = i % 3
                val row = i / 3
                val inRow = minOf(3, items.size - row * 3)
                val (bg, fg) = if (headline) tonal[i % 3] else theme.card to theme.onSurface
                val tx = x + col * (tileW + gap)
                val ty = y + row * (tileH + gap)
                val radii = when {
                    inRow == 1 -> tileSingle
                    col == 0 -> tileStart
                    col == inRow - 1 -> tileEnd
                    else -> tileMiddle
                }
                k.shape(c, RectF(tx, ty, tx + tileW, ty + tileH), radii, k.fill(bg))
                label.color = fg; label.alpha = 185
                value.color = fg
                unit.color = fg; unit.alpha = 185
                val left = tx + k.dp(16f)
                c.drawText(TextUtils.ellipsize(m.label, label, tileW - k.dp(32f), TextUtils.TruncateAt.END).toString(), left, ty + k.dp(30f), label)
                c.drawText(m.value, left, ty + k.dp(64f), value)
                if (m.unit.isNotEmpty()) c.drawText(m.unit, left, ty + k.dp(88f), unit)
            }
        }
    }

    private fun loudness(k: Ink, w: Float, result: AnalysisResult): Laid? {
        val timeline = result.levels.timeline?.takeIf { it.points >= 2 } ?: return null
        val theme = k.theme
        val pad = k.dp(24f)
        val h = k.dp(190f)
        val heading = k.text(16f, theme.onSurface, 600, 0.002f)
        val small = k.text(11f, theme.onMuted, 600, 0.03f)
        val lo = timeline.loudness.min().coerceAtMost(-40f)
        val hi = max(timeline.loudness.max(), lo + 6f)
        return Laid(h) { c, x, y ->
            k.shape(c, RectF(x, y, x + w, y + h), card, k.fill(theme.card))
            c.drawText("Loudness over time", x + pad, y + k.dp(36f), heading)
            val box = RectF(x + pad + k.dp(34f), y + k.dp(52f), x + w - pad, y + h - k.dp(34f))
            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.outline; strokeWidth = k.dp(0.8f); style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(k.dp(4f), k.dp(4f)), 0f)
            }
            small.textAlign = Paint.Align.RIGHT
            for (v in listOf(hi, (hi + lo) / 2f, lo)) {
                val gy = box.bottom - box.height() * ((v - lo) / (hi - lo))
                c.drawLine(box.left, gy, box.right, gy, grid)
                c.drawText("%.0f".format(v), box.left - k.dp(8f), gy + k.dp(4f), small)
            }
            val line = Path()
            timeline.loudness.forEachIndexed { i, v ->
                val px = box.left + box.width() * i / (timeline.points - 1)
                val py = box.bottom - box.height() * ((v - lo) / (hi - lo))
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            val area = Path(line).apply { lineTo(box.right, box.bottom); lineTo(box.left, box.bottom); close() }
            c.drawPath(area, k.fill(theme.primary).apply { alpha = 40 })
            c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.primary; style = Paint.Style.STROKE; strokeWidth = k.dp(2.4f); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            })
            small.textAlign = Paint.Align.LEFT
            c.drawText("0:00", box.left, y + h - k.dp(12f), small)
            small.textAlign = Paint.Align.RIGHT
            c.drawText(Fmt.duration(timeline.durationSec), box.right, y + h - k.dp(12f), small)
            c.drawText("LUFS", x + pad + k.dp(30f), y + k.dp(36f), small)
        }
    }

    /** The spectrogram on its own dark panel, so it reads the same in a light theme. */
    private fun spectrogram(k: Ink, w: Float, input: ReportInput): Laid {
        val theme = k.theme
        val pad = k.dp(16f)
        val panelW = w - 2 * pad
        val panelH = input.crop.height() * panelW / input.crop.width()
        val captionPaint = k.text(12.5f, theme.onMuted, 500, 0.004f)
        val heading = k.text(16f, theme.onSurface, 600, 0.002f)
        val caption = TextUtils.ellipsize(input.caption, captionPaint, panelW - k.dp(8f), TextUtils.TruncateAt.END).toString()
        val h = k.dp(72f) + panelH + pad
        return Laid(h) { c, x, y ->
            k.shape(c, RectF(x, y, x + w, y + h), cardMirrored, k.fill(theme.card))
            c.drawText("Spectrogram", x + pad + k.dp(8f), y + k.dp(38f), heading)
            c.drawText(caption, x + pad + k.dp(8f), y + k.dp(60f), captionPaint)
            val panel = RectF(x + pad, y + k.dp(72f), x + w - pad, y + k.dp(72f) + panelH)
            c.save()
            c.clipPath(Path().apply { addRoundRect(panel, k.dp(22f), k.dp(22f), Path.Direction.CW) })
            c.drawColor(0xFF101014.toInt())
            c.drawBitmap(input.shot, input.crop, panel, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
    }
}
