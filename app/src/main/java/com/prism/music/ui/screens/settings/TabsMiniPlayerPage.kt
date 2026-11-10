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

private val page = SettingsPageId.TABS_MINI_PLAYER

/**
 * Settings → Tabs & mini player (1.8.4's map, plan §2.3). This file is the only place for the page: its composable,
 * its lines in Settings' search ([tabsMiniPlayerIndex]), its "Reset this page" keys ([tabsMiniPlayerLookKeys]), its
 * line on the root ([tabsMiniPlayerValue]) and the old group names it answers to ([tabsMiniPlayerOldGroups]).
 *
 * Interim body: the old Tabs & mini player page as it was (`layoutAndNavigation()` in `OldTabs.kt`, which ends with
 * its own "Reset this page"), inside the kit's scaffold, until the page's rows are rebuilt with the kit. "Swipe between
 * tabs" (Gestures & buttons' by the plan) is still in its Navigation bar group.
 */
@Composable
fun TabsMiniPlayerPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { OldItems { layoutAndNavigation() } }
}

/** The root's line for Tabs & mini player: the bar's style, how many tabs, the mini player's style. */
fun tabsMiniPlayerValue(s: AppSettings, dark: Boolean): String = SettingsSection.INTERFACE.summary(s, dark)

/** The look settings "Reset this page" puts back. */
val tabsMiniPlayerLookKeys: List<String> = listOf(
    // 1.8.0: the tab you're on's tint is with the tab bar now (from Icons & shapes).
    "tab_icon_tint",
    "nav_style", "nav_labels", "nav_hide_on_scroll", "nav_tabs", "start_tab", "mini_player_style", "mini_skip_buttons",
    "mini_prev_button", "mini_play_button", "mini_progress", "mini_progress_color", "mini_fill_strength", "ui_density", "title_size", "card_size", "hide_status_bar", "hide_status_bar_player",
    "tab_names", "nav_bar_size", "nav_bar_fill", "mini_player_joined",
)

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val tabsMiniPlayerOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val tabsMiniPlayerIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.the_tab_you_re_on), tr(R.string.tab_icon_color_tint_accent_light)),
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.tabs_on_a_tablet), tr(R.string.down_the_side) + " " + tr(R.string.at_the_bottom)),
            // Layout & navigation
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.style), tr(R.string.tab_bar_bottom_bar)),
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.labels), tr(R.string.tab_bar_names),
                needs = Needs(tr(R.string.the_minimal_bar_has_no_labels)) { it.navStyle != com.prism.music.data.prefs.NavBarStyle.MINIMAL }),
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.shrink_while_scrolling), tr(R.string.tab_bar_hide)),
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.height), tr(R.string.tab_bar_size_tall_compact)),
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.background), tr(R.string.tab_bar_see_through_solid_opacity)),
            SettingEntry(page, tr(R.string.navigation_bar), tr(R.string.swipe_between_tabs), tr(R.string.tab_bar_swipe_gesture_sideways)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.joined_to_the_tab_bar), tr(R.string.mini_player_merged_tab_bar_one)),
            SettingEntry(page, tr(R.string.tabs), tr(R.string.tabs), tr(R.string.tab_bar_order_home_search_library)),
            SettingEntry(page, tr(R.string.tabs), tr(R.string.open_prism_on), tr(R.string.start_tab_first_screen)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.mini_player), tr(R.string.style_card_slim_pill)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.previous_button), tr(R.string.back_skip)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.play_pause_button), tr(R.string.play_pause)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.next_button), tr(R.string.skip_forward)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.progress)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.progress_bar_color), tr(R.string.scrubber_color_accent_white_cover_spectrum)),
            SettingEntry(page, tr(R.string.mini_player), tr(R.string.fill_strength)),
            SettingEntry(page, tr(R.string.pages), tr(R.string.spacing), tr(R.string.density_compact)),
            SettingEntry(page, tr(R.string.pages), tr(R.string.page_titles)),
            SettingEntry(page, tr(R.string.pages), tr(R.string.album_playlist_cards)),
            SettingEntry(page, tr(R.string.screen), tr(R.string.hide_the_status_bar), tr(R.string.status_bar_clock_notifications_full_screen)),
            SettingEntry(page, tr(R.string.screen), tr(R.string.hide_status_bar_on_the_player), tr(R.string.status_bar_clock_notifications_full_screen)),
        )
    }
}
