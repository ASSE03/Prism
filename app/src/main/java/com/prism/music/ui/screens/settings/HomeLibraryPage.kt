package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SettingsSection

private val page = SettingsPageId.HOME_LIBRARY

/**
 * Settings → Home & Library (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([homeLibraryIndex]), its "Reset this page" keys ([homeLibraryLookKeys]), its line on the
 * root ([homeLibraryValue]) and the old group names it answers to ([homeLibraryOldGroups]).
 *
 * Interim body: the old Home & Library page as it was ([HomeLibraryLook] in `OldHomeLibrary.kt`), inside the kit's
 * scaffold, until the page's rows are rebuilt with the kit. Its Home group begins with the row that opens Customize
 * Home, which stays a screen of its own (plan §2.6).
 */
@Composable
fun HomeLibraryPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { HomeLibraryLook() }
}

/** The root's line for Home & Library. */
fun homeLibraryValue(s: AppSettings, dark: Boolean): String = SettingsSection.HOME.summary(s, dark)

/** The look settings "Reset this page" puts back: none yet (the old page had no reset). */
val homeLibraryLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val homeLibraryOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val homeLibraryIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.this_section), tr(R.string.hide_this_section), tr(R.string.customize_this_home)),
            SettingEntry(page, tr(R.string.search), tr(R.string.open_the_keyboard), tr(R.string.search_typing)),
            SettingEntry(page, tr(R.string.home), tr(R.string.recently_played_shows), tr(R.string.recently_played_albums_playlists_songs_home)),
            // Home screen
            SettingEntry(page, tr(R.string.home), tr(R.string.customize_home), tr(R.string.sections_order_shortcuts_quick_picks_speed)),
            SettingEntry(page, tr(R.string.home), tr(R.string.add_new_sections_automatically), "shelves"),
            SettingEntry(page, tr(R.string.home), tr(R.string.quick_picks_from), tr(R.string.quick_picks_from_keywords)),
            // On Customize Home only (its Options), which isn't a settings page: the result opens that page itself (1.8.4).
            SettingEntry(page, tr(R.string.options), tr(R.string.speed_dial_your_plays_first), tr(R.string.speed_dial_your_plays_first_sub),
                needs = Needs(tr(R.string.turn_off) + " · " + tr(R.string.speed_dial_youtube_music_s_only)) { !it.speedDialYouTubeOnly },
                route = com.prism.music.ui.Routes.CATALOGUE, routeTitle = tr(R.string.customize_home)),
            SettingEntry(page, tr(R.string.home), tr(R.string.continue_line), tr(R.string.continue_album_resume_greeting_hide_context)),
            SettingEntry(page, tr(R.string.replay_card), tr(R.string.replay_card), tr(R.string.replay_teaser_look_style_design_quiet)),
            SettingEntry(page, tr(R.string.replay_card), tr(R.string.replay_card_graph), tr(R.string.replay_card_graph_keywords)),
            SettingEntry(page, tr(R.string.home), tr(R.string.buttons_at_the_top_of_home), tr(R.string.podcasts_button) + " " + tr(R.string.customize_button_at_the_top)),
            SettingEntry(page, tr(R.string.search), tr(R.string.search)),
            SettingEntry(page, tr(R.string.home), tr(R.string.customize_button_at_the_bottom)),
            // Library & Replay
            SettingEntry(page, tr(R.string.library), tr(R.string.playlists_as), tr(R.string.grid_list_view)),
            SettingEntry(page, tr(R.string.library), tr(R.string.albums_as), tr(R.string.grid_list_view)),
            SettingEntry(page, tr(R.string.library), tr(R.string.artists_as), tr(R.string.grid_list_view)),
            SettingEntry(page, tr(R.string.library), tr(R.string.covers_across), tr(R.string.columns_grid_two_three_four)),
            SettingEntry(page, tr(R.string.library), tr(R.string.details_under_each_one), tr(R.string.subtitles_counts_song_count_owner_year)),
            SettingEntry(page, tr(R.string.library), tr(R.string.open_library_on), tr(R.string.start_tab)),
            SettingEntry(page, tr(R.string.library), tr(R.string.full_width_playlist_pictures)),
            SettingEntry(page, tr(R.string.library), tr(R.string.genres_on_playlists), tr(R.string.genres_on_playlists_keywords)),
            SettingEntry(page, tr(R.string.library), tr(R.string.album_names_in_downloads), tr(R.string.album_names_in_downloads_keywords)),
            SettingEntry(page, tr(R.string.library), tr(R.string.library_tiles), tr(R.string.liked_downloads_local_files_replay_podcasts)),
            SettingEntry(page, tr(R.string.artist_pages), tr(R.string.artist_you_title), tr(R.string.artist_you_detail)),
            SettingEntry(page, tr(R.string.artist_pages), tr(R.string.artist_essentials_title), tr(R.string.artist_essentials_detail)),
            SettingEntry(page, tr(R.string.artist_pages), tr(R.string.artist_bio_title), tr(R.string.artist_bio_keywords)),
        )
    }
}
