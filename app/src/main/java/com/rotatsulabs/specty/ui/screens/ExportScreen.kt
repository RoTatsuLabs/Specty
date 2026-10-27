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

/**
 * The image that will be saved, in a framed card: a header with the track, the image at the shape it will have, and a
 * footer with its size. [fill] makes the card as tall as its parent and fits the image to what is left.
 */
@Composable
private fun PreviewCard(title: String, aspect: ReportAspect, bitmap: Bitmap?, modifier: Modifier, fill: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ratio by animateFloatAsState(aspect.width / aspect.height.toFloat(), Motion.spatial(), label = "previewRatio")
    Column(
        modifier.clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 36.dp, bottomStart = 14.dp)).background(scheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainer)))
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp),
        ) {
            Text(
                stringResource(R.string.report_preview).uppercase(),
                style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BoxWithConstraints(
            (if (fill) Modifier.weight(1f) else Modifier).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val room = if (constraints.hasBoundedHeight) maxHeight else 480.dp
            val w = minOf(maxWidth, room * ratio.coerceAtLeast(0.1f))
            Box(
                Modifier.size(w, w / ratio.coerceAtLeast(0.1f)).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    ShapeLoader(size = 40.dp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(Icons.Rounded.GraphicEq, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.report_png), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Text("${aspect.width} × ${aspect.height}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportAspect.entries.forEach { aspect ->
                    val (icon, name) = aspectInfo(aspect)
                    Chip(
                        selected = aspect == options.aspect, onClick = { onOptions(options.copy(aspect = aspect)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(aspect.label, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(name), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            val (_, _, good, bad) = aspectInfo(options.aspect)
            AnimatedContent(good to bad, label = "aspectNotes", transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) }) { (g, b) ->
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Note(Icons.Rounded.Check, stringResource(g))
                    Note(Icons.Rounded.WarningAmber, stringResource(b))
                }
            }
        }

        Group(R.string.report_look) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportLook.entries.forEach { l ->
                    Chip(selected = l == look, onClick = { onLook(l) }) {
                        Text(stringResource(l.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
        }

        Group(R.string.report_content) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sectionRows().forEach { row ->
                    val on = options.has(row.section)
                    Chip(selected = on, onClick = { onOptions(options.toggled(row.section, !on)) }, compact = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (on) Icons.Rounded.Check else row.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(row.title), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { onCaption(it.take(MAX_CAPTION)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.report_caption)) },
            maxLines = 3,
            shape = RoundedCornerShape(20.dp),
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

/** A selectable pill: filled in the primary container when picked, quiet otherwise. */
@Composable
private fun Chip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    MorphSurface(
        modifier,
        corners = Corners(if (compact) 20.dp else 28.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        onClick = onClick,
        animateColor = true,
        role = androidx.compose.ui.semantics.Role.Button,
    ) {
        Column(
            Modifier.padding(horizontal = if (compact) 16.dp else 12.dp, vertical = if (compact) 11.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun Note(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class AspectInfo(val icon: ImageVector, val name: Int, val good: Int, val bad: Int)

private fun aspectInfo(aspect: ReportAspect) = when (aspect) {
    ReportAspect.Portrait -> AspectInfo(Icons.Rounded.CropPortrait, R.string.report_portrait, R.string.report_portrait_good, R.string.report_portrait_bad)
    ReportAspect.Square -> AspectInfo(Icons.Rounded.CropSquare, R.string.report_square, R.string.report_square_good, R.string.report_square_bad)
    ReportAspect.Landscape -> AspectInfo(Icons.Rounded.CropLandscape, R.string.report_landscape, R.string.report_landscape_good, R.string.report_landscape_bad)
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
