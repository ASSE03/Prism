package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.LibraryView
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.HomeChoices
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.LibraryTilesSheet
import com.prism.music.ui.screens.ReplayCardPreview
import com.prism.music.ui.screens.ReplayCardStyle
import com.prism.music.ui.screens.Toggle
import com.prism.music.ui.screens.TopButtons

// ---------------------------------------------------------------------------- Library & Replay

private val libraryTabs = listOf("PLAYLISTS" to tr(R.string.playlists), "SONGS" to tr(R.string.songs), "ALBUMS" to tr(R.string.albums), "ARTISTS" to tr(R.string.artists), "HISTORY" to tr(R.string.history), "DOWNLOADS" to tr(R.string.downloads))

/** Customize Prism → Home & Library (1.7.6): how Home and Library are laid out. */
@Composable
internal fun HomeLibraryLook() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(tr(R.string.home)) {
            Item(tr(R.string.customize_home), tr(R.string.sections_the_top_of_home_shortcuts), onClick = { nav.go(Routes.CATALOGUE) }) { Chevron() }
            Toggle(tr(R.string.add_new_sections_automatically), tr(R.string.new_youtube_music_shelves_appear_on), s.autoAddSections) { prefs.setAutoAddSections(it) }
            Toggle(
                tr(R.string.continue_line), tr(R.string.continue_under_the_greeting), s.homeContinue,
                info = tr(R.string.the_album_you_were_last_playing),
            ) { prefs.setHomeContinue(it) }
            // #349: also on Customize Home; here too, where Settings' search opens it.
            Label(tr(R.string.quick_picks_from), info = tr(R.string.quick_picks_from_info))
            Chips(com.prism.music.data.prefs.QuickPicksSource.entries, s.behavior.quickPicksSource, { it.label }) { prefs.setQuickPicksSource(it) }
            Label(tr(R.string.recently_played_shows), info = tr(R.string.every_song_you_played_or_the))
            Chips(com.prism.music.data.prefs.RecentShows.entries, s.behavior.recentShows, { it.label }) { prefs.setRecentShows(it) }
            // 1.8.0: the two top-bar switches as one choice (same stored settings, HomeChoices).
            Label(tr(R.string.buttons_at_the_top_of_home), info = tr(R.string.off_podcasts_opens_from_library_playlists))
            Chips(TopButtons.entries, HomeChoices.topButtons(s), { it.label }) { HomeChoices.setTopButtons(prefs, it) }
            Toggle(tr(R.string.customize_button_at_the_bottom), tr(R.string.at_the_end_of_home), s.homeCustomizeButton) { prefs.setHomeCustomizeButton(it) }
        }
        // Search's own option (1.8.0: from Controls & gestures, it isn't a gesture).
        Group(tr(R.string.search)) {
            Toggle(tr(R.string.open_the_keyboard), tr(R.string.ready_to_type_when_search_opens), s.searchKeyboard) { prefs.setSearchKeyboard(it) }
        }
        val look = ReplayCardStyle.entries.firstOrNull { it.name == s.replayCard } ?: ReplayCardStyle.WASH
        Group(tr(R.string.replay_card), info = tr(R.string.how_replay_looks_on_home_from, look.label, look.note)) {
            Chips(ReplayCardStyle.entries, look, { it.label }) { prefs.setReplayCard(it.name) }
            // #286: what the bars are (the looks with bars show it below, as the card would).
            Label(tr(R.string.replay_card_graph), info = tr(R.string.replay_card_graph_info))
            Chips(com.prism.music.data.prefs.ReplayGraph.entries, s.view.replayGraph, { it.label }) { prefs.setReplayGraph(it) }
            ReplayCardPreview(look, Modifier.padding(top = 6.dp, bottom = 14.dp))
        }
        LibraryLookGroup()
    }
}

/** How Library shows its playlists, albums and artists. */
@Composable
private fun LibraryLookGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(tr(R.string.library)) {
            // Each tab its own way (1.7.6); the button at the top of Library switches the tab you're on.
            listOf("PLAYLISTS" to tr(R.string.playlists_as), "ALBUMS" to tr(R.string.albums_as), "ARTISTS" to tr(R.string.artists_as)).forEach { (tab, title) ->
                Label(title)
                Chips(LibraryView.entries, s.libraryViews[tab] ?: s.libraryView, { it.label }) { prefs.setLibraryTabView(tab, it) }
            }
            Label(tr(R.string.covers_across), info = tr(R.string.in_the_grid_auto_fits_as))
            Chips(com.prism.music.data.prefs.LibraryColumns.entries, s.libraryColumns, { it.label }) { prefs.setLibraryColumns(it) }
            Toggle(tr(R.string.details_under_each_one), tr(R.string.song_counts_owners_and_years), s.librarySubtitles) { prefs.setLibrarySubtitles(it) }
            Label(tr(R.string.open_library_on))
            // "LOCAL" (On this phone) was a tab before Local files got its own page: Playlists stands in for it.
            val startTab = s.libraryStartTab.takeIf { k -> libraryTabs.any { it.first == k } } ?: "PLAYLISTS"
            Chips(libraryTabs.map { it.first }, startTab, { k -> libraryTabs.firstOrNull { it.first == k }?.second ?: k }) { prefs.setLibraryStartTab(it) }
            Spacer(Modifier.height(6.dp))
            Toggle(tr(R.string.full_width_playlist_pictures), tr(R.string.edge_to_edge_like_an_artist), s.playlistHeroCover) { prefs.setPlaylistHeroCover(it) }
            Toggle(tr(R.string.genres_on_playlists), tr(R.string.genres_on_playlists_detail), s.behavior.playlistGenres) { prefs.setPlaylistGenres(it) }
            // #310 (1.8.1): off, Downloads lists each song with just its artist under it.
            Toggle(
                tr(R.string.album_names_in_downloads), tr(R.string.album_names_in_downloads_detail), s.view.downloadsAlbums,
                info = tr(R.string.album_names_in_downloads_info),
            ) { prefs.setDownloadsAlbums(it) }
            var editingTiles by remember { mutableStateOf(false) }
            Item(
                tr(R.string.library_tiles), tr(R.string.of_shown_pick_and_order_them, s.libraryTiles.size, com.prism.music.data.prefs.LibraryTile.offered(com.prism.music.ui.theme.LocalContainer.current.uploads.has.value).size),
                onClick = { editingTiles = true },
            ) { Chevron() }
            if (editingTiles) LibraryTilesSheet { editingTiles = false }
        }
        Group(tr(R.string.artist_pages)) {
            Toggle(tr(R.string.artist_you_title), tr(R.string.artist_you_detail), s.behavior.artistYou, info = tr(R.string.artist_you_info)) { prefs.setArtistYou(it) }
            Toggle(tr(R.string.artist_essentials_title), tr(R.string.artist_essentials_detail), s.behavior.artistEssentials, info = tr(R.string.artist_essentials_info)) { prefs.setArtistEssentials(it) }
            Toggle(tr(R.string.artist_bio_title), tr(R.string.artist_bio_detail), s.behavior.artistBio, info = tr(R.string.artist_bio_info)) { prefs.setArtistBio(it) }
        }
    }
}
