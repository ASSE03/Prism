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

private val page = SettingsPageId.LYRICS_SING

/**
 * Settings → Lyrics & Sing (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([lyricsSingIndex]), its "Reset this page" keys ([lyricsSingLookKeys]), its line on the
 * root ([lyricsSingValue]) and the old group names it answers to ([lyricsSingOldGroups]).
 *
 * Interim body, inside the kit's scaffold until the page's rows are rebuilt with the kit: the old Lyrics look page
 * ([LyricsLookPage], the groups of `LyricsYourWay.kt`) with its reset, then the old Lyrics & Sing page
 * ([LyricsSettings]: sources, language, Sing, music files), both in `OldLyrics.kt`, as they were. Immersive's background
 * (Player's by the ledger) is still in the look's Background group here.
 */
@Composable
fun LyricsSingPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        LyricsLookPage()
        ResetPage(page.title, lyricsSingLookKeys)
        LyricsSettings()
    }
}

/** The root's line for Lyrics & Sing: the style and where the sung line sits, and "Translated" when lyrics are. */
fun lyricsSingValue(s: AppSettings, dark: Boolean): String =
    listOfNotNull(tr(R.string.style_2, s.lyricsStyle.label, s.lyricsPosition.label), if (s.lyricsTranslate) tr(R.string.translated) else null).joinToString(" · ")

