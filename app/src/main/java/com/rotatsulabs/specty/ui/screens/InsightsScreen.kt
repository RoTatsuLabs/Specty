package com.rotatsulabs.specty.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.design.CoverFlight
import com.rotatsulabs.specty.ui.design.CoverSource
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar

/**
 * What the library holds and how it measured, then the songs that appear more than once so the weaker copy can be
 * spotted. A track opens its analysis like it does anywhere else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    tracks: List<Track>,
    verdicts: Map<String, Verdict>,
    keyFor: (Track) -> String,
    onBack: () -> Unit,
    onOpenTrack: (Track, Rect?, CoverSource?) -> Unit,
    onCompare: (Track, Track) -> Unit,
    dockVisible: Boolean,
    flight: CoverFlight?,
    modifier: Modifier = Modifier,
) {
    val report = remember(tracks, verdicts, keyFor) { LibraryReport.build(tracks, TagFields) { verdicts[keyFor(it)] } }
    val groups = remember(tracks, verdicts, keyFor) {
        Duplicates.find(tracks, pageFields(verdicts, keyFor)) { ResultFilter.of(verdicts[keyFor(it)]).quality }
    }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.insights_share_title)
    val labels = ResultFilter.entries.associateWith { stringResource(it.labelRes) }
    val totals = stringResource(
        R.string.insights_totals, report.total, Fmt.size(report.sizeBytes), Fmt.duration(report.durationMs / 1000.0),
    )
    val checked = stringResource(R.string.insights_checked, report.checked, report.total)
    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(
            title = stringResource(R.string.insights_title),
            onBack = onBack,
            actions = {
                if (report.total > 0) {
                    ExpressiveIconButton(
                        Icons.Rounded.IosShare, stringResource(R.string.insights_share),
                        {
                            val text = buildString {
                                appendLine(shareTitle)
                                appendLine("$totals  ·  $checked")
                                report.results.forEach { (kind, n) -> appendLine("${labels.getValue(kind)}: $n") }
                                if (report.formats.isNotEmpty()) append(report.formats.joinToString(", ") { "${it.first} ${it.second}" })
                            }.trim()
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        tonal = false,
                    )
                }
            },
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            item(key = "summary") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(totals, style = MaterialTheme.typography.titleMedium)
                    Text(checked, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    report.results.forEach { (kind, n) -> ResultBar(labels.getValue(kind), kind, n, report.total) }
                }
            }
            if (report.formats.isNotEmpty()) {
                item(key = "formatsHeader") { SectionTitle(stringResource(R.string.insights_formats)) }
                item(key = "formats") {
                    FlowRow(
                        Modifier.padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        report.formats.forEach { (name, n) ->
                            Surface(shape = SpectyShapes.Pill.toShape(), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "$name  $n", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "dupHeader") { SectionTitle(stringResource(R.string.insights_duplicates)) }
            if (groups.isEmpty()) {
                item(key = "dupNone") {
                    Text(
                        stringResource(R.string.insights_duplicates_none), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            } else {
                item(key = "dupHint") {
                    Text(
                        pluralStringResource(R.plurals.insights_duplicate_songs, groups.size, groups.size) + "  ·  " +
                            stringResource(R.string.insights_duplicates_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                for (group in groups) {
                    val id = group.best.uri.toString()
                    items(group.copies, key = { "d$id${it.uri}" }) { t ->
                        Column {
                            if (t == group.best) BestMark()
                            TrackRow(t, 0, verdicts[keyFor(t)], { b, c -> onOpenTrack(t, b, c) }, Modifier.padding(horizontal = 10.dp), flight)
                            if (t != group.best) {
                                TextButton(onClick = { onCompare(group.best, t) }, Modifier.padding(start = 14.dp)) {
                                    Text(stringResource(R.string.insights_compare))
                                }
                            }
                        }
                    }
                    item(key = "gap$id") { Spacer(Modifier.height(10.dp)) }
                }
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(if (dockVisible) 130.dp else 40.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BestMark() {
    Text(
        stringResource(R.string.insights_best), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 24.dp, top = 6.dp),
    )
}

/** One result kind: its label, its count and a bar showing its share of the library. */
@Composable
private fun ResultBar(label: String, kind: ResultFilter, count: Int, total: Int) {
    val scheme = MaterialTheme.colorScheme
    val color = when (kind) {
        ResultFilter.Lossless, ResultFilter.HiRes, ResultFilter.Atmos -> scheme.tertiary
        ResultFilter.NeedsLook, ResultFilter.LossyFormat -> scheme.error
        else -> scheme.outline
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((count.toFloat() / total.coerceAtLeast(1)).coerceIn(0.02f, 1f)).clip(CircleShape).background(color))
        }
    }
}
