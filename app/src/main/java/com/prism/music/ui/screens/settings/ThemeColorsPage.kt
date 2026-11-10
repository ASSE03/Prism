package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.HomeWidgetsGroup
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.ResetPage
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.colorPage

private val page = SettingsPageId.THEME_COLORS

/**
 * Settings → Theme & colors (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([themeColorsIndex]), its "Reset this page" keys ([themeColorsLookKeys]), its line on the
 * root ([themeColorsValue]) and the old group names it answers to ([themeColorsOldGroups]).
 *
 * Interim body, inside the kit's scaffold until the page's rows are rebuilt with the kit: the old pages it takes over,
 * each whole and as it was, one under the other:
 * - Customize Prism's own page (`appearance()` in `OldTheme.kt`): the theme and Motion, which belong here; and with
 *   them, because that composable isn't cut up, its list of the look pages (each row still opens its page, by its old
 *   key), "Customize by tapping" and "Reset look & layout" (Looks' by the ledger);
 * - Color (`colorPage()` in `ColorSettings.kt`, with its own "Reset colors"). Its preview scrolls with the page here:
 *   it is no longer held under the bar;
 * - Glass & backgrounds ([GlassSettings] in `OldTheme.kt`) with that page's reset;
 * - Home-screen widgets ([HomeWidgetsGroup], from Icons & shapes).
 */
@Composable
fun ThemeColorsPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        OldItems { appearance() }
        OldItems { colorPage() }
        GlassSettings()
        // Named for what it resets (the old Glass & backgrounds page's settings), not for this whole page.
        ResetPage(tr(R.string.glass_backgrounds), themeColorsLookKeys)
        HomeWidgetsGroup()
    }
}

/** The root's line for Theme & colors: the glass and the theme ("Liquid glass · Dark"). */
fun themeColorsValue(s: AppSettings, dark: Boolean): String = listOf(
    when { s.liquidGlass -> tr(R.string.liquid_glass); s.frostedGlass -> tr(R.string.frosted_glass); else -> tr(R.string.solid_bars) },
    s.themeMode.label,
).joinToString(" · ")

/**
 * The look settings "Reset this page" puts back: for now the glass's (the old Glass & backgrounds page's). The colors
 * have their own reset in the old Color groups; Reduce motion is left as it is, as before.
 */
