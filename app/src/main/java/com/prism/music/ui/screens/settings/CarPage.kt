package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds

private val page = SettingsPageId.CAR

/**
 * Settings → Car (1.8.4's map, plan §2.3). This file is the only place for the page: its composable, its lines in
 * Settings' search ([carIndex]), its "Reset this page" keys ([carLookKeys]), its line on the root ([carValue]) and the
 * old group names it answers to ([carOldGroups]).
 *
 * Interim body: the old Devices & connections page, whole and as it was ([DevicesPage] in `OldCar.kt`), inside the
 * kit's scaffold, until the page's rows are rebuilt with the kit. Its Android Auto group is this page's. With it,
 * because that composable isn't cut up: its Connections list (each row still opens its page, by its old key) and
 * Sharing and links, which is Accounts & sharing's and is listed and drawn there too.
 */
@Composable
fun CarPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { DevicesPage() }
}

/** The root's line for Car: lyrics in the car and their style when on, else just "Android Auto". */
fun carValue(s: AppSettings, dark: Boolean): String =
    if (s.carLyrics) tr(R.string.lyrics_in_the_car) + " · " + s.carLyricsStyle.label else tr(R.string.android_auto)

/** The look settings "Reset this page" puts back: none (the car isn't part of a look). */
val carLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val carOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val carIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.android_auto), tr(R.string.lyrics_in_the_car), tr(R.string.android_auto_2)),
            SettingEntry(page, tr(R.string.android_auto), tr(R.string.style), tr(R.string.android_auto_car), needs = carLyrics),
            SettingEntry(page, tr(R.string.android_auto), tr(R.string.keep_the_song_title), tr(R.string.android_auto_car), needs = carLyrics),
            SettingEntry(page, tr(R.string.android_auto), tr(R.string.on_this_phone_too), tr(R.string.lock_screen_notification), needs = carLyrics),
            SettingEntry(page, tr(R.string.android_auto), tr(R.string.covers_in_a_grid), tr(R.string.android_auto_car_home_tiles_grid)),
            SettingEntry(page, tr(R.string.android_auto), tr(R.string.buttons_in_the_car), tr(R.string.android_auto_car_buttons_shuffle_like)),
        )
    }
}
