package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.ResetPage
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SettingsSection

private val page = SettingsPageId.PLAYER

/**
 * Settings → Player (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its lines in
 * Settings' search ([playerIndex]), its "Reset this page" keys ([playerLookKeys]), its line on the root
 * ([playerValue]) and the old group names it answers to ([playerOldGroups]).
 *
 * Interim body: the old Player page as it was ([PlayerSettings] in `OldPlayer.kt`, with the groups of
 * `PlayerYourWay.kt`), inside the kit's scaffold, until the page's rows are rebuilt with the kit.
 */
@Composable
fun PlayerPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        PlayerSettings()
        ResetPage(page.title, playerLookKeys)
    }
}

/** The root's line for Player: its layout, background and animated covers. */
fun playerValue(s: AppSettings, dark: Boolean): String = SettingsSection.PLAYER.summary(s, dark)

/** The look settings "Reset this page" puts back. */
val playerLookKeys: List<String> = listOf(
    "player_background", "player_backdrop_show", "transport_style", "scrubber_style", "cover_size", "player_art_corners",
    "full_bleed_artwork", "video_full_bleed", "art_shrink_on_pause", "show_playing_from", "player_shuffle_repeat", "hide_volume_bar",
    "show_output_device", "immersive_art", "immersive_lyrics", "immersive_controls", "player_handle", "player_nav_band", "play_ring",
    // 1.8.0: what the page now holds (What's on the player, the progress bar, the crossfade's marks from Sound).
    "player_hidden_parts", "player_buttons", "player_buttons_pill", "player_times", "scrubber_color", "scrubber_thickness",
    "scrubber_thumb", "cover_flight", "crossfade_mark", "blend_covers", "video_controls_hide", "mesh_speed", "mesh_intensity",
    "player_blur", "crossfade_shape", "player_gradient_depth",
    "cover_flight_back", "video_buttons_hide", "bleed_roomy", "queue_lengths", "queue_played_moves", "queue_tidy",
)

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val playerOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val playerIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // Player
            SettingEntry(page, tr(R.string.look), tr(R.string.background), tr(R.string.player_blur_cover_color)),
            SettingEntry(page, tr(R.string.look), tr(R.string.show_background)),
            SettingEntry(page, tr(R.string.look), tr(R.string.cover_gradient), tr(R.string.cover_gradient_info)),
            SettingEntry(page, tr(R.string.look), tr(R.string.gradient_depth), tr(R.string.gradient_depth_info), needs = turnOn(tr(R.string.cover_gradient)) { it.playerBackground == com.prism.music.data.prefs.PlayerBackground.GRADIENT }),
            SettingEntry(page, tr(R.string.look), tr(R.string.controls), tr(R.string.buttons_transport)),
            SettingEntry(page, tr(R.string.look), tr(R.string.ring_on_the_play_button), tr(R.string.the_split_light_around_play_and_pause)),
            SettingEntry(page, tr(R.string.progress_bar), tr(R.string.progress_bar), tr(R.string.scrubber_seek_bar)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.cover_size), tr(R.string.large_full_width)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.corners), tr(R.string.cover_rounded)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.edge_to_edge_cover), tr(R.string.edge_to_edge) + " " + tr(R.string.full_bleed_artwork)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.edge_to_edge_video), tr(R.string.edge_to_edge) + " " + tr(R.string.full_bleed_video) + " " + tr(R.string.music_video_edge_to_edge_fill)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.how_the_cover_fits), tr(R.string.how_the_cover_fits_keywords), needs = turnOn(tr(R.string.edge_to_edge_cover)) { it.fullBleedArtwork }),
            SettingEntry(page, tr(R.string.cover), tr(R.string.shrink_when_paused)),
            SettingEntry(page, tr(R.string.immersive_mode), tr(R.string.immersive_mode), tr(R.string.lyric_video_full_screen)),
            SettingEntry(page, tr(R.string.immersive_mode), tr(R.string.artwork), needs = immersive),
            SettingEntry(page, tr(R.string.immersive_mode), tr(R.string.lyrics), needs = immersive),
            SettingEntry(page, tr(R.string.immersive_mode), tr(R.string.keep_controls_on_screen), tr(R.string.play_pause_skip_next_previous_buttons), needs = immersive),
            SettingEntry(page, tr(R.string.animated_covers), tr(R.string.animated_covers), tr(R.string.canvas_motion_video_cover)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.from_the_album), tr(R.string.album_caption_playing_from)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.stream_quality), tr(R.string.codec_bitrate_nerd_stats)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.best_part), tr(R.string.skip_chorus_hook_highlight_most_replayed)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.times), tr(R.string.duration_time_left_remaining)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.shuffle_repeat)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.output_device), tr(R.string.bluetooth_speaker_headphones)),
            // The player, your way (1.7.4)
            SettingEntry(page, tr(R.string.layout), tr(R.string.layout), tr(R.string.player_layout_standard_cover_lyrics_minimal)),
            SettingEntry(page, tr(R.string.layout), tr(R.string.tonearm), tr(R.string.vinyl_record_turntable_arm_needle), needs = turnOn(tr(R.string.vinyl_layout)) { it.playerLayout == com.prism.music.data.prefs.PlayerLayout.VINYL }),
            // 1.8.0: Motion and Colors are one slider (old names still find it).
            SettingEntry(page, tr(R.string.look), tr(R.string.background_movement), tr(R.string.mesh_background_drift_animate_move_still) + " " + tr(R.string.mesh_background_saturation_vivid_soft_rich) + " " + tr(R.string.motion) + " " + tr(R.string.colors)),
            SettingEntry(page, tr(R.string.look), tr(R.string.softness), tr(R.string.blur_background_strength_soft_clear), needs = turnOn(tr(R.string.blurred_cover)) { it.playerBackground == com.prism.music.data.prefs.PlayerBackground.BLUR }),
            SettingEntry(page, tr(R.string.look), tr(R.string.buttons), tr(R.string.player_bottom_row_buttons_lyrics_video)),
            // 1.8.0: one editor for what's on the player (it took in Show on the player, Under the title and the buttons) and the ⋯ menu.
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.what_s_on_the_player), tr(R.string.what_s_on_the_player_keywords)),
            // One line for the row (1.8.4: it was listed twice), with both lines' search words.
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.buttons_in_the_row), tr(R.string.player_bottom_row_buttons_lyrics_video) + " " + tr(R.string.music_video_audio_only)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.edit_this_menu), tr(R.string.edit_this_menu_keywords)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.crossfade_mark), tr(R.string.crossfade_songs), needs = crossfade),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.covers_blend_too), tr(R.string.crossfade_songs), needs = crossfade),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.under_the_title), tr(R.string.order_arrange_rearrange_move_current_lyric)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.current_lyric), tr(R.string.lyrics_line)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.volume), "slider"),
            SettingEntry(page, tr(R.string.title_and_times), tr(R.string.title_and_times), tr(R.string.title_alignment_centered_centre_left)),
            SettingEntry(page, tr(R.string.title_and_times), tr(R.string.title_size), tr(R.string.big_small_large_font)),
            SettingEntry(page, tr(R.string.title_and_times), tr(R.string.title_weight), tr(R.string.bold_heavy_thick_light_regular_font)),
            SettingEntry(page, tr(R.string.title_and_times), tr(R.string.scrolling_titles), tr(R.string.marquee_scroll_long_title_ellipsis)),
            SettingEntry(page, tr(R.string.title_and_times), tr(R.string.each_artist_its_own_link), tr(R.string.each_artist_its_own_link_keywords)),
            SettingEntry(page, tr(R.string.title_and_times), tr(R.string.times_under_the_progress_bar), tr(R.string.elapsed_remaining_time_left_none)),
            SettingEntry(page, tr(R.string.progress_bar), tr(R.string.progress_bar_color), tr(R.string.scrubber_color_accent_white_cover_spectrum)),
            SettingEntry(page, tr(R.string.progress_bar), tr(R.string.progress_bar_thickness), tr(R.string.scrubber_thick_thin_height_bold)),
            SettingEntry(page, tr(R.string.progress_bar), tr(R.string.knob_at_the_playhead), tr(R.string.scrubber_thumb_knob_dot_line_ring)),
            SettingEntry(page, tr(R.string.progress_bar), tr(R.string.volume_bar_like_the_progress_bar), tr(R.string.volume_bar_keywords)),
            // 1.8.1: the bar shaped by the crossfade (drawn only while Crossfade is on).
            SettingEntry(page, tr(R.string.progress_bar), tr(R.string.crossfade_on_the_progress_bar), tr(R.string.crossfade_shape_keywords), needs = crossfade),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.handle_at_the_top), tr(R.string.player_handle_keywords)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.band_behind_the_navigation_buttons), tr(R.string.nav_band_keywords)),
            SettingEntry(page, tr(R.string.visualizer), tr(R.string.visualizer), tr(R.string.visualiser_bars_wave_ring_pulse_glow)),
            SettingEntry(page, tr(R.string.visualizer), tr(R.string.visualizer_color), tr(R.string.visualiser_colour_accent_white_cover)),
            SettingEntry(page, tr(R.string.look), tr(R.string.which_background), tr(R.string.player_background_own_design_photo_pattern)),
            SettingEntry(page, tr(R.string.animated_covers), tr(R.string.fill_the_player), tr(R.string.canvas_background_full_screen_behind), needs = canvas),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.lyrics_and_the_queue), tr(R.string.player_controls_hide_full_screen_lyrics)),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.hide_controls_over_videos), tr(R.string.music_video_controls_hide_tap_full)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.cover_flies_into_place), tr(R.string.animation_transition_open_player_cover_mini)),
            SettingEntry(page, tr(R.string.cover), tr(R.string.cover_flies_back_too), tr(R.string.animation_transition_open_player_cover_mini), needs = turnOn(tr(R.string.cover_flies_into_place)) { it.view.coverFlight }),
            SettingEntry(page, tr(R.string.cover), tr(R.string.more_room_for_the_picture), tr(R.string.more_room_for_the_picture_keywords), needs = turnOn(tr(R.string.edge_to_edge_cover)) { it.fullBleedArtwork || it.videoFullBleed }),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.buttons_on_videos_wait_for_a_tap), tr(R.string.buttons_on_videos_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.song_lengths_in_the_queue), tr(R.string.song_lengths_in_the_queue_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.move_songs_you_have_played), tr(R.string.move_songs_you_have_played_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.tidier_queue), tr(R.string.tidier_queue_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.reopen_on_the_last_page), tr(R.string.reopen_on_the_last_page_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.steadier_lyrics), tr(R.string.steadier_lyrics_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.queue_headings), tr(R.string.played_now_playing_up_next_headings)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.dj_in_the_queue), tr(R.string.dj_in_the_queue_keywords)),
            SettingEntry(page, tr(R.string.lyrics_and_the_queue), tr(R.string.scrolling_up_shows_the_controls), tr(R.string.scroll_swipe_down_lyrics_seek_bar),
                needs = Needs(tr(R.string.pick_hide_after_a_moment_first)) { it.view.panelPlayer == com.prism.music.data.prefs.PanelPlayer.AUTO_HIDE }),
            SettingEntry(page, tr(R.string.immersive_mode), tr(R.string.show_the_video), tr(R.string.immersive_music_video_behind_lyrics), needs = immersive),
            SettingEntry(page, tr(R.string.what_s_on_the_player), tr(R.string.the_volume_slider_changes), tr(R.string.volume_independent_app_volume_prism_s)),
            SettingEntry(page, tr(R.string.desk_mode), tr(R.string.desk_mode), tr(R.string.desk_mode_keywords)),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_title), tr(R.string.lock_screen_keywords)),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_allow), tr(R.string.lock_screen_keywords), needs = lockScreen),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_app_info), tr(R.string.lock_screen_keywords), needs = lockScreen),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_allow_step), tr(R.string.lock_screen_keywords), needs = lockScreen),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_layout_label), tr(R.string.lock_screen_keywords), needs = lockScreen),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_show), tr(R.string.lock_screen_keywords), needs = lockScreen),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_paused_label), tr(R.string.lock_screen_keywords), needs = lockScreen),
            SettingEntry(page, tr(R.string.lock_screen_title), tr(R.string.lock_screen_hide), tr(R.string.lock_screen_keywords), needs = lockScreen),
        )
    }
}
