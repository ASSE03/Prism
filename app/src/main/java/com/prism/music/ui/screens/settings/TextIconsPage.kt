package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.ResetPage
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.theme.IconFill
import com.prism.music.ui.theme.IconPack
import com.prism.music.ui.theme.IconStyle
import com.prism.music.ui.theme.IconWeight
import com.prism.music.ui.theme.LocalContainer

private val page = SettingsPageId.TEXT_ICONS

/**
 * Settings → Text & icons (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([textIconsIndex]), its "Reset this page" keys ([textIconsLookKeys]), its line on the root
 * ([textIconsValue]) and the old group names it answers to ([textIconsOldGroups]).
 *
 * Interim body, inside the kit's scaffold until the page's rows are rebuilt with the kit: the old Text page
 * ([TypeSettings]) and the old Icons & shapes page ([IconSettings], [AppIconSettings], [ShapeSettings]), all in
 * `OldTextIcons.kt`, as they were. With them, because those composables aren't cut up: the lyrics font (Lyrics & Sing's
 * by the ledger) and the player's cover shape (Player's). Home-screen widgets went to Theme & colors.
 */
@Composable
fun TextIconsPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    val c = LocalContainer.current
    val context = LocalContext.current
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        TypeSettings()
        IconSettings()
        AppIconSettings()
        ShapeSettings()
        ResetPage(page.title, textIconsLookKeys) {
            // The icon style lives apart from the settings.
            IconStyle.save(context, IconWeight.REGULAR, true)
            IconStyle.savePack(context, IconPack.PRISM)
            IconStyle.saveLikeStar(context, false)
            IconStyle.saveFill(context, IconFill.OUTLINE)
            c.looks.iconsChanged()
        }
    }
}

/** The root's line for Text & icons: the font, and the text size when it isn't the usual. */
fun textIconsValue(s: AppSettings, dark: Boolean): String = SettingsSection.TEXT.summary(s, dark)

/** The look settings "Reset this page" puts back (the icon style, kept apart from the settings, goes back with them). */
val textIconsLookKeys: List<String> = listOf(
    // The old Text page's.
    "font_choice", "lyrics_font", "text_scale", "type_weight", "type_spacing", "title_font",
    // The old Icons & shapes page's.
    "corners", "corner_scale", "cover_shape_songs", "cover_shape_albums", "cover_shape_artists", "cover_shape_player",
    "button_shape", "play_outlined",
)

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val textIconsOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val textIconsIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.text), tr(R.string.font), tr(R.string.typeface_custom_ttf_otf_outfit_prism)),
            SettingEntry(page, tr(R.string.type), tr(R.string.type), tr(R.string.classic_1_6_bold_headings_titles), shown = tr(R.string.classic_type)),
            SettingEntry(page, tr(R.string.weight_and_spacing), tr(R.string.weight_and_spacing), tr(R.string.bold_light_heavy_text_weight)),
            SettingEntry(page, tr(R.string.weight_and_spacing), tr(R.string.letter_spacing), tr(R.string.tracking_wide_tight_letters)),
            SettingEntry(page, tr(R.string.titles), tr(R.string.titles), tr(R.string.title_font_headings_different_font)),
            SettingEntry(page, tr(R.string.text), tr(R.string.remove_your_font)),
            SettingEntry(page, tr(R.string.text), tr(R.string.installed_fonts), tr(R.string.installed_fonts_keywords)),
            SettingEntry(page, tr(R.string.text), tr(R.string.installed_fonts_for_lyrics), tr(R.string.installed_fonts_keywords)),
            SettingEntry(page, tr(R.string.text), tr(R.string.lyrics_font), "typeface"),
            SettingEntry(page, tr(R.string.text), tr(R.string.lyrics_font_file)),
            SettingEntry(page, tr(R.string.text), tr(R.string.remove_the_lyrics_font_file)),
            SettingEntry(page, tr(R.string.text), tr(R.string.text_size), tr(R.string.font_size_scale_bigger_smaller)),
            SettingEntry(page, tr(R.string.icons), tr(R.string.pack), tr(R.string.icon_style_cupertino_apple)),
            SettingEntry(page, tr(R.string.icons), tr(R.string.weight), tr(R.string.icon_thin_bold)),
            SettingEntry(page, tr(R.string.icons), tr(R.string.rounded), tr(R.string.icon_corners)),
            SettingEntry(page, tr(R.string.icons), tr(R.string.likes_as_stars), tr(R.string.heart_star_like_favourite_favorite_icon)),
            SettingEntry(page, tr(R.string.icons), tr(R.string.restart_prism_to_see_them)),
            SettingEntry(page, tr(R.string.app_icon), tr(R.string.app_icon), tr(R.string.launcher_home_screen_icon_logo)),
            SettingEntry(page, tr(R.string.shape), tr(R.string.corners), tr(R.string.rounded_square_shape_radius_slider)),
            SettingEntry(page, tr(R.string.covers_shapes), tr(R.string.covers_shapes), tr(R.string.cover_shape_circle_squircle_flower_cookie)),
            SettingEntry(page, tr(R.string.buttons), tr(R.string.buttons), tr(R.string.button_shape_pill_rounded_square)),
            SettingEntry(page, tr(R.string.buttons), tr(R.string.play_on_album_and_playlist_pages), tr(R.string.play_button_outlined_filled)),
            SettingEntry(page, tr(R.string.buttons), tr(R.string.switches_sliders_and_buttons), tr(R.string.classic_controls_1_6_material_switch)),
            SettingEntry(page, tr(R.string.covers_shapes), tr(R.string.square_video_covers), tr(R.string.video_thumbnail_crop_black_bars_letterbox)),
        )
    }
}
