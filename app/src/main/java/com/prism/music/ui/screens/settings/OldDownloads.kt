package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.trp
import com.prism.music.ui.components.PrismSlider
import com.prism.music.ui.theme.PrismIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.music.data.local.LocalLossless
import com.prism.music.download.SMART_DOWNLOAD_SIZES
import com.prism.music.download.SmartDownloadWorker
import com.prism.music.download.formatGb
import com.prism.music.download.smartSongEstimate
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.LocalFoldersSheet
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.PrismCheck
import com.prism.music.ui.screens.Toggle

/** Animated covers kept on the phone: the ones saved from the player, and the recently played ones. */
@Composable
private fun CanvasStorage() {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val saved by c.canvas.saved.collectAsState()
    var version by remember { mutableStateOf(0) }
    val sizes by androidx.compose.runtime.produceState(0L to 0L, version, saved) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.canvasStore.savedBytes to c.canvasStore.recentBytes }
    }
    fun mb(bytes: Long) = tr(R.string.t_0f_mb).format(bytes / 1_048_576f)
    Item(
        tr(R.string.saved_animated_covers),
        if ((saved.count == 0)) tr(R.string.none_yet) else tr(R.string.tap_to_remove_all, saved.count, mb(sizes.first)),
        info = tr(R.string.save_one_from_the_player_s),
        onClick = {
            if ((saved.count > 0)) scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.canvasStore.clearSaved() }
                version++
            }
        },
    )
    Item(
        tr(R.string.recently_played_covers),
        tr(R.string.tap_to_clear, mb(sizes.second)),
        info = tr(R.string.kept_up_to_400_mb_so),
        onClick = {
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.canvasStore.clearRecent() }
                version++
            }
        },
    )
}

// ---------------------------------------------------------------------------- Downloads & storage

/**
 * #314 (1.8.1): your own MP3/AAC/Ogg files in place of the stream, beside lossless sync's files in Downloads. Off by
 * default; it needs file access, not lossless playback.
 */
@Composable
private fun OwnSongFiles() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val scope = rememberCoroutineScope()
    val found by c.localLossless.ownFound.collectAsState()
    val synced by c.losslessSync.songs.collectAsState()
    val syncing by c.losslessSync.working.collectAsState()
    val inDownloads = remember(synced) { synced.values.count { it.second.lossy } }
    val askFiles = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) prefs.setOwnLossyFiles(true) // lossless sync scans when this turns on
    }
    Group(tr(R.string.your_own_song_files)) {
        Toggle(
            tr(R.string.use_your_own_song_files),
            when {
                !s.sound.ownLossyFiles -> tr(R.string.own_files_off)
                found < 0 -> tr(R.string.own_files_looking)
                found == 0 -> tr(R.string.own_files_none)
                syncing -> tr(R.string.own_files_adding, found)
                else -> tr(R.string.own_files_status, found, inDownloads)
            },
            s.sound.ownLossyFiles,
            info = tr(R.string.own_files_info),
        ) { on ->
            if (!on) prefs.setOwnLossyFiles(false)
            else if (c.localLossless.hasPermission()) prefs.setOwnLossyFiles(true)
            else askFiles.launch(LocalLossless.permission)
        }
        if (s.sound.ownLossyFiles) Item(
            tr(R.string.scan_your_song_files_again), tr(R.string.also_checked_every_few_hours),
            onClick = { scope.launch { c.losslessSync.sync() } },
        )
    }
}

/** Which playlists stay downloaded as they grow (#298; this list's shape is stuntsnakee's, from PR #304). */
@Composable
private fun KeptPlaylistsSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val library by c.library.playlists.collectAsState()
    val prismLists by c.prismLibrary.lists.collectAsState()
    val savedLists by c.prismLibrary.playlists.collectAsState()
    val signedIn = s.isLoggedIn
    val mine = remember(library, prismLists, savedLists, signedIn) {
        (prismLists.map { it.item() } + savedLists.map { it.item() } + if (signedIn) library.filterIsInstance<com.prism.music.data.model.PlaylistItem>() else emptyList())
            .filter { com.prism.music.download.PlaylistDownloads.canKeep(it.id) && !it.isMix }
            .distinctBy { com.prism.music.download.PlaylistDownloads.keepKey(it.id) }
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(mine, query) { if (query.isBlank()) mine else mine.filter { it.title.contains(query.trim(), ignoreCase = true) } }
    com.prism.music.ui.components.PrismSheet(onDismiss = onDismiss) {
        Text(tr(R.string.keep_playlists_downloaded), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 20.dp))
        if (mine.size > 8) com.prism.music.ui.components.SearchField(query, tr(R.string.find_a_playlist), { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        if (mine.isEmpty()) Text(tr(R.string.no_playlists_yet), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)) {
            items(shown.size, key = { shown[it].id }) { i ->
                val p = shown[i]
                val on = com.prism.music.download.PlaylistDownloads.keepKey(p.id) in s.behavior.keptPlaylists
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.prism.music.ui.components.Artwork(c.covers.art(p), Modifier.size(46.dp), LocalUi.current.smallArt, size = 120, placeholderIcon = PrismIcons.QueueMusic)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.subtitle.isNotBlank()) Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PrismCheck(on, label = p.title) { c.keptPlaylists.set(p.id, it) }
                }
            }
        }
    }
}

