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
import com.prism.music.ui.screens.looksPage

private val page = SettingsPageId.LOOKS

/**
 * Settings → Looks (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its lines in
 * Settings' search ([looksIndex]), its "Reset this page" keys ([looksLookKeys]), its line on the root ([looksValue])
 * and the old group names it answers to ([looksOldGroups]).
 *
 * Interim body: the old Looks page as it was (`looksPage()` in `LookSettings.kt`), then "Reset look & layout"
 * ([ResetLook], which the plan puts under this page's ⋯ as "Reset everything"), inside the kit's scaffold, until the
 * page's rows are rebuilt with the kit. "Customize by tapping", which the ledger sends here, is still drawn by the old
 * Customize Prism page (on Theme & colors) and listed there.
 */
@Composable
fun LooksPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        OldItems { looksPage() }
        ResetLook()
    }
}

/** The root's line for Looks. */
fun looksValue(s: AppSettings, dark: Boolean): String = SettingsSection.LOOKS.summary(s, dark)

/** The look settings "Reset this page" puts back: none (a Look is every look setting; "Reset look & layout" is its own). */
val looksLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val looksOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val looksIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // Appearance → Looks (1.7.3)
            SettingEntry(page, tr(R.string.prism_s_looks), tr(R.string.prism_s_looks), tr(R.string.theme_preset_paper_midnight_neon_soft)),
            SettingEntry(page, tr(R.string.your_looks), tr(R.string.your_looks), tr(R.string.saved_theme_preset_save_rename_duplicate)),
            SettingEntry(page, tr(R.string.from_the_community), tr(R.string.browse_community_looks), tr(R.string.gallery_github_themes_shared_download_others)),
            SettingEntry(page, tr(R.string.from_the_community), tr(R.string.couldn_t_load_the_gallery), tr(R.string.gallery_offline)),
            SettingEntry(page, tr(R.string.from_the_community), tr(R.string.refresh_the_list), tr(R.string.gallery_github_themes)),
            SettingEntry(page, tr(R.string.from_the_community), tr(R.string.add_your_look), tr(R.string.gallery_github_pull_request_contribute_share)),
            SettingEntry(page, tr(R.string.get_a_look), tr(R.string.scan_a_look_s_qr_code), tr(R.string.camera_theme_share_friend)),
            SettingEntry(page, tr(R.string.get_a_look), tr(R.string.paste_a_look_code), tr(R.string.clipboard_theme_share)),
            SettingEntry(page, tr(R.string.get_a_look), tr(R.string.open_a_look_file), tr(R.string.prismlook_file_import_theme)),
            SettingEntry(page, tr(R.string.day_and_night), tr(R.string.day_and_night_looks), tr(R.string.schedule_sunset_sunrise_automatic_dark_mode)),
            SettingEntry(page, tr(R.string.day_and_night), tr(R.string.by_day), tr(R.string.schedule_day_look_morning)),
            SettingEntry(page, tr(R.string.day_and_night), tr(R.string.by_night), tr(R.string.schedule_night_look_evening_dark)),
            SettingEntry(page, tr(R.string.day_and_night), tr(R.string.switch_text), tr(R.string.schedule_sunrise_sunset_set_times)),
            SettingEntry(page, tr(R.string.day_and_night), tr(R.string.day_starts_at), tr(R.string.schedule_time)),
            SettingEntry(page, tr(R.string.day_and_night), tr(R.string.night_starts_at), tr(R.string.schedule_time)),
            SettingEntry(page, tr(R.string.history), tr(R.string.history), tr(R.string.undo_go_back_changes_revert)),
            SettingEntry(page, tr(R.string.history), tr(R.string.clear_history), tr(R.string.delete_forget_look_changes_history)),
            SettingEntry(page, tr(R.string.your_looks), tr(R.string.include_its_photos), tr(R.string.share_file)),
            SettingEntry(page, tr(R.string.your_looks), tr(R.string.include_its_font), tr(R.string.share_file)),
        )
    }
}
