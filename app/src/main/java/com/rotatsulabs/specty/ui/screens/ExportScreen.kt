package com.rotatsulabs.specty.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.SettingGroup
import com.rotatsulabs.specty.ui.design.components.SettingToggleRow
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.groupCorners
import com.rotatsulabs.specty.ui.design.components.verdictStyle
import com.rotatsulabs.specty.ui.spectrogram.ReportAspect
import com.rotatsulabs.specty.ui.spectrogram.ReportExporter
import com.rotatsulabs.specty.ui.spectrogram.ReportFacts
import com.rotatsulabs.specty.ui.spectrogram.ReportOptions
import com.rotatsulabs.specty.ui.spectrogram.ReportSection
import com.rotatsulabs.specty.ui.spectrogram.ReportTheme
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramExporter
import com.rotatsulabs.specty.ui.theme.Motion
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

/**
 * Turns the analysis into an image. The shape and the sections are picked here, the preview is the image that will
 * be saved, and both choices are remembered for the next export.
 */
@Composable
fun ExportScreen(result: AnalysisResult, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    // The image is drawn in the colours the screen has right now, so it matches the app's theme.
    val verdict = verdictStyle(result.authenticity.verdict)
    val tags = mapOf(
        ReportFacts.Quality.Lossless to verdictStyle(Verdict.GENUINE),
        ReportFacts.Quality.HiRes to verdictStyle(Verdict.HI_RES),
        ReportFacts.Quality.Atmos to verdictStyle(Verdict.DOLBY_ATMOS),
    )
    val tag = ReportFacts.quality(result.info, result.authenticity.verdict)?.let { tags[it] }
    val verdictColors = (verdict?.container ?: scheme.surfaceContainerHigh) to (verdict?.content ?: scheme.onSurface)
    val tagColors = (tag?.container ?: scheme.surfaceContainerHigh) to (tag?.content ?: scheme.onSurface)
    val theme = remember(scheme, verdictColors, tagColors) { ReportTheme.of(scheme, verdictColors, tagColors) }

    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var options by remember { mutableStateOf(ReportOptions.of(prefs.getInt(KEY_ASPECT, 0), prefs.getInt(KEY_SECTIONS, -1))) }
    fun update(next: ReportOptions) {
        options = next
        prefs.edit().putInt(KEY_ASPECT, next.aspect.ordinal).putInt(KEY_SECTIONS, next.sectionMask()).apply()
    }

    val input by produceState<com.rotatsulabs.specty.ui.spectrogram.ReportInput?>(null, result) {
        value = withContext(Dispatchers.Default) {
            try { ReportExporter.prepare(context, result) } catch (_: OutOfMemoryError) { null }
        }
    }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(input, options, theme) {
        val ready = input ?: return@LaunchedEffect
        delay(60)
        val bitmap = withContext(Dispatchers.Default) {
            try { ReportExporter.render(ready, result, theme, options) } catch (_: OutOfMemoryError) { null }
        }
        if (bitmap != null) preview = bitmap
    }

    var busy by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    fun export(share: Boolean) {
        val ready = input ?: return
        busy = true
        val chosen = options
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                val bitmap = try { ReportExporter.render(ready, result, theme, chosen) } catch (_: OutOfMemoryError) { null }
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

    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(stringResource(R.string.report_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Reveal(0) { Preview(options.aspect, preview) }

            Reveal(1) {
                SettingGroup(stringResource(R.string.report_format)) {
                    ReportAspect.entries.forEachIndexed { i, aspect ->
                        AspectCard(
                            aspect = aspect,
                            selected = aspect == options.aspect,
                            corners = groupCorners(i, ReportAspect.entries.size),
                            onClick = { update(options.copy(aspect = aspect)) },
                        )
                    }
                }
            }

            Reveal(2) {
                val rows = sectionRows()
                SettingGroup(stringResource(R.string.report_content)) {
                    rows.forEachIndexed { i, row ->
                        SettingToggleRow(
                            title = stringResource(row.title),
                            supporting = stringResource(row.supporting),
                            checked = options.has(row.section),
                            onChecked = { update(options.toggled(row.section, it)) },
                            icon = row.icon,
                            corners = groupCorners(i, rows.size),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
}

/** The image that will be saved, drawn at the shape it will have. The frame follows the shape with a spring. */
@Composable
private fun Preview(aspect: ReportAspect, bitmap: Bitmap?) {
    val ratio by animateFloatAsState(aspect.width / aspect.height.toFloat(), Motion.spatial(), label = "previewRatio")
    val share by animateFloatAsState(
        when (aspect) {
            ReportAspect.Portrait -> 0.56f
            ReportAspect.Square -> 0.78f
            ReportAspect.Landscape -> 1f
        },
        Motion.spatial(), label = "previewWidth",
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(share.coerceIn(0.1f, 1f))
                .aspectRatio(ratio.coerceAtLeast(0.1f))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                ShapeLoader(size = 40.dp)
            }
        }
    }
}

/** One shape to pick: what it is, what it is good at and what it costs. */
@Composable
private fun AspectCard(aspect: ReportAspect, selected: Boolean, corners: Corners, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, name, good, bad) = when (aspect) {
        ReportAspect.Portrait -> AspectInfo(Icons.Rounded.CropPortrait, R.string.report_portrait, R.string.report_portrait_good, R.string.report_portrait_bad)
        ReportAspect.Square -> AspectInfo(Icons.Rounded.CropSquare, R.string.report_square, R.string.report_square_good, R.string.report_square_bad)
        ReportAspect.Landscape -> AspectInfo(Icons.Rounded.CropLandscape, R.string.report_landscape, R.string.report_landscape_good, R.string.report_landscape_bad)
    }
    MorphSurface(
        Modifier.fillMaxWidth(),
        corners,
        if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        onClick = onClick,
        animateColor = true,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${aspect.label}  ·  ${stringResource(name)}", style = MaterialTheme.typography.titleMedium)
                Note(Icons.Rounded.Check, stringResource(good))
                Note(Icons.Rounded.WarningAmber, stringResource(bad))
            }
        }
    }
}

@Composable
private fun Note(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class AspectInfo(val icon: ImageVector, val name: Int, val good: Int, val bad: Int)

private class SectionRow(val section: ReportSection, val title: Int, val supporting: Int, val icon: ImageVector)

@Composable
private fun sectionRows(): List<SectionRow> = remember {
    listOf(
        SectionRow(ReportSection.Details, R.string.report_section_details, R.string.report_section_details_desc, Icons.Rounded.Info),
        SectionRow(ReportSection.Verdict, R.string.report_section_verdict, R.string.report_section_verdict_desc, Icons.Rounded.Verified),
        SectionRow(ReportSection.KeyLevels, R.string.report_section_levels, R.string.report_section_levels_desc, Icons.Rounded.Speed),
        SectionRow(ReportSection.Measurements, R.string.report_section_measurements, R.string.report_section_measurements_desc, Icons.Rounded.BarChart),
        SectionRow(ReportSection.Loudness, R.string.report_section_loudness, R.string.report_section_loudness_desc, Icons.AutoMirrored.Rounded.ShowChart),
        SectionRow(ReportSection.Spectrogram, R.string.report_section_spectrogram, R.string.report_section_spectrogram_desc, Icons.Rounded.GraphicEq),
        SectionRow(ReportSection.Footer, R.string.report_section_footer, R.string.report_section_footer_desc, Icons.Rounded.Tag),
    )
}
