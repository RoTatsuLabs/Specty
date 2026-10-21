package com.rotatsulabs.specty.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.data.LibraryFolder
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.IconBadge
import com.rotatsulabs.specty.ui.design.components.ListRow
import com.rotatsulabs.specty.ui.design.components.SettingGroup
import com.rotatsulabs.specty.ui.design.components.groupCorners

/**
 * Which folders the library is built from: the ones it includes, and folders inside them to leave out. Excluding a
 * folder hides everything below it as well.
 */
@Composable
fun FoldersScreen(
    included: List<LibraryFolder>,
    excluded: List<LibraryFolder>,
    onAddIncluded: () -> Unit,
    onRemoveIncluded: (Uri) -> Unit,
    onAddExcluded: () -> Unit,
    onRemoveExcluded: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(stringResource(R.string.library_menu_folders), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Reveal(0) {
                FolderSection(
                    title = stringResource(R.string.folders_included),
                    hint = stringResource(R.string.folders_included_hint),
                    empty = stringResource(R.string.library_no_folders),
                    folders = included,
                    icon = Icons.Rounded.FolderOpen,
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer,
                    removeLabel = R.string.library_remove_folder,
                    addLabel = stringResource(R.string.library_add_folder),
                    addIcon = Icons.Rounded.CreateNewFolder,
                    onAdd = onAddIncluded,
                    onRemove = onRemoveIncluded,
                )
            }
            Reveal(1) {
                FolderSection(
                    title = stringResource(R.string.folders_excluded),
                    hint = stringResource(R.string.folders_excluded_hint),
                    empty = stringResource(R.string.folders_excluded_none),
                    folders = excluded,
                    icon = Icons.Rounded.FolderOff,
                    container = scheme.errorContainer,
                    content = scheme.onErrorContainer,
                    removeLabel = R.string.folders_include_again,
                    addLabel = stringResource(R.string.folders_exclude_add),
                    addIcon = Icons.Rounded.Add,
                    onAdd = onAddExcluded,
                    onRemove = onRemoveExcluded,
                    // Excluding only means something inside an included folder.
                    addEnabled = included.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun FolderSection(
    title: String,
    hint: String,
    empty: String,
    folders: List<LibraryFolder>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    removeLabel: Int,
    addLabel: String,
    addIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
    addEnabled: Boolean = true,
) {
    SettingGroup(title) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        if (folders.isEmpty()) {
            ListRow(empty, corners = groupCorners(0, 1), leading = { IconBadge(icon, container = container, content = content) })
        }
        folders.forEachIndexed { i, folder ->
            ListRow(
                title = folder.name,
                supporting = folder.path,
                corners = groupCorners(i, folders.size),
                leading = { IconBadge(icon, container = container, content = content) },
                trailing = {
                    ExpressiveIconButton(Icons.Rounded.Close, stringResource(removeLabel, folder.name), { onRemove(folder.uri) }, tonal = false)
                },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            ExpressiveButton(addLabel, onAdd, kind = ButtonKind.Tonal, icon = addIcon, enabled = addEnabled)
        }
    }
}
