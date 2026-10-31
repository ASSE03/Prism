package com.prism.music.ui.player

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.prism.music.data.model.Song
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.components.ActionGroup
import com.prism.music.ui.components.ActionHeader
import com.prism.music.ui.components.ActionItem
import com.prism.music.ui.components.ActionSheet
import com.prism.music.ui.components.PlaylistPicker
import com.prism.music.ui.components.QuickActions
import com.prism.music.ui.components.SheetIconButton
import com.prism.music.ui.components.runAction
import com.prism.music.ui.components.shareTarget
import com.prism.music.ui.theme.LocalContainer

/**
 * The player's ⋯ menu, in the app's frosted look (the song's cover as frosted glass): like and
 * share up top, the everyday actions as tiles, then lyrics, player and "go to" groups.
 */
@Composable
fun PlayerOptionsSheet(
    song: Song,
    hasCanvas: Boolean,
    statsShown: Boolean,
    hasNext: Boolean,
    onLyricsSource: () -> Unit,
    onLyricsTiming: () -> Unit,
    onRedownloadLyrics: () -> Unit,
    onToggleStats: () -> Unit,
    onSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    /** Speed & pitch for songs (1.7.8, R12). */
    onSpeedPitch: () -> Unit = {},
    /** The lyrics have lines in a script other than Latin letters: romanizing them is offered. */
    romanizable: Boolean = false,
    onSing: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val liked by c.library.likedIds.collectAsState()
    val downloads by c.downloadState.collectAsState()
    val videos by c.videoState.collectAsState()
    val hidden by c.canvas.hidden.collectAsState()
    val saved by c.canvas.saved.collectAsState()
    val sleepAt by c.player.sleepAt.collectAsState()
    val sleepEnd by c.player.sleepEndOfSong.collectAsState()
    val together by c.together.state.collectAsState()
    val disliked by c.library.dislikedIds.collectAsState()
    var pickPlaylist by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    var outputs by remember { mutableStateOf(false) }
    var credits by remember { mutableStateOf(false) }
    if (credits) { com.prism.music.ui.components.SongCreditsSheet(song) { credits = false; onDismiss() }; return }
    var versions by remember { mutableStateOf(false) }
    val picks by c.versions.picks.collectAsState()
    if (versions) { com.prism.music.ui.components.SongVersionsSheet(song) { versions = false; onDismiss() }; return }
    if (pickPlaylist) { PlaylistPicker(song) { pickPlaylist = false; onDismiss() }; return }
    // Speakers and TVs, from the menu too (Reddit 1.7: "where's the cast button?").
    if (outputs) {
        val profile by c.account.profile.collectAsState()
        val name = (profile?.name ?: c.settings.current.accountName).trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() }?.let { tr(R.string.s_phone, it) } ?: tr(R.string.this_phone)
        OutputPickerSheet(name) { outputs = false; onDismiss() }
        return
    }
    if (sharing) { com.prism.music.ui.components.ShareSheet(song.shareTarget()) { sharing = false; onDismiss() }; return }

    val isLiked = song.id in liked
    val dl = downloads[song.id]
    val downloaded = dl?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    val sleepMin = if (sleepAt > 0) ((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1 else 0
    val sleeping = sleepAt > 0 || sleepEnd
    val inSession = together !is com.prism.music.playback.together.TogetherState.Idle

    val quick = listOfNotNull(
        ActionItem(PrismIcons.Radio, tr(R.string.radio_2)) { c.player.playRadio(song) },
        ActionItem(
            if (downloaded) PrismIcons.DownloadDone else PrismIcons.Download, tr(R.string.download),
            value = when { downloaded -> tr(R.string.downloaded); dl != null -> "${dl.percent.toInt().coerceAtLeast(0)}%"; else -> null },
            active = downloaded, keepOpen = true,
        ) { if (dl != null) c.downloads.remove(song.id) else c.downloads.download(song) },
        ActionItem(PrismIcons.PlaylistAdd, tr(R.string.playlist), keepOpen = true) { pickPlaylist = true },
        ActionItem(
            PrismIcons.Moon, tr(R.string.sleep),
            value = when { sleepEnd -> tr(R.string.song_end); sleepAt > 0 -> tr(R.string.min, sleepMin); else -> null },
            active = sleeping, onClick = onSleepTimer,
        ),
    )
    val speed by c.player.episodeSpeed.collectAsState()
    if (song.isEpisode) {
        val speeds = EPISODE_SPEEDS
        val episode = listOfNotNull(
            ActionItem(
                PrismIcons.Speed, tr(R.string.playback_speed_2), value = "${speed.toString().removeSuffix(".0")}×", active = speed != 1f, keepOpen = true,
            ) { c.player.setEpisodeSpeed(speeds[(speeds.indexOf(speed) + 1) % speeds.size]) },
            song.podcastId?.let { id -> ActionItem(PrismIcons.Podcasts, tr(R.string.go_to_podcast)) { onCollapse(); nav.go(Routes.podcast(id)) } },
            ActionItem(PrismIcons.Stats, tr(R.string.stats_for_nerds), active = statsShown, onClick = onToggleStats),
        )
        ActionSheet(song.thumbnail, onDismiss) { close ->
            ActionHeader(song.thumbnail, song.title, song.artistText) {
                SheetIconButton(PrismIcons.Share, tr(R.string.share)) { sharing = true }
            }
            QuickActions(quick.filter { it.label != "Radio" && it.label != "Playlist" }) { runAction(it, close) }
            ActionGroup(tr(R.string.episode), episode) { runAction(it, close) }
        }
        return
    }
    val optionSettings = c.settings.flow.collectAsState().value
    val translating = optionSettings.lyricsTranslate
    // Says which language, so turning it on for a song already in it explains itself.
    val translateInto = optionSettings.lyricsTranslateTo.ifBlank { defaultTranslateTarget() }.let { tag ->
        java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale.getDefault()).ifBlank { tag }
    }
    val lyrics = listOf(
        ActionItem(PrismIcons.Mic, tr(R.string.sing), value = if (c.sing.on.collectAsState().value) tr(R.string.on_2) else null, active = c.sing.on.collectAsState().value, onClick = onSing),
        ActionItem(PrismIcons.Lyrics, tr(R.string.lyrics_source), onClick = onLyricsSource),
        ActionItem(PrismIcons.Timer, tr(R.string.lyrics_timing), onClick = onLyricsTiming),
        ActionItem(
            PrismIcons.Translate, tr(R.string.translate_lyrics), value = if (translating) tr(R.string.into, translateInto) else tr(R.string.off), active = translating, keepOpen = true,
        ) { c.settings.setLyricsTranslate(!translating) },
        ActionItem(PrismIcons.Refresh, tr(R.string.download_lyrics_again)) { onRedownloadLyrics(); toast(tr(R.string.downloading_lyrics_again)) },
    ) + listOfNotNull(
        // This song in Latin letters, or not (whatever Settings → Lyrics says for songs in general).
        if (!romanizable) null else {
            val romanOn = romanizeMode(song.id) != com.prism.music.data.prefs.RomanizeMode.OFF
            ActionItem(PrismIcons.Romanize, tr(R.string.romanize_lyrics), value = if (romanOn) tr(R.string.on_2) else tr(R.string.off), active = romanOn, keepOpen = true) {
                c.romanizer.setOverride(song.id, !romanOn)
            }
        },
        // Into your own FLAC / MP3 file, for other players (#16).
        if (song.isLocal || c.downloads.downloads.value[song.id]?.lossless == true)
            ActionItem(PrismIcons.Download, tr(R.string.save_lyrics_into_the_file)) { c.lyricsToFiles.request(listOf(song)) } else null,
    )
    val cover = com.prism.music.data.canvas.CanvasRepository.albumKey(song)
    val immersive = c.settings.flow.collectAsState().value.immersivePlayer
    val musicSpeed by c.player.musicSpeed.collectAsState()
    val musicPitch by c.player.musicPitch.collectAsState()
    val player = listOfNotNull(
        ActionItem(PrismIcons.Cast, tr(R.string.play_on_a_speaker_or_tv), keepOpen = true) { outputs = true },
        if (song.isLive) null else ActionItem(
            PrismIcons.Speed, tr(R.string.speed_pitch_2),
            value = if (musicSpeed == 1f && musicPitch == 0) null else "${com.prism.music.ui.screens.speedText(musicSpeed)} · ${com.prism.music.ui.screens.pitchText(musicPitch)}",
            active = musicSpeed != 1f || musicPitch != 0, onClick = onSpeedPitch,
        ),
        // The cover fills the screen with the lyrics over it; a tap there brings this back.
        ActionItem(PrismIcons.Fullscreen, tr(R.string.immersive_mode), value = if (immersive) tr(R.string.on_2) else null, active = immersive) { c.settings.setImmersivePlayer(!immersive) },
        com.prism.music.ui.components.videoDownloadAction(song, videos[song.id]),
        ActionItem(PrismIcons.Groups, tr(R.string.listen_together), value = if (inSession) tr(R.string.on_2) else null, active = inSession) { onCollapse(); nav.go(Routes.TOGETHER) },
        ActionItem(PrismIcons.Stats, tr(R.string.stats_for_nerds), active = statsShown, onClick = onToggleStats),
        // Where Best part takes you in this song (also: hold the Best part button).
        if (song.isLive) null else ActionItem(
            PrismIcons.Flame, tr(R.string.your_best_part), value = c.bestParts.mine(song.id)?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) },
        ) { BestPartEditor.open = true },
        // Playback diagnostics are on (Settings → About): the listener marks the moment they heard a stutter.
        if (!c.settings.flow.collectAsState().value.audioDiagnostics) null else ActionItem(PrismIcons.Info, tr(R.string.mark_a_stutter)) {
            val at = c.player.position
            com.prism.music.playback.AudioDiagnostics.log(
                "mark", tr(R.string.the_listener_heard_a_stutter_in, song.title, song.artistText, song.id).format(at / 60_000, at / 1000 % 60),
            )
            toast(tr(R.string.marked_at_d_02d).format(at / 60_000, at / 1000 % 60))
        },
        if (hasCanvas) ActionItem(PrismIcons.AnimatedCover, tr(R.string.animated_cover), active = song.id !in hidden, keepOpen = true) { c.canvas.setHidden(song.id, song.id !in hidden) } else null,
        if (!hasCanvas) null else if (saved.has(song.id, cover)) ActionItem(PrismIcons.Download, tr(R.string.animated_cover_saved), active = true, keepOpen = true) { c.canvasStore.unsave(song) }
        else ActionItem(PrismIcons.Download, tr(R.string.save_animated_cover), keepOpen = true) {
            c.canvasStore.save(song)
            toast(tr(R.string.saving_animated_covers_for_this_album))
        },
    )
    val go = listOfNotNull(
        song.album?.id?.let { id -> ActionItem(PrismIcons.Album, tr(R.string.go_to_album)) { onCollapse(); nav.go(Routes.album(id)) } },
    ) + com.prism.music.ui.components.goToArtists(song) { route -> onCollapse(); nav.go(route) } + listOfNotNull(
        if (song.isEpisode || song.isLocal || song.isLive) null else ActionItem(PrismIcons.Info, tr(R.string.song_credits), keepOpen = true) { credits = true },
        // Another recording in its place (Reddit 1.7.9: an album song played as someone's cover).
        if (song.isEpisode || song.isLocal || song.isLive || together is com.prism.music.playback.together.TogetherState.Guest) null
        else ActionItem(PrismIcons.SwapVertical, tr(R.string.play_a_different_version), active = song.id in picks, keepOpen = true) { versions = true },
        if (song.id in disliked) ActionItem(PrismIcons.ThumbDownFilled, tr(R.string.not_for_me), active = true, keepOpen = true) { c.library.setDisliked(song, false) }
        else ActionItem(PrismIcons.ThumbDown, tr(R.string.not_for_me)) {
            c.library.setDisliked(song, true)
            toast(tr(R.string.got_it_prism_will_steer_clear))
            if (hasNext) c.player.next()
        },
    )

    ActionSheet(song.thumbnail, onDismiss) { close ->
        ActionHeader(song.thumbnail, song.title, song.artistText) {
            SheetIconButton(if (isLiked) PrismIcons.HeartFilled else PrismIcons.Heart, if (isLiked) tr(R.string.remove_from_liked) else tr(R.string.widget_like), active = isLiked) {
                c.library.toggleLike(song)
            }
            if (!song.isLocal) SheetIconButton(PrismIcons.Share, tr(R.string.share)) { sharing = true }
        }
        // A file on the phone: no radio, download or music video (a Prism playlist is fine).
        // (Matched by their labels as they read in the listener's language.)
        QuickActions(if (song.isLocal) quick.filter { it.label == tr(R.string.sleep) || it.label == tr(R.string.playlist) } else quick) { runAction(it, close) }
        ActionGroup(tr(R.string.lyrics), lyrics) { runAction(it, close) }
        // The player's look, changed right here over the player ("Customize this", 1.7.3), with the whole Player page a
        // tap further; Settings → Player where there's no sheet to open.
        val customize = com.prism.music.ui.components.LocalCustomize.current
        val settingsRow = ActionItem(PrismIcons.Settings, tr(R.string.player_settings)) {
            if (customize != null) customize(com.prism.music.ui.components.CustomizeTarget.PLAYER, null)
            else { onCollapse(); nav.go(Routes.settings(com.prism.music.ui.screens.SettingsSection.PLAYER.key)) }
        }
        val forFiles = listOf(R.string.play_on_a_speaker_or_tv, R.string.stats_for_nerds, R.string.immersive_mode, R.string.mark_a_stutter, R.string.your_best_part).map { tr(it) }
        ActionGroup(tr(R.string.player), (if (song.isLocal) player.filter { it.label in forFiles } else player) + settingsRow) { runAction(it, close) }
        ActionGroup(tr(R.string.more), go) { runAction(it, close) }
    }
}
