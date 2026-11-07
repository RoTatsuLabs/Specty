package com.rotatsulabs.specty.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.components.rememberStorageGate
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.IconBadge
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.verdictColors
import com.rotatsulabs.specty.ui.design.rememberIsWide
import com.rotatsulabs.specty.ui.spectrogram.ReportAspect
import com.rotatsulabs.specty.ui.spectrogram.ReportExporter
import com.rotatsulabs.specty.ui.spectrogram.ReportFacts
import com.rotatsulabs.specty.ui.spectrogram.ReportInput
import com.rotatsulabs.specty.ui.spectrogram.ReportOptions
import com.rotatsulabs.specty.ui.spectrogram.ReportSection
import com.rotatsulabs.specty.ui.spectrogram.ReportTheme
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramExporter
import com.rotatsulabs.specty.ui.theme.Appearance
import com.rotatsulabs.specty.ui.theme.Motion
import com.rotatsulabs.specty.ui.theme.colorSchemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.FilterChip
import com.rotatsulabs.specty.ui.design.components.Segment
import com.rotatsulabs.specty.ui.design.components.SegmentedControl

private const val PREFS = "specty"
private const val KEY_ASPECT = "report_aspect"
private const val KEY_SECTIONS = "report_sections"
private const val KEY_LOOK = "report_look"

/** Which colours the image is drawn in. */
private enum class ReportLook(val label: Int) {
    Auto(R.string.report_look_auto), Light(R.string.theme_light), Dark(R.string.theme_dark), Amoled(R.string.report_look_amoled)
}

/**
 * Turns the analysis into an image. The preview card shows exactly what will be saved; below it the shape, the colours,
 * the sections and an optional caption are picked. The choices (not the caption) are remembered for the next export.
 * On a wide window the preview sits beside the options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(result: AnalysisResult, appearance: Appearance, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appScheme = MaterialTheme.colorScheme
    val wide = rememberIsWide()

    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var options by remember { mutableStateOf(ReportOptions.of(prefs.getInt(KEY_ASPECT, 0), prefs.getInt(KEY_SECTIONS, -1))) }
    var look by remember { mutableStateOf(ReportLook.entries[prefs.getInt(KEY_LOOK, 0).coerceIn(0, ReportLook.entries.lastIndex)]) }
    var caption by remember { mutableStateOf("") }
    fun update(next: ReportOptions) {
        options = next
        prefs.edit().putInt(KEY_ASPECT, next.aspect.ordinal).putInt(KEY_SECTIONS, next.sectionMask()).apply()
    }

    // The image is drawn in the chosen look; "match app" uses the colours the screen has right now.
    val scheme = remember(look, appearance, appScheme) {
        when (look) {
            ReportLook.Auto -> appScheme
            ReportLook.Light -> colorSchemeFor(context, appearance, dark = false, pureBlack = false)
            ReportLook.Dark -> colorSchemeFor(context, appearance, dark = true, pureBlack = false)
            ReportLook.Amoled -> colorSchemeFor(context, appearance, dark = true, pureBlack = true)
        }
    }
    val theme = remember(scheme, result) {
        val quality = ReportFacts.quality(result.info, result.authenticity.verdict)
        val tagVerdict = when (quality) {
            ReportFacts.Quality.Lossless -> Verdict.GENUINE
            ReportFacts.Quality.HiRes -> Verdict.HI_RES
            ReportFacts.Quality.Atmos -> Verdict.DOLBY_ATMOS
            null -> null
        }
        val neutral = scheme.surfaceContainerHigh to scheme.onSurface
        ReportTheme.of(
            scheme,
            verdict = verdictColors(result.authenticity.verdict, scheme) ?: neutral,
            tag = tagVerdict?.let { verdictColors(it, scheme) } ?: neutral,
        )
    }

    val input by produceState<ReportInput?>(null, result) {
        value = withContext(Dispatchers.Default) {
            try { ReportExporter.prepare(context, result) } catch (_: OutOfMemoryError) { null }
        }
    }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(input, options, theme, caption) {
        val ready = input ?: return@LaunchedEffect
        delay(120)
        val bitmap = withContext(Dispatchers.Default) {
            try { ReportExporter.render(ready, result, theme, options, caption) } catch (_: OutOfMemoryError) { null }
        }
        if (bitmap != null) preview = bitmap
    }

    var busy by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    fun export(share: Boolean) {
        val ready = input ?: return
        busy = true
        val chosen = options
        val note = caption
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                val bitmap = try { ReportExporter.render(ready, result, theme, chosen, note) } catch (_: OutOfMemoryError) { null }
                try {
                    bitmap?.let {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        SpectrogramExporter.savePng(context, it, ReportFacts.fileName(result.info.title, stamp))
                    }
                } finally {
                    bitmap?.recycle()
                }
            }
            busy = false
            when {
                uri == null -> Toast.makeText(context, context.getString(R.string.toast_image_failed), Toast.LENGTH_SHORT).show()
                share -> context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        context.getString(R.string.analysis_share_title),
                    ),
                )
                else -> Toast.makeText(context, context.getString(R.string.toast_saved_gallery), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val gate = rememberStorageGate { export(sharing) }
    fun request(share: Boolean) {
        if (busy || input == null) return
        sharing = share
        gate()
    }

    val previewCard: @Composable (Modifier, Boolean) -> Unit = { m, fill ->
        PreviewCard(result.info.title, options.aspect, preview, m, fill)
    }
    val controls: @Composable () -> Unit = {
        Controls(
            options = options, look = look, caption = caption,
            onOptions = ::update,
            onLook = { look = it; prefs.edit().putInt(KEY_LOOK, it.ordinal).apply() },
            onCaption = { caption = it },
        )
    }
    val actions: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveButton(
                stringResource(if (busy) R.string.working else R.string.report_save), { request(false) },
                modifier = Modifier.weight(1f), icon = Icons.Rounded.Download, enabled = !busy && input != null,
            )
            ExpressiveButton(
                stringResource(R.string.report_share), { request(true) },
                modifier = Modifier.weight(1f), kind = ButtonKind.Tonal, icon = Icons.Rounded.IosShare, enabled = !busy && input != null,
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(stringResource(R.string.report_title), onBack = onBack)
        if (wide) {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(Modifier.weight(1.1f).fillMaxHeight().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                    previewCard(Modifier.fillMaxHeight(), true)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        controls()
                        Spacer(Modifier.height(4.dp))
                    }
                    actions(Modifier.navigationBarsPadding().padding(vertical = 12.dp))
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Reveal(0) { previewCard(Modifier, false) }
                Reveal(1) { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { controls() } }
                Spacer(Modifier.height(4.dp))
            }
            actions(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

/** The image that will be saved, at the shape it will have. [fill] fits it to the height its parent offers. */
@Composable
private fun PreviewCard(title: String, aspect: ReportAspect, bitmap: Bitmap?, modifier: Modifier, fill: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ratio by animateFloatAsState(aspect.width / aspect.height.toFloat(), Motion.spatial(), label = "previewRatio")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val room = if (fill && constraints.hasBoundedHeight) maxHeight else 420.dp
        val w = minOf(maxWidth, room * ratio.coerceAtLeast(0.1f))
        Box(
            Modifier.size(w, w / ratio.coerceAtLeast(0.1f)).clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                ShapeLoader(size = 40.dp)
            }
        }
    }
}