val themeColorsLookKeys: List<String> =
    listOf("liquid_glass", "glass_kind", "frosted_glass", "frosted_pages", "backdrops", "backdrop_dim", "backdrop_motion", "glass_blur", "glass_frost", "glass_more")

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val themeColorsOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val themeColorsIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // Appearance
            SettingEntry(page, tr(R.string.theme), tr(R.string.theme), tr(R.string.dark_light_system_mode_night)),
            SettingEntry(page, tr(R.string.theme), tr(R.string.pure_black), tr(R.string.amoled_oled)),
            SettingEntry(page, tr(R.string.customize_this), tr(R.string.customize_by_tapping), tr(R.string.edit_mode_customise_tap_long_press)),
            // Appearance → Color (1.7.2)
            SettingEntry(page, tr(R.string.spectrum), tr(R.string.spectrum), tr(R.string.split_light_gradient_rainbow_preset_sunset)),
            SettingEntry(page, tr(R.string.spectrum), tr(R.string.white), tr(R.string.white_light_black_monochrome_neutral_grey)),
            SettingEntry(page, tr(R.string.spectrum), tr(R.string.smooth_or_bands), tr(R.string.stripes_hard_spectrum_split_light_rainbow)),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.accent_from), tr(R.string.song_cover_wallpaper_your_pick_now)),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.accent_color), tr(R.string.color_accent_picker_wheel_hex_code)),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.exactly_as_picked), tr(R.string.accent_color_exact_shade_softened_tone)),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.how_it_s_used), tr(R.string.accent_color_exact_softened_as_picked),
                needs = Needs(tr(R.string.pick_your_pick_for_the_accent)) { it.colorSource == com.prism.music.data.prefs.ColorSource.CUSTOM }),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.which_of_its_colors), tr(R.string.song_cover_vibrant_muted_dominant_swatch),
                needs = Needs(tr(R.string.pick_song_s_cover_for_the)) { it.colorSource == com.prism.music.data.prefs.ColorSource.ARTWORK }),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.light_mode_accent), tr(R.string.accent_color_light_theme) + " " + tr(R.string.accent_color_light_theme_another) + " " + tr(R.string.different_color_for_light_mode)),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.cover_color), tr(R.string.song_cover_now_playing_artwork_accent),
                needs = Needs(tr(R.string.pick_song_s_cover_for_the)) { it.colorSource == com.prism.music.data.prefs.ColorSource.ARTWORK }),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.always_colorful), tr(R.string.song_cover_now_playing_artwork_accent_2),
                needs = Needs(tr(R.string.pick_song_s_cover_for_the)) { it.colorSource == com.prism.music.data.prefs.ColorSource.ARTWORK }),
            SettingEntry(page, tr(R.string.accent_color), tr(R.string.between_songs), tr(R.string.song_cover_now_playing_artwork_accent_3),
                needs = Needs(tr(R.string.pick_song_s_cover_for_the)) { it.colorSource == com.prism.music.data.prefs.ColorSource.ARTWORK }),
            SettingEntry(page, tr(R.string.page_background), tr(R.string.page_background), tr(R.string.tint_surface_page_neutral_plain_color)),
            SettingEntry(page, tr(R.string.page_background), tr(R.string.background_color), tr(R.string.tint_surface_page_color),
                needs = Needs(tr(R.string.pick_your_color_under_page_background)) { it.surfaceTint == com.prism.music.data.prefs.SurfaceTint.CUSTOM }),
            SettingEntry(page, tr(R.string.page_background), tr(R.string.tint_strength), tr(R.string.surface_page_tint),
                needs = Needs(tr(R.string.pick_accent_tint_or_your_color)) { it.surfaceTint == com.prism.music.data.prefs.SurfaceTint.ACCENT || it.surfaceTint == com.prism.music.data.prefs.SurfaceTint.CUSTOM }),
            SettingEntry(page, tr(R.string.text_contrast), tr(R.string.text_contrast), tr(R.string.high_contrast_accessibility_readability_legible)),
            SettingEntry(page, tr(R.string.artist_album_pages), tr(R.string.photo_s_color), tr(R.string.artist_album_playlist_page_color_color)),
            SettingEntry(page, tr(R.string.artist_album_pages), tr(R.string.playing_song_in_the_page_s_color), tr(R.string.playing_indicator_bars_now_playing_album_color)),
            SettingEntry(page, tr(R.string.home_screen_widgets), tr(R.string.home_screen_widgets), tr(R.string.widget_background_transparent_see_through_color)),
            SettingEntry(page, tr(R.string.backgrounds), tr(R.string.screen_backgrounds), tr(R.string.wallpaper_photo_pattern_design)),
            SettingEntry(page, tr(R.string.backgrounds), tr(R.string.now_playing_background_fades), tr(R.string.now_playing_background_fades_keywords)),
            // 1.8.0: one Glass choice; Motion moved to the Customize Prism page. Old titles stay as search words.
            SettingEntry(page, tr(R.string.glass), tr(R.string.glass), tr(R.string.blur_transparent) + " " + tr(R.string.liquid_glass) + " " + tr(R.string.frosted_glass) + " " + tr(R.string.glass_style)),
            SettingEntry(page, tr(R.string.glass), tr(R.string.frosted_pages), tr(R.string.blur_artist_album_playlist)),
            SettingEntry(page, tr(R.string.glass), tr(R.string.glass_blur), tr(R.string.glass_blur_info), needs = turnOn(tr(R.string.frosted_glass)) { (it.frostedGlass || it.liquidGlass) && !(it.liquidGlass && it.glassKind == com.prism.music.data.prefs.GlassKind.CLEAR) }),
            SettingEntry(page, tr(R.string.glass), tr(R.string.glass_frost), tr(R.string.glass_frost_info), needs = turnOn(tr(R.string.frosted_glass)) { it.frostedGlass || it.liquidGlass }),
            SettingEntry(page, tr(R.string.glass), tr(R.string.glass_on_more_of_the_screen), tr(R.string.glass_on_more_of_the_screen_detail)),
            SettingEntry(page, tr(R.string.motion), tr(R.string.motion)),
            SettingEntry(page, tr(R.string.motion), tr(R.string.reduce_motion), tr(R.string.animations_accessibility)),
            SettingEntry(page, tr(R.string.motion), tr(R.string.changing_screens), tr(R.string.fades_between_screens_keywords) + " " + tr(R.string.slide_between_screens_keywords) + " " + tr(R.string.fades_between_screens) + " " + tr(R.string.slide_between_screens)),
        )
    }
}
