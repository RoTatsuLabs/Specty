package com.rotatsulabs.specty.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The width of the window in dp; it follows rotation, split screen and foldables. */
@Composable
fun rememberWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/** True when the window is wide enough for two panes. */
@Composable
fun rememberIsWide(): Boolean = AdaptiveMath.isWide(rememberWidthDp())

/** Keeps a screen to a readable column and centres it when the window is wider than that. */
@Composable
fun CenteredPane(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AdaptiveMath.READABLE.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
