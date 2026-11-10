package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.data.local.LocalLossless
import com.prism.music.data.prefs.AudioQuality
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.Crossfade
import com.prism.music.ui.screens.CrossfadeCurveCards
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.MoreOptions
import com.prism.music.ui.screens.NormalizeLevel
import com.prism.music.ui.screens.QueueEnd
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.SoundChoices
import com.prism.music.ui.screens.SoundSettings
import com.prism.music.ui.screens.SpeedAndFades
import com.prism.music.ui.screens.Toggle
import com.prism.music.ui.screens.TrimSilence
import com.prism.music.ui.screens.VideoPolicy

// ---------------------------------------------------------------------------- Playback & sound

@Composable
internal fun SoundPage() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val found by c.localLossless.found.collectAsState()
    val synced by c.losslessSync.songs.collectAsState()
    val syncing by c.losslessSync.working.collectAsState()
    val askFiles = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Lossless still works without file access; it just can't use files on the phone.
        prefs.setLossless(true)
        if (granted) prefs.setLosslessLocal(true) // lossless sync scans when this turns on
    }
    LaunchedEffect(s.losslessLocal) { if (s.losslessLocal && found < 0) c.losslessSync.sync() }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // 1.8.0 (#210): one choice per decision. Each choice is a face over the settings Prism already keeps
        // (autoplay + its source, radioSongsOnly + songsEverywhere, mix + mixAuto), so nothing is migrated and Looks,
        // backups and old builds read them as before ([SoundChoices]).
        Group(tr(R.string.what_plays_next)) {
            Label(tr(R.string.when_the_queue_ends), info = tr(R.string.when_the_queue_ends_info))
            Chips(QueueEnd.entries, SoundChoices.queueEnd(s), { it.label }) { SoundChoices.setQueueEnd(prefs, it) }
            // #159: as asked for, a choice; off, what's left out is what's in the queue and what was heard in the last hours.
            Toggle(
                tr(R.string.no_repeats_title), tr(R.string.no_repeats_detail), s.behavior.autoplayNoRepeats,
                info = tr(R.string.no_repeats_info),
            ) { prefs.setAutoplayNoRepeats(it) }
            // #299: long mixes and compilations stay out of Autoplay.
            if (s.autoplay) SliderSetting(
                tr(R.string.longest_song_for_autoplay), s.behavior.autoplayMaxMinutes.toFloat(), 0f..com.prism.music.data.AutoplayLength.MAX_MINUTES.toFloat(),
                { if (it < 0.5f) tr(R.string.autoplay_no_length_limit) else tr(R.string.min, it.roundToInt()) },
                steps = com.prism.music.data.AutoplayLength.MAX_MINUTES / com.prism.music.data.AutoplayLength.STEP_MINUTES - 1,
                info = tr(R.string.longest_song_for_autoplay_info),
            ) { prefs.setAutoplayMaxMinutes(it.roundToInt()) }
            Label(tr(R.string.music_videos_label), info = tr(R.string.music_videos_label_info))
            Chips(VideoPolicy.entries, SoundChoices.videoPolicy(s), { it.label }) { SoundChoices.setVideoPolicy(prefs, it) }
            Toggle(tr(R.string.keep_playing_videos), tr(R.string.after_a_video_the_next_song), s.videoContinues) { prefs.setVideoContinues(it) }
            // #216 (1.8.1): explicit songs as before, never played, or not shown either.
            Label(tr(R.string.explicit_songs), info = tr(R.string.explicit_songs_info))
            Chips(com.prism.music.data.prefs.ExplicitContent.entries, s.behavior.explicitContent, { it.label }) { prefs.setExplicitContent(it) }
        }
        Group(tr(R.string.crossfade)) {
            Label(tr(R.string.crossfade), info = tr(R.string.crossfade_choice_info))
            Chips(Crossfade.entries, SoundChoices.crossfade(s), { it.label }) { SoundChoices.setCrossfade(prefs, it) }
            // #351: up to 30 s (a song's blend is still at most a third of it: MixEngine.makePlan).
            if (s.mix && !s.mixAuto) SliderSetting(tr(R.string.length), s.mixSeconds.toFloat(), 1f..com.prism.music.data.prefs.MAX_CROSSFADE_SECONDS.toFloat(), { "${it.roundToInt()} s" }, steps = com.prism.music.data.prefs.MAX_CROSSFADE_SECONDS - 2) { prefs.setMixSeconds(it.roundToInt()) }
            if (s.mix) {
                Toggle(
                    tr(R.string.match_tempo_and_beats_dj_style), tr(R.string.beats_line_up_while_songs_overlap), s.mixBeatMatch,
                    info = tr(R.string.the_next_song_plays_at_the),
                ) { prefs.setMixBeatMatch(it) }
                // #247: an album played as an album never blended; this keeps its songs whole anywhere they meet.
                Toggle(
                    tr(R.string.keep_an_album_s_songs_whole), tr(R.string.no_crossfade_between_songs_of_one_album), s.mixKeepAlbums,
                    info = tr(R.string.an_album_playing_through_never_blends),
                ) { prefs.setMixKeepAlbums(it) }
            }
            // The crossfade mark and blended covers are on the player now: Player → What's on the player.
        }
        Group(tr(R.string.speed_pitch_and_fades)) { SpeedAndFades() }
        Group(tr(R.string.quality)) {
            Toggle(
                tr(R.string.lossless_playback),
                when {
                    !s.lossless -> tr(R.string.the_best_audio_and_your_flac)
                    !s.losslessLocal -> tr(R.string.allow_file_access_to_use_your)
                    found < 0 -> tr(R.string.looking_for_lossless_files)
                    found == 0 -> tr(R.string.no_lossless_files_found_yet)
                    syncing -> tr(R.string.lossless_files_found_adding_them, found)
                    else -> tr(R.string.lossless_files_in_downloads, found, synced.size)
                },
                s.lossless,
                info = tr(R.string.always_plays_the_best_audio_there),
            ) { on ->
                if (!on) { prefs.setLossless(false); prefs.setLosslessLocal(false) }
                else if (c.localLossless.hasPermission()) {
                    prefs.setLossless(true); prefs.setLosslessLocal(true)
                } else askFiles.launch(LocalLossless.permission)
            }
            if (s.lossless) {
                if (s.losslessLocal) Item(
                    tr(R.string.scan_for_lossless_files_again), tr(R.string.also_checked_every_few_hours),
                    onClick = { scope.launch { c.losslessSync.sync() } },
                )
                else Item(tr(R.string.allow_access_to_lossless_files), null, onClick = { askFiles.launch(LocalLossless.permission) })
                // Data saver plays songs at this quality on mobile data, lossless or not (#40).
                if (s.dataSaver) {
                    // 1.8.0: the same row name as without lossless (it is the same setting, cellularQuality).
                    Label(tr(R.string.streaming_quality_on_mobile_data))
                    Chips(AudioQuality.entries.filter { it != AudioQuality.HIGH }, s.cellularQuality.takeIf { it != AudioQuality.HIGH } ?: AudioQuality.NORMAL, { it.label }) { prefs.setCellularQuality(it) }
                }
            } else {
                Label(tr(R.string.streaming_quality_on_wi_fi))
                Chips(AudioQuality.entries, s.audioQuality, { it.label }) { prefs.setAudioQuality(it) }
                Label(tr(R.string.streaming_quality_on_mobile_data))
                Chips(AudioQuality.entries, s.cellularQuality, { it.label }) { prefs.setCellularQuality(it) }
            }
            // Reddit 1.7: "am I getting the quality Premium offers?"
            if (s.isLoggedIn) {
                // #373: said plainly when YouTube isn't sending it, rather than songs just playing at the usual quality.
                val premium by c.streams.premiumState.collectAsState()
                val seen by c.innerTube.seen.collectAsState()
                Toggle(
                    tr(R.string.premium_quality),
                    when {
                        !s.premiumStream -> tr(R.string.t_256_kbps_with_youtube_music_premium)
                        seen?.signedIn == false -> tr(R.string.youtube_no_longer_takes_this_sign_in)
                        premium == com.prism.music.data.stream.StreamResolver.PremiumState.MISSING && c.streams.premiumStateIsCurrent() -> tr(R.string.youtube_isnt_sending_256_kbps)
                        else -> tr(R.string.t_256_kbps_with_youtube_music_premium)
                    },
                    s.premiumStream,
                    info = tr(R.string.prism_normally_fetches_songs_without_your),
                ) { prefs.setPremiumStream(it) }
            }
            Label(tr(R.string.music_video_quality))
            Chips(listOf(480, 720, 1080, 1440), s.videoMaxHeight, { "${it}p" }) { prefs.setVideoMaxHeight(it) }
            Spacer(Modifier.height(6.dp))
        }
        Group(tr(R.string.sound)) {
            // Normalize's switch and its level are one choice: Off, or how loud (#210).
            NormalizeLevel()
            // #325: plainer names, and "Songs only".
            Label(tr(R.string.skip_silence), info = tr(R.string.skip_silence_choice_info))
            Chips(TrimSilence.entries, SoundChoices.trimSilence(s), { it.label }) { SoundChoices.setTrimSilence(prefs, it) }
            SoundSettings(onOpenEq = { nav.go(Routes.EQ) })
        }
        MoreOptions(listOf(tr(R.string.stopping_and_pausing), tr(R.string.how_they_cross))) {
            Group(tr(R.string.stopping_and_pausing)) {
                Toggle(tr(R.string.stop_when_swiped_away), tr(R.string.swiping_prism_out_of_recent_apps), s.stopOnSwipeAway) { prefs.setStopOnSwipeAway(it) }
                Toggle(
                    tr(R.string.pause_when_muted), tr(R.string.volume_all_the_way_down_pauses), s.behavior.pauseOnMute,
                    info = tr(R.string.only_the_phone_s_media_volume),
                ) { prefs.setPauseOnMute(it) }
            }
            Group(tr(R.string.how_they_cross)) { CrossfadeCurveCards() }
        }
    }
}