@Composable
internal fun DownloadSettings() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    val context = LocalContext.current
    var cacheBytes by remember { mutableLongStateOf(c.songCacheBytes()) }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(tr(R.string.downloads)) {
            Item(tr(R.string.manage_downloads), tr(R.string.see_and_delete_what_s_on), onClick = { nav.go(Routes.DOWNLOADS) }) { Chevron() }
            Toggle(tr(R.string.download_on_wi_fi_only), null, s.downloadWifiOnly) { prefs.setDownloadWifiOnly(it) }
            Toggle(tr(R.string.download_liked_songs_automatically), tr(R.string.including_likes_from_other_devices), s.downloadLikes) {
                prefs.setDownloadLikes(it)
                if (it) c.downloads.downloadAll(c.library.liked.value)
            }
            // #298 (1.8.2).
            var choosingKept by remember { mutableStateOf(false) }
            val keptCount = s.behavior.keptPlaylists.size
            Item(
                tr(R.string.keep_playlists_downloaded),
                if (keptCount == 0) tr(R.string.none_chosen) else trp(R.plurals.n_playlists_kept, keptCount),
                info = tr(R.string.new_songs_in_these_playlists_download),
                onClick = { choosingKept = true },
            ) { Chevron() }
            if (choosingKept) KeptPlaylistsSheet { choosingKept = false }
        }
        OwnSongFiles()
        // #207, #249 (1.8.2).
        Group(tr(R.string.local_files)) {
            var choosing by remember { mutableStateOf(false) }
            val skipped = s.behavior.localSkipFolders.size
            Item(
                tr(R.string.folders_in_local_files),
                if (skipped == 0) tr(R.string.every_folder_with_music) else trp(R.plurals.n_folders_left_out, skipped),
                info = tr(R.string.untick_a_folder_to_leave_it_out),
                onClick = { choosing = true },
            ) { Chevron() }
            if (choosing) LocalFoldersSheet { choosing = false }
        }
        Group(tr(R.string.smart_downloads)) {
            Toggle(
                tr(R.string.smart_downloads), tr(R.string.songs_you_ll_likely_play_ready), s.smartDownloads,
                info = tr(R.string.one_space_for_music_on_your),
            ) {
                prefs.setSmartDownloads(it)
                SmartDownloadWorker.schedule(context, it)
                if (it) SmartDownloadWorker.runNow(context) else c.trimSongCache()
            }
            val sizes = SMART_DOWNLOAD_SIZES
            var i by remember { mutableFloatStateOf(sizes.indexOfFirst { it >= s.offlineGb }.let { if (it < 0) sizes.lastIndex else it }.toFloat()) }
            val gb = sizes[i.roundToInt().coerceIn(0, sizes.lastIndex)]
            val freeGb = remember { context.filesDir.usableSpace / 1_073_741_824f }
            val picks by c.downloads.smartIds.collectAsState()
            val downloads by c.downloadState.collectAsState()
            val pickBytes = picks.sumOf { downloads[it]?.bytes ?: 0L }
            Label(tr(R.string.space_to_use_2, formatGb(gb)))
            val used = pickBytes + cacheBytes
            val usedText = if (used < 1_073_741_824L) "${used / 1_048_576} MB" else tr(R.string.t_1f_gb).format(used / 1_073_741_824.0)
            Note(
                smartSongEstimate(gb, s.audioQuality).let { n -> trp(R.plurals.about_n_songs_used, n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), "%,d".format(n), usedText) }.format(freeGb) +
                    if (gb > freeGb) tr(R.string.it_stops_when_storage_runs_low) else ""
            )
            PrismSlider(
                i, { i = it }, Modifier.padding(horizontal = 16.dp), valueRange = 0f..sizes.lastIndex.toFloat(), steps = sizes.size - 2,
                onValueChangeFinished = {
                    prefs.setOfflineGb(gb)
                    c.trimSongCache()
                    if (s.smartDownloads) SmartDownloadWorker.runNow(context)
                },
            )
            Item(tr(R.string.clear_smart_downloads), tr(R.string.your_own_downloads_stay), info = tr(R.string.removes_the_songs_it_downloaded_ahead), onClick = {
                c.downloads.clearSmartDownloads()
                cacheBytes = c.songCacheBytes()
            })
        }
        if (s.canvasEnabled) Group(tr(R.string.animated_covers)) {
            Toggle(
                tr(R.string.save_with_downloads), tr(R.string.downloaded_songs_keep_theirs), s.canvasWithDownloads,
                info = tr(R.string.a_downloaded_song_s_animated_cover),
            ) {
                prefs.setCanvasWithDownloads(it)
                if (it) c.downloads.saveCanvases()
            }
            CanvasStorage()
        }
        // How much of the next songs is loaded ahead (1.8.0: from Playback & sound; it's about data and buffering).
        Group(tr(R.string.load_ahead)) {
            Label(
                tr(R.string.load_ahead),
                info = when (s.preload) {
                    com.prism.music.data.prefs.Preload.START -> tr(R.string.now_the_next_song_s_start)
                    else -> tr(R.string.now_whole_songs_so_they_play)
                },
            )
            Chips(com.prism.music.data.prefs.Preload.entries, s.preload, { it.label }) { prefs.setPreload(it) }
        }
        // Every "on mobile data" choice in one place (1.7.6; they were spread over six pages). Each shows while the
        // thing it's about is on. Streaming quality on mobile data stays with the other qualities in Playback & sound.
        Group(tr(R.string.mobile_data), info = tr(R.string.what_prism_may_use_mobile_data)) {
            Toggle(
                tr(R.string.data_saver), tr(R.string.uses_much_less_data_off_wi), s.dataSaver,
                info = tr(R.string.on_mobile_data_pictures_at_the),
            ) { prefs.setDataSaver(it) }
            // #281: a video is what uses the most data of all.
            if (s.dataSaver) Toggle(
                tr(R.string.no_videos_on_mobile_data), null, s.behavior.noVideoOnData,
                info = tr(R.string.no_videos_on_mobile_data_info),
            ) { prefs.setNoVideoOnData(it) }
            Toggle(
                tr(R.string.load_ahead_on_mobile_data),
                if (s.preload == com.prism.music.data.prefs.Preload.START) tr(R.string.the_next_song_s_start_about) else tr(R.string.whole_songs_about_3_8_mb),
                s.preloadCellular,
                info = tr(R.string.off_songs_are_only_loaded_ahead_here),
            ) { prefs.setPreloadCellular(it) }
            if (s.smartDownloads) Toggle(tr(R.string.smart_downloads_on_mobile_data), tr(R.string.about_3_8_mb_a_song), s.smartDownloadsCellular) {
                prefs.setSmartDownloadsCellular(it)
                // The worker's network rule is fixed when it's scheduled, so it's scheduled again with the new one.
                SmartDownloadWorker.schedule(context, true, cellular = it)
                SmartDownloadWorker.runNow(context, force = false, cellular = it)
                c.downloads.refreshSmartIds()
            }
            Toggle(
                tr(R.string.lyrics_on_mobile_data), if (s.lyricsOnMobileData) tr(R.string.looked_up_as_each_song_starts) else tr(R.string.only_once_you_open_the_lyrics), s.lyricsOnMobileData,
                info = tr(R.string.off_prism_doesn_t_look_up),
            ) { prefs.setLyricsOnMobileData(it) }
            if (s.canvasEnabled) Toggle(tr(R.string.animated_covers_on_mobile_data), tr(R.string.saved_covers_play_either_way), s.canvasOnCellular) { prefs.setCanvasOnCellular(it) }
            if (s.canvasEnabled && s.canvasWithDownloads) Toggle(tr(R.string.save_animated_covers_on_mobile_data), tr(R.string.otherwise_they_wait_for_wi_fi), s.canvasWithDownloadsCellular) {
                prefs.setCanvasWithDownloadsCellular(it)
                if (it) c.downloads.saveCanvases()
            }
            if (s.normalize || s.mix) Toggle(
                tr(R.string.measure_songs_on_mobile_data), tr(R.string.for_normalize_volume_and_crossfade_1), s.mixTempoOnCellular,
                info = tr(R.string.normalize_volume_and_crossfade_measure_each),
            ) { prefs.setMixTempoOnCellular(it) }
            Toggle(
                tr(R.string.get_songs_ready_for_sing_on), tr(R.string.a_few_mb_a_song), s.singOnCellular,
                info = tr(R.string.sing_needs_each_song_whole_on),
            ) { prefs.setSingOnCellular(it) }
        }
    }
}
