package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SpotifyPage

private val page = SettingsPageId.ACCOUNTS_SHARING

/**
 * Settings → Accounts & sharing (1.8.4's map, plan §2.3). This file is the only place for the page: its composable,
 * its lines in Settings' search ([accountsSharingIndex]), its "Reset this page" keys ([accountsSharingLookKeys]), its
 * line on the root ([accountsSharingValue]) and the old group names it answers to ([accountsSharingOldGroups]).
 *
 * Interim body, inside the kit's scaffold until the page's rows are rebuilt with the kit: the old Last.fm, Spotify and
 * Discord pages ([LastFmGroup], [SpotifyPage], [DiscordGroup]) and Sharing and links ([SharingGroup], from Devices &
 * connections), as they were. The old keys "lastfm", "spotify" and "discord" open this page at their group. The
 * YouTube Music history row (the plan's "YouTube history") is still in the old Connections list on Car, and its
 * switch on Bring your history.
 */
@Composable
fun AccountsSharingPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        LastFmGroup()
        SpotifyPage()
        DiscordGroup()
        SharingGroup()
    }
}

/** The root's line for Accounts & sharing: what the page holds (the accounts' state isn't in the settings). */
fun accountsSharingValue(s: AppSettings, dark: Boolean): String = tr(R.string.root_accounts_value)

/** The look settings "Reset this page" puts back: none (accounts aren't part of a look). */
val accountsSharingLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val accountsSharingOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val accountsSharingIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.discord), tr(R.string.show_what_you_re_playing), tr(R.string.discord_rich_presence_status_listening)),
            SettingEntry(page, tr(R.string.discord), tr(R.string.sign_out_of_discord)),
            SettingEntry(page, tr(R.string.discord), tr(R.string.about_this), tr(R.string.discord_terms_risk)),
            SettingEntry(page, "Last.fm", tr(R.string.last_fm_scrobbling), tr(R.string.lastfm_last_fm_scrobble)),
            SettingEntry(page, "Last.fm", tr(R.string.connect_last_fm), tr(R.string.lastfm_last_fm_scrobble_scrobbling_account)),
            SettingEntry(page, "Last.fm", tr(R.string.scrobble), tr(R.string.lastfm_scrobble_send_plays)),
            SettingEntry(page, "Last.fm", tr(R.string.first_artist_only), tr(R.string.lastfm_featured_artists_main_artist)),
            SettingEntry(page, "Last.fm", tr(R.string.love_when_i_like_a_song), tr(R.string.lastfm_love_like_heart)),
            SettingEntry(page, "Last.fm", tr(R.string.disconnect), tr(R.string.lastfm_sign_out)),
            // 1.8.1: Spotify, through the listener's own developer app.
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spotify), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spot_set_up), tr(R.string.spot_open_dashboard), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spot_set_up), tr(R.string.spot_redirect_uri), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spot_set_up), tr(R.string.spot_client_id), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spot_connected_as, "").trim(), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spot_recent), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spot_top), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spot_playlists), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spot_keep_in_step), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spotify), tr(R.string.spot_likes), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.spot_connection), tr(R.string.disconnect), tr(R.string.spot_keywords)),
            SettingEntry(page, tr(R.string.sharing_and_links), tr(R.string.sharing_and_links), tr(R.string.share_link)),
            SettingEntry(page, tr(R.string.sharing_and_links), tr(R.string.links_you_share), tr(R.string.share_link_youtube_youtu_be_youtube)),
            SettingEntry(page, tr(R.string.sharing_and_links), tr(R.string.open_youtube_music_links_in_prism), tr(R.string.open_by_default_supported_links_music)),
        )
    }
}
