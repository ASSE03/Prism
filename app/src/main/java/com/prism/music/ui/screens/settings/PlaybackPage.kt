package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds

private val page = SettingsPageId.PLAYBACK

/**
 * Settings → Playback (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its lines
 * in Settings' search ([playbackIndex]), its "Reset this page" keys ([playbackLookKeys]), its line on the root
 * ([playbackValue]) and the old group names it answers to ([playbackOldGroups]).
 *
 * Interim body: the old Playback & sound page, whole and as it was ([SoundPage] in `OldSound.kt`), inside the kit's
 * scaffold, until the page's rows are rebuilt with the kit. That one composable holds this page's groups (What plays
 * next, Crossfade, Speed, pitch and fades, Stopping and pausing, How they cross) and Sound & quality's (Quality,
 * Sound); it isn't cut up, so both pages show all of it for now, and search opens each setting on the page that keeps it.
 */
@Composable
fun PlaybackPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { SoundPage() }
}

/** The root's line for Playback: the crossfade, and "No repeats" when it's on ("Crossfade 6 s · No repeats this session"). */
fun playbackValue(s: AppSettings, dark: Boolean): String = listOfNotNull(
    when {
        !s.mix -> tr(R.string.root_crossfade_off)
        s.mixAuto -> tr(R.string.crossfade)
        else -> tr(R.string.root_crossfade_seconds, s.mixSeconds)
    },
    if (s.behavior.autoplayNoRepeats) tr(R.string.no_repeats_title) else null,
).joinToString(" · ")

/** The look settings "Reset this page" puts back: none (what plays and how isn't part of a look). */
val playbackLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val playbackOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val playbackIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // Playback & sound
            // 1.8.0 (#210): merged choices; the old titles stay as search words.
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.what_plays_next)),
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.when_the_queue_ends), tr(R.string.radio_continue_similar) + " " + tr(R.string.autoplay) + " " + tr(R.string.autoplay_from) + " " + tr(R.string.radio_your_music_prism_radio_youtube)),
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.no_repeats_title), tr(R.string.no_repeats_detail)),
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.longest_song_for_autoplay), tr(R.string.longest_song_for_autoplay_keywords), needs = turnOn(tr(R.string.autoplay)) { it.autoplay }),
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.music_videos_label), tr(R.string.music_videos_audio) + " " + tr(R.string.songs_not_videos) + " " + tr(R.string.songs_not_videos_everywhere)),
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.keep_playing_videos), tr(R.string.music_video_2)),
            SettingEntry(page, tr(R.string.what_plays_next), tr(R.string.explicit_songs), tr(R.string.explicit_songs_keywords)),
            SettingEntry(page, tr(R.string.stopping_and_pausing), tr(R.string.stopping_and_pausing)),
            SettingEntry(page, tr(R.string.stopping_and_pausing), tr(R.string.stop_when_swiped_away), tr(R.string.recents_background)),
            SettingEntry(page, tr(R.string.stopping_and_pausing), tr(R.string.pause_when_muted), tr(R.string.volume_zero_mute_pause_resume)),
            SettingEntry(page, tr(R.string.crossfade), tr(R.string.crossfade), tr(R.string.mix_blend_transition_fade) + " " + tr(R.string.crossfade_songs) + " " + tr(R.string.automatic_length)),
            SettingEntry(page, tr(R.string.crossfade), tr(R.string.length), needs = manualLength),
            SettingEntry(page, tr(R.string.crossfade), tr(R.string.match_tempo_and_beats_dj_style), tr(R.string.bpm_beatmatch), needs = crossfade),
            SettingEntry(page, tr(R.string.crossfade), tr(R.string.keep_an_album_s_songs_whole), tr(R.string.keep_albums_keywords), needs = crossfade),
            SettingEntry(page, tr(R.string.how_they_cross), tr(R.string.how_they_cross), tr(R.string.curve_equal_power_linear_s_curve)),
            SettingEntry(page, tr(R.string.speed_pitch_and_fades), tr(R.string.speed_when_prism_starts), tr(R.string.playback_speed_default_tempo_faster_slower)),
            SettingEntry(page, tr(R.string.speed_pitch_and_fades), tr(R.string.pitch_when_prism_starts), tr(R.string.key_semitones_transpose_higher_lower_slowed)),
            SettingEntry(page, tr(R.string.speed_pitch_and_fades), tr(R.string.sleep_timer_fades_out), tr(R.string.sleep_fade_out_bedtime)),
            SettingEntry(page, tr(R.string.speed_pitch_and_fades), tr(R.string.fade_on_pause_and_play), tr(R.string.pause_fade_gentle_soft)),
        )
    }
}
