package com.prism.music.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.theme.PrismIcons

/** One row of cards under a title, remembering its column (search results, artist pages). */
@Composable
fun TvShelfRow(shelf: TvShelf, onItem: (TvItem) -> Unit, tag: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        TvRowTitle(shelf.title, Modifier.padding(start = 48.dp))
        LazyRow(
            Modifier.rememberedFocus().testTag(tag),
            contentPadding = TvRowPadding,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(shelf.items) { i, item ->
                TvCard(item.art, item.title, item.subtitle, { onItem(item) }, Modifier.testTag("$tag/$i"), round = item.kind == TvItem.Kind.ARTIST)
            }
        }
    }
}

/**
 * Search (TV): the search line (OK opens the TV's keyboard, typing shows suggestions as you go) and the microphone
 * (the remote's own voice button works anywhere too). With nothing typed: recent searches. With results: the top
 * result beside the songs, then albums, artists, playlists and videos in rows. Focus opens on the search line.
 */
@Composable
fun TvSearchScreen(
    query: String,
    onType: () -> Unit,
    onVoice: () -> Unit,
    recent: List<String>,
    onRecent: (String) -> Unit,
    top: TvItem?,
    songs: List<TvSong>,
    shelves: List<TvShelf>,
    onItem: (TvItem) -> Unit,
    onSong: (TvSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier.fillMaxSize().testTag(TAG_SEARCH),
        contentPadding = PaddingValues(top = 27.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    onClick = onType,
                    modifier = Modifier.width(620.dp).height(60.dp).entryFocus().testTag("$TAG_SEARCH/field"),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = scheme.surfaceVariant, focusedContainerColor = scheme.onSurface, contentColor = scheme.onSurface, focusedContentColor = scheme.surface),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(PrismIcons.Search, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(query.ifEmpty { tr(R.string.tv_search_hint) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TvIconButton(PrismIcons.Mic, tr(R.string.tv_voice_search), onVoice, Modifier.testTag("$TAG_SEARCH/voice"))
            }
        }
        if (query.isEmpty()) {
            if (recent.isNotEmpty()) item {
                Column {
                    TvRowTitle(tr(R.string.recent_searches), Modifier.padding(start = 48.dp))
                    LazyRow(Modifier.rememberedFocus(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = TvRowPadding) {
                        itemsIndexed(recent) { i, r -> TvButton(r, { onRecent(r) }, Modifier.testTag("$TAG_SEARCH/recent/$i"), icon = PrismIcons.History) }
                    }
                }
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    if (top != null) Column(Modifier.width(200.dp)) {
                        Text(tr(R.string.top_result), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = scheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                        TvCard(top.art, top.title, top.subtitle, { onItem(top) }, Modifier.testTag("$TAG_SEARCH/top"), round = top.kind == TvItem.Kind.ARTIST, width = 200.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        TvRowTitle(tr(R.string.songs))
                        Column(Modifier.rememberedFocus(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            songs.take(5).forEachIndexed { i, s -> TvSongRow(s.title, s.artist, s.length, { onSong(s) }, Modifier.testTag("$TAG_SEARCH/song/$i"), art = s.art) }
                        }
                    }
                }
            }
            itemsIndexed(shelves) { r, shelf -> TvShelfRow(shelf, onItem, "$TAG_SEARCH/row$r") }
        }
    }
}

/**
 * Library (TV): tabs along the top (playlists, albums, artists, liked songs, history), the chosen one's things in a
 * grid below. Moving along the tabs switches what's shown; Down enters the grid, which remembers its place per tab.
 */
@Composable
fun TvLibraryScreen(
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    items: List<TvItem>,
    onItem: (TvItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(start = 48.dp, top = 27.dp).testTag(TAG_LIBRARY)) {
        TabRow(selectedTabIndex = tab, modifier = Modifier.testTag("$TAG_LIBRARY/tabs")) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = i == tab, onFocus = { onTab(i) }, onClick = { onTab(i) }, modifier = (if (i == tab) Modifier.entryFocus() else Modifier).testTag("$TAG_LIBRARY/tab/$i")) {
                    Text(t, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(132.dp),
            Modifier.fillMaxSize().rememberedFocus().testTag("$TAG_LIBRARY/grid"),
            contentPadding = PaddingValues(end = 48.dp, top = 6.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(items) { i, item ->
                TvCard(item.art, item.title, item.subtitle, { onItem(item) }, Modifier.testTag("$TAG_LIBRARY/grid/$i"), round = item.kind == TvItem.Kind.ARTIST, width = 132.dp)
            }
        }
    }
}

/**
 * An album or playlist (TV): its big cover, title and details on the left with Play and Shuffle; the songs in a list
 * on the right. Focus opens on Play; Right moves into the songs (the list remembers its place). View and play only:
 * editing playlists stays on the phone.
 */
@Composable
fun TvCollectionScreen(
    title: String,
    details: String,
    art: String?,
    songs: List<TvSong>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSong: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playing: Int = -1,
    accent: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxSize().tvBackdrop(accent, scheme.background).padding(TvSafe).testTag(TAG_COLLECTION)) {
        Column(Modifier.width(300.dp)) {
            Artwork(art, Modifier.size(300.dp), shape = RoundedCornerShape(16.dp), size = 720)
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(tr(R.string.widget_play), onPlay, Modifier.entryFocus().testTag("$TAG_COLLECTION/play"), icon = PrismIcons.Play, primary = true)
                TvButton(tr(R.string.shuffle), onShuffle, Modifier.testTag("$TAG_COLLECTION/shuffle"), icon = PrismIcons.Shuffle)
            }
        }
        Spacer(Modifier.width(48.dp))
        LazyColumn(Modifier.weight(1f).rememberedFocus().testTag("$TAG_COLLECTION/songs"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(songs) { i, s -> TvSongRow(s.title, s.artist, s.length, { onSong(i) }, Modifier.testTag("$TAG_COLLECTION/song/$i"), number = i + 1, playing = i == playing) }
        }
    }
}

/**
 * An artist (TV): their picture and name with Play, Shuffle and Radio, then rows: popular songs, albums, singles, music
 * videos, artists fans also like. Focus opens on Play.
 */
@Composable
fun TvArtistScreen(
    name: String,
    details: String?,
    art: String?,
    shelves: List<TvShelf>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRadio: () -> Unit,
    onItem: (TvItem) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier.fillMaxSize().tvBackdrop(accent, scheme.background).testTag(TAG_ARTIST),
        contentPadding = PaddingValues(top = 27.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(art, Modifier.size(200.dp), shape = CircleShape, size = 544, placeholderIcon = PrismIcons.Person)
                Spacer(Modifier.width(36.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (details != null) Text(details, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvButton(tr(R.string.widget_play), onPlay, Modifier.entryFocus().testTag("$TAG_ARTIST/play"), icon = PrismIcons.Play, primary = true)
                        TvButton(tr(R.string.shuffle), onShuffle, Modifier.testTag("$TAG_ARTIST/shuffle"), icon = PrismIcons.Shuffle)
                        TvButton(tr(R.string.tv_radio), onRadio, Modifier.testTag("$TAG_ARTIST/radio"), icon = PrismIcons.Radio)
                    }
                }
            }
        }
        itemsIndexed(shelves) { r, shelf -> TvShelfRow(shelf, onItem, "$TAG_ARTIST/row$r") }
    }
}

const val TAG_SEARCH = "tv_search"
const val TAG_LIBRARY = "tv_library"
const val TAG_COLLECTION = "tv_collection"
const val TAG_ARTIST = "tv_artist"