/** Format, colours, sections and caption, as chip rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    options: ReportOptions,
    look: ReportLook,
    caption: String,
    onOptions: (ReportOptions) -> Unit,
    onLook: (ReportLook) -> Unit,
    onCaption: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(R.string.report_format) {
            SegmentedControl(
                segments = ReportAspect.entries.map { Segment(it, stringResource(aspectName(it))) },
                selected = options.aspect,
                onSelect = { onOptions(options.copy(aspect = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Group(R.string.report_look) {
            SegmentedControl(
                segments = ReportLook.entries.map { Segment(it, stringResource(it.label)) },
                selected = look,
                onSelect = onLook,
                scrollable = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Group(R.string.report_content) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sectionRows().forEach { row ->
                    val on = options.has(row.section)
                    FilterChip(
                        selected = on,
                        onClick = { onOptions(options.toggled(row.section, !on)) },
                        label = { Text(stringResource(row.title)) },
                        leadingIcon = { Icon(if (on) Icons.Rounded.Check else row.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { onCaption(it.take(MAX_CAPTION)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.report_caption)) },
            maxLines = 3,
        )
    }
}

private const val MAX_CAPTION = 160

@Composable
private fun Group(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        content()
    }
}

private fun aspectName(aspect: ReportAspect) = when (aspect) {
    ReportAspect.Portrait -> R.string.report_portrait
    ReportAspect.Square -> R.string.report_square
    ReportAspect.Landscape -> R.string.report_landscape
}

private class SectionRow(val section: ReportSection, val title: Int, val icon: ImageVector)

@Composable
private fun sectionRows(): List<SectionRow> = remember {
    listOf(
        SectionRow(ReportSection.Cover, R.string.report_section_cover, Icons.Rounded.Image),
        SectionRow(ReportSection.Details, R.string.report_section_details, Icons.Rounded.Info),
        SectionRow(ReportSection.Verdict, R.string.report_section_verdict, Icons.Rounded.Verified),
        SectionRow(ReportSection.KeyLevels, R.string.report_section_levels, Icons.Rounded.Speed),
        SectionRow(ReportSection.Measurements, R.string.report_section_measurements, Icons.Rounded.BarChart),
        SectionRow(ReportSection.Loudness, R.string.report_section_loudness, Icons.AutoMirrored.Rounded.ShowChart),
        SectionRow(ReportSection.Spectrogram, R.string.report_section_spectrogram, Icons.Rounded.GraphicEq),
        SectionRow(ReportSection.Footer, R.string.report_section_footer, Icons.Rounded.Tag),
    )
}
