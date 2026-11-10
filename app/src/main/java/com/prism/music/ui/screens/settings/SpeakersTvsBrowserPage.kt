package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.ConnectSettings
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SettingsSection

private val page = SettingsPageId.SPEAKERS_TVS_BROWSER

/**
 * Settings → Speakers, TVs & browser (1.8.4's map, plan §2.3). This file is the only place for the page: its
 * composable, its lines in Settings' search ([speakersTvsBrowserIndex]), its "Reset this page" keys
 * ([speakersTvsBrowserLookKeys]), its line on the root ([speakersTvsBrowserValue]) and the old group names it answers
 * to ([speakersTvsBrowserOldGroups]).
 *
 * Interim body: the old Speakers, TVs & browser page as it was ([ConnectSettings] in `ConnectSettings.kt`), inside the
 * kit's scaffold, until the page's rows are rebuilt with the kit.
 */
@Composable
fun SpeakersTvsBrowserPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { ConnectSettings() }
}

/** The root's line for Speakers, TVs & browser: what's on of the browser remote and handoff. */
fun speakersTvsBrowserValue(s: AppSettings, dark: Boolean): String = SettingsSection.CONNECT.summary(s, dark)

/** The look settings "Reset this page" puts back: none (connections aren't part of a look). */
val speakersTvsBrowserLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val speakersTvsBrowserOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val speakersTvsBrowserIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // Prism Connect
            SettingEntry(page, tr(R.string.speakers_tvs), tr(R.string.play_on_a_speaker_or_tv), tr(R.string.cast_casting_google_cast_google_tv)),
            SettingEntry(page, tr(R.string.prism_on_a_tv), tr(R.string.share_prism_with_a_tv), tr(R.string.tv_install_keywords)),
            SettingEntry(page, tr(R.string.prism_on_a_tv), tr(R.string.downloader_code), tr(R.string.tv_install_keywords)),
            SettingEntry(page, tr(R.string.control_from_a_browser), tr(R.string.control_from_a_browser), tr(R.string.web_remote_computer_laptop_tablet_browser)),
            SettingEntry(page, tr(R.string.control_from_a_browser), tr(R.string.address), tr(R.string.web_remote_url_link_ip), needs = webRemote),
            SettingEntry(page, tr(R.string.control_from_a_browser), tr(R.string.pairing_code), tr(R.string.web_remote_pair_code_browser), shown = tr(R.string.browser_pairing_code), needs = webRemote),
            SettingEntry(page, tr(R.string.control_from_a_browser), tr(R.string.unpair_all_browsers), tr(R.string.web_remote_forget_browsers), needs = webRemote),
            SettingEntry(page, tr(R.string.handoff), tr(R.string.available_for_handoff), tr(R.string.move_playback_another_device_tablet_phone)),
            SettingEntry(page, tr(R.string.handoff), tr(R.string.pairing_code), tr(R.string.handoff_pair_code), shown = tr(R.string.handoff_pairing_code)),
        )
    }
}
