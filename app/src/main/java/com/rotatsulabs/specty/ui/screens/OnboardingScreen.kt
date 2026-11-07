package com.rotatsulabs.specty.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.IconBadge

/**
 * First-run setup: one screen instead of a slideshow. It says what the app does in three lines, then asks for the one
 * thing it needs (a music folder). [onFinish] is called when the person leaves by any route.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, hasFolders: Boolean, onPickFolder: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    BackHandler(onBack = onFinish)

    Column(
        modifier.fillMaxSize()
            .background(scheme.surface)
            // Swallow touches so nothing underneath reacts while setup is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(Modifier.size(72.dp), shape = MaterialTheme.shapes.extraLarge, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(40.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onboarding_0_title), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.onboarding_0_body), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Step(Icons.Rounded.GraphicEq, R.string.onboarding_1_title, R.string.onboarding_1_body)
            Step(Icons.Rounded.Verified, R.string.onboarding_2_title, R.string.onboarding_2_body)
            Step(Icons.Rounded.Speed, R.string.onboarding_3_title, R.string.onboarding_3_body)
        }

        Surface(color = scheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasFolders) {
                    Prompt(Icons.Rounded.CheckCircle, R.string.onboarding_folder_added, R.string.onboarding_folder_added_desc)
                    ExpressiveButton(stringResource(R.string.onboarding_start), onFinish, modifier = Modifier.fillMaxWidth())
                } else {
                    Prompt(Icons.Rounded.FolderOpen, R.string.onboarding_folder_title, R.string.onboarding_folder_desc)
                    ExpressiveButton(stringResource(R.string.onboarding_choose_folder), onPickFolder, modifier = Modifier.fillMaxWidth())
                    ExpressiveButton(stringResource(R.string.onboarding_skip), onFinish, kind = ButtonKind.Text, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: Int, body: Int) {
    ListItem(
        headlineContent = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(stringResource(body)) },
        leadingContent = { IconBadge(icon) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun Prompt(icon: ImageVector, title: Int, body: Int) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