/** The look settings "Reset this page" puts back (the lyrics' look; sources, language and Sing aren't look settings). */
val lyricsSingLookKeys: List<String> = listOf(
    "lyrics_align", "lyrics_scale", "lyrics_blur", "lyrics_progress_line", "lyrics_style", "lyrics_blur_amount", "lyrics_spacing",
    "lyrics_others_alpha", "lyrics_sung_color", "lyrics_sung_custom", "lyrics_other_color", "lyrics_other_custom", "lyrics_weight",
    "lyrics_backing_size", "lyrics_shadow", "lyrics_uppercase", "lyrics_ahead", "lyrics_position", "lyrics_extra_size", "lyrics_extra_color",
    "lyrics_extra_italic", "lyrics_extra_place", "lyrics_background", "immersive_background", "lyrics_bg_color", "lyrics_bg_color2", "lyrics_everywhere",
    "lyrics_strip_align", "lyrics_strip_two_lines", "lyrics_gap_mark",
    // 1.8.0 lyrics look
    "lyrics_motion", "lyrics_word_by_word", "lyrics_card", "lyrics_halo", "lyrics_bold_lines",
)

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val lyricsSingOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val lyricsSingIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // Lyrics
            SettingEntry(page, tr(R.string.text), tr(R.string.focus_blur)),
            SettingEntry(page, tr(R.string.effects), tr(R.string.progress_line), tr(R.string.spectrum_line_underline_sung_lyric)),
            SettingEntry(page, tr(R.string.sources), tr(R.string.lyrics_source_button), tr(R.string.change_lyrics_source)),
            SettingEntry(page, tr(R.string.language), tr(R.string.translate_into), tr(R.string.translation_language) + " " + tr(R.string.translate_lyrics)),
            SettingEntry(page, tr(R.string.language), tr(R.string.romanize_lyrics), tr(R.string.romanization_romaji_pinyin_latin_letters_transli)),
            SettingEntry(page, tr(R.string.language), tr(R.string.romanize_my_own_language_too), tr(R.string.romanization_native_script), needs = romanize),
            SettingEntry(page, tr(R.string.language), tr(R.string.japanese_kanji_readings), tr(R.string.romaji_kanji_dictionary_download_kuromoji_japane)),
            SettingEntry(page, tr(R.string.sing), tr(R.string.sing_s_voice_separation), tr(R.string.karaoke_vocals_remove_instrumental_download_mode)),
            SettingEntry(page, tr(R.string.sing), tr(R.string.get_songs_ready_while_sing_is), tr(R.string.karaoke_prepare_ahead_background_preload_vocals)),
            SettingEntry(page, tr(R.string.sing), tr(R.string.room_for_sung_songs), tr(R.string.karaoke_storage_space)),
            SettingEntry(page, tr(R.string.sing), tr(R.string.clear_sung_songs), tr(R.string.karaoke_storage_delete)),
            SettingEntry(page, tr(R.string.text), tr(R.string.alignment), tr(R.string.left_centre_center_right)),
            SettingEntry(page, tr(R.string.text), tr(R.string.text_size), tr(R.string.lyrics_font_size)),
            // Lyrics, your way (1.7.5).
            // 1.8.0 (#253): the lyrics look in parts; "Lyrics style" is now where you start from.
            SettingEntry(page, tr(R.string.start_from), tr(R.string.lyrics_style), tr(R.string.prism_classic_karaoke_bold_card_glow) + " " + tr(R.string.lyrics_start_from_keywords)),
            SettingEntry(page, tr(R.string.animation), tr(R.string.animation), tr(R.string.lyrics_animation_keywords)),
            SettingEntry(page, tr(R.string.animation), tr(R.string.word_by_word_on_every_line), tr(R.string.lyrics_word_by_word_keywords)),
            SettingEntry(page, tr(R.string.text), tr(R.string.weight), tr(R.string.lyrics_bold_thin_light_heavy_font)),
            SettingEntry(page, tr(R.string.text), tr(R.string.line_spacing), tr(R.string.lyrics_gap_room_between_lines)),
            SettingEntry(page, tr(R.string.text), tr(R.string.other_lines), tr(R.string.lyrics_dim_faint_brightness_opacity_inactive)),
            SettingEntry(page, tr(R.string.text), tr(R.string.capitals), tr(R.string.uppercase_caps_all_capitals_lyrics)),
            SettingEntry(page, tr(R.string.text), tr(R.string.the_line_being_sung), tr(R.string.lyrics_color_sung_highlight_accent_spectrum)),
            SettingEntry(page, tr(R.string.text), tr(R.string.the_other_lines), tr(R.string.lyrics_color_dim_inactive)),
            SettingEntry(page, tr(R.string.text), tr(R.string.where_the_sung_line_sits), tr(R.string.lyrics_position_middle_centre_center_top)),
            SettingEntry(page, tr(R.string.text), tr(R.string.lines_to_come), tr(R.string.lyrics_ahead_next_lines_hide_preview)),
            SettingEntry(page, tr(R.string.effects), tr(R.string.glow), tr(R.string.lyrics_glow_keywords)),
            SettingEntry(page, tr(R.string.effects), tr(R.string.shadow), tr(R.string.lyrics_text_shadow_readable_photo)),
            SettingEntry(page, tr(R.string.effects), tr(R.string.background_vocals), tr(R.string.backing_vocals_size_ad_libs)),
            SettingEntry(page, tr(R.string.effects), tr(R.string.instrumental_breaks), tr(R.string.instrumental_gap_dots_music_note_break)),
            SettingEntry(page, tr(R.string.on_the_player), tr(R.string.the_line_on_the_player), tr(R.string.current_lyric_strip_center_centered_left)),
            SettingEntry(page, tr(R.string.on_the_player), tr(R.string.up_to_two_lines), tr(R.string.current_lyric_strip_wrap_two_lines)),
            SettingEntry(page, tr(R.string.translations_romanized_lines), tr(R.string.translation_look), tr(R.string.translation_romanization_size_color_italic_above)),
            SettingEntry(page, tr(R.string.background), tr(R.string.behind_the_lyrics), tr(R.string.lyrics_background_dark_blurred_animated_cover) + " " + tr(R.string.bold_card)),
            SettingEntry(page, tr(R.string.background), tr(R.string.immersive_mode_background), tr(R.string.immersive_background_cover_dark_blurred_color)),
            SettingEntry(page, tr(R.string.sources), tr(R.string.prefer_karaoke_synced_lyrics), tr(R.string.word_by_word_time_synced)),
            SettingEntry(page, tr(R.string.sources), tr(R.string.uncensor_lyrics), tr(R.string.explicit_swear_stars)),
            SettingEntry(page, tr(R.string.sources), tr(R.string.order), tr(R.string.lyrics_providers_sources_priority)),
            SettingEntry(page, tr(R.string.your_music_files), tr(R.string.read_lyrics_from_your_music_files), tr(R.string.flac_mp3_tags_embed)),
            SettingEntry(page, tr(R.string.your_music_files), tr(R.string.save_lyrics_into_your_music_files), tr(R.string.flac_mp3_tags_embed)),
            SettingEntry(page, tr(R.string.your_music_files), tr(R.string.keep_word_timings), tr(R.string.lyrics_file_words_keywords)),
            SettingEntry(page, tr(R.string.your_music_files), tr(R.string.include_the_translation), tr(R.string.lyrics_file_translation_keywords)),
        )
    }
}
