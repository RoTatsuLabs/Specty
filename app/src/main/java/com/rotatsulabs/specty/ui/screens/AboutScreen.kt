package com.rotatsulabs.specty.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotatsulabs.specty.BuildConfig
import com.rotatsulabs.specty.R
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.rotatsulabs.specty.dev.TapCounter
import com.rotatsulabs.specty.about.Library
import com.rotatsulabs.specty.about.LicenseKind
import com.rotatsulabs.specty.about.OpenSource
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.ListRow
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
import com.rotatsulabs.specty.ui.design.components.SettingGroup
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.groupCorners
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen about page, opened from the overflow menu: the app, where its source lives, and everything it is built from. */
@Composable
fun AboutScreen(
    onReplayIntro: () -> Unit,
    developer: Boolean,
    onUnlockDeveloper: () -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    // A device without a browser must not crash the page.
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    var licence by remember { mutableStateOf<LicenseKind?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    licence?.let { kind -> LicenseDialog(kind, onDismiss = { licence = null }, onOpenOnline = { open(kind.url) }) }

    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(title = stringResource(R.string.about_title), onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Hero(developer, onUnlockDeveloper)

            // The three things a person comes here for sit right under the name.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                ExpressiveButton(stringResource(R.string.about_source), { open(OpenSource.SOURCE_URL) }, kind = ButtonKind.Tonal, icon = Icons.Rounded.Code)
                ExpressiveButton(stringResource(R.string.about_report), { open(OpenSource.SOURCE_URL + "/issues") }, kind = ButtonKind.Tonal, icon = Icons.Rounded.BugReport)
            }

            SettingGroup(stringResource(R.string.about_privacy)) {
                ListRow(
                    stringResource(R.string.about_privacy_title),
                    supporting = stringResource(R.string.about_privacy_desc),
                    icon = Icons.Rounded.Lock,
                    corners = groupCorners(0, 1),
                )
            }

            SettingGroup(stringResource(R.string.about_licence)) {
                val libraries = OpenSource.libraries
                val rows = libraries.size + 2
                ListRow(
                    stringResource(R.string.app_name), supporting = stringResource(R.string.about_licence_tap, OpenSource.app.license.title), icon = Icons.Rounded.Gavel,
                    corners = groupCorners(0, rows), onClick = { licence = OpenSource.app.license },
                    trailing = { Chevron() },
                )
                libraries.forEachIndexed { index, library ->
                    LibraryRow(
                        library = library,
                        corners = groupCorners(index + 1, rows),
                        expanded = expanded == library.name,
                        onToggle = { expanded = if (expanded == library.name) null else library.name },
                        onLicense = { licence = library.license },
                        onOpen = { open(library.url) },
                    )
                }
                ListRow(
                    stringResource(R.string.licences_title), supporting = stringResource(R.string.licences_row_desc), icon = Icons.Rounded.Description,
                    corners = groupCorners(rows - 1, rows), onClick = onOpenLicences,
                    trailing = { Chevron() },
                )
            }

            SettingGroup(stringResource(R.string.about_more)) {
                ListRow(
                    stringResource(R.string.about_intro), icon = Icons.Rounded.Replay,
                    corners = groupCorners(0, 1), onClick = onReplayIntro,
                    trailing = { Chevron() },
                )
            }

            Text(
                stringResource(R.string.about_made_by),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun Hero(developer: Boolean, onUnlockDeveloper: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val taps = remember { TapCounter() }
    var toast by remember { mutableStateOf<Toast?>(null) }
    fun say(text: String) {
        toast?.cancel()
        toast = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EqualizerMark()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Pill(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                when (val r = taps.tap(SystemClock.elapsedRealtime())) {
                    TapCounter.Result.Idle -> Unit
                    is TapCounter.Result.Remaining -> if (!developer) say(context.resources.getQuantityString(R.plurals.about_dev_taps, r.taps, r.taps))
                    TapCounter.Result.Unlocked -> if (developer) say(context.getString(R.string.about_dev_already)) else {
                        onUnlockDeveloper()
                        say(context.getString(R.string.about_dev_on))
                    }
                }
            },
            container = scheme.primaryContainer, content = scheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.about_hero),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OpenIcon() {
    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LibraryRow(
    library: Library,
    corners: Corners,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLicense: () -> Unit,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "libraryTurn")
    MorphSurface(Modifier.fillMaxWidth(), corners, onClick = onToggle) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(library.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(library.version, library.license.spdx).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(if (expanded) R.string.about_collapse else R.string.about_expand), modifier = Modifier.rotate(turn))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_by, library.author), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    Text(stringResource(library.summary), style = MaterialTheme.typography.bodyMedium)
                    library.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveButton(stringResource(R.string.about_licence_button), onLicense, kind = ButtonKind.Tonal, icon = Icons.Rounded.Description)
                        ExpressiveButton(stringResource(R.string.about_website), onOpen, kind = ButtonKind.Tonal, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    }
                }
            }
        }
    }
}

/** The full licence text, read from the app's assets so it is available offline. */
@Composable
private fun LicenseDialog(kind: LicenseKind, onDismiss: () -> Unit, onOpenOnline: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<String>?>(null, kind) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(kind.asset).bufferedReader().use { it.readText() } }
                .getOrNull()
                ?.trim()
                ?.split(Regex("\n\\s*\n"))
                ?: emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(kind.title) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentAlignment = Alignment.Center) {
                val shown = paragraphs
                when {
                    shown == null -> ShapeLoader(size = 40.dp)
                    shown.isEmpty() -> Text(
                        stringResource(R.string.about_licence_missing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown) { paragraph ->
                            Text(
                                paragraph.trim('\n'),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { ExpressiveButton(stringResource(R.string.action_close), onDismiss, kind = ButtonKind.Text) },
        dismissButton = { ExpressiveButton(stringResource(R.string.about_read_online), onOpenOnline, kind = ButtonKind.Text) },
    )
}
