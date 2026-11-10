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

private val page = SettingsPageId.DOWNLOADS_DATA

/**
 * Settings → Downloads & data (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([downloadsDataIndex]), its "Reset this page" keys ([downloadsDataLookKeys]), its line on
 * the root ([downloadsDataValue]) and the old group names it answers to ([downloadsDataOldGroups]).
 *
 * Interim body: the old Downloads, storage & data page as it was ([DownloadSettings] in `OldDownloads.kt`), inside the
 * kit's scaffold, until the page's rows are rebuilt with the kit.
 */
@Composable
fun DownloadsDataPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { DownloadSettings() }
}

/** The root's line for Downloads & data: Smart Downloads' room, Wi-Fi only or any network, Data saver when on. */
fun downloadsDataValue(s: AppSettings, dark: Boolean): String = SettingsSection.DOWNLOADS.summary(s, dark)

/** The look settings "Reset this page" puts back: none (downloads and data aren't part of a look). */
val downloadsDataLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val downloadsDataOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val downloadsDataIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.animated_covers_on_mobile_data), tr(R.string.canvas_cellular_play), needs = canvas),
            SettingEntry(page, tr(R.string.animated_covers), tr(R.string.save_with_downloads), tr(R.string.canvas_offline), needs = canvas),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.save_animated_covers_on_mobile_data), tr(R.string.canvas_cellular), needs = canvasSave),
            SettingEntry(page, tr(R.string.animated_covers), tr(R.string.saved_animated_covers), tr(R.string.canvas_storage), needs = canvas),
            SettingEntry(page, tr(R.string.animated_covers), tr(R.string.recently_played_covers), tr(R.string.canvas_storage_cache), needs = canvas),
            SettingEntry(page, tr(R.string.load_ahead), tr(R.string.load_ahead), tr(R.string.preload_buffer)),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.load_ahead_on_mobile_data), tr(R.string.preload_cellular)),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.data_saver), tr(R.string.cellular_low_data)),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.no_videos_on_mobile_data), tr(R.string.no_videos_on_mobile_data_keywords), needs = turnOn(tr(R.string.data_saver)) { it.dataSaver }),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.measure_songs_on_mobile_data), "cellular", needs = measuring),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.get_songs_ready_for_sing_on), tr(R.string.karaoke_cellular_sing)),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.lyrics_on_mobile_data), tr(R.string.cellular_data_saver_metered_load_lyrics)),
            // Downloads & storage
            SettingEntry(page, tr(R.string.downloads), tr(R.string.manage_downloads), tr(R.string.offline_delete)),
            SettingEntry(page, tr(R.string.downloads), tr(R.string.download_on_wi_fi_only), tr(R.string.cellular_mobile_data)),
            SettingEntry(page, tr(R.string.downloads), tr(R.string.download_liked_songs_automatically), tr(R.string.offline_likes)),
            SettingEntry(page, tr(R.string.downloads), tr(R.string.keep_playlists_downloaded), tr(R.string.keep_playlists_keywords)),
            // #314 (1.8.1).
            SettingEntry(page, tr(R.string.your_own_song_files), tr(R.string.use_your_own_song_files), tr(R.string.own_files_keywords)),
            SettingEntry(page, tr(R.string.your_own_song_files), tr(R.string.scan_your_song_files_again), tr(R.string.own_files_keywords), needs = ownFiles),
            SettingEntry(page, tr(R.string.local_files), tr(R.string.folders_in_local_files), tr(R.string.local_folders_keywords)),
            SettingEntry(page, tr(R.string.smart_downloads), tr(R.string.smart_downloads), tr(R.string.offline_cache_storage_space)),
            SettingEntry(page, tr(R.string.smart_downloads), tr(R.string.space_to_use), tr(R.string.storage_cache_size_gb)),
            SettingEntry(page, tr(R.string.mobile_data), tr(R.string.smart_downloads_on_mobile_data), "cellular", needs = smart),
            SettingEntry(page, tr(R.string.smart_downloads), tr(R.string.clear_smart_downloads), tr(R.string.cache_delete_free_space)),
        )
    }
}
