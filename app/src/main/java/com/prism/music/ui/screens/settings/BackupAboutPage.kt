package com.prism.music.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import com.prism.music.BuildConfig
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.screens.BackupGroup
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.LicensesPage
import com.prism.music.ui.screens.MoreOptions
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SOURCE_URL
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons

private val page = SettingsPageId.BACKUP_ABOUT

/**
 * Settings → Backup & about (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its
 * lines in Settings' search ([backupAboutIndex]), its "Reset this page" keys ([backupAboutLookKeys]), its line on the
 * root ([backupAboutValue]) and the old group names it answers to ([backupAboutOldGroups]). The licenses' screen,
 * opened from its About group, is here too ([LicensesScreen]).
 *
 * Interim body, inside the kit's scaffold until the page's rows are rebuilt with the kit: the old Backup & about page
 * as it was: Backup ([BackupGroup] in `BackupUi.kt`), About ([AboutGroup], which was written inside `SettingsScreen.kt`'s
 * page and is here unchanged) and Diagnostics in its fold ([DiagnosticsGroup] in `OldBackupAbout.kt`).
 */
@Composable
fun BackupAboutPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) {
        BackupGroup()
        AboutGroup()
        // 1.8.0: only for sending a bug report, so folded away.
        MoreOptions(listOf(tr(R.string.diagnostics))) { DiagnosticsGroup() }
    }
}

/** About: Prism's version and source, updates, the tour, the licenses. */
@Composable
private fun AboutGroup() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    Group(tr(R.string.about_3)) {
        val uri = LocalUriHandler.current
        Item(tr(R.string.prism, BuildConfig.VERSION_NAME), tr(R.string.unofficial_not_affiliated_with_google_or), onClick = { runCatching { uri.openUri(SOURCE_URL) } }) {
            Icon(PrismIcons.OpenInNew, tr(R.string.open_on_github), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Item(tr(R.string.your_data_stays_here), tr(R.string.prism_has_no_server), info = tr(R.string.your_history_replay_and_settings_live), onClick = {})
        CheckForUpdates()
        Item(tr(R.string.take_the_tour), tr(R.string.a_minute_s_walk_through_prism), onClick = { c.tourRequests.tryEmit(Unit) }) { Chevron() }
        Item(tr(R.string.open_source_licenses), tr(R.string.gpl_3_0_or_later_and), onClick = { nav.go(Routes.settings(SettingsSection.LICENSES.key)) }) { Chevron() }
        // Debug builds only, so it's left out of Settings search (named title: SettingsIndexTest skips it).
        if (BuildConfig.DEBUG) Item(
            title = tr(R.string.design_gallery), subtitle = tr(R.string.every_component_of_the_redesign_in), onClick = { nav.go(Routes.DESIGN_GALLERY) },
        ) { Chevron() }
    }
}

/**
 * The open-source licenses (`settings/licenses`, the old key): a screen of this page, not a page of the map, opened
 * from About. [LicensesPage] as it was, inside the kit's scaffold.
 */
@Composable
fun LicensesScreen(bottomPadding: Dp, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    SettingsPageScaffold(tr(R.string.open_source_licenses), bottomPadding, showBack = showBack, onSearch = onSearch) {
        item("licenses") { LicensesPage() }
    }
}

/** The root's line for Backup & about: Prism's version, and what the page holds. */
fun backupAboutValue(s: AppSettings, dark: Boolean): String = SettingsSection.ABOUT.summary(s, dark)

/** The look settings "Reset this page" puts back: none. */
val backupAboutLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val backupAboutOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val backupAboutIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // About
            SettingEntry(page, tr(R.string.backup), tr(R.string.back_up), tr(R.string.backup_export_save_move_new_phone)),
            SettingEntry(page, tr(R.string.backup), tr(R.string.restore_from_a_backup), tr(R.string.import_move_new_phone)),
            SettingEntry(page, tr(R.string.about_3), tr(R.string.app_name), tr(R.string.version_github)),
            SettingEntry(page, tr(R.string.about_3), tr(R.string.your_data_stays_here), "privacy"),
            SettingEntry(page, tr(R.string.about_3), tr(R.string.check_for_updates), tr(R.string.update_new_version)),
            SettingEntry(page, tr(R.string.about_3), tr(R.string.take_the_tour), tr(R.string.tutorial_walkthrough_help_guide_what_s)),
            SettingEntry(page, tr(R.string.about_3), tr(R.string.open_source_licenses), tr(R.string.license_gpl)),
            SettingEntry(page, tr(R.string.diagnostics), tr(R.string.playback_diagnostics), tr(R.string.stutter_judder_skip_glitch_lag_bluetooth)),
            SettingEntry(page, tr(R.string.diagnostics), tr(R.string.share_diagnostics), tr(R.string.log_send_report)),
            SettingEntry(page, tr(R.string.diagnostics), tr(R.string.clear_diagnostics), tr(R.string.log_reset)),
            SettingEntry(page, tr(R.string.diagnostics), tr(R.string.last_crash), tr(R.string.crash_closed_unexpectedly_error_report_stack)),
            SettingEntry(page, tr(R.string.diagnostics), tr(R.string.forget_crashes), tr(R.string.crash_delete_clear)),
        )
    }
}
