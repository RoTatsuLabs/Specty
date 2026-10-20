package com.rotatsulabs.specty.ui.components

import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.WavyProgressBar
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.AnalysisState
import com.rotatsulabs.specty.ui.theme.Motion

/** Compact progress / result card docked over the library, so analysis never blocks the screen. */
@Composable
fun AnalysisDock(state: AnalysisState, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, queued: Int = 0) {
    val scheme = MaterialTheme.colorScheme
    val done = state is AnalysisState.Done || state is AnalysisState.Limited
    val failed = state is AnalysisState.Failed
    val container = when {
        failed -> scheme.errorContainer
        done -> scheme.tertiaryContainer
        else -> scheme.secondaryContainer
    }
    val content = when {
        failed -> scheme.onErrorContainer
        done -> scheme.onTertiaryContainer
        else -> scheme.onSecondaryContainer
    }
    val title = when (state) {
        is AnalysisState.Running -> state.title.withoutExplicitMark()
        is AnalysisState.Done -> state.result.info.title.withoutExplicitMark()
        is AnalysisState.Failed -> state.title.withoutExplicitMark()
        is AnalysisState.Limited -> state.info.title.withoutExplicitMark()
        AnalysisState.Idle -> ""
    }
    val subtitle = when (state) {
        is AnalysisState.Running -> (state.progress * 100).toInt().let { pct ->
            if (queued > 0) stringResource(R.string.dock_progress_queued, stageText(state.stage), pct, queued) else stringResource(R.string.dock_progress, stageText(state.stage), pct)
        }
        is AnalysisState.Done -> stringResource(R.string.dock_ready)
        is AnalysisState.Failed -> failureText(state.failure, state.detail)
        is AnalysisState.Limited -> stringResource(R.string.dock_limited)
        AnalysisState.Idle -> ""
    }
    val progress by animateFloatAsState((state as? AnalysisState.Running)?.progress ?: 0f, Motion.effects(), label = "dockProgress")

    MorphSurface(
        modifier = modifier.fillMaxWidth(),
        corners = Corners(28.dp),
        color = container,
        contentColor = content,
        onClick = { if (done) onOpen() },
        enabled = done,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    failed -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(28.dp))
                    done -> Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(28.dp))
                    else -> ShapeLoader(size = 28.dp, color = content)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                ExpressiveIconButton(Icons.Rounded.Close, stringResource(if (done || failed) R.string.action_dismiss else R.string.action_cancel), onDismiss, tonal = false)
            }
            if (state is AnalysisState.Running) {
                WavyProgressBar(
                    progress,
                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
                    color = content,
                    track = content.copy(alpha = 0.18f),
                )
            }
        }
    }
}
