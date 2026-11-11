package com.rotatsulabs.specty.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.design.components.NavItem

/** Where the bottom bar's destinations sit, so the real bar and the Settings preview list the same ones. */
object Tabs {
    const val TRACKS = 0
    const val ALBUMS = 1
    const val ARTISTS = 2
    const val SEARCH = 3
}

/** The destinations of the bottom bar: the three library lists and search set apart. */
@Composable
fun spectyNavItems(): List<NavItem> = listOf(
    NavItem(stringResource(R.string.browse_tracks), Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    NavItem(stringResource(R.string.browse_albums), Icons.Outlined.Album, Icons.Rounded.Album),
    NavItem(stringResource(R.string.browse_artists), Icons.Outlined.Person, Icons.Rounded.Person),
    NavItem(stringResource(R.string.nav_search), Icons.Outlined.Search, Icons.Rounded.Search),
)
