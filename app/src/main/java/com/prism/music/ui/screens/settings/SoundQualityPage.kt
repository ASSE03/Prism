package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.playback.EqPresets
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds

private val page = SettingsPageId.SOUND_QUALITY

/**
 * Settings → Sound & quality (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([soundQualityIndex]), its "Reset this page" keys ([soundQualityLookKeys]), its line on
 * the root ([soundQualityValue]) and the old group names it answers to ([soundQualityOldGroups]).
 *
 * Interim body: the old Playback & sound page, whole and as it was ([SoundPage] in `OldSound.kt`), inside the kit's
 * scaffold, until the page's rows are rebuilt with the kit. Its Quality and Sound groups are this page's; the rest is
 * Playback's. That composable isn't cut up and this page would be empty without it, so both pages show all of it for
 * now; a link or search result for Quality or Sound opens this page at that group.
 */
@Composable
fun SoundQualityPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { SoundPage() }
}

/** The root's line for Sound & quality: the quality, then what's on of Normalize, Spatial and the equalizer. */
fun soundQualityValue(s: AppSettings, dark: Boolean): String = listOfNotNull(
    if (s.lossless) tr(R.string.lossless) else tr(R.string.audio_quality_x, s.audioQuality.label),
    if (s.normalize) tr(R.string.normalized) else null,
    if (s.spatial) tr(R.string.prism_spatial) else null,
    if (s.eq.enabled) "EQ ${EqPresets.label(s.eq.preset)}" else null,
).joinToString(" · ")

/** The look settings "Reset this page" puts back: none (sound isn't part of a look). */
val soundQualityLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val soundQualityOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val soundQualityIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.sound), tr(R.string.skip_silence), tr(R.string.trim_silence_songs_gaps) + " " + tr(R.string.trim_silence) + " " + tr(R.string.skip_silence_in_podcasts) + " " + tr(R.string.trim_silence_episodes_pauses_talk)),
            SettingEntry(page, tr(R.string.quality), tr(R.string.lossless_playback), tr(R.string.flac_hi_res)),
            SettingEntry(page, tr(R.string.quality), tr(R.string.allow_access_to_lossless_files), tr(R.string.flac_permission), needs = lossless),
            SettingEntry(page, tr(R.string.quality), tr(R.string.scan_for_lossless_files_again), "flac", needs = lossless),
            SettingEntry(page, tr(R.string.quality), tr(R.string.streaming_quality_on_wi_fi), tr(R.string.bitrate_audio_quality), needs = notLossless),
            SettingEntry(page, tr(R.string.quality), tr(R.string.streaming_quality_on_mobile_data), tr(R.string.bitrate_audio_quality_cellular) + " " + tr(R.string.bitrate_cellular)),
            SettingEntry(page, tr(R.string.quality), tr(R.string.music_video_quality), tr(R.string.resolution_1080p_720p)),
            SettingEntry(page, tr(R.string.quality), tr(R.string.premium_quality), tr(R.string.youtube_music_premium_256_kbps_aac),
                needs = Needs(tr(R.string.sign_in_to_youtube_music_first)) { it.isLoggedIn }),
            SettingEntry(page, tr(R.string.sound), tr(R.string.same_loudness_for_every_song), tr(R.string.loudness_lufs_level) + " " + tr(R.string.normalize_volume) + " " + tr(R.string.normalize_loudness_lufs_loud_quiet_broadcast)),
            SettingEntry(page, tr(R.string.sound), tr(R.string.albums_as_they_are), tr(R.string.album_mode_normalize_album_gain), needs = turnOn(tr(R.string.normalize_volume)) { it.normalize }),
            SettingEntry(page, tr(R.string.sound), tr(R.string.prism_spatial), tr(R.string.spatial_audio_3d_surround_atmos)),
            SettingEntry(page, tr(R.string.sound), tr(R.string.intensity), tr(R.string.spatial_audio_width), shown = tr(R.string.prism_spatial_intensity), needs = Needs(tr(R.string.turn_on_prism_spatial_first)) { it.spatial }),
            SettingEntry(page, tr(R.string.sound), tr(R.string.equalizer), tr(R.string.eq_bass_boost_treble_loudness_headphone)),
        )
    }
}
